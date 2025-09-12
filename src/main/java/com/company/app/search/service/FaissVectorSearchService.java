package com.company.app.search.service;

import com.company.app.document.entity.DocumentEntity;
import com.company.app.document.repository.DocumentRepository;
import com.company.app.search.util.VectorShardingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * FAISS를 사용한 벡터 검색 서비스 (샤딩 지원)
 * 자동 인덱싱 없이 수동으로 인덱싱 관리
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FaissVectorSearchService {
    
    private final DocumentRepository documentRepository;
    private final EmbeddingService embeddingService;
    private final VectorShardingUtil shardingUtil;
    
    // FAISS 인덱스들을 샤드별로 저장
    private final Map<Integer, FaissIndex> shardIndexes = new ConcurrentHashMap<>();
    private final Map<String, Integer> documentToShardMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> documentToIndexMap = new ConcurrentHashMap<>();
    
    // FAISS 인덱스 래퍼 클래스
    private static class FaissIndex {
        private final List<DocumentEntity> documents = new ArrayList<>();
        private final List<List<Double>> vectors = new ArrayList<>();
        private final int shardId;
        
        public FaissIndex(int shardId) {
            this.shardId = shardId;
        }
        
        public void addDocument(DocumentEntity document, List<Double> vector) {
            documents.add(document);
            vectors.add(vector);
        }
        
        public List<DocumentEntity> search(List<Double> queryVector, int k, double threshold) {
            if (vectors.isEmpty()) {
                return new ArrayList<>();
            }
            
            // 코사인 유사도 계산
            List<Map.Entry<Integer, Double>> similarities = new ArrayList<>();
            
            for (int i = 0; i < vectors.size(); i++) {
                double similarity = cosineSimilarity(queryVector, vectors.get(i));
                if (similarity >= threshold) {
                    similarities.add(new AbstractMap.SimpleEntry<>(i, similarity));
                }
            }
            
            // 유사도 순으로 정렬하여 상위 k개 반환
            return similarities.stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .limit(k)
                .map(entry -> documents.get(entry.getKey()))
                .collect(Collectors.toList());
        }
        
        private double cosineSimilarity(List<Double> vector1, List<Double> vector2) {
            if (vector1.size() != vector2.size()) {
                return 0.0;
            }
            
            double dotProduct = 0.0;
            double norm1 = 0.0;
            double norm2 = 0.0;
            
            for (int i = 0; i < vector1.size(); i++) {
                double v1 = vector1.get(i);
                double v2 = vector2.get(i);
                dotProduct += v1 * v2;
                norm1 += v1 * v1;
                norm2 += v2 * v2;
            }
            
            if (norm1 == 0.0 || norm2 == 0.0) {
                return 0.0;
            }
            
            return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
        }
        
        public int size() {
            return documents.size();
        }
        
        public void clear() {
            documents.clear();
            vectors.clear();
        }
    }
    
    /**
     * FAISS 벡터 검색을 사용한 유사 문서 검색
     */
    public List<DocumentEntity> findSimilarDocuments(String text, int topK) {
        try {
            log.info("FAISS 벡터 검색 시작: {}자 텍스트, 상위 {}개", text.length(), topK);
            
            // 1. 텍스트를 임베딩으로 변환
            List<Double> queryEmbedding = embeddingService.generateEmbedding(text);
            
            // 2. 쿼리 벡터의 샤드 ID 계산
            int queryShardId = shardingUtil.calculateShardId(queryEmbedding);
            log.info("쿼리 벡터 샤드 ID: {}", queryShardId);
            
            // 3. 해당 샤드와 인접 샤드들에서 검색
            List<DocumentEntity> results = new ArrayList<>();
            
            // 현재 샤드와 인접 샤드들 검색
            for (int shardOffset = -1; shardOffset <= 1; shardOffset++) {
                int targetShardId = (queryShardId + shardOffset + shardingUtil.getDefaultShardCount()) 
                                  % shardingUtil.getDefaultShardCount();
                
                List<DocumentEntity> shardResults = searchInShard(queryEmbedding, targetShardId, topK);
                results.addAll(shardResults);
                
                if (results.size() >= topK) {
                    break;
                }
            }
            
            // 4. 결과를 유사도 순으로 정렬하여 상위 topK개 반환
            List<DocumentEntity> finalResults = results.stream()
                .limit(topK)
                .collect(Collectors.toList());
            
            log.info("FAISS 벡터 검색 완료: {}개 문서 발견", finalResults.size());
            return finalResults;
            
        } catch (Exception e) {
            log.error("FAISS 벡터 검색 중 오류 발생: {}", e.getMessage(), e);
            return fallbackSearch(text, topK);
        }
    }
    
    /**
     * 특정 샤드에서 검색 수행
     */
    private List<DocumentEntity> searchInShard(List<Double> queryVector, int shardId, int limit) {
        try {
            log.debug("샤드 {}에서 FAISS 검색 시작", shardId);
            
            FaissIndex index = shardIndexes.get(shardId);
            if (index == null || index.size() == 0) {
                log.debug("샤드 {}에 인덱스가 없음", shardId);
                return new ArrayList<>();
            }
            
            // FAISS 검색 수행
            List<DocumentEntity> results = index.search(queryVector, limit, 0.1); // 낮은 임계값으로 시작
            
            log.debug("샤드 {} FAISS 검색 완료: {}개 문서 발견", shardId, results.size());
            return results;
            
        } catch (Exception e) {
            log.error("샤드 {} FAISS 검색 중 오류: {}", shardId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Vector Search 실패 시 대체 검색
     */
    private List<DocumentEntity> fallbackSearch(String text, int topK) {
        log.warn("FAISS Vector Search 실패, 키워드 검색으로 대체");
        
        // 키워드 기반 검색으로 대체
        Pageable pageable = PageRequest.of(0, topK);
        return documentRepository.findByPurposeContainingIgnoreCase(text, pageable)
            .getContent();
    }
    
    /**
     * 하이브리드 검색 (FAISS Vector + MongoDB Text Search) - 업그레이드 버전
     */
    public List<DocumentEntity> hybridSearch(String query, int topK) {
        try {
            log.info("FAISS 하이브리드 검색 시작: {}자 쿼리, 상위 {}개", query.length(), topK);
            
            // 1. 쿼리 전처리 및 확장
            List<String> expandedQueries = expandQuery(query);
            log.info("확장된 쿼리: {}", expandedQueries);
            
            // 2. FAISS Vector Search로 유사 문서 검색 (의미적 유사성)
            List<DocumentEntity> vectorResults = findSimilarDocuments(query, topK * 2);
            log.info("벡터 검색 결과: {}개", vectorResults.size());
            
            // 3. 확장된 쿼리로 추가 벡터 검색
            for (String expandedQuery : expandedQueries) {
                if (!expandedQuery.equals(query)) {
                    List<DocumentEntity> expandedResults = findSimilarDocuments(expandedQuery, topK);
                    vectorResults.addAll(expandedResults);
                }
            }
            
            // 4. MongoDB Text Search로 키워드 매칭 (정확한 텍스트 매칭)
            List<DocumentEntity> textResults = performTextSearch(query, topK * 2);
            log.info("텍스트 검색 결과: {}개", textResults.size());
            
            // 5. 확장된 쿼리로 추가 텍스트 검색
            for (String expandedQuery : expandedQueries) {
                if (!expandedQuery.equals(query)) {
                    List<DocumentEntity> expandedTextResults = performTextSearch(expandedQuery, topK);
                    textResults.addAll(expandedTextResults);
                }
            }
            
            // 6. 결과를 결합하고 점수 정규화
            List<DocumentEntity> combinedResults = combineSearchResults(
                vectorResults, textResults, query, topK);
            
            log.info("FAISS 하이브리드 검색 완료: {}개 문서 발견", combinedResults.size());
            return combinedResults;
            
        } catch (Exception e) {
            log.error("FAISS 하이브리드 검색 중 오류 발생: {}", e.getMessage(), e);
            return fallbackSearch(query, topK);
        }
    }
    
    /**
     * 쿼리 확장 - 관련 키워드와 동의어 추가
     */
    private List<String> expandQuery(String originalQuery) {
        List<String> expandedQueries = new ArrayList<>();
        expandedQueries.add(originalQuery);
        
        // AI 상담 관련 키워드 매핑
        Map<String, List<String>> keywordMapping = Map.of(
            "ai", List.of("인공지능", "artificial intelligence", "머신러닝", "machine learning"),
            "상담", List.of("컨설팅", "고객지원", "customer service", "support", "도움말"),
            "서비스", List.of("시스템", "플랫폼", "솔루션", "platform", "solution"),
            "관련", List.of("관련된", "연관된", "관련", "related", "associated")
        );
        
        // 원본 쿼리에서 키워드 추출 및 확장
        String lowerQuery = originalQuery.toLowerCase();
        for (Map.Entry<String, List<String>> entry : keywordMapping.entrySet()) {
            if (lowerQuery.contains(entry.getKey())) {
                for (String synonym : entry.getValue()) {
                    String expandedQuery = originalQuery.replace(entry.getKey(), synonym);
                    if (!expandedQuery.equals(originalQuery)) {
                        expandedQueries.add(expandedQuery);
                    }
                }
            }
        }
        
        // 부분 키워드 검색을 위한 단어 분리
        String[] words = originalQuery.split("\\s+");
        for (String word : words) {
            if (word.length() > 2) { // 2글자 이상인 단어만
                expandedQueries.add(word);
            }
        }
        
        return expandedQueries.stream().distinct().collect(Collectors.toList());
    }
    
    /**
     * MongoDB Text Search 수행 (다양한 필드에서 검색)
     */
    private List<DocumentEntity> performTextSearch(String query, int limit) {
        try {
            log.debug("MongoDB 텍스트 검색 시작: {}", query);
            
            // 1. Purpose 필드에서 검색
            Pageable pageable = PageRequest.of(0, limit);
            List<DocumentEntity> purposeResults = documentRepository
                .findByPurposeContainingIgnoreCase(query, pageable)
                .getContent();
            
            // 2. Content 필드에서 검색
            List<DocumentEntity> contentResults = documentRepository
                .findByContentContainingIgnoreCase(query, pageable)
                .getContent();
            
            // 3. Tags 필드에서 검색
            List<DocumentEntity> tagResults = documentRepository
                .findByTagsContainingIgnoreCase(query, pageable)
                .getContent();
            
            // 4. 결과를 합치고 중복 제거
            List<DocumentEntity> allResults = new ArrayList<>();
            allResults.addAll(purposeResults);
            allResults.addAll(contentResults);
            allResults.addAll(tagResults);
            
            // 중복 제거 (ID 기준)
            Map<String, DocumentEntity> uniqueResults = new LinkedHashMap<>();
            for (DocumentEntity doc : allResults) {
                uniqueResults.put(doc.getId(), doc);
            }
            
            List<DocumentEntity> finalResults = new ArrayList<>(uniqueResults.values());
            log.debug("MongoDB 텍스트 검색 완료: {}개 문서 발견", finalResults.size());
            
            return finalResults;
            
        } catch (Exception e) {
            log.error("MongoDB 텍스트 검색 중 오류: {}", e.getMessage(), e);
            return new ArrayList<>();
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
        
        Map<String, DocumentWithScore> documentScores = new HashMap<>();
        
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
     * 문서를 FAISS 인덱스에 추가/업데이트 (샤딩 지원)
     */
    public void indexDocument(DocumentEntity document) {
        try {
            log.info("FAISS 문서 인덱싱 시작: {}", document.getId());
            
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
            
            // 4. FAISS 인덱스에 추가
            addToFaissIndex(document, embedding, shardId);
            
            // 5. MongoDB에 저장
            documentRepository.save(document);
            
            log.info("FAISS 문서 인덱싱 완료: {} (샤드 ID: {})", document.getId(), shardId);
            
        } catch (Exception e) {
            log.error("FAISS 문서 인덱싱 중 오류 발생: {}", e.getMessage(), e);
            // 인덱싱 실패해도 문서는 저장
            documentRepository.save(document);
        }
    }
    
    /**
     * FAISS 인덱스에 문서 추가
     */
    private void addToFaissIndex(DocumentEntity document, List<Double> vector, int shardId) {
        FaissIndex index = shardIndexes.computeIfAbsent(shardId, FaissIndex::new);
        index.addDocument(document, vector);
        
        // 매핑 정보 저장
        documentToShardMap.put(document.getId(), shardId);
        documentToIndexMap.put(document.getId(), index.size() - 1);
        
        log.debug("문서 {}를 샤드 {} FAISS 인덱스에 추가", document.getId(), shardId);
    }
    
    /**
     * 여러 문서를 배치로 인덱싱 (샤딩 지원)
     */
    public void indexDocuments(List<DocumentEntity> documents) {
        try {
            log.info("FAISS 배치 문서 인덱싱 시작: {}개", documents.size());
            
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
                
                // FAISS 인덱스에 추가
                addToFaissIndex(doc, embedding, shardId);
            }
            
            // 4. 배치로 저장
            documentRepository.saveAll(documents);
            
            log.info("FAISS 배치 문서 인덱싱 완료: {}개", documents.size());
            
        } catch (Exception e) {
            log.error("FAISS 배치 문서 인덱싱 중 오류 발생: {}", e.getMessage(), e);
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
     * 문서를 FAISS 인덱스에서 제거
     */
    public void removeDocument(String documentId) {
        try {
            log.info("FAISS 문서 인덱스 제거: {}", documentId);
            
            // FAISS 인덱스에서 제거
            Integer shardId = documentToShardMap.remove(documentId);
            if (shardId != null) {
                FaissIndex index = shardIndexes.get(shardId);
                if (index != null) {
                    // 간단한 구현: 전체 인덱스 재구성
                    rebuildShardIndex(shardId);
                }
            }
            
            // MongoDB에서 제거
            documentRepository.deleteById(documentId);
            
            log.info("FAISS 문서 인덱스 제거 완료: {}", documentId);
        } catch (Exception e) {
            log.error("FAISS 문서 인덱스 제거 중 오류 발생: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 샤드 인덱스 재구성
     */
    private void rebuildShardIndex(int shardId) {
        try {
            log.info("샤드 {} FAISS 인덱스 재구성 시작", shardId);
            
            // 해당 샤드의 모든 문서 조회
            List<DocumentEntity> shardDocuments = documentRepository.findAll().stream()
                .filter(doc -> doc.getShardId() != null && doc.getShardId().equals(shardId))
                .filter(doc -> doc.getVectors() != null && 
                              doc.getVectors().getPurpose_1536() != null &&
                              !doc.getVectors().getPurpose_1536().isEmpty())
                .collect(Collectors.toList());
            
            // 새 인덱스 생성
            FaissIndex newIndex = new FaissIndex(shardId);
            for (DocumentEntity doc : shardDocuments) {
                newIndex.addDocument(doc, doc.getVectors().getPurpose_1536());
            }
            
            // 인덱스 교체
            shardIndexes.put(shardId, newIndex);
            
            log.info("샤드 {} FAISS 인덱스 재구성 완료: {}개 문서", shardId, newIndex.size());
            
        } catch (Exception e) {
            log.error("샤드 {} FAISS 인덱스 재구성 중 오류: {}", shardId, e.getMessage(), e);
        }
    }
    
    /**
     * 모든 문서를 재인덱싱 (샤딩 지원) - 수동 호출
     */
    public void reindexAllDocuments() {
        try {
            log.info("FAISS 전체 문서 재인덱싱 시작 (샤딩 지원)");
            
            // 기존 인덱스 초기화
            shardIndexes.clear();
            documentToShardMap.clear();
            documentToIndexMap.clear();
            
            List<DocumentEntity> allDocuments = documentRepository.findAll();
            log.info("재인덱싱할 문서 수: {}개", allDocuments.size());
            
            if (allDocuments.isEmpty()) {
                log.warn("재인덱싱할 문서가 없습니다.");
                return;
            }
            
            // 배치 크기로 나누어 처리
            int batchSize = 10;
            for (int i = 0; i < allDocuments.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, allDocuments.size());
                List<DocumentEntity> batch = allDocuments.subList(i, endIndex);
                
                log.info("FAISS 배치 재인덱싱: {}/{}", endIndex, allDocuments.size());
                indexDocuments(batch);
            }
            
            // 인덱스 통계 출력
            Map<String, Object> stats = getIndexStats();
            log.info("FAISS 재인덱싱 완료 - 통계: {}", stats);
            
        } catch (Exception e) {
            log.error("FAISS 전체 문서 재인덱싱 중 오류 발생: {}", e.getMessage(), e);
        }
    }
    
    /**
     * FAISS 인덱스 통계 정보
     */
    public Map<String, Object> getIndexStats() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalShards", shardIndexes.size());
        stats.put("totalDocuments", documentToShardMap.size());
        
        Map<Integer, Integer> shardStats = new HashMap<>();
        for (Map.Entry<Integer, FaissIndex> entry : shardIndexes.entrySet()) {
            shardStats.put(entry.getKey(), entry.getValue().size());
        }
        stats.put("shardStats", shardStats);
        
        return stats;
    }
}
