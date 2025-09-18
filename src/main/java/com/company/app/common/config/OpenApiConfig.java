package com.company.app.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MDM (Monolithic Document Management) API")
                        .description("""
                                모놀리식 문서 관리 시스템 API
                                
                                현재 구현된 기능:
                                - JWT 기반 인증 시스템 (회원가입, 로그인, 비밀번호 재설정, 사용자 정보 조회)
                                - 문서 수집 시스템 (개별 등록, 재등록)
                                - 파일 관리 시스템 (업로드, 다운로드, 분석)
                                - 승인 워크플로우 (승인 요청 조회, 승인/반려 결정, 통계)
                                - 문서 관리 (문서 목록, 계층 구조, 카테고리별 조회, 중복 검사)
                                - 검색 및 채팅 (FAISS 벡터 검색, RAG 채팅)
                                - 공개 API (문서 수 조회, 파일 다운로드, 사용자별 문서 조회)
                                
                                기술 스택:
                                - Backend: Spring Boot 3.2 + Java 17 + MongoDB 7.0
                                - Frontend: Next.js 14 + TypeScript + React 18
                                - Vector Search: FAISS + OpenAI Embeddings (1536차원)
                                - Authentication: JWT + Role-based Access Control
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("MDM Team")
                                .email("mdm@company.com")
                                .url("https://github.com/company/mdm"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080/api")
                                .description("Development server"),
                        new Server()
                                .url("https://api.yourdomain.com/api")
                                .description("Production server")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT 토큰을 사용한 인증. 로그인 후 받은 accessToken을 'Bearer {token}' 형식으로 입력하세요.")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
