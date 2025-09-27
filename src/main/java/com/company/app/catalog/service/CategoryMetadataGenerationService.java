package com.company.app.catalog.service;

import com.company.app.catalog.entity.CatalogNode;
import com.company.app.catalog.repository.CatalogNodeRepository;
import com.company.app.chat.service.LLMService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * LLM을 사용한 카테고리 메타데이터 자동 생성 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryMetadataGenerationService {
    
    private final LLMService llmService;
    private final CatalogNodeRepository catalogNodeRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 카테고리명을 기반으로 메타데이터 자동 생성
     */
    public CategoryMetadata generateCategoryMetadata(String categoryName, String categoryCode, 
                                                   String parentCode, Integer level) {
        try {
            log.info("카테고리 메타데이터 자동 생성 시작: {} ({})", categoryName, categoryCode);
            
            // 부모 카테고리 정보 조회
            String parentContext = "";
            if (parentCode != null) {
                Optional<CatalogNode> parent = catalogNodeRepository.findByCode(parentCode);
                if (parent.isPresent()) {
                    parentContext = String.format("상위 카테고리: %s (%s)", 
                        parent.get().getName(), parent.get().getDescription());
                }
            }
            
            // LLM 프롬프트 생성
            String prompt = buildMetadataPrompt(categoryName, categoryCode, parentContext, level);
            
            // LLM 호출
            String llmResponse = llmService.generateAnswer(prompt);
            
            // JSON 응답 파싱
            CategoryMetadata metadata = parseMetadataResponse(llmResponse);
            
            log.info("카테고리 메타데이터 생성 완료: {}", categoryName);
            return metadata;
            
        } catch (Exception e) {
            log.error("카테고리 메타데이터 생성 중 오류: {}", categoryName, e);
            return createFallbackMetadata(categoryName);
        }
    }
    
    /**
     * 메타데이터 생성용 LLM 프롬프트 구성
     */
    private String buildMetadataPrompt(String categoryName, String categoryCode, 
                                     String parentContext, Integer level) {
        
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("다음 카테고리에 대한 메타데이터를 JSON 형식으로 생성해주세요.\n\n");
        
        prompt.append("## 카테고리 정보\n");
        prompt.append("- 카테고리명: ").append(categoryName).append("\n");
        prompt.append("- 카테고리 코드: ").append(categoryCode).append("\n");
        prompt.append("- 레벨: ").append(level).append("\n");
        
        if (!parentContext.isEmpty()) {
            prompt.append("- ").append(parentContext).append("\n");
        }
        
        prompt.append("\n## 생성할 메타데이터\n");
        prompt.append("1. **description**: 카테고리에 대한 명확하고 구체적인 설명 (1-2문장)\n");
        prompt.append("2. **aliases**: 동의어, 유사 용어, 영문명 등 (3-7개)\n");
        prompt.append("3. **includeKeywords**: 이 카테고리에 속할 가능성이 높은 키워드들 (5-10개)\n");
        prompt.append("4. **excludeKeywords**: 이 카테고리에 속하지 않을 키워드들 (3-5개)\n");
        prompt.append("5. **examplePhrases**: 이 카테고리에 속하는 예시 문장들 (3-5개)\n");
        
        prompt.append("\n## 응답 형식\n");
        prompt.append("반드시 다음 JSON 형식으로만 응답하세요:\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"description\": \"카테고리 설명\",\n");
        prompt.append("  \"aliases\": [\"동의어1\", \"동의어2\", \"동의어3\"],\n");
        prompt.append("  \"includeKeywords\": [\"키워드1\", \"키워드2\", \"키워드3\"],\n");
        prompt.append("  \"excludeKeywords\": [\"제외키워드1\", \"제외키워드2\"],\n");
        prompt.append("  \"examplePhrases\": [\"예시문장1\", \"예시문장2\", \"예시문장3\"]\n");
        prompt.append("}\n");
        prompt.append("```\n");
        
        prompt.append("\n## 주의사항\n");
        prompt.append("- 한국어로 작성하되, 필요시 영문 용어도 포함\n");
        prompt.append("- 구체적이고 실용적인 키워드 선택\n");
        prompt.append("- 예시 문장은 실제 프로젝트나 문서에서 나올 법한 내용\n");
        prompt.append("- JSON 형식을 정확히 지켜주세요\n");
        
        return prompt.toString();
    }
    
    /**
     * LLM 응답에서 메타데이터 파싱
     */
    private CategoryMetadata parseMetadataResponse(String llmResponse) {
        try {
            // JSON 블록 추출
            String jsonContent = extractJsonFromResponse(llmResponse);
            
            if (jsonContent == null) {
                throw new IllegalArgumentException("JSON 형식을 찾을 수 없습니다");
            }
            
            // JSON 파싱
            JsonNode jsonNode = objectMapper.readTree(jsonContent);
            
            CategoryMetadata metadata = new CategoryMetadata();
            
            // 필드 추출
            if (jsonNode.has("description")) {
                metadata.setDescription(jsonNode.get("description").asText());
            }
            
            if (jsonNode.has("aliases") && jsonNode.get("aliases").isArray()) {
                List<String> aliases = new ArrayList<>();
                jsonNode.get("aliases").forEach(node -> aliases.add(node.asText()));
                metadata.setAliases(aliases);
            }
            
            if (jsonNode.has("includeKeywords") && jsonNode.get("includeKeywords").isArray()) {
                List<String> includeKeywords = new ArrayList<>();
                jsonNode.get("includeKeywords").forEach(node -> includeKeywords.add(node.asText()));
                metadata.setIncludeKeywords(includeKeywords);
            }
            
            if (jsonNode.has("excludeKeywords") && jsonNode.get("excludeKeywords").isArray()) {
                List<String> excludeKeywords = new ArrayList<>();
                jsonNode.get("excludeKeywords").forEach(node -> excludeKeywords.add(node.asText()));
                metadata.setExcludeKeywords(excludeKeywords);
            }
            
            if (jsonNode.has("examplePhrases") && jsonNode.get("examplePhrases").isArray()) {
                List<String> examplePhrases = new ArrayList<>();
                jsonNode.get("examplePhrases").forEach(node -> examplePhrases.add(node.asText()));
                metadata.setExamplePhrases(examplePhrases);
            }
            
            return metadata;
            
        } catch (Exception e) {
            log.error("LLM 응답 파싱 중 오류", e);
            throw new RuntimeException("메타데이터 파싱 실패", e);
        }
    }
    
    /**
     * 응답에서 JSON 블록 추출
     */
    private String extractJsonFromResponse(String response) {
        if (response == null) return null;
        
        // ```json ... ``` 블록 찾기
        int jsonStart = response.indexOf("```json");
        if (jsonStart != -1) {
            jsonStart = response.indexOf("{", jsonStart);
            int jsonEnd = response.lastIndexOf("```");
            if (jsonEnd > jsonStart) {
                jsonEnd = response.lastIndexOf("}", jsonEnd);
                if (jsonEnd > jsonStart) {
                    return response.substring(jsonStart, jsonEnd + 1);
                }
            }
        }
        
        // { ... } 블록 직접 찾기
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        if (start != -1 && end > start) {
            return response.substring(start, end + 1);
        }
        
        return null;
    }
    
    /**
     * Fallback 메타데이터 생성
     */
    private CategoryMetadata createFallbackMetadata(String categoryName) {
        log.warn("LLM 생성 실패, 기본 메타데이터 사용: {}", categoryName);
        
        CategoryMetadata metadata = new CategoryMetadata();
        metadata.setDescription(categoryName + " 관련 프로젝트 및 문서");
        metadata.setAliases(Arrays.asList(categoryName));
        metadata.setIncludeKeywords(Arrays.asList(categoryName.toLowerCase()));
        metadata.setExcludeKeywords(new ArrayList<>());
        metadata.setExamplePhrases(Arrays.asList(categoryName + " 프로젝트"));
        
        return metadata;
    }
    
    /**
     * 빈 필드만 자동 생성 (기존 값 보존)
     */
    public void fillEmptyFields(CatalogNode category) {
        try {
            log.info("빈 필드 자동 생성 시작: {}", category.getCode());
            
            // 이미 모든 필드가 채워져 있으면 스킵
            if (hasAllFields(category)) {
                log.info("모든 필드가 이미 존재함, 스킵: {}", category.getCode());
                return;
            }
            
            // 메타데이터 생성
            CategoryMetadata generated = generateCategoryMetadata(
                category.getName(), 
                category.getCode(), 
                category.getParentCode(), 
                category.getLevel()
            );
            
            // 빈 필드만 채우기
            if (category.getDescription() == null || category.getDescription().trim().isEmpty()) {
                category.setDescription(generated.getDescription());
            }
            
            if (category.getAliases() == null || category.getAliases().isEmpty()) {
                category.setAliases(generated.getAliases());
            }
            
            if (category.getIncludeKeywords() == null || category.getIncludeKeywords().isEmpty()) {
                category.setIncludeKeywords(generated.getIncludeKeywords());
            }
            
            if (category.getExcludeKeywords() == null || category.getExcludeKeywords().isEmpty()) {
                category.setExcludeKeywords(generated.getExcludeKeywords());
            }
            
            if (category.getExamplePhrases() == null || category.getExamplePhrases().isEmpty()) {
                category.setExamplePhrases(generated.getExamplePhrases());
            }
            
            log.info("빈 필드 자동 생성 완료: {}", category.getCode());
            
        } catch (Exception e) {
            log.error("빈 필드 자동 생성 중 오류: {}", category.getCode(), e);
        }
    }
    
    /**
     * 모든 필드가 채워져 있는지 확인
     */
    private boolean hasAllFields(CatalogNode category) {
        return category.getDescription() != null && !category.getDescription().trim().isEmpty() &&
               category.getAliases() != null && !category.getAliases().isEmpty() &&
               category.getIncludeKeywords() != null && !category.getIncludeKeywords().isEmpty() &&
               category.getExcludeKeywords() != null &&
               category.getExamplePhrases() != null && !category.getExamplePhrases().isEmpty();
    }
    
    /**
     * 카테고리 메타데이터 클래스
     */
    public static class CategoryMetadata {
        private String description;
        private List<String> aliases;
        private List<String> includeKeywords;
        private List<String> excludeKeywords;
        private List<String> examplePhrases;
        
        public CategoryMetadata() {
            this.aliases = new ArrayList<>();
            this.includeKeywords = new ArrayList<>();
            this.excludeKeywords = new ArrayList<>();
            this.examplePhrases = new ArrayList<>();
        }
        
        // Getters and Setters
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<String> getAliases() { return aliases; }
        public void setAliases(List<String> aliases) { this.aliases = aliases; }
        public List<String> getIncludeKeywords() { return includeKeywords; }
        public void setIncludeKeywords(List<String> includeKeywords) { this.includeKeywords = includeKeywords; }
        public List<String> getExcludeKeywords() { return excludeKeywords; }
        public void setExcludeKeywords(List<String> excludeKeywords) { this.excludeKeywords = excludeKeywords; }
        public List<String> getExamplePhrases() { return examplePhrases; }
        public void setExamplePhrases(List<String> examplePhrases) { this.examplePhrases = examplePhrases; }
    }
}

