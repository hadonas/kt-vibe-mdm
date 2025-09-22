package com.company.app.search.service;

import com.company.app.catalog.entity.CatalogNode;
import com.company.app.catalog.repository.CatalogNodeRepository;
import com.company.app.document.entity.DocumentEntity;
import com.company.app.document.repository.DocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch 인덱스 관리 및 데이터 동기화 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchIndexService {
    
    private final RestHighLevelClient elasticsearchClient;
    private final DocumentRepository documentRepository;
    private final CatalogNodeRepository catalogNodeRepository;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    
    // 인덱스 이름
    private static final String CATEGORIES_INDEX = "mdm_categories";
    private static final String CHUNKS_INDEX = "mdm_document_chunks"; // 청크 인덱스만 유지
    
    /**
     * 애플리케이션 시작 시 인덱스 초기화
     */
    @PostConstruct
    public void initializeIndexes() {
        try {
            log.info("Elasticsearch 인덱스 초기화 시작");
            
            // 카테고리 인덱스 생성
            createCategoryIndex();
            
            // 청크 인덱스 생성 (문서 전체 대신 청크만 색인)
            createChunkIndex();
            
            // 카테고리만 동기화 (문서는 청크로 대체)
            new Thread(this::syncCategories).start();
            
            log.info("Elasticsearch 인덱스 초기화 완료");
            
        } catch (Exception e) {
            log.error("Elasticsearch 인덱스 초기화 실패", e);
        }
    }
    
    // createDocumentIndex() 메서드 제거됨 - 청크 기반 인덱싱만 사용
    
    /**
     * 카테고리 인덱스 생성
     */
    private void createCategoryIndex() {
        try {
            // 인덱스가 이미 존재하는지 확인
            GetIndexRequest getIndexRequest = new GetIndexRequest(CATEGORIES_INDEX);
            boolean exists = elasticsearchClient.indices().exists(getIndexRequest, RequestOptions.DEFAULT);
            
            if (exists) {
                log.info("카테고리 인덱스가 이미 존재함: {}", CATEGORIES_INDEX);
                return;
            }
            
            // 카테고리 인덱스 매핑 정의
            Map<String, Object> mapping = new HashMap<>();
            Map<String, Object> properties = new HashMap<>();
            
            // 기본 필드들
            properties.put("id", Map.of("type", "keyword"));
            properties.put("code", Map.of("type", "keyword"));
            properties.put("name", Map.of("type", "text", "analyzer", "standard"));
            properties.put("level", Map.of("type", "integer"));
            properties.put("parentCode", Map.of("type", "keyword"));
            properties.put("active", Map.of("type", "boolean"));
            properties.put("description", Map.of("type", "text", "analyzer", "standard"));
            properties.put("aliases", Map.of("type", "text", "analyzer", "standard"));
            properties.put("includeKeywords", Map.of("type", "text", "analyzer", "standard"));
            properties.put("excludeKeywords", Map.of("type", "text", "analyzer", "standard"));
            properties.put("examplePhrases", Map.of("type", "text", "analyzer", "standard"));
            
            // 벡터 필드 (dense_vector)
            properties.put("vector", Map.of(
                "type", "dense_vector",
                "dims", 1536,
                "index", true,
                "similarity", "cosine"
            ));
            
            mapping.put("properties", properties);
            
            // 인덱스 생성
            CreateIndexRequest createIndexRequest = new CreateIndexRequest(CATEGORIES_INDEX);
            createIndexRequest.mapping(mapping);
            
            elasticsearchClient.indices().create(createIndexRequest, RequestOptions.DEFAULT);
            log.info("카테고리 인덱스 생성 완료: {}", CATEGORIES_INDEX);
            
        } catch (Exception e) {
            log.error("카테고리 인덱스 생성 실패", e);
        }
    }
    
    /**
     * 청크 인덱스 생성
     */
    private void createChunkIndex() {
        try {
            GetIndexRequest getIndexRequest = new GetIndexRequest(CHUNKS_INDEX);
            boolean exists = elasticsearchClient.indices().exists(getIndexRequest, RequestOptions.DEFAULT);
            if (exists) { return; }
            Map<String,Object> mapping = new java.util.HashMap<>();
            Map<String,Object> properties = new java.util.HashMap<>();
            properties.put("documentId", java.util.Map.of("type","keyword"));
            properties.put("chunkIndex", java.util.Map.of("type","integer"));
            properties.put("content", java.util.Map.of("type","text","analyzer","standard"));
            properties.put("sectionHint", java.util.Map.of("type","text","analyzer","standard"));
            properties.put("tokenCount", java.util.Map.of("type","integer"));
            properties.put("embedding", java.util.Map.of("type","dense_vector","dims",1536,"index",true,"similarity","cosine"));
            properties.put("createdAt", java.util.Map.of("type","date"));
            mapping.put("properties", properties);
            CreateIndexRequest createIndexRequest = new CreateIndexRequest(CHUNKS_INDEX);
            createIndexRequest.mapping(mapping);
            elasticsearchClient.indices().create(createIndexRequest, RequestOptions.DEFAULT);
        } catch (Exception e) { log.error("청크 인덱스 생성 실패", e); }
    }
    
    /**
     * 카테고리만 동기화 (문서는 청크로 대체)
     */
    public void syncCategories() {
        try {
            log.info("카테고리 데이터 Elasticsearch 동기화 시작");
            
            // MongoDB에서 모든 활성 카테고리 조회
            List<CatalogNode> categories = catalogNodeRepository.findByActiveTrue();
            
            if (categories.isEmpty()) {
                log.info("동기화할 카테고리가 없음");
                return;
            }
            
            // 배치로 Elasticsearch에 색인
            BulkRequest bulkRequest = new BulkRequest();
            
            for (CatalogNode category : categories) {
                try {
                    Map<String, Object> categoryMap = convertCategoryToMap(category);
                    
                    IndexRequest indexRequest = new IndexRequest(CATEGORIES_INDEX)
                        .id(category.getId())
                        .source(categoryMap, XContentType.JSON);
                    
                    bulkRequest.add(indexRequest);
                    
                } catch (Exception e) {
                    log.warn("카테고리 변환 실패: {}", category.getCode(), e);
                }
            }
            
            if (bulkRequest.numberOfActions() > 0) {
                BulkResponse bulkResponse = elasticsearchClient.bulk(bulkRequest, RequestOptions.DEFAULT);
                log.info("카테고리 배치 색인 완료: {}개 카테고리, 실패: {}개", 
                    bulkRequest.numberOfActions(), bulkResponse.hasFailures() ? "있음" : "없음");
            }
            
        } catch (Exception e) {
            log.error("카테고리 데이터 동기화 실패", e);
        }
    }
    
    // convertDocumentToMap() 메서드 제거됨 - 청크 기반 인덱싱만 사용
    
    /**
     * CatalogNode를 Elasticsearch 매핑으로 변환
     */
    private Map<String, Object> convertCategoryToMap(CatalogNode category) {
        Map<String, Object> categoryMap = new HashMap<>();
        
        categoryMap.put("id", category.getId());
        categoryMap.put("code", category.getCode());
        categoryMap.put("name", category.getName());
        categoryMap.put("level", category.getLevel());
        categoryMap.put("parentCode", category.getParentCode());
        categoryMap.put("active", category.getActive());
        categoryMap.put("description", category.getDescription());
        categoryMap.put("aliases", category.getAliases());
        categoryMap.put("includeKeywords", category.getIncludeKeywords());
        categoryMap.put("excludeKeywords", category.getExcludeKeywords());
        categoryMap.put("examplePhrases", category.getExamplePhrases());
        
        // 벡터 정보 (있는 경우)
        if (category.getVector() != null && !category.getVector().isEmpty()) {
            categoryMap.put("vector", category.getVector());
        } else {
            // 벡터가 없으면 생성
            try {
                String categoryText = buildCategoryText(category);
                List<Double> vector = embeddingService.generateEmbedding(categoryText);
                categoryMap.put("vector", vector);
            } catch (Exception e) {
                log.warn("카테고리 벡터 생성 실패: {}", category.getCode(), e);
            }
        }
        
        return categoryMap;
    }
    
    /**
     * 카테고리 텍스트 구성 (벡터 생성용)
     */
    private String buildCategoryText(CatalogNode category) {
        StringBuilder text = new StringBuilder();
        
        text.append(category.getName());
        
        if (category.getDescription() != null) {
            text.append(" ").append(category.getDescription());
        }
        
        if (category.getAliases() != null) {
            text.append(" ").append(String.join(" ", category.getAliases()));
        }
        
        if (category.getIncludeKeywords() != null) {
            text.append(" ").append(String.join(" ", category.getIncludeKeywords()));
        }
        
        if (category.getExamplePhrases() != null) {
            text.append(" ").append(String.join(" ", category.getExamplePhrases()));
        }
        
        return text.toString();
    }
    
    /**
     * 단일 카테고리 Elasticsearch 인덱스 업데이트
     */
    public void updateCategoryInIndex(CatalogNode category) {
        try {
            log.info("Elasticsearch 카테고리 인덱스 업데이트: {}", category.getCode());
            
            Map<String, Object> categoryMap = convertCategoryToMap(category);
            
            IndexRequest indexRequest = new IndexRequest(CATEGORIES_INDEX)
                .id(category.getId())
                .source(categoryMap, XContentType.JSON);
            
            elasticsearchClient.index(indexRequest, RequestOptions.DEFAULT);
            
            log.info("Elasticsearch 카테고리 인덱스 업데이트 완료: {}", category.getCode());
            
        } catch (Exception e) {
            log.error("Elasticsearch 카테고리 인덱스 업데이트 실패: {}", category.getCode(), e);
            throw new RuntimeException("카테고리 인덱스 업데이트 실패", e);
        }
    }
    
    /**
     * 단일 문서 Elasticsearch 인덱스 업데이트 - 제거됨 (청크만 사용)
     */
    public void updateDocumentInIndex(DocumentEntity document) {
        log.info("문서 인덱스 업데이트 제거됨 - 청크 인덱스 사용: {}", document.getId());
    }
    
    /**
     * 문서 삭제 - 제거됨 (청크 삭제로 대체)
     */
    public void deleteDocumentFromIndex(String documentId) {
        log.info("문서 인덱스 삭제 제거됨 - 청크 삭제 사용: {}", documentId);
    }
    
    /**
     * 카테고리 삭제
     */
    public void deleteCategoryFromIndex(String categoryId) {
        try {
            log.info("Elasticsearch에서 카테고리 삭제: {}", categoryId);
            
            DeleteRequest deleteRequest = new DeleteRequest(CATEGORIES_INDEX, categoryId);
            elasticsearchClient.delete(deleteRequest, RequestOptions.DEFAULT);
            
            log.info("Elasticsearch 카테고리 삭제 완료: {}", categoryId);
            
        } catch (Exception e) {
            log.error("Elasticsearch 카테고리 삭제 실패: {}", categoryId, e);
            throw new RuntimeException("카테고리 인덱스 삭제 실패", e);
        }
    }
    
    /**
     * 인덱스 재생성 (카테고리와 청크만)
     */
    public void recreateIndexes() {
        try {
            log.info("Elasticsearch 인덱스 재생성 시작");
            
            // 기존 인덱스 삭제
            deleteIndexIfExists(CATEGORIES_INDEX);
            deleteIndexIfExists(CHUNKS_INDEX);
            
            // 새 인덱스 생성
            createCategoryIndex();
            createChunkIndex();
            
            // 카테고리만 동기화
            syncCategories();
            
            log.info("Elasticsearch 인덱스 재생성 완료");
            
        } catch (Exception e) {
            log.error("Elasticsearch 인덱스 재생성 실패", e);
        }
    }
    
    private void deleteIndexIfExists(String indexName) {
        try {
            GetIndexRequest getIndexRequest = new GetIndexRequest(indexName);
            boolean exists = elasticsearchClient.indices().exists(getIndexRequest, RequestOptions.DEFAULT);
            
            if (exists) {
                DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(indexName);
                elasticsearchClient.indices().delete(deleteIndexRequest, RequestOptions.DEFAULT);
                log.info("기존 인덱스 삭제 완료: {}", indexName);
            }
        } catch (Exception e) {
            log.warn("인덱스 삭제 실패: {}", indexName, e);
        }
    }
    
    /**
     * 청크 데이터 Elasticsearch 색인
     */
    public void indexChunks(List<com.company.app.document.entity.DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return;
        try {
            BulkRequest bulk = new BulkRequest();
            for (var ch : chunks) {
                Map<String,Object> m = new java.util.HashMap<>();
                m.put("documentId", ch.getDocumentId());
                m.put("chunkIndex", ch.getChunkIndex());
                m.put("content", ch.getContent());
                m.put("sectionHint", ch.getSectionHint());
                m.put("tokenCount", ch.getTokenCount());
                if (ch.getEmbedding() != null) m.put("embedding", ch.getEmbedding());
                m.put("createdAt", ch.getCreatedAt());
                IndexRequest ir = new IndexRequest(CHUNKS_INDEX)
                        .id(ch.getDocumentId()+"_"+ch.getChunkIndex())
                        .source(m, XContentType.JSON);
                bulk.add(ir);
            }
            if (bulk.numberOfActions()>0) {
                BulkResponse resp = elasticsearchClient.bulk(bulk, RequestOptions.DEFAULT);
                if (resp.hasFailures()) log.warn("청크 배치 색인 실패 있음: {}", resp.buildFailureMessage());
            }
        } catch (Exception e) { log.error("청크 색인 실패", e); }
    }
    
    /**
     * 특정 문서 ID에 대한 청크 삭제 (미구현)
     */
    public void deleteChunksByDocumentId(String documentId) {
        try {
            DeleteByQueryRequest request = new DeleteByQueryRequest(CHUNKS_INDEX);
            request.setQuery(QueryBuilders.termQuery("documentId", documentId));
            request.setConflicts("proceed");
            BulkByScrollResponse response = elasticsearchClient.deleteByQuery(request, RequestOptions.DEFAULT);
            log.info("청크 삭제 완료 docId={} deleted={} status={}", documentId, response.getDeleted(), response.getStatus());
        } catch (Exception e) {
            log.warn("청크 삭제 실패 docId={} error={}", documentId, e.getMessage());
        }
    }

    /**
     * 하이브리드 청크 검색 (RRF 기반 RAG retrieval)
     * @param query 사용자가 입력한 질의
     * @param topK 반환할 상위 청크 수
     */
    public List<ChunkSearchHit> searchChunks(String query, int topK) {
        try {
            log.info("하이브리드 검색 실행: query={}, topK={}", query, topK);
            
            // 1. 벡터 검색 수행
            List<ChunkSearchHit> vectorResults = performVectorSearch(query, topK * 2);
            
            // 2. 텍스트 검색 수행
            List<ChunkSearchHit> textResults = performTextSearch(query, topK * 2);
            
            // 3. RRF (Reciprocal Rank Fusion) 적용
            List<ChunkSearchHit> hybridResults = applyRRF(vectorResults, textResults, topK);
            
            log.info("하이브리드 검색 완료: vector={}, text={}, hybrid={}", 
                vectorResults.size(), textResults.size(), hybridResults.size());
            
            return hybridResults;
        } catch (Exception e) {
            log.error("하이브리드 청크 검색 실패", e);
            return java.util.Collections.emptyList();
        }
    }
    
    /**
     * 벡터 검색 수행 (임계값 0.25 적용)
     */
    private List<ChunkSearchHit> performVectorSearch(String query, int size) {
        try {
            List<Double> embedding = embeddingService.generateEmbedding(query);
            
            // 벡터 검색 임계값 0.2 → 0.25로 상향
            Map<String,Object> params = new HashMap<>();
            params.put("query_vector", embedding);
            params.put("threshold", 0.20);
            
            // 임계값을 적용한 스크립트 (코사인 유사도 + 1.0 보정, 임계값 이상만 반환)
            Script script = new Script(ScriptType.INLINE, "painless", 
                "double score = cosineSimilarity(params.query_vector, 'embedding') + 1.0; " +
                "return score >= params.threshold + 1.0 ? score : 0.0", params);

            SearchSourceBuilder ssb = new SearchSourceBuilder()
                    .size(size)
                    .query(QueryBuilders.scriptScoreQuery(QueryBuilders.matchAllQuery(), script))
                    .fetchSource(new String[]{"documentId","chunkIndex","content","sectionHint"}, null);

            SearchRequest sr = new SearchRequest(CHUNKS_INDEX).source(ssb);
            SearchResponse resp = elasticsearchClient.search(sr, RequestOptions.DEFAULT);

            List<ChunkSearchHit> results = new java.util.ArrayList<>();
            for (SearchHit hit : resp.getHits().getHits()) {
                // 임계값 미달 결과 필터링
                if (hit.getScore() < 1.20) continue; // 1.0 + 0.20 임계값

                ChunkSearchHit ch = createChunkSearchHit(hit, "vector");
                results.add(ch);
            }

            log.debug("벡터 검색 결과: {}개 (임계값 0.20 적용)", results.size());
            return results;
        } catch (Exception e) {
            log.error("벡터 검색 실패", e);
            return java.util.Collections.emptyList();
        }
    }
    
    /**
     * 텍스트 검색 수행
     */
    private List<ChunkSearchHit> performTextSearch(String query, int size) {
        try {
            SearchSourceBuilder ssb = new SearchSourceBuilder()
                    .size(size)
                    .query(QueryBuilders.multiMatchQuery(query, "content", "sectionHint"))
                    .fetchSource(new String[]{"documentId","chunkIndex","content","sectionHint"}, null);

            SearchRequest sr = new SearchRequest(CHUNKS_INDEX).source(ssb);
            SearchResponse resp = elasticsearchClient.search(sr, RequestOptions.DEFAULT);

            List<ChunkSearchHit> results = new java.util.ArrayList<>();
            for (SearchHit hit : resp.getHits().getHits()) {
                ChunkSearchHit ch = createChunkSearchHit(hit, "text");
                results.add(ch);
            }
            
            log.debug("텍스트 검색 결과: {}개", results.size());
            return results;
        } catch (Exception e) {
            log.error("텍스트 검색 실패", e);
            return java.util.Collections.emptyList();
        }
    }
    
    /**
     * RRF (Reciprocal Rank Fusion) 적용
     */
    private List<ChunkSearchHit> applyRRF(List<ChunkSearchHit> vectorResults, 
                                         List<ChunkSearchHit> textResults, int topK) {
        Map<String, ChunkSearchHit> chunkMap = new HashMap<>();
        Map<String, Double> rrfScores = new HashMap<>();
        
        final double k = 60.0; // RRF 파라미터
        
        // 벡터 검색 결과에 RRF 점수 적용
        for (int i = 0; i < vectorResults.size(); i++) {
            ChunkSearchHit chunk = vectorResults.get(i);
            String chunkId = chunk.getDocumentId() + "_" + chunk.getChunkIndex();
            
            double rrfScore = 1.0 / (k + i + 1); // RRF 공식
            rrfScores.put(chunkId, rrfScores.getOrDefault(chunkId, 0.0) + rrfScore);
            chunkMap.put(chunkId, chunk);
            
            log.debug("Vector RRF: chunkId={}, rank={}, rrfScore={}", chunkId, i+1, rrfScore);
        }
        
        // 텍스트 검색 결과에 RRF 점수 적용
        for (int i = 0; i < textResults.size(); i++) {
            ChunkSearchHit chunk = textResults.get(i);
            String chunkId = chunk.getDocumentId() + "_" + chunk.getChunkIndex();
            
            double rrfScore = 1.0 / (k + i + 1); // RRF 공식
            rrfScores.put(chunkId, rrfScores.getOrDefault(chunkId, 0.0) + rrfScore);
            chunkMap.putIfAbsent(chunkId, chunk);
            
            log.debug("Text RRF: chunkId={}, rank={}, rrfScore={}", chunkId, i+1, rrfScore);
        }
        
        // RRF 점수로 정렬하고 상위 topK 반환
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    ChunkSearchHit chunk = chunkMap.get(entry.getKey());
                    chunk.setScore(entry.getValue()); // RRF 점수로 업데이트
                    log.debug("Final RRF: chunkId={}, finalScore={}", entry.getKey(), entry.getValue());
                    return chunk;
                })
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * SearchHit에서 ChunkSearchHit 생성
     */
    private ChunkSearchHit createChunkSearchHit(SearchHit hit, String searchType) {
        Map<String,Object> src = hit.getSourceAsMap();
        ChunkSearchHit ch = new ChunkSearchHit();
        ch.setDocumentId((String) src.get("documentId"));
        Object ci = src.get("chunkIndex");
        if (ci instanceof Number) ch.setChunkIndex(((Number)ci).intValue());
        ch.setContent((String) src.get("content"));
        ch.setSectionHint((String) src.get("sectionHint"));
        ch.setScore((double) hit.getScore());
        
        // 문서 메타 추가
        documentRepository.findById(ch.getDocumentId()).ifPresent(doc -> {
            if (doc.getSerial()!=null) ch.setDocumentSerial(doc.getSerial().getFull());
            ch.setDocumentPurpose(doc.getPurpose());
        });
        
        log.debug("{} 검색 결과: docId={}, chunkIndex={}, score={}", 
            searchType, ch.getDocumentId(), ch.getChunkIndex(), ch.getScore());
        
        return ch;
    }

    /** RAG 검색 결과 DTO */
    @lombok.Data
    public static class ChunkSearchHit {
        private String documentId;
        private Integer chunkIndex;
        private String content;
        private String sectionHint;
        private Double score;
        private String documentSerial;
        private String documentPurpose;
    }
}
