package com.company.app.search.service;

import com.company.app.catalog.entity.CatalogNode;
import com.company.app.catalog.repository.CatalogNodeRepository;
import com.company.app.document.entity.DocumentEntity;
import com.company.app.document.repository.DocumentRepository;
import com.company.app.search.util.VectorShardingUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Elasticsearch 벡터 검색 서비스 (샤딩 지원).
 * 실제 ES KNN 쿼리 미구현 상태에서는 임베딩 코사인 유사도로 대체.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchVectorSearchService {

    private final DocumentRepository documentRepository;
    private final CatalogNodeRepository catalogNodeRepository;
    private final EmbeddingService embeddingService;
    private final VectorShardingUtil shardingUtil;
    private final MongoTemplate mongoTemplate;

    /** 문서 벡터 검색 (샤딩 지원) */
    public List<DocumentEntity> searchDocuments(String text, int topK) {
        try {
            log.info("Elasticsearch 벡터 검색 시작 (샤딩 지원): {}자 텍스트", text.length());
            
            var queryEmbedding = embeddingService.generateEmbedding(text);
            
            // 1. 쿼리 벡터의 샤드 ID 계산
            int queryShardId = shardingUtil.calculateShardId(queryEmbedding);
            log.debug("쿼리 벡터 샤드 ID: {}", queryShardId);
            
            // 2. 해당 샤드와 인접 샤드들에서 검색
            List<DocumentEntity> allResults = new ArrayList<>();
            
            // 현재 샤드와 인접 샤드들 검색 (-1, 0, +1)
            for (int shardOffset = -1; shardOffset <= 1; shardOffset++) {
                int targetShardId = (queryShardId + shardOffset + shardingUtil.getDefaultShardCount()) 
                                  % shardingUtil.getDefaultShardCount();
                
                List<DocumentEntity> shardResults = searchInShard(queryEmbedding, targetShardId, topK);
                allResults.addAll(shardResults);
                
                log.debug("샤드 {} 검색 결과: {}개", targetShardId, shardResults.size());
            }
            
            // 3. 전체 결과를 유사도 순으로 정렬하여 상위 topK개 반환
            List<DocumentEntity> finalResults = allResults.stream()
                    .distinct() // 중복 제거
                    .sorted((a, b) -> Double.compare(
                            similarity(queryEmbedding, b.getVectors().getPurpose_1536()),
                            similarity(queryEmbedding, a.getVectors().getPurpose_1536())))
                    .limit(topK)
                    .collect(Collectors.toList());
            
            log.info("Elasticsearch 벡터 검색 완료: 총 {}개 샤드에서 {}개 결과", 3, finalResults.size());
            return finalResults;
            
        } catch (Exception e) {
            log.warn("ES 문서 검색 실패: {}", e.getMessage());
            return List.of();
        }
    }
    
    /**
     * 특정 샤드에서 벡터 검색 수행
     */
    private List<DocumentEntity> searchInShard(List<Double> queryEmbedding, int shardId, int limit) {
        try {
            // 해당 샤드의 문서들만 조회
            Query query = new Query(Criteria.where("shardId").is(shardId));
            List<DocumentEntity> shardDocuments = mongoTemplate.find(query, DocumentEntity.class, "documents");
            
            if (shardDocuments.isEmpty()) {
                log.debug("샤드 {}에 문서가 없음", shardId);
                return new ArrayList<>();
            }
            
            // 벡터가 있는 문서만 필터링하고 유사도 계산
            return shardDocuments.stream()
                    .filter(d -> d.getVectors() != null && d.getVectors().getPurpose_1536() != null)
                    .sorted((a, b) -> Double.compare(
                            similarity(queryEmbedding, b.getVectors().getPurpose_1536()),
                            similarity(queryEmbedding, a.getVectors().getPurpose_1536())))
                    .limit(limit)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            log.warn("샤드 {} 검색 실패: {}", shardId, e.getMessage());
            return new ArrayList<>();
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
