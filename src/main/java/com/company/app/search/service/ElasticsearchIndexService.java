package com.company.app.search.service;

import com.company.app.catalog.entity.CatalogNode;
import com.company.app.catalog.repository.CatalogNodeRepository;
import com.company.app.document.entity.DocumentEntity;
import com.company.app.document.entity.DocumentChunk;
import com.company.app.document.repository.DocumentRepository;
import com.company.app.document.repository.DocumentChunkRepository;
import com.company.app.search.util.VectorShardingUtil;
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

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;

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
    private final DocumentChunkRepository chunkRepository;
    private final VectorShardingUtil shardingUtil;

    // 샤드 관련 설정
    @Value("${vector.shard.count:4}")
    private int shardCount;

    @Value("${vector.search.fanout:1}")
    private int shardFanout;

    @Value("${vector.search.min.sufficient.ratio:0.5}")
    private double minSufficientRatio;

    // 인덱스 이름
    private static final String CATEGORIES_INDEX = "mdm_categories";
    private static final String CHUNKS_INDEX = "mdm_document_chunks"; // (단일 인덱스 - 과거 호환)
    private static final String SHARD_INDEX_PREFIX = "mdm_document_chunks_shard_"; // 샤드 인덱스 prefix
    private static final String SHARD_INDEX_WILDCARD = SHARD_INDEX_PREFIX + "*"; // 검색 시 사용

    /**
     * 샤드 인덱스 이름 생성
     */
    private String getShardIndexName(int shardId) {
        return SHARD_INDEX_PREFIX + shardId;
    }

    private List<String> resolveShardIndices(int baseShard) {
        List<String> indices = new ArrayList<>();
        for (int d = -shardFanout; d <= shardFanout; d++) {
            int s = baseShard + d;
            if (s < 0 || s >= shardCount) continue;
            indices.add(getShardIndexName(s));
        }
        return indices;
    }

    private List<String> allShardIndices() {
        List<String> all = new ArrayList<>();
        for (int i = 0; i < shardCount; i++) all.add(getShardIndexName(i));
        return all;
    }
    
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
     * 청크 데이터 샤드별 Elasticsearch 색인
     */
    public void indexChunks(List<com.company.app.document.entity.DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return;
        try {
            // 샤드별로 청크 그룹화
            Map<Integer, List<com.company.app.document.entity.DocumentChunk>> chunksByShardId = new HashMap<>();
            
            for (var chunk : chunks) {
                // 청크의 임베딩을 기반으로 샤드 ID 계산
                int shardId = 0; // 기본값
                if (chunk.getEmbedding() != null && !chunk.getEmbedding().isEmpty()) {
                    shardId = shardingUtil.calculateShardId(chunk.getEmbedding());
                }
                
                chunksByShardId.computeIfAbsent(shardId, k -> new java.util.ArrayList<>()).add(chunk);
            }
            
            // 샤드별로 별도 인덱스에 저장
            for (Map.Entry<Integer, List<com.company.app.document.entity.DocumentChunk>> entry : chunksByShardId.entrySet()) {
                int shardId = entry.getKey();
                List<com.company.app.document.entity.DocumentChunk> shardChunks = entry.getValue();
                
                String shardIndexName = CHUNKS_INDEX + "_shard_" + shardId;
                indexChunksToShard(shardChunks, shardIndexName, shardId);
            }
            
        } catch (Exception e) { 
            log.error("샤드별 청크 색인 실패", e); 
        }
    }
    
    /**
     * 특정 샤드 인덱스에 청크들 색인
     */
    private void indexChunksToShard(List<com.company.app.document.entity.DocumentChunk> chunks, String indexName, int shardId) {
        try {
            // 샤드 인덱스가 없으면 생성
            createShardIndexIfNotExists(indexName);
            
            BulkRequest bulk = new BulkRequest();
            for (var ch : chunks) {
                Map<String,Object> m = new java.util.HashMap<>();
                m.put("documentId", ch.getDocumentId());
                m.put("chunkIndex", ch.getChunkIndex());
                m.put("content", ch.getContent());
                m.put("sectionHint", ch.getSectionHint());
                m.put("tokenCount", ch.getTokenCount());
                m.put("shardId", shardId);  // 샤드 ID 추가
                if (ch.getEmbedding() != null) m.put("embedding", ch.getEmbedding());
                m.put("createdAt", ch.getCreatedAt());
                
                IndexRequest ir = new IndexRequest(indexName)
                        .id(ch.getDocumentId()+"_"+ch.getChunkIndex())
                        .source(m, XContentType.JSON);
                bulk.add(ir);
            }
            
            if (bulk.numberOfActions() > 0) {
                BulkResponse resp = elasticsearchClient.bulk(bulk, RequestOptions.DEFAULT);
                if (resp.hasFailures()) {
                    log.warn("샤드 {} 청크 배치 색인 실패 있음: {}", shardId, resp.buildFailureMessage());
                } else {
                    log.info("샤드 {} 청크 {}개 색인 완료", shardId, chunks.size());
                }
            }
        } catch (Exception e) { 
            log.error("샤드 {} 청크 색인 실패", shardId, e); 
        }
    }
    
    /**
     * 샤드 인덱스가 없으면 생성
     */
    private void createShardIndexIfNotExists(String indexName) {
        try {
            GetIndexRequest getIndexRequest = new GetIndexRequest(indexName);
            boolean exists = elasticsearchClient.indices().exists(getIndexRequest, RequestOptions.DEFAULT);
            if (exists) return;
            
            Map<String,Object> mapping = new java.util.HashMap<>();
            Map<String,Object> properties = new java.util.HashMap<>();
            properties.put("documentId", java.util.Map.of("type","keyword"));
            properties.put("chunkIndex", java.util.Map.of("type","integer"));
            properties.put("content", java.util.Map.of("type","text","analyzer","standard"));
            properties.put("sectionHint", java.util.Map.of("type","text","analyzer","standard"));
            properties.put("tokenCount", java.util.Map.of("type","integer"));
            properties.put("shardId", java.util.Map.of("type","integer"));
            properties.put("embedding", java.util.Map.of("type","dense_vector","dims",1536,"index",true,"similarity","cosine"));
            properties.put("createdAt", java.util.Map.of("type","date"));
            mapping.put("properties", properties);
            
            CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexName);
            createIndexRequest.mapping(mapping);
            elasticsearchClient.indices().create(createIndexRequest, RequestOptions.DEFAULT);
            
            log.info("샤드 인덱스 생성 완료: {}", indexName);
        } catch (Exception e) { 
            log.error("샤드 인덱스 생성 실패: {}", indexName, e); 
        }
    }
    
    /**
     * 기존 MongoDB 청크들을 샤드별 Elasticsearch 인덱스로 마이그레이션
     */
    public void migrateChunksToShards() {
        try {
            log.info("청크 샤드 마이그레이션 시작");
            
            // MongoDB에서 모든 청크 조회
            List<com.company.app.document.entity.DocumentChunk> allChunks = chunkRepository.findAll();
            
            if (allChunks.isEmpty()) {
                log.info("마이그레이션할 청크가 없음");
                return;
            }
            
            log.info("총 {}개 청크 마이그레이션 시작", allChunks.size());
            
            // 기존 청크 인덱스 삭제 (옵션)
            deleteIndexIfExists(CHUNKS_INDEX);
            
            // 청크들을 샤드별로 인덱싱
            indexChunks(allChunks);
            
            log.info("청크 샤드 마이그레이션 완료: {}개 청크", allChunks.size());
            
        } catch (Exception e) {
            log.error("청크 샤드 마이그레이션 실패", e);
        }
    }
    
    /**
     * 모든 샤드 인덱스 재생성
     */
    public void recreateShardIndexes() {
        try {
            log.info("샤드 인덱스 재생성 시작");
            
            // 기존 샤드 인덱스들 삭제
            for (int shardId = 0; shardId < shardingUtil.getDefaultShardCount(); shardId++) {
                String shardIndexName = CHUNKS_INDEX + "_shard_" + shardId;
                deleteIndexIfExists(shardIndexName);
            }
            
            // 기존 통합 청크 인덱스도 삭제
            deleteIndexIfExists(CHUNKS_INDEX);
            
            // 청크 마이그레이션 실행
            migrateChunksToShards();
            
            log.info("샤드 인덱스 재생성 완료");
            
        } catch (Exception e) {
            log.error("샤드 인덱스 재생성 실패", e);
        }
    }
    
    /**
     * 특정 문서 ID에 대한 청크 삭제 (샤드 기반)
     */
    public void deleteChunksByDocumentId(String documentId) {
        try {
            log.info("문서 청크 삭제 시작: {}", documentId);
            
            // MongoDB에서 해당 문서의 청크들을 먼저 조회하여 샤드 정보 파악
            List<DocumentChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
            
            log.info("MongoDB에서 찾은 청크 개수: {}, documentId: {}", chunks.size(), documentId);
            
            if (chunks.isEmpty()) {
                log.warn("MongoDB에 청크가 없어서 Elasticsearch 샤드별 삭제를 시도합니다: {}", documentId);
                // MongoDB에 청크가 없어도 Elasticsearch에는 있을 수 있으므로 모든 샤드에서 삭제 시도
                int totalDeleted = 0;
                for (int shardId = 0; shardId < shardingUtil.getDefaultShardCount(); shardId++) {
                    try {
                        String shardIndexName = getShardIndexName(shardId);
                        DeleteByQueryRequest request = new DeleteByQueryRequest(shardIndexName);
                        request.setQuery(QueryBuilders.termQuery("documentId", documentId));
                        request.setConflicts("proceed");
                        
                        BulkByScrollResponse response = elasticsearchClient.deleteByQuery(request, RequestOptions.DEFAULT);
                        int deleted = (int) response.getDeleted();
                        totalDeleted += deleted;
                        
                        if (deleted > 0) {
                            log.info("샤드 {}에서 청크 삭제: documentId={}, deleted={}", shardId, documentId, deleted);
                        }
                    } catch (Exception e) {
                        log.warn("샤드 {}에서 청크 삭제 실패: documentId={}, error={}", shardId, documentId, e.getMessage());
                    }
                }
                log.info("전체 샤드 검색 완료: documentId={}, totalDeleted={}", documentId, totalDeleted);
                return;
            }
            
            // 샤드별로 그룹화
            Map<Integer, List<DocumentChunk>> chunksByShard = chunks.stream()
                .collect(Collectors.groupingBy(chunk -> 
                    shardingUtil.calculateShardId(chunk.getEmbedding())));
            
            int totalDeleted = 0;
            
            // 각 샤드에서 청크 삭제
            for (Map.Entry<Integer, List<DocumentChunk>> entry : chunksByShard.entrySet()) {
                int shardId = entry.getKey();
                
                try {
                    String shardIndexName = getShardIndexName(shardId);
                    
                    // 샤드별 인덱스에서 청크 삭제
                    DeleteByQueryRequest request = new DeleteByQueryRequest(shardIndexName);
                    request.setQuery(QueryBuilders.termQuery("documentId", documentId));
                    request.setConflicts("proceed");
                    
                    BulkByScrollResponse response = elasticsearchClient.deleteByQuery(request, RequestOptions.DEFAULT);
                    totalDeleted += (int) response.getDeleted();
                    
                    log.info("샤드 {}에서 청크 삭제 완료: documentId={}, deleted={}", 
                        shardId, documentId, response.getDeleted());
                    
                } catch (Exception e) {
                    log.warn("샤드 {}에서 청크 삭제 실패: documentId={}, error={}", 
                        shardId, documentId, e.getMessage());
                }
            }
            
            log.info("문서 청크 삭제 완료: documentId={}, totalDeleted={}, shardsAffected={}", 
                documentId, totalDeleted, chunksByShard.size());
            
        } catch (Exception e) {
            log.error("문서 청크 삭제 실패: documentId={}", documentId, e);
            // 기존 방식으로 fallback
            try {
                DeleteByQueryRequest request = new DeleteByQueryRequest(CHUNKS_INDEX);
                request.setQuery(QueryBuilders.termQuery("documentId", documentId));
                request.setConflicts("proceed");
                BulkByScrollResponse response = elasticsearchClient.deleteByQuery(request, RequestOptions.DEFAULT);
                log.info("Fallback 청크 삭제 완료: docId={}, deleted={}", documentId, response.getDeleted());
            } catch (Exception fallbackException) {
                log.warn("Fallback 청크 삭제도 실패: docId={}", documentId, fallbackException);
            }
        }
    }

    /**
     * 하이브리드 청크 검색 (RRF 기반 RAG retrieval)
     * @param query 사용자가 입력한 질의
     * @param topK 반환할 상위 청크 수
     */
    public List<ChunkSearchHit> searchChunks(String query, int topK) {
        try {
            log.info("하이브리드 검색 실행: query='{}', topK={}", query, topK);

            // 1. 벡터 + 2. 텍스트 검색 (샤드 와일드카드 사용)
            List<ChunkSearchHit> vectorResults = performVectorSearch(query, topK * 3); // 더 넉넉히 가져와 RRF 다양성 확보
            List<ChunkSearchHit> textResults = performTextSearch(query, topK * 3);

            log.info("원시 검색 결과: vectorRaw={}, textRaw={}", vectorResults.size(), textResults.size());

            // 3. RRF 융합
            List<ChunkSearchHit> hybridResults = applyRRF(vectorResults, textResults, topK);
            log.info("RRF 결과: hybrid={} (요청 topK={})", hybridResults.size(), topK);

            // 4. 하이브리드 결과 없음 -> 단순 match fallback
            if (hybridResults.isEmpty()) {
                log.warn("하이브리드 결과 없음. fallback 단순 match 검색 수행");
                List<ChunkSearchHit> fallback = performPlainMatchSearch(query, topK);
                log.info("Fallback 결과: {}", fallback.size());
                return fallback;
            }
            return hybridResults;
        } catch (Exception e) {
            log.error("하이브리드 청크 검색 실패 - fallback 시도", e);
            try {
                return performPlainMatchSearch(query, topK);
            } catch (Exception inner) {
                log.error("Fallback 검색도 실패", inner);
                return java.util.Collections.emptyList();
            }
        }
    }

    /**
     * 단순 match 검색 fallback (content 필드)
     */
    private List<ChunkSearchHit> performPlainMatchSearch(String query, int size) throws IOException {
        SearchSourceBuilder ssb = new SearchSourceBuilder()
            .query(QueryBuilders.matchQuery("content", query))
            .size(size)
            .fetchSource(new String[]{"content","documentId","chunkIndex","documentSerial","sectionHint"}, null);

        SearchRequest sr = new SearchRequest(SHARD_INDEX_WILDCARD).source(ssb);
        SearchResponse resp = elasticsearchClient.search(sr, RequestOptions.DEFAULT);
        List<ChunkSearchHit> list = new ArrayList<>();
        for (SearchHit hit : resp.getHits().getHits()) {
            list.add(createChunkSearchHit(hit, "fallback-text"));
        }
        return list;
    }
    
    /**
     * 벡터 검색 수행 (임계값 0.25 적용)
     */
    private List<ChunkSearchHit> performVectorSearch(String query, int size) {
        try {
            List<Double> embedding = embeddingService.generateEmbedding(query);
            int baseShard = shardingUtil.calculateShardId(embedding); // 기준 샤드
            List<String> candidateIndices = resolveShardIndices(baseShard);

            double threshold = 0.20; // 기존 임계값 유지 (score: 1.0 + threshold)
            List<ChunkSearchHit> results = vectorSearchInternal(embedding, candidateIndices, size, threshold);
            log.debug("벡터 1차(shards={}) 결과 {}개", candidateIndices, results.size());

            // 결과가 충분치 않으면 전체 샤드 확장
            if (results.size() < size * minSufficientRatio) {
                List<String> all = allShardIndices();
                if (all.size() > candidateIndices.size()) {
                    log.info("벡터 결과 부족({} < {}) → 전체 샤드 확장", results.size(), (int)(size * minSufficientRatio));
                    results = vectorSearchInternal(embedding, all, size, threshold);
                    log.debug("벡터 확장 결과 {}개", results.size());
                }
            }
            return results;
        } catch (Exception e) {
            log.error("벡터 검색 실패", e);
            return Collections.emptyList();
        }
    }

    private List<ChunkSearchHit> vectorSearchInternal(List<Double> embedding, List<String> indices, int size, double threshold) throws IOException {
        if (indices.isEmpty()) return Collections.emptyList();

        Map<String,Object> params = new HashMap<>();
        params.put("query_vector", embedding);
        params.put("threshold", threshold);
        Script script = new Script(ScriptType.INLINE, "painless",
                "double score = cosineSimilarity(params.query_vector, 'embedding') + 1.0; return score >= params.threshold + 1.0 ? score : 0.0",
                params);

        SearchSourceBuilder ssb = new SearchSourceBuilder()
                .size(size)
                .query(QueryBuilders.scriptScoreQuery(QueryBuilders.matchAllQuery(), script))
                .fetchSource(new String[]{"documentId","chunkIndex","content","sectionHint"}, null);

        SearchRequest sr = new SearchRequest(indices.toArray(new String[0])).source(ssb);
        SearchResponse resp = elasticsearchClient.search(sr, RequestOptions.DEFAULT);

        List<ChunkSearchHit> list = new ArrayList<>();
        for (SearchHit hit : resp.getHits().getHits()) {
            if (hit.getScore() < 1.0 + threshold) continue; // 필터
            list.add(createChunkSearchHit(hit, "vector"));
        }
        return list;
    }

    private List<ChunkSearchHit> performTextSearch(String query, int size) {
        try {
            // 동일한 샤드 범위 적용 (임베딩 재계산 비용은 소량 쿼리이므로 허용)
            List<Double> embedding = embeddingService.generateEmbedding(query);
            int baseShard = shardingUtil.calculateShardId(embedding);
            List<String> candidateIndices = resolveShardIndices(baseShard);

            List<ChunkSearchHit> results = textSearchInternal(query, candidateIndices, size);
            log.debug("텍스트 1차(shards={}) 결과 {}개", candidateIndices, results.size());

            if (results.size() < size * minSufficientRatio) {
                List<String> all = allShardIndices();
                if (all.size() > candidateIndices.size()) {
                    log.info("텍스트 결과 부족({} < {}) → 전체 샤드 확장", results.size(), (int)(size * minSufficientRatio));
                    results = textSearchInternal(query, all, size);
                    log.debug("텍스트 확장 결과 {}개", results.size());
                }
            }
            return results;
        } catch (Exception e) {
            log.error("텍스트 검색 실패", e);
            return Collections.emptyList();
        }
    }

    private List<ChunkSearchHit> textSearchInternal(String query, List<String> indices, int size) throws IOException {
        if (indices.isEmpty()) return Collections.emptyList();

        SearchSourceBuilder ssb = new SearchSourceBuilder()
                .size(size)
                .query(QueryBuilders.multiMatchQuery(query, "content", "sectionHint"))
                .fetchSource(new String[]{"documentId","chunkIndex","content","sectionHint"}, null);

        SearchRequest sr = new SearchRequest(indices.toArray(new String[0])).source(ssb);
        SearchResponse resp = elasticsearchClient.search(sr, RequestOptions.DEFAULT);
        List<ChunkSearchHit> list = new ArrayList<>();
        for (SearchHit hit : resp.getHits().getHits()) {
            list.add(createChunkSearchHit(hit, "text"));
        }
        return list;
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

    /* ===================== 카테고리 하이브리드 검색 (vector + text + RRF) ===================== */

    @lombok.Data
    public static class CategorySearchHit {
        private String id;
        private String code;
        private String name;
        private String description;
        private Double score; // RRF 최종 점수
        private Double vectorScore; // 원시 vector score (선택적)
        private Double textScore;   // 원시 text score (선택적)
    }

    public List<CategorySearchHit> searchCategoriesHybrid(String query, int topK) {
        try {
            log.info("카테고리 하이브리드 검색 실행: query='{}', topK={}", query, topK);
            List<CategorySearchHit> vector = performCategoryVectorSearch(query, topK * 3);
            List<CategorySearchHit> text = performCategoryTextSearch(query, topK * 3);
            List<CategorySearchHit> fused = applyRRFCategories(vector, text, topK);
            if (fused.isEmpty()) {
                log.warn("카테고리 하이브리드 결과 없음 - 텍스트 fallback");
                return performCategoryTextSearch(query, topK);
            }
            return fused;
        } catch (Exception e) {
            log.error("카테고리 하이브리드 검색 실패", e);
            try { return performCategoryTextSearch(query, topK); } catch (Exception ex) { return Collections.emptyList(); }
        }
    }

    private List<CategorySearchHit> performCategoryVectorSearch(String query, int size) {
        try {
            List<Double> embedding = embeddingService.generateEmbedding(query);
            double threshold = 0.25; // 카테고리는 더 느슨하게
            Map<String,Object> params = new HashMap<>();
            params.put("query_vector", embedding);
            params.put("threshold", threshold);
            Script script = new Script(ScriptType.INLINE, "painless",
                    "double s = cosineSimilarity(params.query_vector, 'vector') + 1.0; return s >= params.threshold + 1.0 ? s : 0.0", params);
            SearchSourceBuilder ssb = new SearchSourceBuilder()
                    .size(size)
                    .query(org.elasticsearch.index.query.QueryBuilders.scriptScoreQuery(org.elasticsearch.index.query.QueryBuilders.matchAllQuery(), script))
                    .fetchSource(new String[]{"id","code","name","description"}, null);
            SearchRequest sr = new SearchRequest(CATEGORIES_INDEX).source(ssb);
            SearchResponse resp = elasticsearchClient.search(sr, RequestOptions.DEFAULT);
            List<CategorySearchHit> list = new ArrayList<>();
            for (SearchHit hit : resp.getHits()) {
                if (hit.getScore() < 1.0 + threshold) continue;
                CategorySearchHit ch = createCategoryHit(hit);
                ch.setVectorScore((double) hit.getScore());
                list.add(ch);
            }
            return list;
        } catch (Exception e) {
            log.error("카테고리 벡터 검색 실패", e);
            return Collections.emptyList();
        }
    }

    private List<CategorySearchHit> performCategoryTextSearch(String query, int size) {
        try {
            SearchSourceBuilder ssb = new SearchSourceBuilder()
                    .size(size)
                    .query(org.elasticsearch.index.query.QueryBuilders.multiMatchQuery(query,
                            "name","description","aliases","includeKeywords","examplePhrases"))
                    .fetchSource(new String[]{"id","code","name","description"}, null);
            SearchRequest sr = new SearchRequest(CATEGORIES_INDEX).source(ssb);
            SearchResponse resp = elasticsearchClient.search(sr, RequestOptions.DEFAULT);
            List<CategorySearchHit> list = new ArrayList<>();
            for (SearchHit hit : resp.getHits()) {
                CategorySearchHit ch = createCategoryHit(hit);
                ch.setTextScore((double) hit.getScore());
                list.add(ch);
            }
            return list;
        } catch (Exception e) {
            log.error("카테고리 텍스트 검색 실패", e);
            return Collections.emptyList();
        }
    }

    private List<CategorySearchHit> applyRRFCategories(List<CategorySearchHit> vectorResults, List<CategorySearchHit> textResults, int topK) {
        Map<String, CategorySearchHit> map = new HashMap<>();
        Map<String, Double> scores = new HashMap<>();
        double k = 60.0;
        for (int i = 0; i < vectorResults.size(); i++) {
            CategorySearchHit h = vectorResults.get(i);
            String id = h.getId();
            double r = 1.0 / (k + i + 1);
            scores.put(id, scores.getOrDefault(id, 0.0) + r);
            map.put(id, h);
        }
        for (int i = 0; i < textResults.size(); i++) {
            CategorySearchHit h = textResults.get(i);
            String id = h.getId();
            double r = 1.0 / (k + i + 1);
            scores.put(id, scores.getOrDefault(id, 0.0) + r);
            map.putIfAbsent(id, h);
        }
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String,Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> { CategorySearchHit h = map.get(e.getKey()); h.setScore(e.getValue()); return h; })
                .collect(Collectors.toList());
    }

    private CategorySearchHit createCategoryHit(SearchHit hit) {
        Map<String,Object> src = hit.getSourceAsMap();
        CategorySearchHit ch = new CategorySearchHit();
        ch.setId((String) src.get("id"));
        ch.setCode((String) src.get("code"));
        ch.setName((String) src.get("name"));
        ch.setDescription((String) src.get("description"));
        return ch;
    }
}
