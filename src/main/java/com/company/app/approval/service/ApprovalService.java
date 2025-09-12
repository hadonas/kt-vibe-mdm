package com.company.app.approval.service;

import com.company.app.approval.dto.ApprovalDecisionRequest;
import com.company.app.ingest.entity.IngestRequest;
import com.company.app.ingest.repository.IngestRequestRepository;
import com.company.app.document.entity.DocumentEntity;
import com.company.app.document.repository.DocumentRepository;
import com.company.app.common.dto.Serial;
import com.company.app.common.dto.Category;
import com.company.app.common.dto.Source;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalService {
    
    private final IngestRequestRepository ingestRequestRepository;
    private final DocumentRepository documentRepository;
    
    public Page<IngestRequest> getApprovalRequests(int page, int size, String status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "requestedAt"));
        
        if (status != null && !status.isEmpty()) {
            IngestRequest.Status statusEnum = IngestRequest.Status.valueOf(status.toUpperCase());
            return ingestRequestRepository.findByStatus(statusEnum, pageable);
        } else {
            return ingestRequestRepository.findAll(pageable);
        }
    }
    
    @Transactional
    public Map<String, Object> decideApproval(String requestId, ApprovalDecisionRequest decision, String approverId) {
        IngestRequest request = ingestRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("승인 요청을 찾을 수 없습니다: " + requestId));
        
        if (request.getStatus() != IngestRequest.Status.PENDING) {
            throw new IllegalArgumentException("이미 처리된 요청입니다.");
        }
        
        // 승인 결정 적용
        if ("APPROVE".equals(decision.getDecision())) {
            request.setStatus(IngestRequest.Status.APPROVED);
            log.info("승인 요청 승인됨: {} by {}", requestId, approverId);
            
            // TODO: 실제 문서 처리 로직 (벡터 DB 저장, 파일 처리 등)
            processApprovedRequest(request);
            
        } else if ("REJECT".equals(decision.getDecision())) {
            request.setStatus(IngestRequest.Status.REJECTED);
            log.info("승인 요청 반려됨: {} by {}", requestId, approverId);
        }
        
        request.setApproverId(approverId);
        request.setApprovedAt(LocalDateTime.now());
        request.setApprovalReason(decision.getReason());
        
        IngestRequest savedRequest = ingestRequestRepository.save(request);
        
        return Map.of(
            "success", true,
            "message", "APPROVE".equals(decision.getDecision()) ? "승인되었습니다." : "반려되었습니다.",
            "requestId", savedRequest.getId(),
            "status", savedRequest.getStatus().toString()
        );
    }
    
    public IngestRequest getApprovalRequest(String requestId) {
        return ingestRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("승인 요청을 찾을 수 없습니다: " + requestId));
    }
    
    private void processApprovedRequest(IngestRequest request) {
        log.info("승인된 요청 처리 시작: {}", request.getId());
        
        try {
            // 1. DocumentEntity 생성
            DocumentEntity document = createDocumentFromIngestRequest(request);
            
            // 2. 벡터 DB에 저장
            DocumentEntity savedDocument = documentRepository.save(document);
            log.info("문서가 벡터 DB에 저장됨: {}", savedDocument.getId());
            
            // 3. 처리 완료 상태로 변경
            request.setStatus(IngestRequest.Status.COMPLETED);
            request.setProcessedAt(LocalDateTime.now());
            
            log.info("승인된 요청 처리 완료: {}", request.getId());
        } catch (Exception e) {
            log.error("승인된 요청 처리 실패: {}", request.getId(), e);
            request.setStatus(IngestRequest.Status.FAILED);
            throw new RuntimeException("승인된 요청 처리 중 오류가 발생했습니다.", e);
        }
    }
    
    private DocumentEntity createDocumentFromIngestRequest(IngestRequest request) {
        // Serial 생성 (임시로 현재 시간 기반)
        String subCode = request.getProposedCategory().getSubCode();
        int number = (int) (System.currentTimeMillis() % 10000); // 임시 번호
        Serial serial = Serial.of(subCode, number);
        
        // Category 생성
        Category category = new Category(
            request.getProposedCategory().getMajorCode(),
            request.getProposedCategory().getMajorName(),
            request.getProposedCategory().getMidCode(),
            request.getProposedCategory().getMidName(),
            request.getProposedCategory().getSubCode(),
            request.getProposedCategory().getSubName()
        );
        
        // Source 생성
        Source source = new Source(
            request.getSource().getType(),
            request.getSource().getRepoUrl(),
            request.getSource().getFiles()
        );
        
        // DocumentEntity 생성
        DocumentEntity document = new DocumentEntity();
        document.setOwnerId(request.getOwnerId());
        document.setSerial(serial);
        document.setCategory(category);
        document.setPurpose(request.getProposedPurpose());
        document.setContent(request.getExtractedText());
        document.setSource(source);
        document.setTags(request.getTags());
        document.setRequestedAt(request.getRequestedAt());
        document.setApprovedAt(request.getApprovedAt());
        document.setVersion(1);
        
        // 벡터 임베딩은 나중에 별도 서비스에서 처리
        // document.setVectors(createVectorEmbeddings(request));
        
        return document;
    }
}
