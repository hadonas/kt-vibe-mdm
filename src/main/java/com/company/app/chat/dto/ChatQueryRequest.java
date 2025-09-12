package com.company.app.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatQueryRequest {
    @NotBlank(message = "쿼리는 필수입니다")
    private String query;
}
