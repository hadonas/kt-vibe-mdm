package com.company.app.common.controller;

import com.company.app.document.entity.DocumentEntity;
import com.company.app.document.repository.DocumentRepository;
import com.company.app.auth.entity.User;
import com.company.app.file.service.LocalFileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
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

@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
@Slf4j
public class PublicController {
    
    private final DocumentRepository documentRepository;
    private final LocalFileStorageService localFileStorageService;
    
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
            String filePath = localFileStorageService.getExistingDocumentFilePath(document);
            
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
