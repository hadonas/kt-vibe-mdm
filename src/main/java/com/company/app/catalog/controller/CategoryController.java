package com.company.app.catalog.controller;

import com.company.app.catalog.entity.CatalogNode;
import com.company.app.catalog.repository.CatalogNodeRepository;
import com.company.app.catalog.service.CategoryEmbeddingService;
import com.company.app.catalog.service.CategoryMetadataGenerationService;
import com.company.app.catalog.service.DocumentReclassificationService;
import com.company.app.catalog.service.HybridCategorySearchService;
import com.company.app.catalog.service.SmartClassificationService;
import com.company.app.document.repository.DocumentRepository;
import com.company.app.document.entity.DocumentEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Category Management", description = "스마트 카테고리 관리 API")
public class CategoryController {
    
    private final CatalogNodeRepository catalogNodeRepository;
    private final CategoryEmbeddingService embeddingService;
    private final CategoryMetadataGenerationService metadataGenerationService;
    private final DocumentReclassificationService reclassificationService;
    private final HybridCategorySearchService hybridSearchService;
    private final SmartClassificationService classificationService;
    private final DocumentRepository documentRepository;
    
    @Operation(summary = "카테고리 생성", description = "새로운 카테고리를 생성하고 임베딩을 생성합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "카테고리 생성 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "409", description = "이미 존재하는 카테고리 코드"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping
    public ResponseEntity<Map<String, Object>> createCategory(@RequestBody CategoryCreateRequest request) {
        try {
            log.info("카테고리 생성 요청: {}", request.getCode());
            
            // 중복 체크
            if (catalogNodeRepository.findByCode(request.getCode()).isPresent()) {
                return ResponseEntity.status(409)
                    .body(Map.of("error", "이미 존재하는 카테고리 코드입니다: " + request.getCode()));
            }
            
            // 카테고리 생성
            CatalogNode category = new CatalogNode();
            category.setLevel(request.getLevel());
            category.setCode(request.getCode());
            category.setName(request.getName());
            category.setParentCode(request.getParentCode());
            category.setDescription(request.getDescription());
            category.setAliases(request.getAliases());
            category.setIncludeKeywords(request.getIncludeKeywords());
            category.setExcludeKeywords(request.getExcludeKeywords());
            category.setExamplePhrases(request.getExamplePhrases());
            category.setActive(true);
            category.setOrder(request.getOrder() != null ? request.getOrder() : 0);
            
            // 기본 가중치 설정
            if (request.getScoreWeights() != null) {
                category.setScoreWeights(request.getScoreWeights());
            } else {
                category.setScoreWeights(new CatalogNode.ScoreWeights());
            }
            
            // 빈 필드 자동 생성 (LLM 사용)
            if (Boolean.TRUE.equals(request.getAutoGenerate())) {
                try {
                    log.info("빈 필드 자동 생성 시작: {}", category.getName());
                    metadataGenerationService.fillEmptyFields(category);
                    log.info("빈 필드 자동 생성 완료: {}", category.getName());
                } catch (Exception e) {
                    log.warn("빈 필드 자동 생성 실패, 계속 진행: {}", category.getName(), e);
                }
            }
            
            // 저장
            CatalogNode savedCategory = catalogNodeRepository.save(category);
            
            // 임베딩 생성
            try {
                embeddingService.updateCategoryEmbedding(savedCategory.getCode());
                log.info("카테고리 임베딩 생성 완료: {}", savedCategory.getCode());
            } catch (Exception e) {
                log.warn("카테고리 임베딩 생성 실패: {}", savedCategory.getCode(), e);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", savedCategory.getId());
            response.put("code", savedCategory.getCode());
            response.put("name", savedCategory.getName());
            response.put("message", "카테고리가 성공적으로 생성되었습니다.");
            
            return ResponseEntity.status(201).body(response);
            
        } catch (Exception e) {
            log.error("카테고리 생성 중 오류", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "카테고리 생성 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    @Operation(summary = "카테고리 수정", description = "카테고리 정보를 수정하고 임베딩을 재생성합니다.")
    @PutMapping("/{code}")
    public ResponseEntity<Map<String, Object>> updateCategory(
            @Parameter(description = "카테고리 코드", required = true)
            @PathVariable String code,
            @RequestBody CategoryUpdateRequest request) {
        
        try {
            log.info("카테고리 수정 요청: {}", code);
            
            Optional<CatalogNode> categoryOpt = catalogNodeRepository.findByCode(code);
            if (categoryOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            CatalogNode category = categoryOpt.get();
            
            // 필드 업데이트
            if (request.getName() != null) category.setName(request.getName());
            if (request.getDescription() != null) category.setDescription(request.getDescription());
            if (request.getAliases() != null) category.setAliases(request.getAliases());
            if (request.getIncludeKeywords() != null) category.setIncludeKeywords(request.getIncludeKeywords());
            if (request.getExcludeKeywords() != null) category.setExcludeKeywords(request.getExcludeKeywords());
            if (request.getExamplePhrases() != null) category.setExamplePhrases(request.getExamplePhrases());
            if (request.getScoreWeights() != null) category.setScoreWeights(request.getScoreWeights());
            if (request.getActive() != null) category.setActive(request.getActive());
            if (request.getOrder() != null) category.setOrder(request.getOrder());
            
            // 저장
            CatalogNode savedCategory = catalogNodeRepository.save(category);
            
            // 임베딩 재생성
            try {
                embeddingService.updateCategoryEmbedding(savedCategory.getCode());
                log.info("카테고리 임베딩 재생성 완료: {}", savedCategory.getCode());
            } catch (Exception e) {
                log.warn("카테고리 임베딩 재생성 실패: {}", savedCategory.getCode(), e);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("code", savedCategory.getCode());
            response.put("name", savedCategory.getName());
            response.put("message", "카테고리가 성공적으로 수정되었습니다.");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("카테고리 수정 중 오류", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "카테고리 수정 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    @Operation(summary = "카테고리 검색", description = "하이브리드 검색으로 카테고리를 찾습니다.")
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchCategories(
            @Parameter(description = "검색 텍스트", required = true)
            @RequestParam String query,
            @Parameter(description = "결과 수", example = "10")
            @RequestParam(defaultValue = "10") int limit) {
        
        try {
            log.info("카테고리 검색 요청: query={}, limit={}", query, limit);
            
            List<HybridCategorySearchService.CategorySearchResult> results = 
                hybridSearchService.searchCategories(query, limit);
            
            Map<String, Object> response = new HashMap<>();
            response.put("query", query);
            response.put("totalResults", results.size());
            response.put("categories", results);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("카테고리 검색 중 오류", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "검색 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    @Operation(summary = "문서 분류", description = "문서 요약을 기반으로 스마트 분류를 수행합니다.")
    @PostMapping("/classify")
    public ResponseEntity<Map<String, Object>> classifyDocument(@RequestBody ClassificationRequest request) {
        try {
            log.info("문서 분류 요청: 제목={}", request.getTitle());
            
            SmartClassificationService.ClassificationResult result = 
                classificationService.classifyDocument(request.getSummary(), request.getTitle());
            
            Map<String, Object> response = new HashMap<>();
            response.put("successful", result.isSuccessful());
            response.put("selectedCategory", result.getSelectedCategory());
            response.put("confidence", result.getConfidence());
            response.put("reason", result.getReason());
            response.put("candidates", result.getCandidates());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("문서 분류 중 오류", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "분류 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    @Operation(summary = "모든 카테고리 임베딩 재생성", description = "모든 카테고리의 임베딩을 재생성합니다.")
    @PostMapping("/embeddings/regenerate")
    public ResponseEntity<Map<String, Object>> regenerateAllEmbeddings() {
        try {
            log.info("모든 카테고리 임베딩 재생성 요청");
            
            embeddingService.updateAllCategoryEmbeddings();
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "모든 카테고리 임베딩이 재생성되었습니다.");
            response.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("카테고리 임베딩 재생성 중 오류", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "임베딩 재생성 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    @Operation(summary = "카테고리 임베딩 재생성", description = "특정 카테고리의 임베딩을 재생성합니다.")
    @PostMapping("/{code}/embedding")
    public ResponseEntity<Map<String, Object>> regenerateCategoryEmbedding(
            @Parameter(description = "카테고리 코드", required = true)
            @PathVariable String code) {
        
        try {
            log.info("카테고리 임베딩 재생성 요청: {}", code);
            
            CatalogNode updatedCategory = embeddingService.updateCategoryEmbedding(code);
            
            Map<String, Object> response = new HashMap<>();
            response.put("code", updatedCategory.getCode());
            response.put("name", updatedCategory.getName());
            response.put("lastVectorUpdate", updatedCategory.getLastVectorUpdate());
            response.put("message", "카테고리 임베딩이 재생성되었습니다.");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("카테고리 임베딩 재생성 중 오류", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "임베딩 재생성 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    @Operation(summary = "카테고리 목록 조회", description = "활성 카테고리 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<Map<String, Object>> getCategories(
            @Parameter(description = "레벨 필터")
            @RequestParam(required = false) Integer level,
            @Parameter(description = "부모 코드 필터")
            @RequestParam(required = false) String parentCode) {
        
        try {
            List<CatalogNode> categories;
            
            if (level != null && parentCode != null) {
                categories = catalogNodeRepository.findByLevelAndParentCodeAndActiveTrue(level, parentCode);
            } else if (level != null) {
                categories = catalogNodeRepository.findByLevelAndActiveTrue(level);
            } else if (parentCode != null) {
                categories = catalogNodeRepository.findByParentCodeAndActiveTrue(parentCode);
            } else {
                categories = catalogNodeRepository.findByActiveTrue();
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("categories", categories);
            response.put("totalCount", categories.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("카테고리 목록 조회 중 오류", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    @Operation(summary = "카테고리 상세 조회", description = "특정 카테고리의 상세 정보를 조회합니다.")
    @GetMapping("/{code}")
    public ResponseEntity<Map<String, Object>> getCategory(
            @Parameter(description = "카테고리 코드", required = true)
            @PathVariable String code) {
        
        try {
            Optional<CatalogNode> categoryOpt = catalogNodeRepository.findByCode(code);
            if (categoryOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            CatalogNode category = categoryOpt.get();
            
            Map<String, Object> response = new HashMap<>();
            response.put("category", category);
            response.put("hasEmbedding", category.getVector() != null && !category.getVector().isEmpty());
            response.put("embeddingDimensions", category.getVector() != null ? category.getVector().size() : 0);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("카테고리 조회 중 오류", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    @Operation(summary = "카테고리 메타데이터 생성 미리보기", description = "카테고리명을 기반으로 LLM이 생성할 메타데이터를 미리 확인합니다.")
    @PostMapping("/metadata/preview")
    public ResponseEntity<Map<String, Object>> previewCategoryMetadata(@RequestBody MetadataPreviewRequest request) {
        try {
            log.info("카테고리 메타데이터 미리보기 요청: {}", request.getName());
            
            CategoryMetadataGenerationService.CategoryMetadata metadata = 
                metadataGenerationService.generateCategoryMetadata(
                    request.getName(), 
                    request.getCode(), 
                    request.getParentCode(), 
                    request.getLevel()
                );
            
            Map<String, Object> response = new HashMap<>();
            response.put("categoryName", request.getName());
            response.put("categoryCode", request.getCode());
            response.put("generatedMetadata", metadata);
            response.put("message", "LLM이 생성한 메타데이터 미리보기입니다.");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("카테고리 메타데이터 미리보기 중 오류", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "메타데이터 생성 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    @Operation(summary = "기존 카테고리 빈 필드 자동 생성", description = "기존 카테고리의 빈 필드들을 LLM으로 자동 생성합니다.")
    @PostMapping("/{code}/auto-fill")
    public ResponseEntity<Map<String, Object>> autoFillCategoryFields(
            @Parameter(description = "카테고리 코드", required = true)
            @PathVariable String code) {
        
        try {
            log.info("카테고리 빈 필드 자동 생성 요청: {}", code);
            
            Optional<CatalogNode> categoryOpt = catalogNodeRepository.findByCode(code);
            if (categoryOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            CatalogNode category = categoryOpt.get();
            
            // 빈 필드 자동 생성
            metadataGenerationService.fillEmptyFields(category);
            
            // 저장
            CatalogNode savedCategory = catalogNodeRepository.save(category);
            
            // 임베딩 재생성
            try {
                embeddingService.updateCategoryEmbedding(savedCategory.getCode());
            } catch (Exception e) {
                log.warn("임베딩 재생성 실패: {}", savedCategory.getCode(), e);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("code", savedCategory.getCode());
            response.put("name", savedCategory.getName());
            response.put("updatedCategory", savedCategory);
            response.put("message", "빈 필드가 자동으로 생성되었습니다.");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("카테고리 빈 필드 자동 생성 중 오류", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "빈 필드 생성 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    @Operation(summary = "모든 카테고리 빈 필드 일괄 자동 생성", description = "모든 카테고리의 빈 필드들을 LLM으로 일괄 자동 생성합니다.")
    @PostMapping("/auto-fill-all")
    public ResponseEntity<Map<String, Object>> autoFillAllCategoryFields() {
        try {
            log.info("모든 카테고리 빈 필드 일괄 자동 생성 요청");
            
            List<CatalogNode> allCategories = catalogNodeRepository.findByActiveTrue();
            
            int processedCount = 0;
            int updatedCount = 0;
            int failureCount = 0;
            
            for (CatalogNode category : allCategories) {
                try {
                    processedCount++;
                    
                    // 빈 필드가 있는지 확인
                    boolean hadEmptyFields = hasEmptyFields(category);
                    
                    if (hadEmptyFields) {
                        // 빈 필드 자동 생성
                        metadataGenerationService.fillEmptyFields(category);
                        catalogNodeRepository.save(category);
                        updatedCount++;
                        
                        // 임베딩 재생성
                        try {
                            embeddingService.updateCategoryEmbedding(category.getCode());
                        } catch (Exception e) {
                            log.warn("임베딩 재생성 실패: {}", category.getCode(), e);
                        }
                        
                        log.info("카테고리 업데이트 완료: {} ({})", category.getCode(), category.getName());
                    }
                    
                } catch (Exception e) {
                    failureCount++;
                    log.error("카테고리 처리 실패: {} ({})", category.getCode(), category.getName(), e);
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("totalCategories", allCategories.size());
            response.put("processedCount", processedCount);
            response.put("updatedCount", updatedCount);
            response.put("failureCount", failureCount);
            response.put("message", String.format("일괄 처리 완료: %d개 처리, %d개 업데이트, %d개 실패", 
                processedCount, updatedCount, failureCount));
            response.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("모든 카테고리 빈 필드 일괄 생성 중 오류", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "일괄 처리 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    /**
     * 빈 필드가 있는지 확인
     */
    private boolean hasEmptyFields(CatalogNode category) {
        return (category.getDescription() == null || category.getDescription().trim().isEmpty()) ||
               (category.getAliases() == null || category.getAliases().isEmpty()) ||
               (category.getIncludeKeywords() == null || category.getIncludeKeywords().isEmpty()) ||
               (category.getExamplePhrases() == null || category.getExamplePhrases().isEmpty());
    }
    
    // DTO 클래스들
    
    public static class CategoryCreateRequest {
        private Integer level;
        private String code;
        private String name;
        private String parentCode;
        private String description;
        private List<String> aliases;
        private List<String> includeKeywords;
        private List<String> excludeKeywords;
        private List<String> examplePhrases;
        private CatalogNode.ScoreWeights scoreWeights;
        private Integer order;
        private Boolean autoGenerate; // 빈 필드 자동 생성 여부
        
        // Getters and Setters
        public Integer getLevel() { return level; }
        public void setLevel(Integer level) { this.level = level; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getParentCode() { return parentCode; }
        public void setParentCode(String parentCode) { this.parentCode = parentCode; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<String> getAliases() { return aliases; }
        public void setAliases(List<String> aliases) { this.aliases = aliases; }
        public List<String> getIncludeKeywords() { return includeKeywords; }
        public void setIncludeKeywords(List<String> includeKeywords) { this.includeKeywords = includeKeywords; }
        public List<String> getExcludeKeywords() { return excludeKeywords; }
        public void setExcludeKeywords(List<String> excludeKeywords) { this.excludeKeywords = excludeKeywords; }
        public List<String> getExamplePhrases() { return examplePhrases; }
        public void setExamplePhrases(List<String> examplePhrases) { this.examplePhrases = examplePhrases; }
        public CatalogNode.ScoreWeights getScoreWeights() { return scoreWeights; }
        public void setScoreWeights(CatalogNode.ScoreWeights scoreWeights) { this.scoreWeights = scoreWeights; }
        public Integer getOrder() { return order; }
        public void setOrder(Integer order) { this.order = order; }
        public Boolean getAutoGenerate() { return autoGenerate; }
        public void setAutoGenerate(Boolean autoGenerate) { this.autoGenerate = autoGenerate; }
    }
    
    public static class CategoryUpdateRequest {
        private String name;
        private String description;
        private List<String> aliases;
        private List<String> includeKeywords;
        private List<String> excludeKeywords;
        private List<String> examplePhrases;
        private CatalogNode.ScoreWeights scoreWeights;
        private Boolean active;
        private Integer order;
        
        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<String> getAliases() { return aliases; }
        public void setAliases(List<String> aliases) { this.aliases = aliases; }
        public List<String> getIncludeKeywords() { return includeKeywords; }
        public void setIncludeKeywords(List<String> includeKeywords) { this.includeKeywords = includeKeywords; }
        public List<String> getExcludeKeywords() { return excludeKeywords; }
        public void setExcludeKeywords(List<String> excludeKeywords) { this.excludeKeywords = excludeKeywords; }
        public List<String> getExamplePhrases() { return examplePhrases; }
        public void setExamplePhrases(List<String> examplePhrases) { this.examplePhrases = examplePhrases; }
        public CatalogNode.ScoreWeights getScoreWeights() { return scoreWeights; }
        public void setScoreWeights(CatalogNode.ScoreWeights scoreWeights) { this.scoreWeights = scoreWeights; }
        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
        public Integer getOrder() { return order; }
        public void setOrder(Integer order) { this.order = order; }
    }
    
    public static class ClassificationRequest {
        private String title;
        private String summary;
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
    }
    
    public static class MetadataPreviewRequest {
        private String name;
        private String code;
        private String parentCode;
        private Integer level;
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getParentCode() { return parentCode; }
        public void setParentCode(String parentCode) { this.parentCode = parentCode; }
        public Integer getLevel() { return level; }
        public void setLevel(Integer level) { this.level = level; }
    }
    
    @Operation(summary = "모든 문서 스마트 재분류", description = "모든 기존 문서를 스마트 분류 시스템으로 재분류합니다.")
    @PostMapping("/reclassify/all")
    public ResponseEntity<Map<String, Object>> reclassifyAllDocuments() {
        try {
            log.info("모든 문서 스마트 재분류 요청");
            
            // 비동기로 실행 (시간이 오래 걸릴 수 있음)
            CompletableFuture.runAsync(() -> {
                try {
                    DocumentReclassificationService.ReclassificationResult result = 
                        reclassificationService.reclassifyAllDocuments();
                    log.info("모든 문서 재분류 완료: 성공률 {:.1f}%", result.getSuccessRate());
                } catch (Exception e) {
                    log.error("모든 문서 재분류 중 오류", e);
                }
            });
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "모든 문서 재분류가 백그라운드에서 시작되었습니다.");
            response.put("startTime", LocalDateTime.now());
            response.put("note", "처리 시간이 오래 걸릴 수 있습니다. 로그를 확인해주세요.");
            
            return ResponseEntity.accepted().body(response);
            
        } catch (Exception e) {
            log.error("모든 문서 재분류 요청 처리 중 오류", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "재분류 요청 처리 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    @Operation(summary = "카테고리별 문서 재분류", description = "특정 카테고리의 문서들을 스마트 분류로 재분류합니다.")
    @PostMapping("/reclassify/category/{categoryCode}")
    public ResponseEntity<Map<String, Object>> reclassifyDocumentsByCategory(
            @Parameter(description = "카테고리 코드", required = true)
            @PathVariable String categoryCode) {
        
        try {
            log.info("카테고리별 문서 재분류 요청: {}", categoryCode);
            
            DocumentReclassificationService.ReclassificationResult result = 
                reclassificationService.reclassifyDocumentsByCategory(categoryCode);
            
            Map<String, Object> response = new HashMap<>();
            response.put("categoryCode", categoryCode);
            response.put("result", result);
            response.put("message", String.format("카테고리 %s의 문서 재분류 완료", categoryCode));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("카테고리별 문서 재분류 중 오류", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "재분류 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    @Operation(summary = "카테고리별 문서 개수 조회", description = "각 카테고리별로 문서 개수를 조회합니다.")
    @GetMapping("/document-counts")
    public ResponseEntity<Map<String, Object>> getCategoryDocumentCounts() {
        try {
            log.info("카테고리별 문서 개수 조회 시작");
            
            Map<String, Long> documentCounts = new HashMap<>();
            
            // 모든 활성 카테고리 조회
            List<CatalogNode> categories = catalogNodeRepository.findByActiveTrue();
            
            for (CatalogNode category : categories) {
                try {
                    // 각 카테고리별 문서 개수 조회
                    long count = 0;
                    
                    // 레벨에 따라 적절한 쿼리 메서드 사용
                    if (category.getLevel() == 1) {
                        // 대분류: majorCode로 검색
                        count = documentRepository.countByCategoryMajorCode(category.getCode());
                    } else if (category.getLevel() == 2) {
                        // 중분류: midCode로 검색
                        count = documentRepository.countByCategoryMidCode(category.getCode());
                    } else if (category.getLevel() == 3) {
                        // 소분류: subCode로 검색
                        count = documentRepository.countByCategorySubCode(category.getCode());
                    } else {
                        // 4레벨 이상: fullCode로 검색
                        count = documentRepository.countByCategoryFullCode(category.getCode());
                    }
                    
                    documentCounts.put(category.getCode(), count);
                    
                } catch (Exception e) {
                    log.warn("카테고리 {}의 문서 개수 조회 실패: {}", category.getCode(), e.getMessage());
                    documentCounts.put(category.getCode(), 0L);
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("documentCounts", documentCounts);
            response.put("totalCategories", categories.size());
            response.put("timestamp", java.time.LocalDateTime.now());
            
            log.info("카테고리별 문서 개수 조회 완료: {} 카테고리", categories.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("카테고리별 문서 개수 조회 중 오류", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "문서 개수 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    @Operation(summary = "카테고리별 문서 목록 조회", description = "특정 카테고리에 속한 문서 목록을 조회합니다.")
    @GetMapping("/{categoryCode}/documents")
    public ResponseEntity<Map<String, Object>> getCategoryDocuments(
            @PathVariable String categoryCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            log.info("카테고리별 문서 목록 조회 시작: {}", categoryCode);
            
            // 카테고리 존재 여부 확인
            Optional<CatalogNode> categoryOpt = catalogNodeRepository.findByCode(categoryCode);
            if (categoryOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            CatalogNode category = categoryOpt.get();
            List<DocumentEntity> documents = new ArrayList<>();
            
            // 카테고리 레벨에 따라 적절한 쿼리 메서드 사용
            if (category.getLevel() == 1) {
                // 대분류: majorCode로 검색
                documents = documentRepository.findByCategoryMajorCode(categoryCode);
            } else if (category.getLevel() == 2) {
                // 중분류: midCode로 검색
                documents = documentRepository.findByCategoryMidCode(categoryCode);
            } else if (category.getLevel() == 3) {
                // 소분류: subCode로 검색
                documents = documentRepository.findByCategorySubCode(categoryCode);
            } else {
                // 4레벨 이상: fullCode로 검색
                documents = documentRepository.findByCategoryFullCode(categoryCode);
            }
            
            // 페이징 처리
            int start = page * size;
            int end = Math.min(start + size, documents.size());
            List<DocumentEntity> pagedDocuments = documents.subList(start, end);
            
            // 응답 데이터 구성
            List<Map<String, Object>> documentList = pagedDocuments.stream()
                .map(doc -> {
                    Map<String, Object> docMap = new HashMap<>();
                    docMap.put("id", doc.getId());
                    docMap.put("serial", doc.getSerial().getFull());
                    docMap.put("purpose", doc.getPurpose());
                    docMap.put("createdAt", doc.getCreatedAt());
                    docMap.put("updatedAt", doc.getUpdatedAt());
                    docMap.put("tags", doc.getTags());
                    docMap.put("category", doc.getCategory());
                    docMap.put("ownerId", doc.getOwnerId());
                    
                    // 파일 정보
                    if (doc.getSource() != null) {
                        if (doc.getSource().getRepoUrl() != null) {
                            docMap.put("sourceType", "repository");
                            docMap.put("sourceUrl", doc.getSource().getRepoUrl());
                        } else if (doc.getSource().getFiles() != null && !doc.getSource().getFiles().isEmpty()) {
                            docMap.put("sourceType", "file");
                            docMap.put("fileIds", doc.getSource().getFiles());
                        }
                    }
                    
                    return docMap;
                })
                .collect(java.util.stream.Collectors.toList());
            
            Map<String, Object> response = new HashMap<>();
            response.put("categoryCode", categoryCode);
            response.put("categoryName", category.getName());
            response.put("categoryLevel", category.getLevel());
            response.put("documents", documentList);
            response.put("totalDocuments", documents.size());
            response.put("currentPage", page);
            response.put("pageSize", size);
            response.put("totalPages", (int) Math.ceil((double) documents.size() / size));
            response.put("hasNext", end < documents.size());
            response.put("hasPrevious", page > 0);
            
            log.info("카테고리별 문서 목록 조회 완료: {} - {} 문서", categoryCode, documents.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("카테고리별 문서 목록 조회 중 오류", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "문서 목록 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    @Operation(summary = "모든 카테고리 메타데이터 강제 재생성", description = "LLM을 사용해서 모든 카테고리의 메타데이터를 강제로 재생성합니다.")
    @PostMapping("/force-regenerate-metadata")
    public ResponseEntity<Map<String, Object>> forceRegenerateAllMetadata() {
        try {
            log.info("모든 카테고리 메타데이터 강제 재생성 시작");
            
            List<CatalogNode> categories = catalogNodeRepository.findByActiveTrue();
            int totalCount = categories.size();
            int successCount = 0;
            int failureCount = 0;
            List<String> errors = new ArrayList<>();
            
            for (CatalogNode category : categories) {
                try {
                    log.info("카테고리 메타데이터 강제 재생성: {} ({})", category.getName(), category.getCode());
                    
                    // LLM을 사용해서 메타데이터 생성
                    CategoryMetadataGenerationService.CategoryMetadata metadata = 
                        metadataGenerationService.generateCategoryMetadata(
                            category.getName(), 
                            category.getCode(), 
                            category.getParentCode(), 
                            category.getLevel()
                        );
                    
                    // 기존 데이터를 강제로 덮어쓰기
                    category.setDescription(metadata.getDescription());
                    category.setAliases(metadata.getAliases());
                    category.setIncludeKeywords(metadata.getIncludeKeywords());
                    category.setExcludeKeywords(metadata.getExcludeKeywords());
                    category.setExamplePhrases(metadata.getExamplePhrases());
                    
                    // 메타데이터 저장
                    catalogNodeRepository.save(category);
                    
                    // 임베딩도 함께 재생성
                    try {
                        embeddingService.updateCategoryEmbedding(category.getCode());
                        log.info("카테고리 임베딩 재생성 완료: {}", category.getCode());
                    } catch (Exception embeddingError) {
                        log.warn("카테고리 {} 임베딩 재생성 실패: {}", category.getCode(), embeddingError.getMessage());
                        // 임베딩 실패는 전체 실패로 처리하지 않음
                    }
                    
                    successCount++;
                    
                    log.info("카테고리 메타데이터 및 임베딩 강제 재생성 완료: {}", category.getCode());
                    
                } catch (Exception e) {
                    log.error("카테고리 {} 메타데이터 재생성 실패: {}", category.getCode(), e.getMessage());
                    errors.add(String.format("%s: %s", category.getCode(), e.getMessage()));
                    failureCount++;
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "모든 카테고리 메타데이터 및 임베딩 강제 재생성 완료");
            response.put("totalCount", totalCount);
            response.put("successCount", successCount);
            response.put("failureCount", failureCount);
            response.put("errors", errors);
            response.put("timestamp", LocalDateTime.now());
            
            log.info("모든 카테고리 메타데이터 및 임베딩 강제 재생성 완료: {}/{} 성공", successCount, totalCount);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("카테고리 메타데이터 강제 재생성 중 전체 오류", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "메타데이터 재생성 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
}
