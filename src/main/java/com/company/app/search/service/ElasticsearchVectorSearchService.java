package com.company.app.search.service;

import com.company.app.catalog.entity.CatalogNode;
import com.company.app.catalog.repository.CatalogNodeRepository;
import com.company.app.document.entity.DocumentEntity;
import com.company.app.document.repository.DocumentRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Elasticsearch 벡터 검색 폴백 구현.
 * 실제 ES KNN 쿼리 미구현 상태에서는 임베딩 코사인 유사도로 대체.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchVectorSearchService {

    private final DocumentRepository documentRepository;
    private final CatalogNodeRepository catalogNodeRepository;
    private final EmbeddingService embeddingService;

    /** 문서 벡터 검색 (임시) */
    public List<DocumentEntity> searchDocuments(String text, int topK) {
        try {
            var queryEmbedding = embeddingService.generateEmbedding(text);
            return documentRepository.findAll().stream()
                    .filter(d -> d.getVectors() != null && d.getVectors().getPurpose_1536() != null)
                    .sorted((a,b) -> Double.compare(
                            similarity(queryEmbedding, b.getVectors().getPurpose_1536()),
                            similarity(queryEmbedding, a.getVectors().getPurpose_1536())))
                    .limit(topK)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("ES 문서 검색 폴백 실패: {}", e.getMessage());
            return List.of();
        }
    }

    @Data
    @AllArgsConstructor
    public static class CategorySearchResult {
        private CatalogNode category;
        private double vectorScore;
    }

    /** 카테고리 벡터 검색 (임시) */
    public List<CategorySearchResult> searchCategories(String text, int topK) {
        try {
            var queryEmbedding = embeddingService.generateEmbedding(text);
            return catalogNodeRepository.findByActiveTrue().stream()
                    .filter(c -> c.getVector() != null && !c.getVector().isEmpty())
                    .map(c -> new CategorySearchResult(c, similarity(queryEmbedding, c.getVector())))
                    .sorted(Comparator.comparingDouble(CategorySearchResult::getVectorScore).reversed())
                    .limit(topK)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("ES 카테고리 검색 폴백 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private double similarity(List<Double> a, List<Double> b) {
        try { return embeddingService.cosineSimilarity(a,b); } catch (Exception e) { return 0.0; }
    }
}
