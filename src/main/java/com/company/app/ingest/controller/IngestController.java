package com.company.app.ingest.controller;

import com.company.app.ingest.dto.SingleIngestRequest;
import com.company.app.ingest.service.IngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/ingest")
@RequiredArgsConstructor
@Tag(name = "Ingest", description = "문서 수집 관련 API")
public class IngestController {
    
    private final IngestService ingestService;
    
    @PostMapping("/single")
    @Operation(summary = "개별 등록 (레포 URL 또는 파일)")
    public ResponseEntity<?> singleIngest(@Valid @RequestBody SingleIngestRequest request,
                                        Authentication authentication) {
        String userId;
        if (authentication.getPrincipal() instanceof com.company.app.auth.entity.User user) {
            userId = user.getId();
        } else {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "인증되지 않은 사용자입니다."));
        }
        
        if (request.getRepoUrl() != null) {
            if (Boolean.TRUE.equals(request.getSubmit())) {
                // 실제 레포지토리 등록 요청 생성
                var response = ingestService.createRepoIngestRequest(userId, request);
                return ResponseEntity.status(201).body(response);
            } else {
                // 레포지토리 개요 생성 및 유사 항목 조회 (미리보기)
                var preview = ingestService.createRepoPreview(request.getRepoUrl(), request.getAccessToken());
                return ResponseEntity.ok(preview);
            }
        } else if (request.getFileIds() != null && !request.getFileIds().isEmpty()) {
            // 파일 기반 등록 요청 생성
            var response = ingestService.createFileIngestRequest(userId, request);
            return ResponseEntity.status(201).body(response);
        } else {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "레포 URL 또는 파일 ID가 필요합니다."));
        }
    }
    
    
    @PostMapping("/{id}/resubmit")
    @Operation(summary = "재등록/수정 (버전업)")
    public ResponseEntity<?> resubmit(@PathVariable String id,
                                    @RequestBody Map<String, Object> request,
                                    Authentication authentication) {
        String userId = authentication.getName();
        
        try {
            var response = ingestService.resubmitRequest(id, userId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
