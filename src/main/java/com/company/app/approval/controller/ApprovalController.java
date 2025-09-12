package com.company.app.approval.controller;

import com.company.app.approval.service.ApprovalService;
import com.company.app.approval.dto.ApprovalDecisionRequest;
import com.company.app.auth.entity.User;
import com.company.app.ingest.entity.IngestRequest;
import com.company.app.ingest.repository.IngestRequestRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/approval")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Approval", description = "승인 관리 관련 API")
public class ApprovalController {
    
    private final IngestRequestRepository ingestRequestRepository;
    private final ApprovalService approvalService;
    
    /**
     * 승인 요청 목록 조회
     */
    @GetMapping("/requests")
    @PreAuthorize("hasRole('APPROVER') or hasRole('ADMIN')")
    @Operation(summary = "승인 요청 목록 조회")
    public ResponseEntity<Map<String, Object>> getApprovalRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        try {
            log.info("승인 요청 목록 조회 요청: page={}, size={}, status={}, search={}", page, size, status, search);
            
            Pageable pageable = PageRequest.of(page, size, Sort.by("requestedAt").descending());
            Page<IngestRequest> requests;
            
            if (status != null && !status.isEmpty()) {
                try {
                    IngestRequest.Status requestStatus = IngestRequest.Status.valueOf(status.toUpperCase());
                    requests = ingestRequestRepository.findByStatus(requestStatus, pageable);
                } catch (IllegalArgumentException e) {
                    log.warn("잘못된 상태 값: {}", status);
                    requests = ingestRequestRepository.findAll(pageable);
                }
            } else {
                requests = ingestRequestRepository.findAll(pageable);
            }
            
            // 검색 필터 적용
            if (search != null && !search.trim().isEmpty()) {
                // 실제로는 MongoDB 쿼리로 검색해야 하지만, 여기서는 간단히 필터링
                requests = requests.map(request -> {
                    if (request.getProposedPurpose() != null && 
                        request.getProposedPurpose().toLowerCase().contains(search.toLowerCase())) {
                        return request;
                    }
                    return null;
                }).map(request -> request != null ? request : null);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("content", requests.getContent());
            response.put("totalElements", requests.getTotalElements());
            response.put("totalPages", requests.getTotalPages());
            response.put("currentPage", page);
            response.put("size", size);
            response.put("first", requests.isFirst());
            response.put("last", requests.isLast());
            
            log.info("승인 요청 목록 조회 완료: {}개 요청", requests.getTotalElements());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("승인 요청 목록 조회 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 승인 요청 상세 조회
     */
    @GetMapping("/requests/{id}")
    @PreAuthorize("hasRole('APPROVER') or hasRole('ADMIN')")
    @Operation(summary = "승인 요청 상세 조회")
    public ResponseEntity<IngestRequest> getApprovalRequest(@PathVariable String id) {
        try {
            log.info("승인 요청 상세 조회: {}", id);
            
            return ingestRequestRepository.findById(id)
                .map(request -> {
                    log.info("승인 요청 상세 조회 완료: {}", id);
                    return ResponseEntity.ok(request);
                })
                .orElse(ResponseEntity.notFound().build());
                
        } catch (Exception e) {
            log.error("승인 요청 상세 조회 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 승인/반려 결정
     */
    @PostMapping("/requests/{id}/decide")
    @PreAuthorize("hasRole('APPROVER') or hasRole('ADMIN')")
    @Operation(summary = "승인/반려 결정")
    public ResponseEntity<Map<String, Object>> decideApprovalRequest(
            @PathVariable String id,
            @RequestBody Map<String, Object> decision,
            Authentication authentication) {
        try {
            log.info("승인/반려 결정 요청: {}, decision={}", id, decision);
            
            String approverId = authentication.getName();
            String decisionType = (String) decision.get("decision");
            String reason = (String) decision.get("reason");
            
            if (decisionType == null || (!decisionType.equals("APPROVE") && !decisionType.equals("REJECT"))) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "잘못된 결정 유형입니다. APPROVE 또는 REJECT를 사용하세요."));
            }
            
            IngestRequest request = ingestRequestRepository.findById(id)
                .orElse(null);
            
            if (request == null) {
                return ResponseEntity.notFound().build();
            }
            
            if (request.getStatus() != IngestRequest.Status.PENDING) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "이미 처리된 요청입니다."));
            }
            
            // 승인/반려 처리
            ApprovalDecisionRequest decisionRequest = new ApprovalDecisionRequest();
            decisionRequest.setDecision(decisionType);
            decisionRequest.setReason(reason);
            
            approvalService.decideApproval(id, decisionRequest, approverId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "승인/반려 처리가 완료되었습니다.");
            response.put("requestId", id);
            response.put("decision", decisionType);
            
            log.info("승인/반려 결정 완료: {} -> {}", id, decisionType);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("승인/반려 결정 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "승인/반려 처리 중 오류가 발생했습니다."));
        }
    }
    
    /**
     * 사용자별 승인 요청 조회
     */
    @GetMapping("/my-requests")
    @Operation(summary = "사용자별 승인 요청 조회")
    public ResponseEntity<Map<String, Object>> getMyRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            Authentication authentication) {
        try {
            User user = (User) authentication.getPrincipal();
            String userId = user.getId();
            log.info("사용자별 승인 요청 조회 요청: userId={}, page={}, size={}, status={}", userId, page, size, status);
            
            Pageable pageable = PageRequest.of(page, size, Sort.by("requestedAt").descending());
            Page<IngestRequest> requests;
            
            if (status != null && !status.isEmpty()) {
                try {
                    IngestRequest.Status requestStatus = IngestRequest.Status.valueOf(status.toUpperCase());
                    requests = ingestRequestRepository.findByOwnerIdAndStatus(userId, requestStatus, pageable);
                } catch (IllegalArgumentException e) {
                    log.warn("잘못된 상태 값: {}", status);
                    requests = ingestRequestRepository.findByOwnerId(userId, pageable);
                }
            } else {
                requests = ingestRequestRepository.findByOwnerId(userId, pageable);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("content", requests.getContent());
            response.put("totalElements", requests.getTotalElements());
            response.put("totalPages", requests.getTotalPages());
            response.put("currentPage", page);
            response.put("size", size);
            response.put("first", requests.isFirst());
            response.put("last", requests.isLast());
            
            log.info("사용자별 승인 요청 조회 완료: {}개 요청", requests.getTotalElements());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("사용자별 승인 요청 조회 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 승인 요청 통계 조회
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('APPROVER') or hasRole('ADMIN')")
    @Operation(summary = "승인 요청 통계 조회")
    public ResponseEntity<Map<String, Object>> getApprovalStats() {
        try {
            log.info("승인 요청 통계 조회 요청");
            
            long total = ingestRequestRepository.count();
            long pending = ingestRequestRepository.findByStatus(IngestRequest.Status.PENDING).size();
            long approved = ingestRequestRepository.findByStatus(IngestRequest.Status.APPROVED).size();
            long rejected = ingestRequestRepository.findByStatus(IngestRequest.Status.REJECTED).size();
            long completed = ingestRequestRepository.findByStatus(IngestRequest.Status.COMPLETED).size();
            long failed = ingestRequestRepository.findByStatus(IngestRequest.Status.FAILED).size();
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("total", total);
            stats.put("pending", pending);
            stats.put("approved", approved);
            stats.put("rejected", rejected);
            stats.put("completed", completed);
            stats.put("failed", failed);
            
            log.info("승인 요청 통계 조회 완료: 총 {}개", total);
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("승인 요청 통계 조회 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}