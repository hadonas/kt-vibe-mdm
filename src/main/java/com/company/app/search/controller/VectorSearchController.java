package com.company.app.search.controller;

import com.company.app.search.service.ElasticsearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@Slf4j
public class VectorSearchController {
    
    private final ElasticsearchIndexService elasticsearchIndexService;
    
    /**
     * 모든 인덱스를 재생성 (카테고리와 청크만)
     */
    @PostMapping("/reindex")
    public ResponseEntity<Map<String, String>> reindexAllDocuments() {
        try {
            log.info("Elasticsearch 재인덱싱 요청 받음");
            elasticsearchIndexService.recreateIndexes();
            
            return ResponseEntity.ok(Map.of(
                "message", "Elasticsearch 재인덱싱이 완료되었습니다.",
                "status", "success"
            ));
            
        } catch (Exception e) {
            log.error("Elasticsearch 재인덱싱 중 오류 발생", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "message", "Elasticsearch 재인덱싱 중 오류가 발생했습니다: " + e.getMessage(),
                "status", "error"
            ));
        }
    }
}
