package com.company.app.catalog.entity;

import com.company.app.common.dto.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "catalog_nodes")
public class CatalogNode extends BaseEntity {
    
    @Id
    private String id;
    
    private Integer level; // 계층 레벨 (1부터 시작, 무제한)
    
    @Indexed(unique = true)
    private String code;
    
    @TextIndexed
    private String name;
    
    @Indexed
    private String parentCode;
    
    private Boolean active;
    private Integer order;
    
    // 새로운 분류 개선 필드들
    @TextIndexed
    private String description; // 분류 설명 (사람이 관리)
    
    @TextIndexed
    private List<String> aliases; // 동의어/유사 용어
    
    private List<String> includeKeywords; // 반드시 포함되면 가산점
    private List<String> excludeKeywords; // 포함되면 감산/차단
    
    @TextIndexed
    private List<String> examplePhrases; // 예시 문장 (임베딩 seed)
    
    private List<Double> vector; // 카테고리 임베딩 벡터 (1536차원)
    
    private ScoreWeights scoreWeights; // 가중치 미세조정
    
    private LocalDateTime lastVectorUpdate; // 벡터 마지막 업데이트 시간
    
    private List<CatalogNode> children; // 하위 카테고리들
    
    public boolean isMajor() {
        return level == 1;
    }
    
    public boolean isMid() {
        return level == 2;
    }
    
    public boolean isSub() {
        return level == 3;
    }
    
    /**
     * 레벨이 지정된 값인지 확인
     */
    public boolean isLevel(int targetLevel) {
        return level != null && level.equals(targetLevel);
    }
    
    /**
     * 리프 노드인지 확인 (하위 카테고리가 없는 최종 분류)
     */
    public boolean isLeaf() {
        return children == null || children.isEmpty();
    }
    
    /**
     * 가중치 점수 계산 클래스
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreWeights {
        private Double keyword = 1.0;    // 키워드 매칭 가중치
        private Double bm25 = 1.0;       // BM25 점수 가중치
        private Double vector = 1.0;     // 벡터 유사도 가중치
        private Double xencoder = 1.0;   // Cross-encoder 점수 가중치
    }
}
