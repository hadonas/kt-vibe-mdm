package com.company.app.search.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${llm.api.key:sk-test-key}")
    private String apiKey;
    
    @Value("${llm.api.base:https://api.openai.com/v1}")
    private String apiBase;
    
    /**
     * 텍스트를 벡터 임베딩으로 변환
     */
    public List<Double> generateEmbedding(String text) {
        // API 키가 테스트용이면 Mock 응답 사용
        if (apiKey.equals("sk-test-key") || apiKey.isEmpty()) {
            log.info("테스트 API 키 사용, Mock 임베딩 생성: {}자", text.length());
            return generateMockEmbedding(text);
        }
        
        try {
            log.info("텍스트 임베딩 생성 시작: {}자", text.length());
            
            // OpenAI Embeddings API 요청
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("input", text);
            requestBody.put("model", "text-embedding-3-small"); // 1536 차원
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            String url = apiBase + "/embeddings";
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                JsonNode embeddingNode = jsonNode.get("data").get(0).get("embedding");
                
                List<Double> embedding = objectMapper.convertValue(embeddingNode, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Double.class));
                
                log.info("임베딩 생성 완료: {}차원", embedding.size());
                return embedding;
            } else {
                log.error("임베딩 API 오류: {}", response.getStatusCode());
                return generateMockEmbedding(text);
            }
            
        } catch (Exception e) {
            log.error("임베딩 생성 중 오류 발생: {}", e.getMessage(), e);
            return generateMockEmbedding(text);
        }
    }
    
    /**
     * 여러 텍스트를 한 번에 임베딩으로 변환
     */
    public List<List<Double>> generateEmbeddings(List<String> texts) {
        // API 키가 테스트용이면 Mock 응답 사용
        if (apiKey.equals("sk-test-key") || apiKey.isEmpty()) {
            log.info("테스트 API 키 사용, Mock 배치 임베딩 생성: {}개", texts.size());
            return texts.stream()
                .map(this::generateMockEmbedding)
                .toList();
        }
        
        try {
            log.info("배치 임베딩 생성 시작: {}개 텍스트", texts.size());
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("input", texts);
            requestBody.put("model", "text-embedding-3-small");
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            String url = apiBase + "/embeddings";
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                JsonNode dataNode = jsonNode.get("data");
                
                List<List<Double>> embeddings = objectMapper.convertValue(dataNode,
                    objectMapper.getTypeFactory().constructCollectionType(List.class,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Double.class)));
                
                log.info("배치 임베딩 생성 완료: {}개", embeddings.size());
                return embeddings;
            } else {
                log.error("배치 임베딩 API 오류: {}", response.getStatusCode());
                return texts.stream()
                    .map(this::generateMockEmbedding)
                    .toList();
            }
            
        } catch (Exception e) {
            log.error("배치 임베딩 생성 중 오류 발생: {}", e.getMessage(), e);
            return texts.stream()
                .map(this::generateMockEmbedding)
                .toList();
        }
    }
    
    /**
     * Mock 임베딩 생성 (API 실패 시 사용)
     */
    private List<Double> generateMockEmbedding(String text) {
        // 간단한 해시 기반 Mock 임베딩 (1536 차원)
        List<Double> embedding = new java.util.ArrayList<>();
        int hash = text.hashCode();
        
        for (int i = 0; i < 1536; i++) {
            double value = Math.sin(hash + i) * 0.1; // -0.1 ~ 0.1 범위
            embedding.add(value);
        }
        
        log.warn("Mock 임베딩 생성: {}자 텍스트", text.length());
        return embedding;
    }
    
    /**
     * 두 벡터 간의 코사인 유사도 계산
     */
    public double cosineSimilarity(List<Double> vectorA, List<Double> vectorB) {
        if (vectorA.size() != vectorB.size()) {
            throw new IllegalArgumentException("벡터 차원이 다릅니다");
        }
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < vectorA.size(); i++) {
            dotProduct += vectorA.get(i) * vectorB.get(i);
            normA += vectorA.get(i) * vectorA.get(i);
            normB += vectorB.get(i) * vectorB.get(i);
        }
        
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
