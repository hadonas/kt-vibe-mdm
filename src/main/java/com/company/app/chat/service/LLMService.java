package com.company.app.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class LLMService {
    
    @Value("${LLM_API_BASE:https://api.openai.com/v1}")
    private String llmApiBase;
    
    @Value("${LLM_MODEL:gpt-5-mini}")
    private String llmModel;
    
    @Value("${AI_API_KEY:}")
    private String apiKey;
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public String generateAnswer(String query, List<String> contextDocuments) {
        log.info("LLM 답변 생성 시작 - 질문: '{}', 컨텍스트 문서 수: {}", query, contextDocuments.size());
        log.info("API 키 상태: {}", apiKey != null && !apiKey.isEmpty() && !apiKey.equals("your-openai-api-key-here") ? "설정됨" : "미설정");
        log.info("API 키 값: {}", apiKey != null ? apiKey.substring(0, Math.min(10, apiKey.length())) + "..." : "null");
        
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("your-openai-api-key-here")) {
            log.warn("OpenAI API 키가 설정되지 않았습니다. Mock 응답을 반환합니다.");
            return generateMockAnswer(query, contextDocuments);
        }
        
        try {
            // 컨텍스트 문서들을 하나의 문자열로 결합
            StringBuilder context = new StringBuilder();
            for (int i = 0; i < contextDocuments.size(); i++) {
                context.append(String.format("문서 %d:\n%s\n\n", i + 1, contextDocuments.get(i)));
            }
            
            // 프롬프트 구성 (간결하게)
            String prompt = String.format("""
                다음 문서를 참고하여 질문에 답변하세요.
                
                문서:
                %s
                
                질문: %s
                
                한국어로 답변하세요. 관련 정보가 없으면 "관련 정보를 찾을 수 없습니다"라고 답변하세요.
                """, context.toString(), query);
            
            // OpenAI API 요청 구성
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", llmModel);
            requestBody.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
            ));
            requestBody.put("max_completion_tokens", 2000);
            // gpt-5-mini는 temperature 기본값(1)만 지원하므로 제거
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            String url = llmApiBase + "/chat/completions";
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                String responseBody = response.getBody();
                log.info("LLM API 응답 본문: {}", responseBody);
                JsonNode jsonNode = objectMapper.readTree(responseBody);
                String answer = jsonNode.get("choices").get(0).get("message").get("content").asText();
                log.info("LLM 응답 생성 완료: {}자", answer.length());
                return answer;
            } else {
                log.error("LLM API 오류: {}", response.getStatusCode());
                return generateMockAnswer(query, contextDocuments);
            }
            
        } catch (Exception e) {
            log.error("LLM API 호출 중 오류 발생: {}", e.getMessage(), e);
            System.out.println("LLM API 에러 상세: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            if (e.getCause() != null) {
                System.out.println("원인: " + e.getCause().getMessage());
            }
            return generateMockAnswer(query, contextDocuments);
        }
    }
    
    private String generateMockAnswer(String query, List<String> contextDocuments) {
        if (contextDocuments.isEmpty()) {
            return "죄송합니다. 관련된 문서를 찾을 수 없습니다. 다른 키워드로 검색해보시거나 관리자에게 문의해주세요.";
        }
        
        StringBuilder answer = new StringBuilder();
        answer.append("검색된 관련 문서를 바탕으로 답변드리겠습니다:\n\n");
        
        for (int i = 0; i < contextDocuments.size(); i++) {
            String doc = contextDocuments.get(i);
            String snippet = doc.length() > 200 ? doc.substring(0, 200) + "..." : doc;
            answer.append(String.format("%d. %s\n", i + 1, snippet));
        }
        
        answer.append("\n위 문서들을 참고하여 질문에 대한 답변을 찾으실 수 있습니다.");
        return answer.toString();
    }
}
