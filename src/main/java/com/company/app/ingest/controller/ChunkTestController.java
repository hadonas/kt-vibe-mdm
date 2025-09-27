package com.company.app.ingest.controller;

import com.company.app.ingest.service.ChunkAnalyzer;
import com.company.app.ingest.service.ChunkQualityValidator;
import com.company.app.ingest.service.SemanticChunker;
import com.company.app.ingest.service.TokenCounter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 청킹 알고리즘 테스트 및 분석용 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/chunk")
@RequiredArgsConstructor
public class ChunkTestController {
    
    private final SemanticChunker semanticChunker;
    private final TokenCounter tokenCounter;
    private final ChunkAnalyzer chunkAnalyzer;
    private final ChunkQualityValidator qualityValidator;
    
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.75;
    
    /**
     * 텍스트 청킹 테스트 (통합 버전)
     */
    @PostMapping("/test")
    public ResponseEntity<?> testChunking(@RequestBody Map<String, Object> request) {
        String text = (String) request.get("text");
        Boolean withQualityValidation = (Boolean) request.getOrDefault("withQualityValidation", true);
        Integer maxTokens = (Integer) request.getOrDefault("maxTokens", 500);
        String docTypeStr = (String) request.getOrDefault("documentType", "TEXT");
        
        if (text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("텍스트가 필요합니다.");
        }
        
        try {
            // 문서 타입 파싱
            SemanticChunker.DocumentType docType;
            try {
                docType = SemanticChunker.DocumentType.valueOf(docTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                docType = SemanticChunker.DocumentType.TEXT;
            }
            
            // 청킹 수행
            List<String> chunks = semanticChunker.chunkText(text, docType, maxTokens, 
                DEFAULT_SIMILARITY_THRESHOLD, withQualityValidation);
            
            // 결과 분석
            Map<String, Object> result = new HashMap<>();
            result.put("originalTextLength", text.length());
            result.put("originalTokens", tokenCounter.countTokens(text));
            result.put("documentType", docType.toString());
            result.put("chunkCount", chunks.size());
            result.put("chunks", chunks);
            
            // 청크별 상세 분석
            List<Map<String, Object>> chunkAnalysis = chunks.stream()
                .map(chunk -> {
                    Map<String, Object> analysis = new HashMap<>();
                    analysis.put("content", chunk);
                    analysis.put("tokenCount", tokenCounter.countTokens(chunk));
                    analysis.put("characterCount", chunk.length());
                    
                    // 구조적 요소 감지
                    analysis.put("hasTable", chunk.contains("|") || chunk.contains("\t"));
                    analysis.put("hasList", chunk.matches(".*(?:^|\\n)\\s*[-*+•]\\s+.*"));
                    analysis.put("hasCode", chunk.contains("```") || chunk.contains("`"));
                    
                    // 메타데이터 분석
                    List<String> documentKeywords = chunkAnalyzer.extractDocumentKeywords(text);
                    ChunkAnalyzer.ChunkMetadata metadata = chunkAnalyzer.analyzeChunk(
                        chunk, "SECTION", 3, documentKeywords);
                    
                    analysis.put("importance", metadata.importance());
                    analysis.put("keywords", metadata.keywords());
                    analysis.put("contentType", metadata.contentType());
                    analysis.put("structureScore", metadata.structureScore());
                    analysis.put("readabilityScore", metadata.readabilityScore());
                    analysis.put("qualityScore", chunkAnalyzer.evaluateChunkQuality(metadata, chunk));
                    
                    return analysis;
                })
                .toList();
            
            result.put("chunkAnalysis", chunkAnalysis);
            
            // 문서 구조 분석 결과 추가
            Map<String, Object> structureAnalysis = new HashMap<>();
            structureAnalysis.put("detectedTables", countMatches(text, "\\|.*\\|"));
            structureAnalysis.put("detectedLists", countMatches(text, "(?m)^\\s*[-*+•]\\s+"));
            structureAnalysis.put("detectedHeaders", countMatches(text, "(?m)^\\s*#{1,6}\\s+"));
            structureAnalysis.put("detectedCodeBlocks", countMatches(text, "```"));
            
            result.put("structureAnalysis", structureAnalysis);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("청킹 테스트 중 오류 발생", e);
            return ResponseEntity.internalServerError().body("청킹 처리 중 오류: " + e.getMessage());
        }
    }
    
    /**
     * 청크 품질 분석
     */
    @PostMapping("/analyze-quality")
    public ResponseEntity<?> analyzeChunkQuality(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> chunks = (List<String>) request.get("chunks");
        String fullText = (String) request.get("fullText");
        
        if (chunks == null || chunks.isEmpty()) {
            return ResponseEntity.badRequest().body("청크 목록이 필요합니다.");
        }
        
        try {
            ChunkQualityValidator.ChunkValidationResult result = 
                qualityValidator.validateAndOptimizeChunks(chunks, fullText);
            
            Map<String, Object> response = new HashMap<>();
            response.put("originalChunkCount", chunks.size());
            response.put("optimizedChunkCount", result.validatedChunks().size());
            response.put("warnings", result.warnings());
            response.put("statistics", result.statistics());
            
            // 최적화된 청크 정보
            List<Map<String, Object>> optimizedChunks = result.validatedChunks().stream()
                .map(chunk -> {
                    Map<String, Object> info = new HashMap<>();
                    info.put("content", chunk.content());
                    info.put("tokenCount", tokenCounter.countTokens(chunk.content()));
                    info.put("qualityScore", chunk.qualityScore());
                    info.put("needsAttention", chunk.needsAttention());
                    info.put("importance", chunk.metadata().importance());
                    info.put("contentType", chunk.metadata().contentType());
                    return info;
                })
                .toList();
            
            response.put("optimizedChunks", optimizedChunks);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("청크 품질 분석 중 오류 발생", e);
            return ResponseEntity.internalServerError().body("품질 분석 중 오류: " + e.getMessage());
        }
    }
    
    /**
     * 토큰 계산 테스트
     */
    @PostMapping("/count-tokens")
    public ResponseEntity<?> countTokens(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        
        if (text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("텍스트가 필요합니다.");
        }
        
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("text", text);
            result.put("characterCount", text.length());
            result.put("tokenCount", tokenCounter.countTokens(text));
            result.put("koreanTokens", tokenCounter.countKoreanTokens(text));
            result.put("englishTokens", tokenCounter.countEnglishTokens(text));
            
            // 기존 방식과 비교
            int legacyTokens = text.length() / 4;
            result.put("legacyTokenEstimate", legacyTokens);
            result.put("improvementRatio", (double) tokenCounter.countTokens(text) / legacyTokens);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("토큰 계산 중 오류 발생", e);
            return ResponseEntity.internalServerError().body("토큰 계산 중 오류: " + e.getMessage());
        }
    }
    
