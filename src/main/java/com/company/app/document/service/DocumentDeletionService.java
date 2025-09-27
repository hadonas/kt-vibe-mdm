package com.company.app.document.service;

import com.company.app.catalog.service.CategoryUsageService;
import com.company.app.common.dto.Category;
import com.company.app.document.entity.DocumentEntity;
import com.company.app.document.repository.DocumentRepository;
import com.company.app.document.repository.DocumentChunkRepository;
import com.company.app.file.service.LocalFileStorageService;
import com.company.app.search.service.ElasticsearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 통합 문서 삭제 서비스: ES, Mongo 문서 & 청크, 파일, 카테고리 카운트 감소.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentDeletionService {
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final ElasticsearchIndexService elasticsearchIndexService;
    private final LocalFileStorageService localFileStorageService;
    private final CategoryUsageService categoryUsageService;

    public record DeletionResult(String documentId, boolean esDoc, boolean esChunks, boolean mongoChunks,
                                 boolean mongoDoc, boolean fileDeleted, String message) {}

    @Transactional
    public DeletionResult deleteDocument(DocumentEntity document) {
        String id = document.getId();
        boolean esDoc=false, esChunks=false, mongoChunks=false, mongoDoc=false, fileDeleted=false;
        try {
            // ES chunks
            try { elasticsearchIndexService.deleteChunksByDocumentId(id); esChunks=true; } catch (Exception e) { log.warn("ES 청크 삭제 실패: {}", id, e); }
            // Mongo chunks
            try { chunkRepository.deleteByDocumentId(id); mongoChunks=true; } catch (Exception e) { log.warn("Mongo 청크 삭제 실패: {}", id, e); }
            // ES doc
            try { elasticsearchIndexService.deleteDocumentFromIndex(id); esDoc=true; } catch (Exception e) { log.warn("ES 문서 삭제 실패: {}", id, e); }
            // Mongo doc
            try { documentRepository.delete(document); mongoDoc=true; } catch (Exception e) { log.warn("Mongo 문서 삭제 실패: {}", id, e); }
            // File
            try { fileDeleted = localFileStorageService.deleteDocumentFile(document); } catch (Exception e) { log.warn("파일 삭제 실패: {}", id, e); }
            // Category counts decrement
            try {
                Category cat = document.getCategory();
                if (cat != null) {
                    categoryUsageService.decrementUsage(cat.getCodes(), cat.getLeafCode());
                }
            } catch (Exception e) { log.warn("카테고리 카운트 감소 실패: {}", id, e); }
            return new DeletionResult(id, esDoc, esChunks, mongoChunks, mongoDoc, fileDeleted, "삭제 완료");
        } catch (Exception e) {
            return new DeletionResult(id, esDoc, esChunks, mongoChunks, mongoDoc, fileDeleted, "삭제 중 오류: "+e.getMessage());
        }
    }

    @Transactional
    public List<DeletionResult> deleteDocuments(Collection<DocumentEntity> documents) {
        List<DeletionResult> results = new ArrayList<>();
        for (DocumentEntity d : documents) {
            results.add(deleteDocument(d));
        }
        return results;
    }
}
