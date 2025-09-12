package com.company.app.approval.controller;

import com.company.app.approval.dto.ApprovalDecisionRequest;
import com.company.app.approval.service.ApprovalService;
import com.company.app.common.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/approval")
@RequiredArgsConstructor
@Tag(name = "Approval", description = "승인 관리 관련 API")
public class ApprovalController {
    
    private final ApprovalService approvalService;
    
    @GetMapping("/requests")
    @Operation(summary = "승인 요청 목록 조회")
    @PreAuthorize("hasRole('APPROVER') or hasRole('ADMIN')")
    public ResponseEntity<?> getApprovalRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            Authentication authentication) {
        try {
            var requests = approvalService.getApprovalRequests(page, size, status);
            return ResponseEntity.ok(requests);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.of("BAD_REQUEST", e.getMessage(), "/approval/requests"));
        }
    }
    
    @PostMapping("/requests/{id}/decide")
    @Operation(summary = "승인 요청 결정 (승인/반려)")
    @PreAuthorize("hasRole('APPROVER') or hasRole('ADMIN')")
    public ResponseEntity<?> decideApproval(
            @PathVariable String id,
            @Valid @RequestBody ApprovalDecisionRequest request,
            Authentication authentication) {
        try {
            String approverId;
            if (authentication.getPrincipal() instanceof com.company.app.auth.entity.User user) {
                approverId = user.getId();
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ErrorResponse.of("UNAUTHORIZED", "인증되지 않은 사용자입니다.", "/approval/requests/" + id + "/decide"));
            }
            
            var result = approvalService.decideApproval(id, request, approverId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.of("BAD_REQUEST", e.getMessage(), "/approval/requests/" + id + "/decide"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ErrorResponse.of("INTERNAL_ERROR", "승인 처리 중 오류가 발생했습니다.", "/approval/requests/" + id + "/decide"));
        }
    }
    
    @GetMapping("/requests/{id}")
    @Operation(summary = "승인 요청 상세 조회")
    @PreAuthorize("hasRole('APPROVER') or hasRole('ADMIN')")
    public ResponseEntity<?> getApprovalRequest(@PathVariable String id) {
        try {
            var request = approvalService.getApprovalRequest(id);
            return ResponseEntity.ok(request);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
