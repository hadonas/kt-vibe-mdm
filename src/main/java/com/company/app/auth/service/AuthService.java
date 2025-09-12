package com.company.app.auth.service;

import com.company.app.auth.dto.AuthResponse;
import com.company.app.auth.dto.LoginRequest;
import com.company.app.auth.dto.SignupRequest;
import com.company.app.auth.entity.User;
import com.company.app.auth.repository.UserRepository;
import com.company.app.common.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    
    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }
        
        User user = new User();
        user.setEmail(request.getEmail());
        user.setName(request.getName());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRoles(List.of(User.Role.USER));
        user.setLastLoginAt(LocalDateTime.now());
        
        User savedUser = userRepository.save(user);
        
        String accessToken = jwtUtil.generateToken(savedUser);
        String refreshToken = jwtUtil.generateToken(savedUser); // TODO: 별도 refresh token 로직 구현
        
        return new AuthResponse(accessToken, refreshToken, convertToUserDto(savedUser));
    }
    
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));
        
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        
        String accessToken = jwtUtil.generateToken(user);
        String refreshToken = jwtUtil.generateToken(user); // TODO: 별도 refresh token 로직 구현
        
        return new AuthResponse(accessToken, refreshToken, convertToUserDto(user));
    }
    
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이메일입니다."));
        
        // TODO: 이메일 발송 로직 구현
        // 임시로 로그만 출력
        System.out.println("Password reset email sent to: " + email);
    }
    
    public AuthResponse.UserDto getCurrentUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        return convertToUserDto(user);
    }
    
    private AuthResponse.UserDto convertToUserDto(User user) {
        return new AuthResponse.UserDto(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRoles(),
                user.getCreatedAt(),
                user.getLastLoginAt()
        );
    }
}
