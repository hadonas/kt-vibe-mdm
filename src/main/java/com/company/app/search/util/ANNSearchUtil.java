package com.company.app.search.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ANN (Approximate Nearest Neighbor) 검색을 위한 유틸리티
 */
@Component
@Slf4j
public class ANNSearchUtil {
    
    private static final int DEFAULT_CANDIDATES = 100;
    private static final int DEFAULT_K = 10;
    
    /**
     * 벡터 간 코사인 유사도를 계산합니다.
     * 
     * @param vector1 첫 번째 벡터
     * @param vector2 두 번째 벡터
     * @return 코사인 유사도 (-1 ~ 1)
     */
    public double cosineSimilarity(List<Double> vector1, List<Double> vector2) {
        if (vector1 == null || vector2 == null || 
            vector1.size() != vector2.size() || 
            vector1.isEmpty()) {
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
    
    /**
     * 벡터 간 유클리드 거리를 계산합니다.
     * 
     * @param vector1 첫 번째 벡터
     * @param vector2 두 번째 벡터
     * @return 유클리드 거리
     */
    public double euclideanDistance(List<Double> vector1, List<Double> vector2) {
        if (vector1 == null || vector2 == null || 
            vector1.size() != vector2.size() || 
            vector1.isEmpty()) {
            return Double.MAX_VALUE;
        }
        
        double sum = 0.0;
        for (int i = 0; i < vector1.size(); i++) {
            double diff = vector1.get(i) - vector2.get(i);
            sum += diff * diff;
        }
        
        return Math.sqrt(sum);
    }
    
    /**
     * LSH (Locality Sensitive Hashing) 기반 후보 검색
     * 
     * @param queryVector 쿼리 벡터
     * @param candidateVectors 후보 벡터들
     * @param numCandidates 반환할 후보 수
     * @return 후보 인덱스들
     */
    public List<Integer> lshCandidates(List<Double> queryVector, 
                                     List<List<Double>> candidateVectors, 
                                     int numCandidates) {
        if (queryVector == null || candidateVectors == null || candidateVectors.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 간단한 LSH 구현: 벡터의 첫 8개 비트를 해시로 사용
        int queryHash = calculateVectorHash(queryVector);
        
        List<Map.Entry<Integer, Integer>> hashDistances = new ArrayList<>();
        
        for (int i = 0; i < candidateVectors.size(); i++) {
            List<Double> candidate = candidateVectors.get(i);
            if (candidate != null && candidate.size() == queryVector.size()) {
                int candidateHash = calculateVectorHash(candidate);
                int hashDistance = Integer.bitCount(queryHash ^ candidateHash);
                hashDistances.add(new AbstractMap.SimpleEntry<>(i, hashDistance));
            }
        }
        
        // 해시 거리 순으로 정렬하여 상위 후보들 반환
        return hashDistances.stream()
            .sorted(Map.Entry.comparingByValue())
            .limit(numCandidates)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    /**
     * 벡터의 해시 값을 계산합니다 (LSH용)
     * 
     * @param vector 벡터
     * @return 해시 값
     */
    private int calculateVectorHash(List<Double> vector) {
        int hash = 0;
        int bitsPerDimension = 1; // 각 차원당 1비트 사용
        
        for (int i = 0; i < Math.min(32, vector.size()); i++) {
            if (vector.get(i) > 0) {
                hash |= (1 << (i % 32));
            }
        }
        
        return hash;
    }
    
    /**
     * ANN 검색을 수행합니다.
     * 
     * @param queryVector 쿼리 벡터
     * @param candidateVectors 후보 벡터들
     * @param k 반환할 최근접 이웃 수
     * @param similarityThreshold 유사도 임계값
     * @return (인덱스, 유사도) 쌍의 리스트
     */
    public List<Map.Entry<Integer, Double>> annSearch(List<Double> queryVector,
                                                    List<List<Double>> candidateVectors,
                                                    int k,
                                                    double similarityThreshold) {
        if (queryVector == null || candidateVectors == null || candidateVectors.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 1. LSH로 후보들을 먼저 필터링
        List<Integer> lshCandidates = lshCandidates(queryVector, candidateVectors, DEFAULT_CANDIDATES);
        
        // 2. 후보들에 대해 정확한 유사도 계산
        List<Map.Entry<Integer, Double>> similarities = new ArrayList<>();
        
        for (int candidateIndex : lshCandidates) {
            if (candidateIndex < candidateVectors.size()) {
                List<Double> candidate = candidateVectors.get(candidateIndex);
                double similarity = cosineSimilarity(queryVector, candidate);
                
                if (similarity >= similarityThreshold) {
                    similarities.add(new AbstractMap.SimpleEntry<>(candidateIndex, similarity));
                }
            }
        }
        
        // 3. 유사도 순으로 정렬하여 상위 k개 반환
        return similarities.stream()
            .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
            .limit(k)
            .collect(Collectors.toList());
    }
    
    /**
     * 기본 파라미터로 ANN 검색을 수행합니다.
     * 
     * @param queryVector 쿼리 벡터
     * @param candidateVectors 후보 벡터들
     * @return (인덱스, 유사도) 쌍의 리스트
     */
    public List<Map.Entry<Integer, Double>> annSearch(List<Double> queryVector,
                                                    List<List<Double>> candidateVectors) {
        return annSearch(queryVector, candidateVectors, DEFAULT_K, 0.0);
    }
    
    /**
     * 벡터를 정규화합니다 (단위 벡터로 변환)
     * 
     * @param vector 벡터
     * @return 정규화된 벡터
     */
    public List<Double> normalizeVector(List<Double> vector) {
        if (vector == null || vector.isEmpty()) {
            return new ArrayList<>();
        }
        
        double norm = Math.sqrt(vector.stream().mapToDouble(v -> v * v).sum());
        
        if (norm == 0.0) {
            return new ArrayList<>(vector);
        }
        
        return vector.stream()
            .map(v -> v / norm)
            .collect(Collectors.toList());
    }
    
    /**
     * 벡터의 차원을 확인합니다.
     * 
     * @param vector 벡터
     * @return 차원 수
     */
    public int getVectorDimension(List<Double> vector) {
        return vector != null ? vector.size() : 0;
    }
    
    /**
     * 벡터가 유효한지 확인합니다.
     * 
     * @param vector 벡터
     * @return 유효성 여부
     */
    public boolean isValidVector(List<Double> vector) {
        return vector != null && !vector.isEmpty() && 
               vector.stream().allMatch(v -> !Double.isNaN(v) && !Double.isInfinite(v));
    }
}
