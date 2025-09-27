package com.company.app.document.controller;

import com.company.app.document.service.DocumentManagementService;
import com.company.app.document.repository.DocumentRepository;
import com.company.app.file.service.LocalFileStorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.io.ByteArrayResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
// Base path intentionally excludes global context-path (/api) to avoid duplication
@RequestMapping("/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentManagementService documentManagementService;
    private final DocumentRepository documentRepository;
    private final LocalFileStorageService localFileStorageService;

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDocument(@PathVariable String id) {
        boolean deleted = documentManagementService.deleteDocumentAndChunks(id);
        if (deleted) {
            return ResponseEntity.ok().body(java.util.Map.of(
                    "success", true,
                    "message", "문서 및 청크 삭제 완료",
                    "documentId", id));
        }
        return ResponseEntity.status(404).body(java.util.Map.of(
                "success", false,
                "message", "문서를 찾을 수 없습니다",
                "documentId", id));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<?> downloadDocument(@PathVariable String id) {
        var opt = documentRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(java.util.Map.of(
                    "success", false,
                    "message", "문서를 찾을 수 없습니다",
                    "documentId", id));
        }
        
        var doc = opt.get();
        
        // GridFS에 저장된 원본 파일 다운로드
        try {
            byte[] fileBytes = localFileStorageService.getDocumentFileBytes(doc);
            if (fileBytes == null) {
                return ResponseEntity.status(404).body(java.util.Map.of(
                        "success", false,
                        "message", "문서 파일을 찾을 수 없습니다",
                        "documentId", id));
            }
            
            String filename = localFileStorageService.getDocumentFileName(doc);
            ByteArrayResource resource = new ByteArrayResource(fileBytes);
            
            // 파일 확장자에 따른 MIME 타입 설정
            MediaType mediaType = getMediaTypeForFile(filename);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentLength(fileBytes.length)
                    .contentType(mediaType)
                    .body(resource);
                    
        } catch (Exception e) {
            log.error("문서 다운로드 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(java.util.Map.of(
                    "success", false,
                    "message", "문서 다운로드 중 오류가 발생했습니다",
                    "documentId", id));
        }
    }
    
    /**
     * 파일 확장자에 따른 MIME 타입 반환
     */
    private MediaType getMediaTypeForFile(String filename) {
        if (filename == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        switch (extension) {
            case "txt":
                return MediaType.TEXT_PLAIN;
            case "pdf":
                return MediaType.APPLICATION_PDF;
            case "doc":
            case "docx":
                return MediaType.valueOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "xls":
            case "xlsx":
                return MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "ppt":
            case "pptx":
                return MediaType.valueOf("application/vnd.openxmlformats-officedocument.presentationml.presentation");
            case "jpg":
            case "jpeg":
                return MediaType.IMAGE_JPEG;
            case "png":
                return MediaType.IMAGE_PNG;
            case "gif":
                return MediaType.IMAGE_GIF;
            default:
                return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
