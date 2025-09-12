package com.company.app.search.service;

import com.company.app.document.entity.DocumentEntity;
import com.company.app.document.repository.DocumentRepository;
import com.company.app.search.util.VectorShardingUtil;
import com.company.app.chat.service.LLMService;
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
    private final LLMService llmService;
    
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
        
        public void removeDocument(int index) {
            if (index >= 0 && index < documents.size()) {
                documents.remove(index);
                vectors.remove(index);
            }
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
     * AI 기반 쿼리 확장 - 관련 키워드와 동의어 추가
     */
    private List<String> expandQuery(String originalQuery) {
        List<String> expandedQueries = new ArrayList<>();
        expandedQueries.add(originalQuery);
        
        try {
            // AI를 사용하여 쿼리 확장
            List<String> aiExpandedQueries = expandQueryWithAI(originalQuery);
            expandedQueries.addAll(aiExpandedQueries);
            
            log.info("AI 기반 쿼리 확장 완료: {} -> {}", originalQuery, aiExpandedQueries);
            
        } catch (Exception e) {
            log.warn("AI 쿼리 확장 실패: {}", e.getMessage());
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
     * AI를 사용한 쿼리 확장
     */
    private List<String> expandQueryWithAI(String query) {
        try {
            // AI에게 쿼리 확장 요청
            String prompt = String.format(
                "다음 검색 쿼리를 분석하고 관련된 키워드와 동의어를 생성해주세요. " +
                "기술 스택, 프레임워크, 카테고리별로 관련 키워드를 찾아주세요.\n\n" +
                "원본 쿼리: \"%s\"\n\n" +
                "다음 형식으로 응답해주세요:\n" +
                "1. 관련 기술 키워드 (예: spring, java, backend)\n" +
                "2. 동의어 (예: 서버, server, api)\n" +
                "3. 카테고리 키워드 (예: 웹개발, 프론트엔드, 백엔드)\n" +
                "4. 관련 프레임워크 (예: nextjs, react, django)\n\n" +
                "각 항목을 쉼표로 구분하여 한 줄씩 작성해주세요.",
                query
            );
            
            // LLM 서비스 호출 (실제 구현에서는 LLMService 사용)
            String aiResponse = callLLMForQueryExpansion(prompt);
            
            // AI 응답을 파싱하여 키워드 리스트 생성
            return parseAIResponse(aiResponse);
            
        } catch (Exception e) {
            log.error("AI 쿼리 확장 중 오류: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * LLM을 호출하여 쿼리 확장 수행
     */
    private String callLLMForQueryExpansion(String prompt) {
        try {
            // 실제 LLM 서비스 호출
            return llmService.generateText(prompt);
            
        } catch (Exception e) {
            log.error("LLM 호출 실패: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * AI 응답을 파싱하여 키워드 리스트 생성
     */
    private List<String> parseAIResponse(String aiResponse) {
        List<String> keywords = new ArrayList<>();
        
        try {
            String[] lines = aiResponse.split("\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                
                // 번호나 마커 제거 (예: "1. ", "2. ", "- ")
                String cleanLine = line.replaceAll("^\\d+\\.\\s*", "")
                                     .replaceAll("^-\\s*", "")
                                     .trim();
                
                if (!cleanLine.isEmpty()) {
                    // 쉼표로 구분된 키워드들을 분리
                    String[] parts = cleanLine.split(",");
                    for (String part : parts) {
                        String keyword = part.trim();
                        if (!keyword.isEmpty() && keyword.length() > 1) {
                            keywords.add(keyword);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("AI 응답 파싱 실패: {}", e.getMessage());
        }
        
        return keywords;
    }
    
    
    /**
     * 쿼리에서 필터링 키워드 추출
     */
    private List<String> extractFilterKeywords(String query) {
        List<String> filterKeywords = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        
        // 기술 스택 키워드 추출
        String[] techKeywords = {
            "백엔드", "backend", "spring", "java", "python", "nodejs", "express", "django", "flask", "fastapi",
            "프론트엔드", "frontend", "nextjs", "react", "vue", "angular", "javascript", "typescript", "html", "css",
            "모바일", "mobile", "android", "ios", "flutter", "react native",
            "데이터베이스", "database", "mysql", "mongodb", "postgresql",
            "웹", "web", "웹개발", "web development", "웹사이트", "website"
        };
        
        for (String keyword : techKeywords) {
            if (lowerQuery.contains(keyword.toLowerCase())) {
                filterKeywords.add(keyword);
            }
        }
        
        return filterKeywords;
    }
    
    /**
     * 문서가 검색 결과에 포함되어야 하는지 판단
     */
    private boolean shouldIncludeDocument(DocumentEntity doc, String query, List<String> filterKeywords) {
        if (filterKeywords.isEmpty()) {
            return true; // 필터링 키워드가 없으면 모든 문서 포함
        }
        
        String docContent = (doc.getContent() != null ? doc.getContent() : "").toLowerCase();
        String docPurpose = (doc.getPurpose() != null ? doc.getPurpose() : "").toLowerCase();
        String docTags = (doc.getTags() != null ? String.join(" ", doc.getTags()) : "").toLowerCase();
        
        // 카테고리 정보 확인
        String categoryInfo = "";
        if (doc.getCategory() != null) {
            categoryInfo = (doc.getCategory().getMajorName() + " " + 
                          doc.getCategory().getMidName() + " " + 
                          doc.getCategory().getSubName()).toLowerCase();
        }
        
        String allDocText = docContent + " " + docPurpose + " " + docTags + " " + categoryInfo;
        
        // 프론트엔드 관련 키워드가 있으면 프론트엔드 문서만 포함
        if (containsFrontendKeywords(filterKeywords)) {
            return containsFrontendKeywords(List.of(allDocText)) || 
                   (doc.getCategory() != null && 
                    doc.getCategory().getSubName() != null && 
                    doc.getCategory().getSubName().toLowerCase().contains("프론트"));
        }
        
        // 백엔드 관련 키워드가 있으면 백엔드 문서만 포함
        if (containsBackendKeywords(filterKeywords)) {
            return containsBackendKeywords(List.of(allDocText)) || 
                   (doc.getCategory() != null && 
                    doc.getCategory().getSubName() != null && 
                    doc.getCategory().getSubName().toLowerCase().contains("백엔드"));
        }
        
        // 웹개발 관련 키워드가 있으면 웹개발 문서만 포함
        if (containsWebKeywords(filterKeywords)) {
            return containsWebKeywords(List.of(allDocText)) || 
                   (doc.getCategory() != null && 
                    doc.getCategory().getMidName() != null && 
                    doc.getCategory().getMidName().toLowerCase().contains("웹"));
        }
        
        // 모바일 관련 키워드가 있으면 모바일 문서만 포함
        if (containsMobileKeywords(filterKeywords)) {
            return containsMobileKeywords(List.of(allDocText));
        }
        
        return true; // 기본적으로 모든 문서 포함
    }
    
    /**
     * 프론트엔드 관련 키워드 확인
     */
    private boolean containsFrontendKeywords(List<String> keywords) {
        String[] frontendKeywords = {
            "프론트엔드", "frontend", "nextjs", "react", "vue", "angular", 
            "javascript", "typescript", "html", "css", "ui", "ux"
        };
        
        for (String keyword : keywords) {
            for (String frontendKeyword : frontendKeywords) {
                if (keyword.toLowerCase().contains(frontendKeyword.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * 백엔드 관련 키워드 확인
     */
    private boolean containsBackendKeywords(List<String> keywords) {
        String[] backendKeywords = {
            "백엔드", "backend", "spring", "java", "python", "nodejs", "express", 
            "django", "flask", "fastapi", "서버", "server", "api", "데이터베이스", "database"
        };
        
        for (String keyword : keywords) {
            for (String backendKeyword : backendKeywords) {
                if (keyword.toLowerCase().contains(backendKeyword.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * 웹개발 관련 키워드 확인
     */
    private boolean containsWebKeywords(List<String> keywords) {
        String[] webKeywords = {
            "웹", "web", "웹개발", "web development", "웹사이트", "website", 
            "웹 애플리케이션", "web application"
        };
        
        for (String keyword : keywords) {
            for (String webKeyword : webKeywords) {
                if (keyword.toLowerCase().contains(webKeyword.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * 모바일 관련 키워드 확인
     */
    private boolean containsMobileKeywords(List<String> keywords) {
        String[] mobileKeywords = {
            "모바일", "mobile", "android", "ios", "flutter", "react native", "앱", "app"
        };
        
        for (String keyword : keywords) {
            for (String mobileKeyword : mobileKeywords) {
                if (keyword.toLowerCase().contains(mobileKeyword.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
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
        
        // 쿼리 분석을 통한 필터링 키워드 추출
        List<String> filterKeywords = extractFilterKeywords(query);
        log.info("필터링 키워드: {}", filterKeywords);
        
        // Vector Search 결과 점수 부여 (0.7 가중치)
        for (int i = 0; i < vectorResults.size(); i++) {
            DocumentEntity doc = vectorResults.get(i);
            double score = 0.7 * (1.0 - (double) i / vectorResults.size());
            
            // 키워드 필터링 적용
            if (shouldIncludeDocument(doc, query, filterKeywords)) {
                documentScores.put(doc.getId(), new DocumentWithScore(doc, score));
            }
        }
        
        // Keyword Search 결과 점수 부여 (0.3 가중치)
        for (int i = 0; i < keywordResults.size(); i++) {
            DocumentEntity doc = keywordResults.get(i);
            double score = 0.3 * (1.0 - (double) i / keywordResults.size());
            
            // 키워드 필터링 적용
            if (shouldIncludeDocument(doc, query, filterKeywords)) {
                if (documentScores.containsKey(doc.getId())) {
                    // 이미 있는 문서면 점수 합산
                    DocumentWithScore existing = documentScores.get(doc.getId());
                    documentScores.put(doc.getId(), 
                        new DocumentWithScore(doc, existing.score + score));
                } else {
                    documentScores.put(doc.getId(), new DocumentWithScore(doc, score));
                }
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
     * 점수 정보를 포함한 검색 결과를 반환하는 하이브리드 검색
     */
    public List<DocumentWithScore> hybridSearchWithScores(String query, int topK) {
        try {
            log.info("FAISS 하이브리드 검색 (점수 포함) 시작: {}자 쿼리, 상위 {}개", query.length(), topK);
            
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
            List<DocumentWithScore> combinedResults = combineSearchResultsWithScores(
                vectorResults, textResults, query, topK);
            
            log.info("FAISS 하이브리드 검색 (점수 포함) 완료: {}개 문서 발견", combinedResults.size());
            return combinedResults;
            
        } catch (Exception e) {
            log.error("FAISS 하이브리드 검색 (점수 포함) 중 오류 발생: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 점수 정보를 포함한 검색 결과를 결합하고 점수 정규화
     */
    private List<DocumentWithScore> combineSearchResultsWithScores(
            List<DocumentEntity> vectorResults, 
            List<DocumentEntity> keywordResults, 
            String query, 
            int topK) {
        
        Map<String, DocumentWithScore> documentScores = new HashMap<>();
        
        // 쿼리 분석을 통한 필터링 키워드 추출
        List<String> filterKeywords = extractFilterKeywords(query);
        log.info("필터링 키워드 (점수 포함): {}", filterKeywords);
        
        // Vector Search 결과 점수 부여 (0.7 가중치)
        for (int i = 0; i < vectorResults.size(); i++) {
            DocumentEntity doc = vectorResults.get(i);
            double score = 0.7 * (1.0 - (double) i / vectorResults.size());
            
            // 키워드 필터링 적용
            if (shouldIncludeDocument(doc, query, filterKeywords)) {
                documentScores.put(doc.getId(), new DocumentWithScore(doc, score));
            }
        }
        
        // Keyword Search 결과 점수 부여 (0.3 가중치)
        for (int i = 0; i < keywordResults.size(); i++) {
            DocumentEntity doc = keywordResults.get(i);
            double score = 0.3 * (1.0 - (double) i / keywordResults.size());
            
            // 키워드 필터링 적용
            if (shouldIncludeDocument(doc, query, filterKeywords)) {
                if (documentScores.containsKey(doc.getId())) {
                    // 이미 있는 문서면 점수 합산
                    DocumentWithScore existing = documentScores.get(doc.getId());
                    documentScores.put(doc.getId(), 
                        new DocumentWithScore(doc, existing.score + score));
                } else {
                    documentScores.put(doc.getId(), new DocumentWithScore(doc, score));
                }
            }
        }
        
        // 점수 순으로 정렬하여 상위 K개 반환
        return documentScores.values().stream()
            .sorted((a, b) -> Double.compare(b.score, a.score))
            .limit(topK)
            .collect(Collectors.toList());
    }
    
    
    /**
     * 문서와 점수를 함께 저장하는 내부 클래스
     */
    public static class DocumentWithScore {
        final DocumentEntity document;
        final double score;
        
        DocumentWithScore(DocumentEntity document, double score) {
            this.document = document;
            this.score = score;
        }
        
        public DocumentEntity getDocument() {
            return document;
        }
        
        public double getScore() {
            return score;
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
