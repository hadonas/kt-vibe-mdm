package com.company.app.approval.service;

import com.company.app.approval.dto.ApprovalDecisionRequest;
import com.company.app.ingest.entity.IngestRequest;
import com.company.app.ingest.repository.IngestRequestRepository;
import com.company.app.document.entity.DocumentEntity;
import com.company.app.document.repository.DocumentRepository;
import com.company.app.common.dto.Serial;
import com.company.app.common.dto.Category;
import com.company.app.common.dto.Source;
import com.company.app.file.service.LocalFileStorageService;
import com.company.app.file.service.FileAnalysisService;
import com.company.app.ingest.service.RepositoryAnalysisService;
import com.company.app.ingest.service.ChunkIngestionService;
import com.company.app.search.service.ElasticsearchIndexService;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalService {
    
    private final IngestRequestRepository ingestRequestRepository;
    private final DocumentRepository documentRepository;
    private final LocalFileStorageService localFileStorageService;
    private final FileAnalysisService fileAnalysisService;
    private final RepositoryAnalysisService repositoryAnalysisService;
    private final ChunkIngestionService chunkIngestionService; // 청킹 서비스
    private final ElasticsearchIndexService elasticsearchIndexService; // ES 색인
    
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
            
            // 2. 벡터 DB에 저장 (승인 시에만)
            DocumentEntity savedDocument = documentRepository.save(document);
            
            // 3. 샤드 ID 설정 (문서 ID가 생성된 후)
            savedDocument.setShardId(calculateShardId(savedDocument.getId()));
            savedDocument = documentRepository.save(savedDocument);
            
            log.info("문서가 벡터 DB에 저장됨: {}, 샤드 ID: {}", savedDocument.getId(), savedDocument.getShardId());
            
            // 4. 임시 파일을 최종 저장소로 이동
            String savedFilePath = moveTempFileToFinal(request, savedDocument);
            log.info("문서가 최종 저장소에 저장됨: {}", savedFilePath);
            
            // 5. 의미 기반 청킹 및 청크 저장 + ES 색인
            try {
                var chunks = chunkIngestionService.chunkAndPersist(savedDocument);
                elasticsearchIndexService.indexChunks(chunks);
            } catch (Exception ce) {
                log.error("문서 청킹/색인 실패: {}", savedDocument.getId(), ce);
            }
            // 6. 처리 완료 상태로 변경
            request.setStatus(IngestRequest.Status.COMPLETED);
            request.setProcessedAt(LocalDateTime.now());
            
            log.info("승인된 요청 처리 완료: {}", request.getId());
        } catch (Exception e) {
            log.error("승인된 요청 처리 실패: {}", request.getId(), e);
            request.setStatus(IngestRequest.Status.FAILED);
            throw new RuntimeException("승인된 요청 처리 중 오류가 발생했습니다.", e);
        }
    }
    
    /**
     * 임시 파일을 최종 저장소로 이동
     */
    private String moveTempFileToFinal(IngestRequest request, DocumentEntity document) {
        try {
            // 소스 타입에 따라 다른 처리
            if (request.getSource().getType().equals("REPO") && request.getSource().getRepoUrl() != null) {
                // GitHub 레포지토리인 경우: 분석 결과를 최종 저장소로 이동
                String analysisResult = generateRepositoryAnalysisResult(request);
                return localFileStorageService.saveRepositoryAnalysis(
                    document, 
                    request.getSource().getRepoUrl(), 
                    analysisResult
                );
            } else {
                // 일반 파일인 경우: 임시 파일을 최종 저장소로 이동
                // TODO: 파일 업로드의 경우 임시 저장소에서 최종 저장소로 이동하는 로직 구현
                return localFileStorageService.saveDocument(document);
            }
        } catch (Exception e) {
            log.error("임시 파일을 최종 저장소로 이동 중 오류 발생: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 문서를 로컬 파일 시스템에 저장 (기존 메서드 - 호환성을 위해 유지)
     */
    private String saveDocumentToLocalFileSystem(DocumentEntity document, IngestRequest request) {
        try {
            // 소스 타입에 따라 다른 저장 방식 사용
            if (request.getSource().getType().equals("REPO") && request.getSource().getRepoUrl() != null) {
                // GitHub 레포지토리인 경우: 분석 결과와 함께 저장
                String analysisResult = generateRepositoryAnalysisResult(request);
                return localFileStorageService.saveRepositoryAnalysis(
                    document, 
                    request.getSource().getRepoUrl(), 
                    analysisResult
                );
            } else {
                // 일반 파일인 경우: 파일 내용 그대로 저장
                return localFileStorageService.saveDocument(document);
            }
        } catch (Exception e) {
            log.error("로컬 파일 저장 중 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("로컬 파일 저장에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * GitHub 레포지토리 분석 결과 생성
     */
    private String generateRepositoryAnalysisResult(IngestRequest request) {
        try {
            // RepositoryAnalysisService를 사용하여 분석 수행
            if (request.getSource().getRepoUrl() != null) {
                // 실제 분석은 RepositoryAnalysisService에서 수행
                // 여기서는 추출된 텍스트를 기반으로 간단한 분석 결과 생성
                StringBuilder analysis = new StringBuilder();
                analysis.append("## 레포지토리 분석 개요\n");
                analysis.append("- **URL**: ").append(request.getSource().getRepoUrl()).append("\n");
                analysis.append("- **분석일**: ").append(LocalDateTime.now()).append("\n");
                analysis.append("- **파일 수**: ").append(request.getSource().getFiles() != null ? request.getSource().getFiles().size() : 0).append("개\n\n");
                
                analysis.append("## 추출된 내용\n");
                analysis.append(request.getExtractedText());
                
                return analysis.toString();
            } else {
                return "레포지토리 URL이 없습니다.";
            }
        } catch (Exception e) {
            log.error("레포지토리 분석 결과 생성 중 오류: {}", e.getMessage(), e);
            return "분석 중 오류가 발생했습니다: " + e.getMessage();
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
        document.setPurpose(request.getProposedTitle() != null ? request.getProposedTitle() : 
                           (request.getProposedPurpose() != null ? request.getProposedPurpose() : "제목 없음"));
        
        // DOCX/XLSX 파일의 경우 FileAnalysisService를 통해 내용 추출
        String content = request.getExtractedText();
        if (content == null && request.getSource().getType() == Source.SourceType.PLAN && 
            request.getSource().getFiles() != null && !request.getSource().getFiles().isEmpty()) {
            try {
                String fileId = request.getSource().getFiles().get(0);
                // 임시 파일에서 내용 추출 (이미 FileAnalysisService에서 처리된 내용)
                // 실제로는 IngestRequest에 extractedText가 저장되어야 함
                content = "DOCX 파일 내용이 추출되지 않았습니다. 파일 분석을 다시 수행해주세요.";
            } catch (Exception e) {
                log.warn("파일 내용 추출 실패: {}", e.getMessage());
                content = "파일 내용을 추출할 수 없습니다.";
            }
        }
        document.setContent(content);
        document.setSource(source);
        document.setTags(request.getTags());
        document.setRequestedAt(request.getRequestedAt());
        document.setApprovedAt(LocalDateTime.now()); // 승인 시점을 현재 시간으로 설정
        document.setVersion(1);
        
        // 원본 파일명 설정 (파일 기반 등록인 경우)
        if (request.getSource().getType() == Source.SourceType.PLAN) {
            // IngestRequest에 저장된 원본 파일명 사용
            if (request.getOriginalFileName() != null && !request.getOriginalFileName().isEmpty()) {
                document.setOriginalFileName(request.getOriginalFileName());
            } else if (request.getSource().getFiles() != null && !request.getSource().getFiles().isEmpty()) {
                // 원본 파일명이 없으면 첫 번째 파일 ID를 사용
                String firstFileName = request.getSource().getFiles().get(0);
                document.setOriginalFileName(firstFileName);
            }
        }
        
        // 벡터 임베딩은 나중에 별도 서비스에서 처리
        // document.setVectors(createVectorEmbeddings(request));
        
        return document;
    }
    
    /**
     * 반려된 요청 처리
     */
    public void processRejectedRequest(IngestRequest request, String approverId, String reason) {
        log.info("반려 요청 처리 시작: {}, approver: {}", request.getId(), approverId);
        
        try {
            // 요청 상태를 REJECTED로 변경
            request.setStatus(IngestRequest.Status.REJECTED);
            request.setApprovedAt(LocalDateTime.now());
            request.setApproverId(approverId);
            request.setApprovalReason(reason);
            
            // 요청 저장
            ingestRequestRepository.save(request);
            
            // 임시 파일 정리
            localFileStorageService.cleanupTempFiles(request.getId());
            
            log.info("반려 요청 처리 완료: {}", request.getId());
            
        } catch (Exception e) {
            log.error("반려 요청 처리 중 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("반려 요청 처리 중 오류가 발생했습니다.", e);
        }
    }
    
    /**
     * 문서 ID를 기반으로 샤드 ID 계산
     * @param documentId 문서 ID
     * @return 샤드 ID (0-7 범위)
     */
    private Integer calculateShardId(String documentId) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(documentId.getBytes());
            
            // 해시값의 첫 번째 바이트를 사용하여 0-7 범위의 샤드 ID 생성
            int shardId = Math.abs(hash[0]) % 8;
            return shardId;
        } catch (NoSuchAlgorithmException e) {
            log.warn("MD5 해시 계산 실패, 기본 샤드 ID 사용: {}", e.getMessage());
            return 0; // 기본값
        }
    }
}
