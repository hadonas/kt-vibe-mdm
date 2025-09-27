package com.company.app.maintenance.service;

import com.company.app.document.entity.DocumentEntity;
import com.company.app.document.repository.DocumentRepository;
import com.company.app.document.repository.DocumentChunkRepository;
import com.company.app.file.service.LocalFileStorageService;
import com.company.app.search.service.ElasticsearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 고아 문서/청크 정리 서비스
 * MongoDB 문서, Elasticsearch 청크, 파일시스템 간 일관성 확인 및 정리
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrphanCleanupService {
    
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final ElasticsearchService elasticsearchService;
    private final LocalFileStorageService localFileStorageService;
    
    public record OrphanReport(
        List<String> orphanedESChunks,     // ES에만 있는 청크들
        List<String> orphanedMongoChunks,  // Mongo에만 있는 청크들
        List<String> missingFiles,         // 파일이 없는 문서들
        List<String> orphanedFiles,        // 문서가 없는 파일들
        int totalESChunks,
        int totalMongoChunks,
        int totalDocuments,
        int totalFiles
    ) {}
    
    public record CleanupResult(
        int deletedESChunks,
        int deletedMongoChunks,
        int deletedFiles,
        List<String> errors
    ) {}
    
    /**
     * 고아 데이터 분석
     */
    public OrphanReport analyzeOrphans() {
        try {
            log.info("고아 데이터 분석 시작");
            
            // 1. 모든 MongoDB 문서 ID 수집
            List<String> allDocumentIds = documentRepository.findAll()
                .stream()
                .map(DocumentEntity::getId)
                .collect(Collectors.toList());
            
            // 2. 모든 MongoDB 청크의 documentId 수집
            Set<String> mongoChunkDocIds = chunkRepository.findAll()
                .stream()
                .map(chunk -> chunk.getDocumentId())
                .collect(Collectors.toSet());
            
            // 3. Elasticsearch에서 모든 청크의 documentId 수집
            Set<String> esChunkDocIds = new HashSet<>();
            try {
                // ES 청크 조회 (페이징으로 처리)
                int page = 0;
                int size = 1000;
                boolean hasMore = true;
                
                while (hasMore) {
                    var searchResult = elasticsearchService.searchChunks("*", page, size);
                    if (searchResult != null && searchResult.getHits() != null) {
                        for (var hit : searchResult.getHits()) {
                            if (hit.getSource() != null && hit.getSource().containsKey("documentId")) {
                                esChunkDocIds.add(hit.getSource().get("documentId").toString());
                            }
                        }
                        hasMore = searchResult.getHits().size() == size;
                        page++;
                    } else {
                        hasMore = false;
                    }
                }
            } catch (Exception e) {
                log.warn("ES 청크 조회 중 오류: {}", e.getMessage());
            }
            
            // 4. 파일 시스템 분석
            List<String> missingFiles = new ArrayList<>();
            List<String> allFiles = localFileStorageService.getAllStoredFiles();
            
            for (DocumentEntity doc : documentRepository.findAll()) {
                if (doc.getSerial() != null && doc.getSerial().getFull() != null) {
                    String expectedFile = doc.getSerial().getFull();
                    boolean fileExists = allFiles.stream()
                        .anyMatch(f -> f.contains(expectedFile));
                    if (!fileExists) {
                        missingFiles.add(doc.getId() + " (" + expectedFile + ")");
                    }
                }
            }
            
            // 5. 고아 청크 식별
            Set<String> validDocIds = new HashSet<>(allDocumentIds);
            
            List<String> orphanedESChunks = esChunkDocIds.stream()
                .filter(id -> !validDocIds.contains(id))
                .collect(Collectors.toList());
                
            List<String> orphanedMongoChunks = mongoChunkDocIds.stream()
                .filter(id -> !validDocIds.contains(id))
                .collect(Collectors.toList());
            
            // 6. 고아 파일 식별 (간단히 문서 없는 파일들)
            List<String> orphanedFiles = new ArrayList<>();
            Set<String> expectedFilePatterns = documentRepository.findAll()
                .stream()
                .filter(doc -> doc.getSerial() != null && doc.getSerial().getFull() != null)
                .map(doc -> doc.getSerial().getFull())
                .collect(Collectors.toSet());
                
            for (String file : allFiles) {
                boolean hasMatchingDoc = expectedFilePatterns.stream()
                    .anyMatch(pattern -> file.contains(pattern));
                if (!hasMatchingDoc) {
                    orphanedFiles.add(file);
                }
            }
            
            OrphanReport report = new OrphanReport(
                orphanedESChunks,
                orphanedMongoChunks,
                missingFiles,
                orphanedFiles,
                esChunkDocIds.size(),
                mongoChunkDocIds.size(),
                allDocumentIds.size(),
                allFiles.size()
            );
            
            log.info("고아 데이터 분석 완료: ES고아청크={}, Mongo고아청크={}, 누락파일={}, 고아파일={}", 
                orphanedESChunks.size(), orphanedMongoChunks.size(), missingFiles.size(), orphanedFiles.size());
                
            return report;
            
        } catch (Exception e) {
            log.error("고아 데이터 분석 중 오류", e);
            return new OrphanReport(List.of(), List.of(), List.of(), List.of(), 0, 0, 0, 0);
        }
    }
    
    /**
     * 고아 데이터 정리 실행
     */
    public CleanupResult cleanupOrphans(OrphanReport report) {
        List<String> errors = new ArrayList<>();
        int deletedESChunks = 0;
        int deletedMongoChunks = 0;
        int deletedFiles = 0;
        
        try {
            log.info("고아 데이터 정리 시작");
            
            // 1. ES 고아 청크 삭제
            for (String orphanDocId : report.orphanedESChunks()) {
                try {
                    elasticsearchService.deleteChunksByDocumentId(orphanDocId);
                    deletedESChunks++;
                } catch (Exception e) {
                    errors.add("ES 청크 삭제 실패: " + orphanDocId + " - " + e.getMessage());
                }
            }
            
            // 2. MongoDB 고아 청크 삭제
            for (String orphanDocId : report.orphanedMongoChunks()) {
                try {
                    chunkRepository.deleteByDocumentId(orphanDocId);
                    deletedMongoChunks++;
                } catch (Exception e) {
                    errors.add("Mongo 청크 삭제 실패: " + orphanDocId + " - " + e.getMessage());
                }
            }
            
            // 3. 고아 파일 삭제 (신중하게 - 백업 권장)
            for (String orphanFile : report.orphanedFiles()) {
                try {
                    if (localFileStorageService.deleteFileByPath(orphanFile)) {
                        deletedFiles++;
                    }
                } catch (Exception e) {
                    errors.add("파일 삭제 실패: " + orphanFile + " - " + e.getMessage());
                }
            }
            
            log.info("고아 데이터 정리 완료: ES청크={}개, Mongo청크={}개, 파일={}개, 오류={}개", 
                deletedESChunks, deletedMongoChunks, deletedFiles, errors.size());
                
        } catch (Exception e) {
            log.error("고아 데이터 정리 중 오류", e);
            errors.add("전체 정리 과정 오류: " + e.getMessage());
        }
        
        return new CleanupResult(deletedESChunks, deletedMongoChunks, deletedFiles, errors);
    }
}
