package com.company.app.search.service;

import com.company.app.document.entity.DocumentEntity;
import com.company.app.document.repository.DocumentRepository;
import com.company.app.search.util.ANNSearchUtil;
import com.company.app.search.util.VectorShardingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VectorSearchService {
    
    private final MongoTemplate mongoTemplate;
    private final DocumentRepository documentRepository;
    private final EmbeddingService embeddingService;
    private final VectorShardingUtil shardingUtil;
    private final ANNSearchUtil annSearchUtil;
    
    /**
     * Vector Search를 사용한 유사 문서 검색 (ANN + 샤딩)
     * 1536차원 벡터를 사용합니다.
     */
    public List<DocumentEntity> findSimilarDocuments(String text, int topK) {
        try {
            log.info("ANN 벡터 검색 시작: {}자 텍스트, 상위 {}개", text.length(), topK);
            
            // 1. 텍스트를 임베딩으로 변환
            List<Double> queryEmbedding = embeddingService.generateEmbedding(text);
            
            // 2. ANN 검색 실행
            List<DocumentEntity> results = executeANNSearch(queryEmbedding, topK);
            
            log.info("ANN 벡터 검색 완료: {}개 문서 발견", results.size());
            return results;
            
        } catch (Exception e) {
            log.error("ANN 벡터 검색 중 오류 발생: {}", e.getMessage(), e);
            return fallbackSearch(text, topK);
        }
    }
    
    /**
     * ANN 검색 실행 (샤딩 지원)
     */
    private List<DocumentEntity> executeANNSearch(List<Double> queryVector, int limit) {
        try {
            // 1. 쿼리 벡터의 샤드 ID 계산
            int queryShardId = shardingUtil.calculateShardId(queryVector);
            log.info("쿼리 벡터 샤드 ID: {}", queryShardId);
            
            // 2. 해당 샤드와 인접 샤드들에서 검색
            List<DocumentEntity> results = new ArrayList<>();
            
            // 현재 샤드와 인접 샤드들 검색
            for (int shardOffset = -1; shardOffset <= 1; shardOffset++) {
                int targetShardId = (queryShardId + shardOffset + shardingUtil.getDefaultShardCount()) 
                                  % shardingUtil.getDefaultShardCount();
                
                List<DocumentEntity> shardResults = searchInShard(queryVector, targetShardId, limit);
                results.addAll(shardResults);
                
                if (results.size() >= limit) {
                    break;
                }
            }
            
            // 3. 결과를 유사도 순으로 정렬하여 상위 limit개 반환
            return results.stream()
                .limit(limit)
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("ANN 검색 실행 중 오류: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 특정 샤드에서 검색 수행
     */
    private List<DocumentEntity> searchInShard(List<Double> queryVector, int shardId, int limit) {
        try {
            log.debug("샤드 {}에서 검색 시작", shardId);
            
            // 1. 해당 샤드의 모든 문서 조회
            Query query = new Query(Criteria.where("shardId").is(shardId));
            List<DocumentEntity> shardDocuments = mongoTemplate.find(query, DocumentEntity.class, "documents");
            
            if (shardDocuments.isEmpty()) {
                log.debug("샤드 {}에 문서가 없음", shardId);
                return new ArrayList<>();
            }
            
            // 2. 벡터가 있는 문서만 필터링
            List<DocumentEntity> documentsWithVectors = shardDocuments.stream()
                .filter(doc -> doc.getVectors() != null && 
                              doc.getVectors().getPurpose_1536() != null &&
                              !doc.getVectors().getPurpose_1536().isEmpty())
                .collect(Collectors.toList());
            
            if (documentsWithVectors.isEmpty()) {
                log.debug("샤드 {}에 벡터가 있는 문서가 없음", shardId);
                return new ArrayList<>();
            }
            
            // 3. ANN 검색 수행
            List<List<Double>> candidateVectors = documentsWithVectors.stream()
                .map(doc -> doc.getVectors().getPurpose_1536())
                .collect(Collectors.toList());
            
            List<Map.Entry<Integer, Double>> annResults = annSearchUtil.annSearch(
                queryVector, 
                candidateVectors, 
                limit, 
                0.1  // 낮은 임계값으로 시작
            );
            
            // 4. 결과를 DocumentEntity로 변환
            List<DocumentEntity> results = new ArrayList<>();
            for (Map.Entry<Integer, Double> result : annResults) {
                int index = result.getKey();
                double similarity = result.getValue();
                
                if (index < documentsWithVectors.size()) {
                    DocumentEntity doc = documentsWithVectors.get(index);
                    log.debug("샤드 {} - 문서 ID: {}, 유사도: {:.3f}", 
                        shardId, doc.getId(), similarity);
                    results.add(doc);
                }
            }
            
            log.debug("샤드 {} 검색 완료: {}개 문서 발견", shardId, results.size());
            return results;
            
        } catch (Exception e) {
            log.error("샤드 {} 검색 중 오류: {}", shardId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Vector Search 실패 시 대체 검색
     */
    private List<DocumentEntity> fallbackSearch(String text, int topK) {
        log.warn("ANN Vector Search 실패, 키워드 검색으로 대체");
        
        // 키워드 기반 검색으로 대체
        Pageable pageable = PageRequest.of(0, topK);
        return documentRepository.findByPurposeContainingIgnoreCase(text, pageable)
            .getContent();
    }
    
    /**
     * 하이브리드 검색 (Vector + Keyword)
     */
    public List<DocumentEntity> hybridSearch(String query, int topK) {
        try {
            log.info("하이브리드 검색 시작: {}자 쿼리, 상위 {}개", query.length(), topK);
            
            // 1. ANN Vector Search로 유사 문서 검색
            List<DocumentEntity> vectorResults = findSimilarDocuments(query, topK * 2);
            
            // 2. Keyword Search로 텍스트 매칭
            Pageable pageable = PageRequest.of(0, topK * 2);
            List<DocumentEntity> keywordResults = documentRepository
                .findByPurposeContainingIgnoreCase(query, pageable)
                .getContent();
            
            // 3. 결과를 결합하고 점수 정규화
            List<DocumentEntity> combinedResults = combineSearchResults(
                vectorResults, keywordResults, query, topK);
            
            log.info("하이브리드 검색 완료: {}개 문서 발견", combinedResults.size());
            return combinedResults;
            
        } catch (Exception e) {
            log.error("하이브리드 검색 중 오류 발생: {}", e.getMessage(), e);
            return fallbackSearch(query, topK);
        }
    }
    
    /**
     * 검색 결과를 결합하고 점수 정규화
     */
    private List<DocumentEntity> combineSearchResults(
            List<DocumentEntity> vectorResults, 
            List<DocumentEntity> keywordResults, 
            String query, 
            int topK) {
        
        Map<String, DocumentWithScore> documentScores = new java.util.HashMap<>();
        
        // Vector Search 결과 점수 부여 (0.7 가중치)
        for (int i = 0; i < vectorResults.size(); i++) {
            DocumentEntity doc = vectorResults.get(i);
            double score = 0.7 * (1.0 - (double) i / vectorResults.size());
            documentScores.put(doc.getId(), new DocumentWithScore(doc, score));
        }
        
        // Keyword Search 결과 점수 부여 (0.3 가중치)
        for (int i = 0; i < keywordResults.size(); i++) {
            DocumentEntity doc = keywordResults.get(i);
            double score = 0.3 * (1.0 - (double) i / keywordResults.size());
            
            if (documentScores.containsKey(doc.getId())) {
                // 이미 있는 문서면 점수 합산
                DocumentWithScore existing = documentScores.get(doc.getId());
                documentScores.put(doc.getId(), 
                    new DocumentWithScore(doc, existing.score + score));
            } else {
                documentScores.put(doc.getId(), new DocumentWithScore(doc, score));
            }
        }
        
        // 점수 순으로 정렬하여 상위 K개 반환
        return documentScores.values().stream()
            .sorted((a, b) -> Double.compare(b.score, a.score))
            .limit(topK)
            .map(dws -> dws.document)
            .collect(Collectors.toList());
    }
    
    /**
     * 문서와 점수를 함께 저장하는 내부 클래스
     */
    private static class DocumentWithScore {
        final DocumentEntity document;
        final double score;
        
        DocumentWithScore(DocumentEntity document, double score) {
            this.document = document;
            this.score = score;
        }
    }
    
    /**
     * 문서를 Vector Search 인덱스에 추가/업데이트 (샤딩 지원)
     * 1536차원 벡터를 생성하고 샤드 ID를 할당합니다.
     */
    public void indexDocument(DocumentEntity document) {
        try {
            log.info("문서 인덱싱 시작: {}", document.getId());
            
            // 1. 텍스트를 임베딩으로 변환
            String textToEmbed = document.getPurpose() + " " + 
                (document.getContent() != null ? document.getContent() : "");
            
            List<Double> embedding = embeddingService.generateEmbedding(textToEmbed);
            
            // 2. 벡터 임베딩을 문서에 저장 (1536차원)
            if (document.getVectors() == null) {
                document.setVectors(new DocumentEntity.VectorEmbeddings());
            }
            document.getVectors().setPurpose_1536(embedding);
            
            // 3. 샤드 ID 계산 및 할당
            int shardId = shardingUtil.calculateShardId(embedding);
            document.setShardId(shardId);
            
            // 4. MongoDB에 저장
            documentRepository.save(document);
            
            log.info("문서 인덱싱 완료: {} (샤드 ID: {})", document.getId(), shardId);
            
        } catch (Exception e) {
            log.error("문서 인덱싱 중 오류 발생: {}", e.getMessage(), e);
            // 인덱싱 실패해도 문서는 저장
            documentRepository.save(document);
        }
    }
    
    /**
     * 여러 문서를 배치로 인덱싱 (샤딩 지원)
     * 1536차원 벡터를 생성하고 샤드 ID를 할당합니다.
     */
    public void indexDocuments(List<DocumentEntity> documents) {
        try {
            log.info("배치 문서 인덱싱 시작: {}개", documents.size());
            
            // 1. 모든 문서의 텍스트를 수집
            List<String> texts = documents.stream()
                .map(doc -> doc.getPurpose() + " " + 
                    (doc.getContent() != null ? doc.getContent() : ""))
                .collect(Collectors.toList());
            
            // 2. 배치로 임베딩 생성
            List<List<Double>> embeddings = embeddingService.generateEmbeddings(texts);
            
            // 3. 각 문서에 임베딩 저장 및 샤드 ID 할당
            for (int i = 0; i < documents.size(); i++) {
                DocumentEntity doc = documents.get(i);
                List<Double> embedding = embeddings.get(i);
                
                if (doc.getVectors() == null) {
                    doc.setVectors(new DocumentEntity.VectorEmbeddings());
                }
                doc.getVectors().setPurpose_1536(embedding);
                
                // 샤드 ID 계산 및 할당
                int shardId = shardingUtil.calculateShardId(embedding);
                doc.setShardId(shardId);
            }
            
            // 4. 배치로 저장
            documentRepository.saveAll(documents);
            
            log.info("배치 문서 인덱싱 완료: {}개", documents.size());
            
        } catch (Exception e) {
            log.error("배치 문서 인덱싱 중 오류 발생: {}", e.getMessage(), e);
            // 개별 저장으로 대체
            for (DocumentEntity doc : documents) {
                try {
                    indexDocument(doc);
                } catch (Exception ex) {
                    log.error("개별 문서 인덱싱 실패: {}", doc.getId(), ex);
                }
            }
        }
    }
    
    /**
     * 문서를 Vector Search 인덱스에서 제거
     */
    public void removeDocument(String documentId) {
        try {
            log.info("문서 인덱스 제거: {}", documentId);
            documentRepository.deleteById(documentId);
            log.info("문서 인덱스 제거 완료: {}", documentId);
        } catch (Exception e) {
            log.error("문서 인덱스 제거 중 오류 발생: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 모든 문서를 재인덱싱 (샤딩 지원)
     */
    public void reindexAllDocuments() {
        try {
            log.info("전체 문서 재인덱싱 시작 (샤딩 지원)");
            
            List<DocumentEntity> allDocuments = documentRepository.findAll();
            log.info("재인덱싱할 문서 수: {}개", allDocuments.size());
            
            // 배치 크기로 나누어 처리
            int batchSize = 10;
            for (int i = 0; i < allDocuments.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, allDocuments.size());
                List<DocumentEntity> batch = allDocuments.subList(i, endIndex);
                
                log.info("배치 재인덱싱: {}/{}", endIndex, allDocuments.size());
                indexDocuments(batch);
            }
            
            log.info("전체 문서 재인덱싱 완료");
            
        } catch (Exception e) {
            log.error("전체 문서 재인덱싱 중 오류 발생: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Vector Search 인덱스 생성
     * MongoDB Atlas에서 실행해야 합니다.
     */
    public void createVectorIndex() {
        log.info("Vector Search 인덱스 생성 안내 (샤딩 지원)");
        log.info("MongoDB Atlas 콘솔에서 다음 인덱스들을 수동으로 생성하세요:");
        
        for (int shardId = 0; shardId < shardingUtil.getDefaultShardCount(); shardId++) {
            String collectionName = shardingUtil.getShardCollectionName(shardId);
            log.info("샤드 {} 인덱스:", shardId);
            log.info("""
                {
                  "fields": [
                    {
                      "type": "vector",
                      "path": "vectors.purpose_1536",
                      "numDimensions": 1536,
                      "similarity": "cosine"
                    },
                    {
                      "type": "filter",
                      "path": "shardId"
                    },
                    {
                      "type": "filter",
                      "path": "category.subCode"
                    },
                    {
                      "type": "filter", 
                      "path": "ownerId"
                    }
                  ]
                }
                """);
        }
    }
}