    /**
     * 청킹 알고리즘 비교
     */
    @PostMapping("/compare")
    public ResponseEntity<?> compareChunkingMethods(@RequestBody Map<String, Object> request) {
        String text = (String) request.get("text");
        Integer maxTokens = (Integer) request.getOrDefault("maxTokens", 500);
        
        if (text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("텍스트가 필요합니다.");
        }
        
        try {
            Map<String, Object> comparison = new HashMap<>();
            
            // 기본 청킹 (품질 검증 없음)
            List<String> basicChunks = semanticChunker.chunkText(text, SemanticChunker.DocumentType.TEXT, 
                maxTokens, DEFAULT_SIMILARITY_THRESHOLD, false);
            Map<String, Object> basicResult = analyzeChunkSet(basicChunks, "기본 청킹");
            comparison.put("basic", basicResult);
            
            // 품질 검증 포함 청킹
            List<String> qualityChunks = semanticChunker.chunkText(text, SemanticChunker.DocumentType.TEXT, 
                maxTokens, DEFAULT_SIMILARITY_THRESHOLD, true);
            Map<String, Object> qualityResult = analyzeChunkSet(qualityChunks, "품질 검증 청킹");
            comparison.put("withQuality", qualityResult);
            
            // 개선 정도 계산
            Map<String, Object> improvement = new HashMap<>();
            improvement.put("chunkCountChange", qualityChunks.size() - basicChunks.size());
            improvement.put("averageTokensImprovement", 
                calculateAverageTokens(qualityChunks) - calculateAverageTokens(basicChunks));
            
            comparison.put("improvement", improvement);
            comparison.put("originalText", Map.of(
                "length", text.length(),
                "tokens", tokenCounter.countTokens(text)
            ));
            
            return ResponseEntity.ok(comparison);
            
        } catch (Exception e) {
            log.error("청킹 방법 비교 중 오류 발생", e);
            return ResponseEntity.internalServerError().body("비교 분석 중 오류: " + e.getMessage());
        }
    }
    
    private Map<String, Object> analyzeChunkSet(List<String> chunks, String methodName) {
        Map<String, Object> analysis = new HashMap<>();
        analysis.put("method", methodName);
        analysis.put("chunkCount", chunks.size());
        analysis.put("averageTokens", calculateAverageTokens(chunks));
        analysis.put("minTokens", chunks.stream().mapToInt(tokenCounter::countTokens).min().orElse(0));
        analysis.put("maxTokens", chunks.stream().mapToInt(tokenCounter::countTokens).max().orElse(0));
        
        return analysis;
    }
    
    private double calculateAverageTokens(List<String> chunks) {
        return chunks.stream()
            .mapToInt(tokenCounter::countTokens)
            .average()
            .orElse(0.0);
    }
    
    /**
     * 텍스트에서 정규표현식 패턴 매칭 횟수 계산
     */
    private long countMatches(String text, String regex) {
        try {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(text);
            return matcher.results().count();
        } catch (Exception e) {
            log.warn("패턴 매칭 중 오류: {}", regex, e);
            return 0;
        }
    }
}
