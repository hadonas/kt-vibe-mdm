package com.company.app.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private String error;
    private String message;
    private LocalDateTime timestamp;
    private String path;
    
    public static ErrorResponse of(String error, String message, String path) {
        return new ErrorResponse(error, message, LocalDateTime.now(), path);
    }
}
