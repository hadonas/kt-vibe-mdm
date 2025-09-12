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
    
    public String generateAnswer(String prompt) {
        log.info("LLM 답변 생성 시작 - 프롬프트 길이: {}자", prompt.length());
        log.info("API 키 상태: {}", apiKey != null && !apiKey.isEmpty() && !apiKey.equals("your-openai-api-key-here") ? "설정됨" : "미설정");
        
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("your-openai-api-key-here")) {
            log.warn("OpenAI API 키가 설정되지 않았습니다. Mock 응답을 반환합니다.");
            return generateMockAnswer(prompt);
        }
        
        try {
            return callLLMAPI(prompt, 2000);
        } catch (Exception e) {
            log.error("LLM API 호출 중 오류 발생: {}", e.getMessage(), e);
            return generateMockAnswer(prompt);
        }
    }
    
    private String generateMockAnswer(String prompt) {
        return "죄송합니다. LLM 서비스에 연결할 수 없습니다. 관리자에게 문의해주세요.";
    }
    
    /**
     * 단순 텍스트 생성 (쿼리 확장용)
     */
    public String generateText(String prompt) {
        log.info("LLM 텍스트 생성 시작 - 프롬프트: '{}'", prompt);
        
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("your-openai-api-key-here")) {
            log.warn("OpenAI API 키가 설정되지 않았습니다. Mock 응답을 반환합니다.");
            return generateMockText(prompt);
        }
        
        try {
            return callLLMAPI(prompt, 1000);
        } catch (Exception e) {
            log.error("LLM API 호출 중 오류 발생: {}", e.getMessage(), e);
            return generateMockText(prompt);
        }
    }
    
    /**
     * 공통 LLM API 호출 메서드
     */
    private String callLLMAPI(String prompt, int maxTokens) {
        try {
            // OpenAI API 요청 구성
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", llmModel);
            requestBody.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
            ));
            requestBody.put("max_completion_tokens", maxTokens);
            
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
                throw new RuntimeException("LLM API 오류: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("LLM API 호출 중 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("LLM API 호출 실패", e);
        }
    }
    
    private String generateMockText(String prompt) {
        // 간단한 모의 응답 생성
        if (prompt.contains("백엔드")) {
            return "1. spring, java, backend, 서버, server, api\n" +
                   "2. 데이터베이스, database, mysql, mongodb\n" +
                   "3. 웹개발, 백엔드, 서버사이드\n" +
                   "4. spring boot, django, flask, fastapi";
        } else if (prompt.contains("프론트엔드") || prompt.contains("nextjs")) {
            return "1. react, nextjs, frontend, 웹, web, javascript\n" +
                   "2. 사용자 인터페이스, ui, ux, 클라이언트\n" +
                   "3. 웹개발, 프론트엔드, 웹사이트\n" +
                   "4. next.js, vue, angular, typescript";
        } else if (prompt.contains("웹")) {
            return "1. 웹개발, web development, 웹사이트, website\n" +
                   "2. 프론트엔드, 백엔드, fullstack\n" +
                   "3. 웹 애플리케이션, web application, 웹앱\n" +
                   "4. html, css, javascript, react, spring";
        } else {
            return "1. 개발, development, 프로젝트, project\n" +
                   "2. 시스템, system, 플랫폼, platform\n" +
                   "3. 소프트웨어, software, 애플리케이션, application\n" +
                   "4. 기술, technology, 솔루션, solution";
        }
    }
}
