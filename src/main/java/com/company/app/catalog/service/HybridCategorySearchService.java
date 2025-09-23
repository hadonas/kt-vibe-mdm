package com.company.app.catalog.service;

import com.company.app.catalog.entity.CatalogNode;
import com.company.app.catalog.repository.CatalogNodeRepository;
import com.company.app.search.service.ElasticsearchIndexService;
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
    private final ElasticsearchIndexService elasticsearchIndexService;
    
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
            
            // 키워드 추출 로직 제거됨 (직접 RRF 검색)
            
        // 신규: ElasticsearchIndexService 하이브리드 (vector + text + RRF)
        var hits = elasticsearchIndexService.searchCategoriesHybrid(documentSummary, limit);
        List<CategorySearchResult> converted = hits.stream().map(h -> new CategorySearchResult(
            // category 객체 없이 최소 필드만 매핑 (필요 시 후처리로 카테고리 로드)
            catalogNodeRepository.findByCode(h.getCode()).orElse(null),
            h.getVectorScore() != null ? h.getVectorScore() : 0.0,
            h.getTextScore() != null ? h.getTextScore() : 0.0,
            h.getScore() != null ? h.getScore() : 0.0
        )).filter(r -> r.getCategory() != null).collect(Collectors.toList());

        log.info("카테고리 하이브리드 검색 완료: {}개 후보", converted.size());
        return converted;
            
        } catch (Exception e) {
            log.error("하이브리드 카테고리 검색 중 오류", e);
            return Collections.emptyList();
        }
    }
    
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

