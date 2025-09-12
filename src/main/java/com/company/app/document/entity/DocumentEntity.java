package com.company.app.document.entity;

import com.company.app.common.dto.ACL;
import com.company.app.common.dto.BaseEntity;
import com.company.app.common.dto.Category;
import com.company.app.common.dto.Serial;
import com.company.app.common.dto.Source;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "documents")
public class DocumentEntity extends BaseEntity {
    
    @Id
    private String id;
    
    private String ownerId;
    private Serial serial;
    private Category category;
    private String purpose;
    private String content;
    private Source source;
    private String originalFileName; // 원본 파일명 저장
    private List<String> tags;
    private VectorEmbeddings vectors;
    private Integer shardId;  // 해시 기반 샤딩을 위한 샤드 ID
    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
    private Integer version;
    private ACL acl;
    private List<TransferRecord> transferHistory;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VectorEmbeddings {
        private List<Double> purpose_768;
        private List<Double> content_1024;
        private List<Double> purpose_1536;  // 1536차원 벡터 추가
        private List<Double> content_1536;  // 1536차원 벡터 추가
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransferRecord {
        private String fromUserId;
        private String toUserId;
        private LocalDateTime transferredAt;
        private String reason;
    }
}
