package com.company.app.chat.service;

import com.company.app.chat.dto.ChatQueryRequest;
import com.company.app.chat.dto.ChatQueryResponse;
import com.company.app.search.service.ElasticsearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ElasticsearchIndexService elasticsearchIndexService;
    private final LLMService llmService;
    
    public ChatQueryResponse processQuery(String userId, ChatQueryRequest request) {
        System.out.println("=== ChatService.processQuery 호출됨 ===");
        System.out.println("사용자 ID: " + userId);
        System.out.println("질문: " + request.getQuery());
        try {
            log.info("Processing chat query from user {}: {}", userId, request.getQuery());
            
            // 1. 하이브리드 검색으로 관련 청크 찾기 (RRF 기반)
            var chunkResults = elasticsearchIndexService.searchChunks(request.getQuery(), 8);
            log.info("청크 검색 결과: {} 개 청크 발견", chunkResults != null ? chunkResults.size() : 0);
            
            // 2. 검색 결과를 소스로 변환 및 컨텍스트 구성
            List<ChatQueryResponse.Source> sources = new ArrayList<>();
            List<String> contextDocuments = new ArrayList<>();
            
            // RRF 점수 임계값 설정 (낮은 점수 청크 제외)
            double relevanceThreshold = 0.01; // RRF 점수는 작은 값이므로 낮은 임계값 사용
            
            if (chunkResults != null && !chunkResults.isEmpty()) {
                for (var chunk : chunkResults) {
                    double score = chunk.getScore();
                    
                    // 연관성이 낮은 청크는 제외
                    if (score < relevanceThreshold) {
                        log.info("연관성 낮은 청크 제외: DocId={}, ChunkIndex={}, Score={}", 
                            chunk.getDocumentId(), chunk.getChunkIndex(), score);
                        continue;
                    }
                    
                    log.info("청크 변환: DocId={}, ChunkIndex={}, Content 길이={}, Score={}", 
                        chunk.getDocumentId(), 
                        chunk.getChunkIndex(),
                        chunk.getContent() != null ? chunk.getContent().length() : 0,
                        score);
                    
                    // 문서 정보 조회
                    String filename = chunk.getDocumentSerial() != null ? 
                        chunk.getDocumentSerial() + ".md" : "문서_" + chunk.getChunkIndex() + ".md";
                    
                    // 소스 정보 생성 (청크 기반)
                    sources.add(ChatQueryResponse.Source.builder()
                            .docId(chunk.getDocumentId())
                            .serial(chunk.getDocumentSerial() != null ? chunk.getDocumentSerial() : "N/A")
                            .snippet(chunk.getContent() != null ? 
                                chunk.getContent().substring(0, Math.min(200, chunk.getContent().length())) + "..." : 
                                "내용 없음")
                            .filename(filename)
                            .score(score) // RRF 점수 사용
                            .build());
                    
                    // LLM 컨텍스트용 청크 내용 추가
                    if (chunk.getContent() != null && !chunk.getContent().trim().isEmpty()) {
                        String chunkContext = String.format(
                            "[문서: %s, 섹션: %s]\n%s", 
                            chunk.getDocumentSerial() != null ? chunk.getDocumentSerial() : "N/A",
                            chunk.getSectionHint() != null ? chunk.getSectionHint() : "섹션 정보 없음",
                            chunk.getContent()
                        );
                        contextDocuments.add(chunkContext);
                    }
                    
                    // 최대 5개 청크만 사용 (토큰 제한 고려)
                    if (sources.size() >= 5) {
                        break;
                    }
                }
            } else {
                log.warn("청크 검색 결과가 비어있습니다.");
            }
            
            // 3. 프롬프트 생성 및 LLM을 사용한 답변 생성
            log.info("LLM 서비스 호출 시작 - 컨텍스트 청크 수: {}", contextDocuments.size());
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
     * LLM을 위한 프롬프트 생성 (청크 기반)
     */
    private String buildPrompt(String query, List<String> contextChunks) {
        // 컨텍스트 청크들을 하나의 문자열로 결합
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < contextChunks.size(); i++) {
            context.append(String.format("참고 자료 %d:\n%s\n\n", i + 1, contextChunks.get(i)));
        }
        
        // 청크 기반 질문 중심의 프롬프트 구성
        return String.format("""
            당신은 기술 문서 검색 시스템의 AI 어시스턴트입니다. 주어진 문서 청크들을 바탕으로 사용자의 질문에 정확하고 직접적인 답변을 제공해야 합니다.
            
            ## 답변 가이드라인:
            1. 질문과 직접적으로 관련된 정보만 답변하세요
            2. 여러 문서 청크에서 관련 정보를 종합하여 답변하세요
            3. 답변은 간결하고 명확하게 작성하세요
            4. 질문에 대한 구체적인 답변만 제공하고, 추가적인 제안이나 설명은 하지 마세요
            5. 관련 정보가 없으면 "질문과 관련된 정보를 찾을 수 없습니다"라고 답변하세요
            6. 한국어로 자연스럽게 답변하세요
            7. 가능하면 어떤 문서나 섹션에서 정보를 가져왔는지 간단히 언급하세요
            
            ## 참고 문서 청크:
            %s
            
            ## 사용자 질문:
            %s
            
            위 가이드라인에 따라 참고 자료를 바탕으로 질문에 직접적으로 답변해주세요.
            """, context.toString(), query);
    }
    
}
