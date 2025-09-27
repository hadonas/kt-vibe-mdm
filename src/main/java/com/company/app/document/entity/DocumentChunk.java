package com.company.app.document.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 문서 청크 (의미 기반 분할 결과)
 */
@Data
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "document_chunks")
public class DocumentChunk {
    @Id
    private String id;

    @Indexed
    private String documentId;          // 원본 문서 ID
    private Integer chunkIndex;         // 순서
    private String content;             // 청크 텍스트
    private Integer tokenCount;         // 토큰 수 (추정치)
    private List<Double> embedding;     // 1536 차원 벡터
    private Double score;               // 중요도 점수 (선택)
    private String sectionHint;         // 섹션/제목 힌트
    private LocalDateTime createdAt;
}
