package com.company.app.ingest.dto;

import com.company.app.common.dto.Category;
import lombok.Data;

import java.util.List;

@Data
public class SingleIngestRequest {
    private String repoUrl;
    private String accessToken;
    private List<String> fileIds;
    private String purpose;
    private List<String> tags;
    private Boolean submit; // true면 실제 등록, false/null이면 미리보기만
    private Category proposedCategory;
    private String proposedTitle;
    private String originalFileName; // 원본 파일명
}
