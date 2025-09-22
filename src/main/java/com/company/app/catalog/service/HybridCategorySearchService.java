package com.company.app.catalog.service;

import com.company.app.catalog.entity.CatalogNode;
import com.company.app.catalog.repository.CatalogNodeRepository;
import com.company.app.search.service.EmbeddingService;
import com.company.app.search.service.ElasticsearchVectorSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 하이브리드 카테고리 검색 서비스 (BM25 + Vector Search)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HybridCategorySearchService {
    
    private final CatalogNodeRepository catalogNodeRepository;
    private final EmbeddingService embeddingService;
    private final ElasticsearchVectorSearchService elasticsearchVectorSearchService;
    
    @Value("${classification.candidate-count:10}")
    private int candidateCount;
    
    @Value("${classification.bm25-weight:0.3}")
    private double bm25Weight;
    
    @Value("${classification.vector-weight:0.7}")
    private double vectorWeight;
    
    /**
     * 하이브리드 카테고리 검색 (BM25 + Vector Search)
     */
    public List<CategorySearchResult> searchCategories(String documentSummary, int limit) {
        try {
            log.info("하이브리드 카테고리 검색 시작: {}자 요약", documentSummary.length());
            
            // 1. 키워드 추출
            List<String> keywords = extractKeywords(documentSummary);
            
            // 2. BM25 기반 텍스트 검색
            List<CategorySearchResult> bm25Results = performBM25Search(documentSummary, keywords);
            
            // 3. Vector Search 기반 유사도 검색
            List<CategorySearchResult> vectorResults = performVectorSearch(documentSummary);
            
            // 4. 하이브리드 점수 계산 및 결합
            List<CategorySearchResult> hybridResults = combineSearchResults(bm25Results, vectorResults);
            
            // 5. 상위 limit개 반환
            List<CategorySearchResult> finalResults = hybridResults.stream()
                .limit(limit)
                .collect(Collectors.toList());
            
            log.info("하이브리드 카테고리 검색 완료: {}개 후보", finalResults.size());
            return finalResults;
            
        } catch (Exception e) {
            log.error("하이브리드 카테고리 검색 중 오류", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * BM25 기반 텍스트 검색
     */
    private List<CategorySearchResult> performBM25Search(String text, List<String> keywords) {
        List<CategorySearchResult> results = new ArrayList<>();
        
        try {
            // 1. 텍스트 검색
            List<CatalogNode> textSearchResults = catalogNodeRepository.findByTextSearch(text);
            
            // 2. 키워드 검색
            List<CatalogNode> keywordSearchResults = catalogNodeRepository.findByKeywords(keywords, 
                String.join("|", keywords));
            
            // 3. 결과 결합 및 BM25 점수 계산
            Set<String> processedCodes = new HashSet<>();
            
            for (CatalogNode category : textSearchResults) {
                if (processedCodes.add(category.getCode())) {
                    double bm25Score = calculateBM25Score(text, keywords, category);
                    results.add(new CategorySearchResult(category, 0.0, bm25Score, 0.0));
                }
            }
            
            for (CatalogNode category : keywordSearchResults) {
                if (processedCodes.add(category.getCode())) {
                    double bm25Score = calculateBM25Score(text, keywords, category);
                    results.add(new CategorySearchResult(category, 0.0, bm25Score, 0.0));
                }
            }
            
            log.debug("BM25 검색 결과: {}개", results.size());
            
        } catch (Exception e) {
            log.error("BM25 검색 중 오류", e);
        }
        
        return results;
    }
    
    /**
     * Vector Search 기반 유사도 검색 (Elasticsearch 우선, fallback 지원)
     */
    private List<CategorySearchResult> performVectorSearch(String text) {
        try {
            log.info("벡터 검색으로 카테고리 후보 조회 시작");
            
            // 1. Elasticsearch 벡터 검색 시도
            List<ElasticsearchVectorSearchService.CategorySearchResult> esResults = 
                elasticsearchVectorSearchService.searchCategories(text, candidateCount);
            
            if (!esResults.isEmpty()) {
                // Elasticsearch 결과를 HybridCategorySearchService.CategorySearchResult로 변환
                List<CategorySearchResult> results = new ArrayList<>();
                for (ElasticsearchVectorSearchService.CategorySearchResult esResult : esResults) {
                    results.add(new CategorySearchResult(
                        esResult.getCategory(), 
                        0.0, // BM25 점수는 0으로 설정
                        esResult.getVectorScore(), 
                        0.0 // 하이브리드 점수는 나중에 계산
                    ));
                }
                
                log.info("Elasticsearch 벡터 검색 완료: {}개 카테고리 후보", results.size());
                return results;
            }
            
        } catch (Exception e) {
            log.warn("Elasticsearch 벡터 검색 실패, fallback 사용: {}", e.getMessage());
        }
        
        // 2. Fallback: 기존 방식 사용
        return performFallbackVectorSearch(text);
    }
    
    /**
     * Fallback Vector Search (기존 방식)
     */
    private List<CategorySearchResult> performFallbackVectorSearch(String text) {
        List<CategorySearchResult> results = new ArrayList<>();
        
        try {
            log.info("Fallback 벡터 검색 시작");
            
            // 1. 텍스트 임베딩 생성
            List<Double> textEmbedding = embeddingService.generateEmbedding(text);
            
            // 2. 임베딩이 있는 모든 카테고리 조회
            List<CatalogNode> categoriesWithEmbedding = catalogNodeRepository.findByActiveTrueAndVectorNotNull();
            
            // 3. 각 카테고리와의 유사도 계산
            for (CatalogNode category : categoriesWithEmbedding) {
                try {
                    double similarity = embeddingService.cosineSimilarity(textEmbedding, category.getVector());
                    results.add(new CategorySearchResult(category, 0.0, similarity, 0.0));
                } catch (Exception e) {
                    log.warn("카테고리 유사도 계산 실패: {}", category.getCode(), e);
                }
            }
            
            log.debug("Fallback Vector 검색 결과: {}개", results.size());
            
        } catch (Exception e) {
            log.error("Fallback Vector 검색 중 오류", e);
        }
        
        return results;
    }
    
    /**
     * 검색 결과 결합 및 하이브리드 점수 계산
     */
    private List<CategorySearchResult> combineSearchResults(List<CategorySearchResult> bm25Results, 
                                                          List<CategorySearchResult> vectorResults) {
        
        Map<String, CategorySearchResult> combinedResults = new HashMap<>();
        
        // BM25 결과 추가
        for (CategorySearchResult result : bm25Results) {
            combinedResults.put(result.getCategory().getCode(), result);
        }
        
        // Vector 결과 추가/병합
        for (CategorySearchResult result : vectorResults) {
            String code = result.getCategory().getCode();
            
            if (combinedResults.containsKey(code)) {
                // 기존 결과와 병합
                CategorySearchResult existing = combinedResults.get(code);
                existing.setVectorScore(result.getVectorScore());
            } else {
                // 새로운 결과 추가
                combinedResults.put(code, result);
            }
        }
        
        // 하이브리드 점수 계산
        for (CategorySearchResult result : combinedResults.values()) {
            double hybridScore = calculateHybridScore(result);
            result.setHybridScore(hybridScore);
        }
        
        // 하이브리드 점수 순으로 정렬
        return combinedResults.values().stream()
            .sorted(Comparator.comparing(CategorySearchResult::getHybridScore).reversed())
            .collect(Collectors.toList());
    }
    
    /**
     * BM25 점수 계산 (간단한 구현)
     */
    private double calculateBM25Score(String text, List<String> keywords, CatalogNode category) {
        double score = 0.0;
        
        // 기본 텍스트 매칭
        String categoryText = buildCategorySearchText(category).toLowerCase();
        String searchText = text.toLowerCase();
        
        // 키워드 매칭
        for (String keyword : keywords) {
            String lowerKeyword = keyword.toLowerCase();
            
            // 이름 매칭 (높은 가중치)
            if (category.getName().toLowerCase().contains(lowerKeyword)) {
                score += 3.0;
            }
            
            // 설명 매칭
            if (category.getDescription() != null && 
                category.getDescription().toLowerCase().contains(lowerKeyword)) {
                score += 2.0;
            }
            
            // 동의어 매칭
            if (category.getAliases() != null) {
                for (String alias : category.getAliases()) {
                    if (alias.toLowerCase().contains(lowerKeyword)) {
                        score += 2.5;
                    }
                }
            }
            
            // 포함 키워드 매칭 (보너스)
            if (category.getIncludeKeywords() != null && 
                category.getIncludeKeywords().contains(lowerKeyword)) {
                score += 4.0;
            }
            
            // 제외 키워드 매칭 (페널티)
            if (category.getExcludeKeywords() != null && 
                category.getExcludeKeywords().contains(lowerKeyword)) {
                score -= 5.0;
            }
        }
        
        // 전체 텍스트 유사성 (간단한 구현)
        int matchCount = 0;
        for (String word : searchText.split("\\s+")) {
            if (categoryText.contains(word)) {
                matchCount++;
            }
        }
        score += matchCount * 0.5;
        
        return Math.max(0.0, score);
    }
    
    /**
     * 하이브리드 점수 계산 (레벨별 가중치 포함)
     */
    private double calculateHybridScore(CategorySearchResult result) {
        CatalogNode category = result.getCategory();
        
        // 기본 가중치
        double bm25Weight = this.bm25Weight;
        double vectorWeight = this.vectorWeight;
        
        // 카테고리별 가중치 적용
        if (category.getScoreWeights() != null) {
            bm25Weight *= category.getScoreWeights().getBm25();
            vectorWeight *= category.getScoreWeights().getVector();
        }
        
        // 기본 하이브리드 점수
        double hybridScore = (result.getBm25Score() * bm25Weight) + (result.getVectorScore() * vectorWeight);
        
        // 레벨별 세분화 보너스 적용
        double levelBonus = calculateLevelBonus(category);
        
        return hybridScore * (1.0 + levelBonus);
    }
    
    /**
     * 카테고리 레벨에 따른 세분화 보너스 계산
     * 더 구체적인 (하위 레벨) 카테고리일수록 높은 보너스
     */
    private double calculateLevelBonus(CatalogNode category) {
        int level = category.getLevel();
        
        // 레벨별 보너스 (더 구체적일수록 높은 보너스)
        switch (level) {
            case 1: // 최상위 레벨 (예: 소프트웨어)
                return 0.0; // 보너스 없음
            case 2: // 중간 레벨 (예: 웹개발, AI/머신러닝)
                return 0.15; // 15% 보너스
            case 3: // 하위 레벨 (예: 프론트엔드, 백엔드, 자연어처리)
                return 0.30; // 30% 보너스
            case 4: // 세부 레벨
                return 0.45; // 45% 보너스
            case 5: // 매우 세부 레벨
                return 0.60; // 60% 보너스
            default:
                return Math.max(0.0, (level - 1) * 0.15); // 레벨이 높을수록 더 큰 보너스
        }
    }
    
    /**
     * 카테고리 검색용 텍스트 구성
     */
    private String buildCategorySearchText(CatalogNode category) {
        StringBuilder text = new StringBuilder();
        
        text.append(category.getName()).append(" ");
        
        if (category.getDescription() != null) {
            text.append(category.getDescription()).append(" ");
        }
        
        if (category.getAliases() != null) {
            text.append(String.join(" ", category.getAliases())).append(" ");
        }
        
        if (category.getExamplePhrases() != null) {
            text.append(String.join(" ", category.getExamplePhrases())).append(" ");
        }
        
        return text.toString().trim();
    }
    
    /**
     * 키워드 추출 (간단한 구현)
     */
    private List<String> extractKeywords(String text) {
        // 불용어 제거 후 중요 키워드 추출
        Set<String> stopWords = Set.of("이", "그", "저", "것", "들", "는", "은", "가", "을", "를", 
                                      "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by");
        
        return Arrays.stream(text.toLowerCase().split("\\W+"))
            .filter(word -> word.length() > 1)
            .filter(word -> !stopWords.contains(word))
            .distinct()
            .limit(20) // 최대 20개 키워드
            .collect(Collectors.toList());
    }
    
    /**
     * 카테고리 검색 결과 클래스
     */
    public static class CategorySearchResult {
        private final CatalogNode category;
        private double vectorScore;
        private double bm25Score;
        private double hybridScore;
        
        public CategorySearchResult(CatalogNode category, double vectorScore, double bm25Score, double hybridScore) {
            this.category = category;
            this.vectorScore = vectorScore;
            this.bm25Score = bm25Score;
            this.hybridScore = hybridScore;
        }
        
        // Getters and Setters
        public CatalogNode getCategory() { return category; }
        public double getVectorScore() { return vectorScore; }
        public void setVectorScore(double vectorScore) { this.vectorScore = vectorScore; }
        public double getBm25Score() { return bm25Score; }
        public void setBm25Score(double bm25Score) { this.bm25Score = bm25Score; }
        public double getHybridScore() { return hybridScore; }
        public void setHybridScore(double hybridScore) { this.hybridScore = hybridScore; }
    }
}

