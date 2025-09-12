package com.company.app.approval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ApprovalDecisionRequest {
    
    @NotBlank(message = "결정은 필수입니다")
    @Pattern(regexp = "APPROVE|REJECT", message = "결정은 APPROVE 또는 REJECT이어야 합니다")
    private String decision;
    
    private String reason;
}
