package com.company.app.ingest.service;

import com.company.app.common.dto.Category;
import com.company.app.ingest.dto.SingleIngestRequest;
import com.company.app.ingest.entity.IngestRequest;
import com.company.app.ingest.repository.IngestRequestRepository;
import com.company.app.file.service.LocalFileStorageService;
import com.company.app.file.service.FileAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestService {
    
    private final IngestRequestRepository ingestRequestRepository;
    private final RepositoryAnalysisService repositoryAnalysisService;
    private final LocalFileStorageService localFileStorageService;
    private final FileAnalysisService fileAnalysisService;
    
    public Map<String, Object> createRepoPreview(String repoUrl, String accessToken) {
        try {
            log.info("Starting repository analysis for URL: {}", repoUrl);
            
            // 실제 레포지토리 분석 수행
            RepositoryAnalysisService.RepositoryAnalysisResult analysisResult = 
                repositoryAnalysisService.analyzeRepository(repoUrl, accessToken);
            
            Map<String, Object> preview = new HashMap<>();
            preview.put("extractedText", analysisResult.getExtractedText());
            preview.put("proposedCategory", analysisResult.getProposedCategory());
            preview.put("proposedTitle", analysisResult.getProposedTitle());
            preview.put("proposedPurpose", analysisResult.getProposedPurpose());
            preview.put("expectedEffects", analysisResult.getExpectedEffects());
            preview.put("techStack", analysisResult.getTechStack());
            preview.put("similarCandidates", analysisResult.getSimilarCandidates());
            
            log.info("Repository analysis completed successfully for URL: {}", repoUrl);
            return preview;
            
        } catch (Exception e) {
            log.error("Repository analysis failed for URL: {}", repoUrl, e);
            
            // 오류 발생 시 기본값 반환
            Map<String, Object> preview = new HashMap<>();
            preview.put("extractedText", "레포지토리 분석 중 오류가 발생했습니다. URL을 확인하고 다시 시도해주세요.");
            preview.put("proposedCategory", new Category("A", "소프트웨어", "A01", "웹개발", "A0101", "프론트엔드"));
            preview.put("proposedTitle", "프로젝트");
            preview.put("proposedPurpose", "소프트웨어 개발 프로젝트");
            preview.put("expectedEffects", "프로젝트의 구체적인 효과는 추가 분석이 필요합니다.");
            preview.put("techStack", List.of("Unknown"));
            preview.put("similarCandidates", List.of());
            
            return preview;
        }
    }
    
    public Map<String, Object> createFileIngestRequest(String userId, SingleIngestRequest request) {
        IngestRequest ingestRequest = new IngestRequest();
        ingestRequest.setOwnerId(userId);
        ingestRequest.setSource(new com.company.app.common.dto.Source(
                com.company.app.common.dto.Source.SourceType.PLAN, null, request.getFileIds()));
        ingestRequest.setProposedPurpose(request.getPurpose());
        ingestRequest.setTags(request.getTags());
        ingestRequest.setStatus(IngestRequest.Status.PENDING);
        ingestRequest.setRequestedAt(LocalDateTime.now());
        
        // 파일 분석 결과에서 온 카테고리와 제목 정보 저장
        if (request.getProposedCategory() != null) {
            ingestRequest.setProposedCategory(request.getProposedCategory());
        }
        if (request.getProposedTitle() != null) {
            ingestRequest.setProposedTitle(request.getProposedTitle());
        }
        if (request.getOriginalFileName() != null) {
            ingestRequest.setOriginalFileName(request.getOriginalFileName());
        }
        
        // DOCX/XLSX 파일의 경우 FileAnalysisService를 통해 내용 추출
        if (request.getFileIds() != null && !request.getFileIds().isEmpty()) {
            try {
                String fileId = request.getFileIds().get(0);
                // 임시 파일에서 내용 추출
                String extractedText = fileAnalysisService.extractTextFromTemporaryFile(fileId);
                if (extractedText != null && !extractedText.isEmpty()) {
                    ingestRequest.setExtractedText(extractedText);
                }
            } catch (Exception e) {
                log.warn("파일 내용 추출 실패: {}", e.getMessage());
                ingestRequest.setExtractedText("파일 내용을 추출할 수 없습니다.");
            }
        }
        
        IngestRequest saved = ingestRequestRepository.save(ingestRequest);
        
        Map<String, Object> response = new HashMap<>();
        response.put("id", saved.getId());
        response.put("status", saved.getStatus().name());
        response.put("requestedAt", saved.getRequestedAt());
        
        return response;
    }
    
    public Map<String, Object> createRepoIngestRequest(String userId, SingleIngestRequest request) {
        IngestRequest ingestRequest = new IngestRequest();
        ingestRequest.setOwnerId(userId);
        ingestRequest.setSource(new com.company.app.common.dto.Source(
                com.company.app.common.dto.Source.SourceType.REPO, request.getRepoUrl(), null));
        ingestRequest.setProposedPurpose(request.getPurpose());
        ingestRequest.setTags(request.getTags());
        ingestRequest.setStatus(IngestRequest.Status.PENDING);
        ingestRequest.setRequestedAt(LocalDateTime.now());
        
        // 레포지토리 분석 결과를 저장 (간단한 버전)
        try {
            var preview = createRepoPreview(request.getRepoUrl(), request.getAccessToken());
            if (preview.containsKey("extractedText")) {
                ingestRequest.setExtractedText((String) preview.get("extractedText"));
            }
            if (preview.containsKey("proposedCategory")) {
                ingestRequest.setProposedCategory((Category) preview.get("proposedCategory"));
            }
            if (preview.containsKey("proposedTitle")) {
                ingestRequest.setProposedTitle((String) preview.get("proposedTitle"));
            }
        } catch (Exception e) {
            log.warn("레포지토리 분석 중 오류 발생, 기본값으로 설정: {}", e.getMessage());
            ingestRequest.setExtractedText("레포지토리 분석 중 오류가 발생했습니다.");
        }
        
        IngestRequest saved = ingestRequestRepository.save(ingestRequest);
        
        // 임시 저장소에 분석 결과 저장
        try {
            if (saved.getExtractedText() != null && !saved.getExtractedText().isEmpty()) {
                String tempFilePath = localFileStorageService.saveTempRepositoryAnalysis(
                    saved.getId(), 
                    request.getRepoUrl(), 
                    saved.getExtractedText()
                );
                log.info("레포지토리 분석 결과가 임시 저장소에 저장됨: {}", tempFilePath);
            }
        } catch (Exception e) {
            log.warn("임시 저장소에 분석 결과 저장 실패: {}", e.getMessage());
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("id", saved.getId());
        response.put("status", saved.getStatus().name());
        response.put("requestedAt", saved.getRequestedAt());
        response.put("message", "레포지토리 등록 요청이 성공적으로 제출되었습니다.");
        
        return response;
    }
    
    
    public Map<String, Object> resubmitRequest(String id, String userId, Map<String, Object> request) {
        // TODO: 재등록 로직 구현
        Map<String, Object> response = new HashMap<>();
        response.put("id", id);
        response.put("status", "PENDING");
        response.put("requestedAt", LocalDateTime.now());
        
        return response;
    }
}
