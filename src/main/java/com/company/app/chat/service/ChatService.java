package com.company.app.chat.service;

import com.company.app.chat.dto.ChatQueryRequest;
import com.company.app.chat.dto.ChatQueryResponse;
import com.company.app.search.service.FaissVectorSearchService;
import com.company.app.document.entity.DocumentEntity;
import com.company.app.file.service.LocalFileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final FaissVectorSearchService faissVectorSearchService;
    private final LLMService llmService;
    private final LocalFileStorageService localFileStorageService;
    
    public ChatQueryResponse processQuery(String userId, ChatQueryRequest request) {
        System.out.println("=== ChatService.processQuery 호출됨 ===");
        System.out.println("사용자 ID: " + userId);
        System.out.println("질문: " + request.getQuery());
        try {
            log.info("Processing chat query from user {}: {}", userId, request.getQuery());
            
            // 1. 하이브리드 검색으로 관련 문서 찾기 (FAISS Vector + Text Search, 점수 포함)
            var searchResults = faissVectorSearchService.hybridSearchWithScores(request.getQuery(), 5);
            log.info("벡터 검색 결과: {} 개 문서 발견", searchResults != null ? searchResults.size() : 0);
            
            // 2. 검색 결과를 소스로 변환 (유사도 순으로 정렬됨, 연관성 낮은 문서 필터링)
            List<ChatQueryResponse.Source> sources = new ArrayList<>();
            List<String> contextDocuments = new ArrayList<>();
            
            // 유사도 임계값 설정 (0.3 이하는 제외)
            double relevanceThreshold = 0.3;
            
            if (searchResults != null && !searchResults.isEmpty()) {
                for (var result : searchResults) {
                    DocumentEntity doc = result.getDocument();
                    double score = result.getScore();
                    
                    // 연관성이 낮은 문서는 제외
                    if (score < relevanceThreshold) {
                        log.info("연관성 낮은 문서 제외: ID={}, Score={}", doc.getId(), score);
                        continue;
                    }
                    
                    log.info("문서 변환: ID={}, Serial={}, Content 길이={}, Score={}", 
                        doc.getId(), 
                        doc.getSerial() != null ? doc.getSerial().getFull() : "N/A",
                        doc.getContent() != null ? doc.getContent().length() : 0,
                        score);
                    
                    // 파일명 조회
                    String filePath = localFileStorageService.getExistingDocumentFilePath(doc);
                    String filename = filePath != null ? 
                        filePath.substring(filePath.lastIndexOf("/") + 1) : 
                        (doc.getSerial() != null ? doc.getSerial().getFull() : "N/A") + ".md";
                    
                    // 소스 정보 생성 (실제 검색 점수 사용)
                    sources.add(ChatQueryResponse.Source.builder()
                            .docId(doc.getId())
                            .serial(doc.getSerial() != null ? doc.getSerial().getFull() : "N/A")
                            .snippet(doc.getContent() != null ? 
                                doc.getContent().substring(0, Math.min(200, doc.getContent().length())) + "..." : 
                                "내용 없음")
                            .filename(filename)
                            .score(score) // 실제 검색 점수 사용
                            .build());
                    
                    // LLM 컨텍스트용 전체 문서 내용 추가
                    if (doc.getContent() != null && !doc.getContent().trim().isEmpty()) {
                        contextDocuments.add(doc.getContent());
                    }
                }
            } else {
                log.warn("벡터 검색 결과가 비어있습니다.");
            }
            
            // 3. 프롬프트 생성 및 LLM을 사용한 답변 생성
            log.info("LLM 서비스 호출 시작 - 컨텍스트 문서 수: {}", contextDocuments.size());
            String prompt = buildPrompt(request.getQuery(), contextDocuments);
            String answer = llmService.generateAnswer(prompt);
            log.info("LLM 서비스 호출 완료 - 답변 길이: {}", answer.length());
            
            return ChatQueryResponse.builder()
                    .answer(answer)
                    .sources(sources)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error processing chat query", e);
            
            // 에러 발생 시 기본 응답
            return ChatQueryResponse.builder()
                    .answer("죄송합니다. 현재 시스템에 문제가 발생했습니다. 잠시 후 다시 시도해주세요.")
                    .sources(new ArrayList<>())
                    .build();
        }
    }
    
    /**
     * LLM을 위한 프롬프트 생성
     */
    private String buildPrompt(String query, List<String> contextDocuments) {
        // 컨텍스트 문서들을 하나의 문자열로 결합
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < contextDocuments.size(); i++) {
            context.append(String.format("문서 %d:\n%s\n\n", i + 1, contextDocuments.get(i)));
        }
        
        // 질문 중심의 프롬프트 구성
        return String.format("""
            당신은 기술 문서 검색 시스템의 AI 어시스턴트입니다. 주어진 문서들을 바탕으로 사용자의 질문에 정확하고 직접적인 답변을 제공해야 합니다.
            
            ## 답변 가이드라인:
            1. 질문과 직접적으로 관련된 정보만 답변하세요
            2. 질문과 무관한 문서나 정보는 언급하지 마세요
            3. 답변은 간결하고 명확하게 작성하세요
            4. 질문에 대한 구체적인 답변만 제공하고, 추가적인 제안이나 설명은 하지 마세요
            5. 관련 정보가 없으면 "질문과 관련된 정보를 찾을 수 없습니다"라고 답변하세요
            6. 한국어로 자연스럽게 답변하세요
            
            ## 참고 문서:
            %s
            
            ## 사용자 질문:
            %s
            
            위 가이드라인에 따라 질문에 직접적으로 답변해주세요.
            """, context.toString(), query);
    }
    
}
