package com.company.app.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Source {
    private SourceType type;
    private String repoUrl;
    private List<String> files;
    
    public enum SourceType {
        REPO, PLAN
    }
}
