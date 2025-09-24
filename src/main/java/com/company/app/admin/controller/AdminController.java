package com.company.app.admin.controller;

import com.company.app.document.entity.DocumentEntity;
import com.company.app.document.repository.DocumentRepository;
import com.company.app.document.repository.DocumentChunkRepository;
import com.company.app.catalog.repository.CatalogNodeRepository;
import com.company.app.file.service.LocalFileStorageService;
import com.company.app.search.service.ElasticsearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Optional;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {
    
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final LocalFileStorageService localFileStorageService;
    private final ElasticsearchIndexService elasticsearchIndexService;
    
    /**
     * 모든 문서 목록 조회
     */
    @GetMapping("/documents")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getAllDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            log.info("문서 목록 조회 요청: page={}, size={}", page, size);
            
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<DocumentEntity> documents = documentRepository.findAll(pageable);
            
            Map<String, Object> result = new HashMap<>();
            result.put("content", documents.getContent());
            result.put("totalElements", documents.getTotalElements());
            result.put("totalPages", documents.getTotalPages());
            result.put("currentPage", documents.getNumber());
            result.put("size", documents.getSize());
            
            log.info("문서 목록 조회 완료: {}개 문서", documents.getTotalElements());
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("문서 목록 조회 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 문서 분류별 계층 구조 조회 (가변 계층 지원)
     */
    @GetMapping("/documents/hierarchy")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getDocumentHierarchy() {
        try {
            log.info("문서 분류별 계층 구조 조회 요청");
            
            // 1. 모든 문서 조회
            List<DocumentEntity> allDocuments = documentRepository.findAll();
            
            // 2. 가변 계층 구조로 그룹화
            Map<String, List<DocumentEntity>> documentsByCategory = allDocuments.stream()
                .filter(doc -> doc.getCategory() != null)
                .collect(Collectors.groupingBy(doc -> {
                    // Category의 getFullCode() method는 이미 fallback 로직 포함
                    return doc.getCategory().getFullCode();
                }));
            
            // 3. 계층 구조 데이터 생성
            List<Map<String, Object>> hierarchyData = new ArrayList<>();
            
            for (Map.Entry<String, List<DocumentEntity>> entry : documentsByCategory.entrySet()) {
                String categoryCode = entry.getKey();
                List<DocumentEntity> docs = entry.getValue();
                
                if (!docs.isEmpty()) {
                    DocumentEntity firstDoc = docs.get(0);
                    Map<String, Object> categoryInfo = new HashMap<>();
                    
                    if (firstDoc.getCategory().getHierarchy() != null && !firstDoc.getCategory().getHierarchy().isEmpty()) {
                        // 가변 계층 구조 사용
                        categoryInfo.put("code", categoryCode);
                        categoryInfo.put("fullName", firstDoc.getCategory().getFullName());
                        categoryInfo.put("hierarchy", firstDoc.getCategory().getHierarchy());
                        categoryInfo.put("count", docs.size());
                    } else {
                        // 기존 3레벨 구조 사용
                        categoryInfo.put("code", categoryCode);
                        categoryInfo.put("fullName", firstDoc.getCategory().getMajorName() + " > " + 
                                                   firstDoc.getCategory().getMidName() + " > " + 
                                                   firstDoc.getCategory().getSubName());
                        categoryInfo.put("majorName", firstDoc.getCategory().getMajorName());
                        categoryInfo.put("midName", firstDoc.getCategory().getMidName());
                        categoryInfo.put("subName", firstDoc.getCategory().getSubName());
                        categoryInfo.put("count", docs.size());
                    }
                    
                    hierarchyData.add(categoryInfo);
                }
            }
            
            // 코드 순으로 정렬
            hierarchyData.sort(Comparator.comparing(cat -> (String) cat.get("code")));
            
            Map<String, Object> response = new HashMap<>();
            response.put("hierarchy", hierarchyData);
            response.put("totalDocuments", allDocuments.size());
            response.put("totalCategories", hierarchyData.size());
            
            log.info("문서 분류별 계층 구조 조회 완료: {}개 카테고리, {}개 문서", hierarchyData.size(), allDocuments.size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("문서 분류별 계층 구조 조회 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 등록된 문서들의 코드별 정렬 조회
     */
    @GetMapping("/documents/codes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getDocumentsByCode(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "serial") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        try {
            log.info("문서 코드별 정렬 조회 요청: page={}, size={}, sortBy={}, sortDir={}", page, size, sortBy, sortDir);
            
            // 정렬 방향 설정
            Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
            Sort sort = Sort.by(direction, sortBy);
            
            // 페이지네이션 설정
            Pageable pageable = PageRequest.of(page, size, sort);
            
            // 문서 조회
            Page<DocumentEntity> documentPage = documentRepository.findAll(pageable);
            
            // 응답 데이터 구성
            List<Map<String, Object>> documents = documentPage.getContent().stream()
                .map(doc -> {
                    Map<String, Object> docData = new HashMap<>();
                    docData.put("id", doc.getId());
                    docData.put("serial", doc.getSerial() != null ? doc.getSerial().getFull() : "");
                    docData.put("purpose", doc.getPurpose());
                    docData.put("category", doc.getCategory() != null ? 
                        doc.getCategory().getMajorName() + " > " + 
                        doc.getCategory().getMidName() + " > " + 
                        doc.getCategory().getSubName() : "");
                    docData.put("status", doc.getApprovedAt() != null ? "APPROVED" : "PENDING");
                    docData.put("createdAt", doc.getCreatedAt());
                    docData.put("approvedAt", doc.getApprovedAt());
                    docData.put("ownerId", doc.getOwnerId());
                    return docData;
                })
                .collect(Collectors.toList());
            
            Map<String, Object> response = new HashMap<>();
            response.put("documents", documents);
            response.put("totalElements", documentPage.getTotalElements());
            response.put("totalPages", documentPage.getTotalPages());
            response.put("currentPage", documentPage.getNumber());
            response.put("size", documentPage.getSize());
            response.put("first", documentPage.isFirst());
            response.put("last", documentPage.isLast());
            
            log.info("문서 코드별 정렬 조회 완료: {}개 문서, {}페이지", documentPage.getTotalElements(), documentPage.getTotalPages());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("문서 코드별 정렬 조회 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 특정 분류의 문서 목록 조회 (가변 계층 지원)
     */
    @GetMapping("/documents/category/{categoryCode}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getDocumentsByCategory(
            @PathVariable String categoryCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            log.info("분류별 문서 조회 요청: {}, page={}, size={}", categoryCode, page, size);
            
            // 분류별 문서 조회
            List<DocumentEntity> allDocuments = documentRepository.findAll();
            List<DocumentEntity> filteredDocuments = allDocuments.stream()
                .filter(doc -> doc.getCategory() != null && matchesCategory(doc, categoryCode))
                .sorted(Comparator.comparing(doc -> doc.getSerial() != null ? doc.getSerial().getFull() : ""))
                .collect(Collectors.toList());
            
            // 페이지네이션 적용
            int start = page * size;
            int end = Math.min(start + size, filteredDocuments.size());
            List<DocumentEntity> pageDocuments = filteredDocuments.subList(start, end);
            
            // 응답 데이터 구성
            List<Map<String, Object>> documents = pageDocuments.stream()
                .map(doc -> {
                    Map<String, Object> docData = new HashMap<>();
                    docData.put("id", doc.getId());
                    docData.put("serial", doc.getSerial() != null ? doc.getSerial().getFull() : "");
                    docData.put("purpose", doc.getPurpose());
                    docData.put("status", doc.getApprovedAt() != null ? "APPROVED" : "PENDING");
                    docData.put("createdAt", doc.getCreatedAt());
                    docData.put("approvedAt", doc.getApprovedAt());
                    docData.put("ownerId", doc.getOwnerId());
                    
                    // 카테고리 정보 추가
                    if (doc.getCategory().getHierarchy() != null && !doc.getCategory().getHierarchy().isEmpty()) {
                        docData.put("categoryName", doc.getCategory().getFullName());
                    } else {
                        docData.put("categoryName", doc.getCategory().getMajorName() + " > " + 
                                                  doc.getCategory().getMidName() + " > " + 
                                                  doc.getCategory().getSubName());
                    }
                    
                    return docData;
                })
                .collect(Collectors.toList());
            
            Map<String, Object> response = new HashMap<>();
            response.put("documents", documents);
            response.put("totalElements", filteredDocuments.size());
            response.put("totalPages", (int) Math.ceil((double) filteredDocuments.size() / size));
            response.put("currentPage", page);
            response.put("size", size);
            response.put("category", categoryCode);
            
            log.info("분류별 문서 조회 완료: {}개 문서", filteredDocuments.size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("분류별 문서 조회 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 문서가 특정 카테고리에 속하는지 확인하는 헬퍼 메서드
     */
    private boolean matchesCategory(DocumentEntity doc, String categoryCode) {
        if (doc.getCategory() == null) return false;
        
        // fullCode 우선 확인
        if (doc.getCategory().getFullCode() != null) {
            return doc.getCategory().getFullCode().equals(categoryCode);
        }
        
        // Category의 fullCode와 비교
        return doc.getCategory().getFullCode().equals(categoryCode);
    }
    

    /**
     * 로컬 파일 저장소의 계층 구조 조회
     */
    @GetMapping("/files/hierarchy")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getFileHierarchy() {
        try {
            log.info("파일 계층 구조 조회 요청");
            
            // LocalFileStorageService를 통해 계층 구조 조회
            Map<String, Object> hierarchy = localFileStorageService.getFileHierarchy();
            
            log.info("파일 계층 구조 조회 완료");
            return ResponseEntity.ok(hierarchy);
            
        } catch (Exception e) {
            log.error("파일 계층 구조 조회 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 특정 카테고리의 파일 목록 조회
     */
    @GetMapping("/files/category/{majorCode}/{midCode}/{subCode}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getFilesByCategory(
            @PathVariable String majorCode,
            @PathVariable String midCode,
            @PathVariable String subCode) {
        try {
            log.info("카테고리별 파일 조회 요청: {}/{}/{}", majorCode, midCode, subCode);
            
            // LocalFileStorageService를 통해 특정 카테고리의 파일 목록 조회
            Map<String, Object> files = localFileStorageService.getFilesByCategory(majorCode, midCode, subCode);
            
            log.info("카테고리별 파일 조회 완료: {}개 파일", files.get("fileCount"));
            return ResponseEntity.ok(files);
            
        } catch (Exception e) {
            log.error("카테고리별 파일 조회 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    
    
    
    /**
     * 카테고리별 문서 삭제 (가변 계층 지원)
     */
    @DeleteMapping("/documents/category/{categoryCode}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteDocumentsByCategory(@PathVariable String categoryCode) {
        try {
            log.info("카테고리별 문서 삭제 요청: {}", categoryCode);
            
            // 1. 해당 카테고리의 모든 문서 조회
            List<DocumentEntity> allDocuments = documentRepository.findAll();
            List<DocumentEntity> documentsToDelete = allDocuments.stream()
                .filter(doc -> doc.getCategory() != null && matchesCategory(doc, categoryCode))
                .collect(Collectors.toList());
            
            if (documentsToDelete.isEmpty()) {
                log.warn("삭제할 문서를 찾을 수 없음: {}", categoryCode);
                return ResponseEntity.notFound().build();
            }
            
            int deletedCount = 0;
            int fileDeletedCount = 0;
            
            for (DocumentEntity document : documentsToDelete) {
                try {
                    
                    // 1. Elasticsearch 청크 삭제 (MongoDB 삭제 전에 실행)
                    try {
                        elasticsearchIndexService.deleteChunksByDocumentId(document.getId());
                        log.info("Elasticsearch에서 청크 삭제: {}", document.getId());
                    } catch (Exception e) {
                        log.warn("Elasticsearch 청크 삭제 실패: {}", document.getId(), e);
                    }
                    
                    // 2. MongoDB 청크 삭제
                    try {
                        chunkRepository.deleteByDocumentId(document.getId());
                        log.info("MongoDB에서 청크 삭제: {}", document.getId());
                    } catch (Exception e) {
                        log.warn("MongoDB 청크 삭제 실패: {}", document.getId(), e);
                    }
                    
                    // 3. Elasticsearch 인덱스에서 문서 제거
                    try {
                        elasticsearchIndexService.deleteDocumentFromIndex(document.getId());
                        log.info("Elasticsearch 인덱스에서 문서 제거: {}", document.getId());
                    } catch (Exception e) {
                        log.warn("Elasticsearch 문서 삭제 실패: {}", document.getId(), e);
                        // Elasticsearch 실패는 전체 실패로 처리하지 않음
                    }
                    
                    // 4. MongoDB에서 문서 삭제
                    documentRepository.delete(document);
                    deletedCount++;
                    log.info("MongoDB에서 문서 삭제: {}", document.getId());
                    
                    // 5. 파일시스템에서 파일 삭제
                    if (localFileStorageService.deleteDocumentFile(document)) {
                        fileDeletedCount++;
                        log.info("파일시스템에서 파일 삭제 성공: {}", document.getSerial() != null ? document.getSerial().getFull() : document.getId());
                    } else {
                        log.warn("파일시스템에서 파일 삭제 실패: {}", document.getSerial() != null ? document.getSerial().getFull() : document.getId());
                    }
                    
                } catch (Exception e) {
                    log.error("문서 삭제 중 오류 발생 (문서 ID: {}): {}", document.getId(), e.getMessage(), e);
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("categoryCode", categoryCode);
            result.put("deletedDocuments", deletedCount);
            result.put("deletedFiles", fileDeletedCount);
            result.put("message", String.format("카테고리 %s의 %d개 문서와 %d개 파일이 삭제되었습니다.", categoryCode, deletedCount, fileDeletedCount));
            
            log.info("카테고리별 문서 삭제 완료: {} (문서: {}개, 파일: {}개)", categoryCode, deletedCount, fileDeletedCount);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("카테고리별 문서 삭제 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 코드로 문서 삭제 (벡터DB + 파일시스템)
     */
    @DeleteMapping("/documents/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteDocumentByCode(@PathVariable String code) {
        try {
            log.info("코드로 문서 삭제 요청: {}", code);
            
            // 1. MongoDB에서 해당 코드의 문서들 조회
            Optional<DocumentEntity> documentOpt = documentRepository.findBySerialFull(code);
            
            if (documentOpt.isEmpty()) {
                log.warn("MongoDB에서 문서를 찾을 수 없음, 파일시스템에서만 삭제 시도: {}", code);
                
                // Fallback: 파일시스템에서만 삭제 시도
                boolean fileDeleted = localFileStorageService.deleteFileByCode(code);
                
                if (fileDeleted) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("code", code);
                    result.put("deletedDocuments", 0);
                    result.put("deletedFiles", 1);
                    result.put("message", String.format("코드 %s의 파일이 파일시스템에서 삭제되었습니다. (MongoDB에 문서가 없었음)", code));
                    
                    log.info("파일시스템에서만 파일 삭제 완료: {}", code);
                    return ResponseEntity.ok(result);
                } else {
                    log.warn("파일시스템에서도 삭제할 파일을 찾을 수 없음: {}", code);
                    return ResponseEntity.notFound().build();
                }
            }
            
            List<DocumentEntity> documents = List.of(documentOpt.get());
            
            int deletedCount = 0;
            int fileDeletedCount = 0;
            
            for (DocumentEntity document : documents) {
                try {
                    
                    // 1. Elasticsearch 청크 삭제 (MongoDB 삭제 전에 실행)
                    try {
                        elasticsearchIndexService.deleteChunksByDocumentId(document.getId());
                        log.info("Elasticsearch에서 청크 삭제: {}", document.getId());
                    } catch (Exception e) {
                        log.warn("Elasticsearch 청크 삭제 실패: {}", document.getId(), e);
                    }
                    
                    // 2. MongoDB 청크 삭제
                    try {
                        chunkRepository.deleteByDocumentId(document.getId());
                        log.info("MongoDB에서 청크 삭제: {}", document.getId());
                    } catch (Exception e) {
                        log.warn("MongoDB 청크 삭제 실패: {}", document.getId(), e);
                    }
                    
                    // 3. Elasticsearch 인덱스에서 문서 제거
                    try {
                        elasticsearchIndexService.deleteDocumentFromIndex(document.getId());
                        log.info("Elasticsearch 인덱스에서 문서 제거: {}", document.getId());
                    } catch (Exception e) {
                        log.warn("Elasticsearch 문서 삭제 실패: {}", document.getId(), e);
                        // Elasticsearch 실패는 전체 실패로 처리하지 않음
                    }
                    
                    // 4. MongoDB에서 문서 삭제
                    documentRepository.delete(document);
                    deletedCount++;
                    log.info("MongoDB에서 문서 삭제: {}", document.getId());
                    
                    // 5. 파일시스템에서 파일 삭제
                    if (localFileStorageService.deleteDocumentFile(document)) {
                        fileDeletedCount++;
                        log.info("파일시스템에서 파일 삭제 성공: {}", document.getSerial().getFull());
                    } else {
                        log.warn("파일시스템에서 파일 삭제 실패: {}", document.getSerial().getFull());
                    }
                    
                } catch (Exception e) {
                    log.error("문서 삭제 중 오류 발생 (문서 ID: {}): {}", document.getId(), e.getMessage(), e);
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("code", code);
            result.put("deletedDocuments", deletedCount);
            result.put("deletedFiles", fileDeletedCount);
            result.put("message", String.format("코드 %s의 %d개 문서와 %d개 파일이 삭제되었습니다.", code, deletedCount, fileDeletedCount));
            
            log.info("코드로 문서 삭제 완료: {} (문서: {}개, 파일: {}개)", code, deletedCount, fileDeletedCount);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("코드로 문서 삭제 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 모든 중복 문서 조회 (같은 코드를 가진 문서들)
     */
    @GetMapping("/documents/duplicates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getDuplicateDocuments() {
        try {
            log.info("중복 문서 조회 요청");
            
            // 모든 문서를 코드별로 그룹화
            Map<String, List<DocumentEntity>> documentsByCode = documentRepository.findAll()
                .stream()
                .filter(doc -> doc.getSerial() != null && doc.getSerial().getFull() != null)
                .collect(Collectors.groupingBy(doc -> doc.getSerial().getFull()));
            
            // 중복된 코드만 필터링 (2개 이상의 문서를 가진 코드)
            Map<String, List<Map<String, Object>>> duplicates = new HashMap<>();
            
            for (Map.Entry<String, List<DocumentEntity>> entry : documentsByCode.entrySet()) {
                if (entry.getValue().size() > 1) {
                    String code = entry.getKey();
                    List<Map<String, Object>> docList = entry.getValue().stream()
                        .map(doc -> {
                            Map<String, Object> docInfo = new HashMap<>();
                            docInfo.put("id", doc.getId());
                            docInfo.put("purpose", doc.getPurpose());
                            docInfo.put("createdAt", doc.getCreatedAt());
                            docInfo.put("approvedAt", doc.getApprovedAt());
                            return docInfo;
                        })
                        .collect(Collectors.toList());
                    duplicates.put(code, docList);
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("duplicates", duplicates);
            result.put("totalDuplicateCodes", duplicates.size());
            result.put("totalDuplicateDocuments", duplicates.values().stream()
                .mapToInt(List::size)
                .sum());
            
            log.info("중복 문서 조회 완료: {}개 코드, {}개 문서", duplicates.size(), 
                duplicates.values().stream().mapToInt(List::size).sum());
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("중복 문서 조회 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    
}
