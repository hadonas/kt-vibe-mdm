package com.company.app.file.controller;

import com.company.app.file.service.FileAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Tag(name = "File", description = "파일 관련 API")
@Slf4j
public class FileController {
    
    private final FileAnalysisService fileAnalysisService;
    
    @PostMapping("/analyze")
    @Operation(summary = "파일 분석 (카테고리 및 목적 추출)")
    public ResponseEntity<?> analyzeFile(@RequestParam("file") MultipartFile file,
                                       Authentication authentication) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "파일이 비어있습니다."));
            }
            
            // 파일 크기 제한 (10MB)
            if (file.getSize() > 10 * 1024 * 1024) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "파일 크기는 10MB를 초과할 수 없습니다."));
            }
            
            // 지원되는 파일 형식 확인
            String fileName = file.getOriginalFilename();
            if (fileName == null || !isSupportedFileType(fileName)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "지원되지 않는 파일 형식입니다. DOCX, XLSX 파일만 지원됩니다."));
            }
            
            FileAnalysisService.FileAnalysisResult result = fileAnalysisService.analyzeFile(file);
            
            return ResponseEntity.ok(Map.of(
                "proposedCategory", result.getProposedCategory(),
                "proposedPurpose", result.getProposedPurpose(),
                "proposedTitle", result.getProposedTitle(),
                "fileName", fileName,
                "fileSize", file.getSize()
            ));
            
        } catch (Exception e) {
            log.error("파일 분석 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "파일 분석 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    private boolean isSupportedFileType(String fileName) {
        String extension = getFileExtension(fileName).toLowerCase();
        return "docx".equals(extension) || "xlsx".equals(extension);
    }
    
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return fileName.substring(lastDotIndex + 1);
    }
    
    @PostMapping("/upload")
    @Operation(summary = "파일 업로드")
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file,
                                                         Authentication authentication) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "파일이 비어있습니다."));
            }
            
            // 파일 크기 제한 (50MB)
            if (file.getSize() > 50 * 1024 * 1024) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "파일 크기는 50MB를 초과할 수 없습니다."));
            }
            
            // 임시 파일로 저장
            String fileId = fileAnalysisService.saveTemporaryFile(file);
            return ResponseEntity.ok(Map.of(
                "fileId", fileId, 
                "originalName", file.getOriginalFilename(),
                "fileSize", file.getSize()
            ));
        } catch (Exception e) {
            log.error("파일 업로드 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "파일 업로드 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @GetMapping("/download/{fileId}")
    @Operation(summary = "파일 다운로드")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String fileId,
                                              Authentication authentication) {
        try {
            byte[] fileContent = fileAnalysisService.getTemporaryFile(fileId);
            String fileName = fileAnalysisService.getTemporaryFileName(fileId);
            
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                    .body(fileContent);
        } catch (Exception e) {
            log.error("파일 다운로드 실패: {}", fileId, e);
            return ResponseEntity.notFound().build();
        }
    }
}
