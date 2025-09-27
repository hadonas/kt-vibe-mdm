package com.company.app.search.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * 벡터 임베딩을 위한 해시 기반 샤딩 유틸리티
 */
@Component
@Slf4j
public class VectorShardingUtil {
    
    @Value("${vector.shard.count:4}")
    private int shardCount;
    
    private static final String HASH_ALGORITHM = "MD5";
    
    /**
     * 벡터를 기반으로 샤드 ID를 계산합니다.
     * 
     * @param vector 벡터 임베딩
     * @param shardCount 샤드 개수
     * @return 샤드 ID (0 ~ shardCount-1)
     */
    public int calculateShardId(List<Double> vector, int shardCount) {
        if (vector == null || vector.isEmpty()) {
            return 0;
        }
        
        try {
            // 벡터의 첫 10개 값을 문자열로 변환하여 해시 생성
            StringBuilder vectorString = new StringBuilder();
            int maxElements = Math.min(10, vector.size());
            for (int i = 0; i < maxElements; i++) {
                vectorString.append(String.format("%.6f", vector.get(i)));
            }
            
            // MD5 해시 생성
            MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = md.digest(vectorString.toString().getBytes(StandardCharsets.UTF_8));
            
            // 해시를 정수로 변환하여 샤드 ID 계산
            int hashValue = Math.abs(java.nio.ByteBuffer.wrap(hashBytes).getInt());
            int shardId = hashValue % shardCount;
            
            log.debug("벡터 샤딩: {}차원 벡터 -> 샤드 ID: {}", vector.size(), shardId);
            return shardId;
            
        } catch (NoSuchAlgorithmException e) {
            log.error("해시 알고리즘 오류: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * 기본 샤드 개수로 샤드 ID를 계산합니다.
     * 
     * @param vector 벡터 임베딩
     * @return 샤드 ID
     */
    public int calculateShardId(List<Double> vector) {
        return calculateShardId(vector, shardCount);
    }
    
    /**
     * 텍스트를 기반으로 샤드 ID를 계산합니다.
     * 
     * @param text 텍스트
     * @param shardCount 샤드 개수
     * @return 샤드 ID
     */
    public int calculateShardIdFromText(String text, int shardCount) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        
        try {
            // 텍스트의 해시 생성
            MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = md.digest(text.getBytes(StandardCharsets.UTF_8));
            
            // 해시를 정수로 변환하여 샤드 ID 계산
            int hashValue = Math.abs(java.nio.ByteBuffer.wrap(hashBytes).getInt());
            int shardId = hashValue % shardCount;
            
            log.debug("텍스트 샤딩: '{}' -> 샤드 ID: {}", text.substring(0, Math.min(50, text.length())), shardId);
            return shardId;
            
        } catch (NoSuchAlgorithmException e) {
            log.error("해시 알고리즘 오류: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * 기본 샤드 개수로 텍스트 기반 샤드 ID를 계산합니다.
     * 
     * @param text 텍스트
     * @return 샤드 ID
     */
    public int calculateShardIdFromText(String text) {
        return calculateShardIdFromText(text, shardCount);
    }
    
    /**
     * 샤드 ID를 컬렉션 이름으로 변환합니다.
     * 
     * @param shardId 샤드 ID
     * @return 컬렉션 이름
     */
    public String getShardCollectionName(int shardId) {
        return "documents_shard_" + shardId;
    }
    
    /**
     * 기본 샤드 개수를 반환합니다.
     * 
     * @return 샤드 개수
     */
    public int getDefaultShardCount() {
        return shardCount;
    }
}
