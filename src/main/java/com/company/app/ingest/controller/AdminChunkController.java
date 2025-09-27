package com.company.app.ingest.controller;

import com.company.app.ingest.service.ChunkMigrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
// Base path intentionally excludes global context-path (/api) to avoid duplication
@RequestMapping("/admin/chunks")
@RequiredArgsConstructor
@Slf4j
public class AdminChunkController {

    private final ChunkMigrationService migrationService;

    @PostMapping("/migrate")
    public ResponseEntity<?> migrate() {
        try {
            migrationService.runMigration(); // 수동 마이그레이션 실행
            return ResponseEntity.ok(java.util.Map.of(
                    "success", true,
                    "message", "기존 문서 청킹 마이그레이션 실행 완료"));
        } catch (Exception e) {
            log.error("마이그레이션 실행 실패", e);
            return ResponseEntity.internalServerError().body(java.util.Map.of(
                    "success", false,
                    "message", e.getMessage()));
        }
    }
}
