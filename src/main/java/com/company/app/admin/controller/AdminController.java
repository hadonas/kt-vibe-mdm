package com.company.app.admin.controller;

import com.company.app.document.entity.DocumentEntity;
import com.company.app.document.repository.DocumentRepository;
import com.company.app.catalog.entity.CatalogNode;
import com.company.app.catalog.repository.CatalogNodeRepository;
import com.company.app.auth.entity.User;
import com.company.app.file.service.LocalFileStorageService;
import com.company.app.search.service.FaissVectorSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.*;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Optional;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {
    
    private final DocumentRepository documentRepository;
    private final CatalogNodeRepository catalogNodeRepository;
    private final LocalFileStorageService localFileStorageService;
    private final FaissVectorSearchService faissVectorSearchService;
    
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
     * 문서 분류별 계층 구조 조회
     */
    @GetMapping("/documents/hierarchy")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getDocumentHierarchy() {
        try {
            log.info("문서 분류별 계층 구조 조회 요청");
            
            // 1. 모든 문서 조회
            List<DocumentEntity> allDocuments = documentRepository.findAll();
            
            // 2. 카테고리별로 그룹화
            Map<String, Map<String, Map<String, List<DocumentEntity>>>> hierarchy = 
                allDocuments.stream()
                    .filter(doc -> doc.getCategory() != null)
                    .collect(Collectors.groupingBy(
                        doc -> doc.getCategory().getMajorName(),
                        Collectors.groupingBy(
                            doc -> doc.getCategory().getMidName(),
                            Collectors.groupingBy(
                                doc -> doc.getCategory().getSubName()
                            )
                        )
                    ));
            
            // 3. 계층 구조 생성
            List<Map<String, Object>> hierarchyData = hierarchy.entrySet().stream()
                .map(majorEntry -> {
                    Map<String, Object> major = new HashMap<>();
                    major.put("name", majorEntry.getKey());
                    major.put("code", majorEntry.getValue().values().iterator().next().values().iterator().next().get(0).getCategory().getMajorCode());
                    major.put("count", majorEntry.getValue().values().stream()
                        .mapToInt(midMap -> midMap.values().stream()
                            .mapToInt(List::size)
                            .sum())
                        .sum());
                    
                    List<Map<String, Object>> midCategories = majorEntry.getValue().entrySet().stream()
                        .map(midEntry -> {
                            Map<String, Object> mid = new HashMap<>();
                            mid.put("name", midEntry.getKey());
                            mid.put("code", midEntry.getValue().values().iterator().next().get(0).getCategory().getMidCode());
                            mid.put("count", midEntry.getValue().values().stream()
                                .mapToInt(List::size)
                                .sum());
                            
                            List<Map<String, Object>> subCategories = midEntry.getValue().entrySet().stream()
                                .map(subEntry -> {
                                    Map<String, Object> sub = new HashMap<>();
                                    sub.put("name", subEntry.getKey());
                                    sub.put("code", subEntry.getValue().get(0).getCategory().getSubCode());
                                    sub.put("count", subEntry.getValue().size());
                                    return sub;
                                })
                                .sorted(Comparator.comparing(sub -> (String) sub.get("name")))
                                .collect(Collectors.toList());
                            
                            mid.put("subCategories", subCategories);
                            return mid;
                        })
                        .sorted(Comparator.comparing(mid -> (String) mid.get("name")))
                        .collect(Collectors.toList());
                    
                    major.put("midCategories", midCategories);
                    return major;
                })
                .sorted(Comparator.comparing(major -> (String) major.get("name")))
                .collect(Collectors.toList());
            
            Map<String, Object> response = new HashMap<>();
            response.put("hierarchy", hierarchyData);
            response.put("totalDocuments", allDocuments.size());
            response.put("totalCategories", hierarchy.size());
            
            log.info("문서 분류별 계층 구조 조회 완료: {}개 대분류, {}개 문서", hierarchy.size(), allDocuments.size());
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
     * 특정 분류의 문서 목록 조회
     */
    @GetMapping("/documents/category/{majorCode}/{midCode}/{subCode}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getDocumentsByCategory(
            @PathVariable String majorCode,
            @PathVariable String midCode,
            @PathVariable String subCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            log.info("분류별 문서 조회 요청: {}/{}/{}, page={}, size={}", majorCode, midCode, subCode, page, size);
            
            // MongoDB 쿼리로 특정 분류의 문서 조회
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "serial.full"));
            
            // 분류별 문서 조회 (실제 구현에서는 MongoDB 쿼리 사용)
            List<DocumentEntity> allDocuments = documentRepository.findAll();
            List<DocumentEntity> filteredDocuments = allDocuments.stream()
                .filter(doc -> doc.getCategory() != null &&
                    doc.getCategory().getMajorCode().equals(majorCode) &&
                    doc.getCategory().getMidCode().equals(midCode) &&
                    doc.getCategory().getSubCode().equals(subCode))
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
                    return docData;
                })
                .collect(Collectors.toList());
            
            Map<String, Object> response = new HashMap<>();
            response.put("documents", documents);
            response.put("totalElements", filteredDocuments.size());
            response.put("totalPages", (int) Math.ceil((double) filteredDocuments.size() / size));
            response.put("currentPage", page);
            response.put("size", size);
            response.put("category", majorCode + "/" + midCode + "/" + subCode);
            
            log.info("분류별 문서 조회 완료: {}개 문서", filteredDocuments.size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("분류별 문서 조회 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 전체 문서 수 조회 (모든 사용자 접근 가능)
     */
    @GetMapping("/documents/count")
    @Operation(summary = "전체 문서 수 조회")
    public ResponseEntity<Map<String, Object>> getDocumentCount() {
        try {
            log.info("전체 문서 수 조회 요청");
            
            long totalDocuments = documentRepository.count();
            
            Map<String, Object> response = new HashMap<>();
            response.put("totalDocuments", totalDocuments);
            
            log.info("전체 문서 수 조회 완료: {}개", totalDocuments);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("전체 문서 수 조회 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
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
     * 코드 기반 파일 다운로드 (모든 인증된 사용자 접근 가능)
     */
    @GetMapping("/files/download-by-code")
    @Operation(summary = "코드로 파일 다운로드")
    public ResponseEntity<Resource> downloadFileByCode(@RequestParam String code) {
        try {
            log.info("코드 기반 파일 다운로드 요청: {}", code);
            
            // 코드로 문서 찾기
            Optional<DocumentEntity> documentOpt = documentRepository.findBySerialFull(code);
            if (documentOpt.isEmpty()) {
                log.warn("코드에 해당하는 문서를 찾을 수 없음: {}", code);
                return ResponseEntity.notFound().build();
            }
            
            DocumentEntity document = documentOpt.get();
            String filePath = localFileStorageService.getDocumentFilePath(document);
            
            if (filePath == null || filePath.isEmpty()) {
                log.warn("문서의 파일 경로가 없음: {}", code);
                return ResponseEntity.notFound().build();
            }
            
            // 파일 경로 검증 (보안을 위해 storage 디렉토리 내의 파일만 허용)
            Path file = Paths.get(filePath);
            Path storagePath = Paths.get(localFileStorageService.getStoragePath());
            
            if (!file.startsWith(storagePath)) {
                log.warn("접근이 허용되지 않은 파일 경로: {}", filePath);
                return ResponseEntity.badRequest().build();
            }
            
            Resource resource = new UrlResource(file.toUri());
            
            if (!resource.exists() || !resource.isReadable()) {
                log.warn("파일을 찾을 수 없거나 읽을 수 없음: {}", filePath);
                return ResponseEntity.notFound().build();
            }
            
            String fileName = file.getFileName().toString();
            String contentType = determineContentType(fileName);
            String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
            
            log.info("코드 기반 파일 다운로드 완료: {} -> {}", code, fileName);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + code + "." + extension + "\"")
                    .body(resource);
                    
        } catch (Exception e) {
            log.error("코드 기반 파일 다운로드 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 파일 다운로드 (모든 인증된 사용자 접근 가능)
     */
    @GetMapping("/files/download")
    @Operation(summary = "파일 다운로드")
    public ResponseEntity<Resource> downloadFile(@RequestParam String filePath) {
        try {
            log.info("파일 다운로드 요청: {}", filePath);
            
            // 파일 경로 검증 (보안을 위해 storage 디렉토리 내의 파일만 허용)
            Path file = Paths.get(filePath);
            Path storagePath = Paths.get(localFileStorageService.getStoragePath());
            
            if (!file.startsWith(storagePath)) {
                log.warn("접근이 허용되지 않은 파일 경로: {}", filePath);
                return ResponseEntity.badRequest().build();
            }
            
            Resource resource = new UrlResource(file.toUri());
            
            if (!resource.exists() || !resource.isReadable()) {
                log.warn("파일을 찾을 수 없거나 읽을 수 없음: {}", filePath);
                return ResponseEntity.notFound().build();
            }
            
            String fileName = file.getFileName().toString();
            String contentType = determineContentType(fileName);
            
            log.info("파일 다운로드 완료: {}", fileName);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(resource);
                    
        } catch (Exception e) {
            log.error("파일 다운로드 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 파일 확장자에 따른 Content-Type 결정
     */
    private String determineContentType(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        
        return switch (extension) {
            case "md" -> "text/markdown";
            case "txt" -> "text/plain";
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "html" -> "text/html";
            case "css" -> "text/css";
            case "js" -> "application/javascript";
            case "java" -> "text/x-java-source";
            case "py" -> "text/x-python";
            case "jsx", "tsx" -> "text/javascript";
            case "ts" -> "text/typescript";
            default -> "application/octet-stream";
        };
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
                    // 2. FAISS 벡터 인덱스에서 제거
                    faissVectorSearchService.removeDocument(document.getId());
                    log.info("FAISS 벡터 인덱스에서 문서 제거: {}", document.getId());
                    
                    // 3. MongoDB에서 문서 삭제
                    documentRepository.delete(document);
                    deletedCount++;
                    log.info("MongoDB에서 문서 삭제: {}", document.getId());
                    
                    // 4. 파일시스템에서 파일 삭제
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
    
    /**
     * 사용자별 문서 조회
     */
    @GetMapping("/my-documents")
    @Operation(summary = "사용자별 문서 조회")
    public ResponseEntity<Map<String, Object>> getMyDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        try {
            User user = (User) authentication.getPrincipal();
            String userId = user.getId();
            log.info("사용자별 문서 조회 요청: userId={}, page={}, size={}", userId, page, size);
            
            Pageable pageable = PageRequest.of(page, size, Sort.by("approvedAt").descending());
            Page<DocumentEntity> documents = documentRepository.findByOwnerId(userId, pageable);
            
            Map<String, Object> response = new HashMap<>();
            response.put("content", documents.getContent());
            response.put("totalElements", documents.getTotalElements());
            response.put("totalPages", documents.getTotalPages());
            response.put("currentPage", page);
            response.put("size", size);
            response.put("first", documents.isFirst());
            response.put("last", documents.isLast());
            
            log.info("사용자별 문서 조회 완료: {}개 문서", documents.getTotalElements());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("사용자별 문서 조회 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
}
