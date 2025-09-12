package com.company.app.auth.controller;

import com.company.app.auth.dto.LoginRequest;
import com.company.app.auth.dto.SignupRequest;
import com.company.app.auth.service.AuthService;
import com.company.app.common.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "인증 관련 API")
public class AuthController {
    
    private final AuthService authService;
    
    @PostMapping("/signup")
    @Operation(summary = "사용자 회원가입")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request) {
        try {
            var response = authService.signup(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorResponse.of("CONFLICT", e.getMessage(), "/auth/signup"));
        }
    }
    
    @PostMapping("/login")
    @Operation(summary = "사용자 로그인")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            var response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ErrorResponse.of("UNAUTHORIZED", e.getMessage(), "/auth/login"));
        }
    }
    
    @PostMapping("/password/forgot")
    @Operation(summary = "비밀번호 재설정 요청")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        try {
            authService.forgotPassword(request.get("email"));
            return ResponseEntity.ok(Map.of("message", "비밀번호 재설정 이메일이 발송되었습니다."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.of("NOT_FOUND", e.getMessage(), "/auth/password/forgot"));
        }
    }
    
    @GetMapping("/me")
    @Operation(summary = "현재 사용자 정보 조회")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        try {
            // authentication.getPrincipal()이 User 객체이므로 직접 사용
            if (authentication.getPrincipal() instanceof com.company.app.auth.entity.User user) {
                var userDto = authService.getCurrentUser(user.getId());
                return ResponseEntity.ok(userDto);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ErrorResponse.of("UNAUTHORIZED", "인증되지 않은 사용자입니다.", "/auth/me"));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.of("NOT_FOUND", e.getMessage(), "/auth/me"));
        }
    }
}
