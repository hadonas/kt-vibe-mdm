package com.company.app.chat.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChatQueryResponse {
    private String answer;
    private List<Source> sources;
    
    @Data
    @Builder
    public static class Source {
        private String docId;
        private String serial;
        private String snippet;
        private double score;
    }
}
