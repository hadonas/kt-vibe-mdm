package com.company.app.search.controller;

import com.company.app.search.service.FaissVectorSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@Slf4j
public class VectorSearchController {
    
    private final FaissVectorSearchService vectorSearchService;
    
    /**
     * 모든 문서를 재인덱싱
     */
    @PostMapping("/reindex")
    public ResponseEntity<Map<String, String>> reindexAllDocuments() {
        try {
            log.info("문서 재인덱싱 요청 받음");
            vectorSearchService.reindexAllDocuments();
            
            return ResponseEntity.ok(Map.of(
                "message", "문서 재인덱싱이 완료되었습니다.",
                "status", "success"
            ));
            
        } catch (Exception e) {
            log.error("문서 재인덱싱 중 오류 발생", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "message", "문서 재인덱싱 중 오류가 발생했습니다: " + e.getMessage(),
                "status", "error"
            ));
        }
    }
}
