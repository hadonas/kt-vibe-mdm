package com.company.app.chat.service;

import com.company.app.chat.dto.ChatQueryRequest;
import com.company.app.chat.dto.ChatQueryResponse;
import com.company.app.search.service.FaissVectorSearchService;
import com.company.app.document.entity.DocumentEntity;
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
    
    public ChatQueryResponse processQuery(String userId, ChatQueryRequest request) {
        System.out.println("=== ChatService.processQuery 호출됨 ===");
        System.out.println("사용자 ID: " + userId);
        System.out.println("질문: " + request.getQuery());
        try {
            log.info("Processing chat query from user {}: {}", userId, request.getQuery());
            
            // 1. 하이브리드 검색으로 관련 문서 찾기 (FAISS Vector + Text Search, 점수 포함)
            var searchResults = faissVectorSearchService.hybridSearchWithScores(request.getQuery(), 5);
            log.info("벡터 검색 결과: {} 개 문서 발견", searchResults != null ? searchResults.size() : 0);
            
            // 2. 검색 결과를 소스로 변환 (유사도 순으로 정렬됨)
            List<ChatQueryResponse.Source> sources = new ArrayList<>();
            List<String> contextDocuments = new ArrayList<>();
            
            if (searchResults != null && !searchResults.isEmpty()) {
                for (var result : searchResults) {
                    DocumentEntity doc = result.getDocument();
                    double score = result.getScore();
                    
                    log.info("문서 변환: ID={}, Serial={}, Content 길이={}, Score={}", 
                        doc.getId(), 
                        doc.getSerial() != null ? doc.getSerial().getFull() : "N/A",
                        doc.getContent() != null ? doc.getContent().length() : 0,
                        score);
                    
                    // 소스 정보 생성 (실제 검색 점수 사용)
                    sources.add(ChatQueryResponse.Source.builder()
                            .docId(doc.getId())
                            .serial(doc.getSerial() != null ? doc.getSerial().getFull() : "N/A")
                            .snippet(doc.getContent() != null ? 
                                doc.getContent().substring(0, Math.min(200, doc.getContent().length())) + "..." : 
                                "내용 없음")
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
            
            // 3. LLM을 사용한 답변 생성
            log.info("LLM 서비스 호출 시작 - 컨텍스트 문서 수: {}", contextDocuments.size());
            String answer = llmService.generateAnswer(request.getQuery(), contextDocuments);
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
    
}
