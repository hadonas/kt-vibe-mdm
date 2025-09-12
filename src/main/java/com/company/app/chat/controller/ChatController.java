package com.company.app.chat.controller;

import com.company.app.chat.dto.ChatQueryRequest;
import com.company.app.chat.dto.ChatQueryResponse;
import com.company.app.chat.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "RAG 채팅 관련 API")
public class ChatController {
    
    private final ChatService chatService;
    
    @PostMapping("/query")
    @Operation(summary = "RAG 채팅 쿼리")
    public ResponseEntity<ChatQueryResponse> query(@Valid @RequestBody ChatQueryRequest request,
                                                  Authentication authentication) {
        System.out.println("=== ChatController.query 호출됨 ===");
        System.out.println("요청: " + request.getQuery());
        
        String userId;
        if (authentication.getPrincipal() instanceof com.company.app.auth.entity.User user) {
            userId = user.getId();
            System.out.println("사용자 ID: " + userId);
        } else {
            System.out.println("인증 실패");
            return ResponseEntity.status(401).build();
        }
        
        try {
            System.out.println("ChatService.processQuery 호출 시작");
            ChatQueryResponse response = chatService.processQuery(userId, request);
            System.out.println("ChatService.processQuery 호출 완료");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.out.println("ChatController에서 예외 발생: " + e.getMessage());
            e.printStackTrace();
            // 에러 발생 시 기본 응답 반환
            ChatQueryResponse errorResponse = ChatQueryResponse.builder()
                    .answer("죄송합니다. 현재 시스템에 문제가 발생했습니다. 잠시 후 다시 시도해주세요.")
                    .sources(java.util.Collections.emptyList())
                    .build();
            return ResponseEntity.ok(errorResponse);
        }
    }
}
