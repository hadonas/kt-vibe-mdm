package com.company.app.ingest.entity;

import com.company.app.common.dto.ACL;
import com.company.app.common.dto.BaseEntity;
import com.company.app.common.dto.Category;
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
@Document(collection = "ingest_requests")
public class IngestRequest extends BaseEntity {
    
    @Id
    private String id;
    
    private String ownerId;
    private Source source;
    private String extractedText;
    private Category proposedCategory;
    private String proposedPurpose;
    private List<String> tags;
    private List<SimilarCandidate> similarCandidates;
    private Status status;
    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
    private String approverId;
    private String approvalReason;
    private LocalDateTime processedAt;
    private List<Version> versions;
    private ACL acl;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimilarCandidate {
        private String docId;
        private Double score;
        private String serial;
        private String purpose;
        private String snippet;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Version {
        private Integer version;
        private String purpose;
        private List<String> tags;
        private String reason;
        private LocalDateTime createdAt;
        private String createdBy;
    }
    
    public enum Status {
        PENDING, APPROVED, REJECTED, COMPLETED, FAILED
    }
}
