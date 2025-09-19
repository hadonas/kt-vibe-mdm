package com.company.app.catalog.service;

import com.company.app.catalog.entity.CatalogNode;
import com.company.app.catalog.repository.CatalogNodeRepository;
import com.company.app.search.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 카테고리 임베딩 생성 및 관리 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryEmbeddingService {
    
    private final CatalogNodeRepository catalogNodeRepository;
    private final EmbeddingService embeddingService;
    
    /**
     * 카테고리의 임베딩 벡터 생성
     */
    public List<Double> generateCategoryEmbedding(CatalogNode category) {
        try {
            log.info("카테고리 임베딩 생성 시작: {} ({})", category.getCode(), category.getName());
            
            // 카테고리 정보를 텍스트로 결합
            String categoryText = buildCategoryText(category);
            
            // 임베딩 생성
            List<Double> embedding = embeddingService.generateEmbedding(categoryText);
            
            log.info("카테고리 임베딩 생성 완료: {} ({}차원)", category.getCode(), embedding.size());
            return embedding;
            
        } catch (Exception e) {
            log.error("카테고리 임베딩 생성 중 오류: {}", category.getCode(), e);
            throw new RuntimeException("카테고리 임베딩 생성 실패", e);
        }
    }
    
    /**
     * 카테고리 정보를 텍스트로 결합
     */
    private String buildCategoryText(CatalogNode category) {
        StringBuilder text = new StringBuilder();
        
        // 기본 정보
        text.append("카테고리: ").append(category.getName()).append("\n");
        text.append("코드: ").append(category.getCode()).append("\n");
        
        // 설명
        if (category.getDescription() != null && !category.getDescription().trim().isEmpty()) {
            text.append("설명: ").append(category.getDescription()).append("\n");
        }
        
        // 동의어
        if (category.getAliases() != null && !category.getAliases().isEmpty()) {
            text.append("동의어: ").append(String.join(", ", category.getAliases())).append("\n");
        }
        
        // 포함 키워드
        if (category.getIncludeKeywords() != null && !category.getIncludeKeywords().isEmpty()) {
            text.append("관련 키워드: ").append(String.join(", ", category.getIncludeKeywords())).append("\n");
        }
        
        // 예시 문장들
        if (category.getExamplePhrases() != null && !category.getExamplePhrases().isEmpty()) {
            text.append("예시:\n");
            for (String example : category.getExamplePhrases()) {
                text.append("- ").append(example).append("\n");
            }
        }
        
        // 계층 정보 (부모 카테고리)
        if (category.getParentCode() != null) {
            Optional<CatalogNode> parent = catalogNodeRepository.findByCode(category.getParentCode());
            if (parent.isPresent()) {
                text.append("상위 분류: ").append(parent.get().getName()).append("\n");
            }
        }
        
        return text.toString().trim();
    }
    
    /**
     * 카테고리의 임베딩 업데이트
     */
    public CatalogNode updateCategoryEmbedding(String categoryCode) {
        try {
            Optional<CatalogNode> categoryOpt = catalogNodeRepository.findByCode(categoryCode);
            if (categoryOpt.isEmpty()) {
                throw new IllegalArgumentException("카테고리를 찾을 수 없습니다: " + categoryCode);
            }
            
            CatalogNode category = categoryOpt.get();
            
            // 임베딩 생성
            List<Double> embedding = generateCategoryEmbedding(category);
            
            // 카테고리 업데이트
            category.setVector(embedding);
            category.setLastVectorUpdate(LocalDateTime.now());
            
            return catalogNodeRepository.save(category);
            
        } catch (Exception e) {
            log.error("카테고리 임베딩 업데이트 중 오류: {}", categoryCode, e);
            throw new RuntimeException("카테고리 임베딩 업데이트 실패", e);
        }
    }
    
    /**
     * 모든 카테고리의 임베딩 생성/업데이트
     */
    public void updateAllCategoryEmbeddings() {
        try {
            log.info("모든 카테고리 임베딩 업데이트 시작");
            
            List<CatalogNode> allCategories = catalogNodeRepository.findByActiveTrue();
            log.info("업데이트할 카테고리 수: {}개", allCategories.size());
            
            int successCount = 0;
            int failureCount = 0;
            
            for (CatalogNode category : allCategories) {
                try {
                    updateCategoryEmbedding(category.getCode());
                    successCount++;
                    log.debug("카테고리 임베딩 업데이트 완료: {}", category.getCode());
                } catch (Exception e) {
                    failureCount++;
                    log.error("카테고리 임베딩 업데이트 실패: {}", category.getCode(), e);
                }
            }
            
            log.info("모든 카테고리 임베딩 업데이트 완료: 성공 {}개, 실패 {}개", successCount, failureCount);
            
        } catch (Exception e) {
            log.error("모든 카테고리 임베딩 업데이트 중 오류", e);
            throw new RuntimeException("카테고리 임베딩 일괄 업데이트 실패", e);
        }
    }
    
    /**
     * 카테고리 임베딩 벡터 조회
     */
    public List<Double> getCategoryEmbedding(String categoryCode) {
        Optional<CatalogNode> categoryOpt = catalogNodeRepository.findByCode(categoryCode);
        if (categoryOpt.isPresent() && categoryOpt.get().getVector() != null) {
            return categoryOpt.get().getVector();
        }
        return null;
    }
    
    /**
     * 임베딩이 있는 모든 카테고리 조회
     */
    public List<CatalogNode> getCategoriesWithEmbedding() {
        return catalogNodeRepository.findByActiveTrueAndVectorNotNull();
    }
    
    /**
     * 카테고리 간 유사도 계산
     */
    public double calculateCategorySimilarity(String categoryCode1, String categoryCode2) {
        List<Double> embedding1 = getCategoryEmbedding(categoryCode1);
        List<Double> embedding2 = getCategoryEmbedding(categoryCode2);
        
        if (embedding1 == null || embedding2 == null) {
            return 0.0;
        }
        
        return embeddingService.cosineSimilarity(embedding1, embedding2);
    }
    
    /**
     * 텍스트와 카테고리 간 유사도 계산
     */
    public double calculateTextCategorySimilarity(String text, String categoryCode) {
        try {
            List<Double> textEmbedding = embeddingService.generateEmbedding(text);
            List<Double> categoryEmbedding = getCategoryEmbedding(categoryCode);
            
            if (categoryEmbedding == null) {
                return 0.0;
            }
            
            return embeddingService.cosineSimilarity(textEmbedding, categoryEmbedding);
            
        } catch (Exception e) {
            log.error("텍스트-카테고리 유사도 계산 중 오류: {}", categoryCode, e);
            return 0.0;
        }
    }
}
