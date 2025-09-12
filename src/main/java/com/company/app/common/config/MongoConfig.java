package com.company.app.common.config;

import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
public class MongoConfig {

    @PostConstruct
    public void createIndexes() {
        // 인덱스 생성은 애플리케이션 시작 후 별도로 처리
        // Spring Boot의 자동 설정이 application.yml의 spring.data.mongodb.uri를 사용하도록 둡니다
    }
}