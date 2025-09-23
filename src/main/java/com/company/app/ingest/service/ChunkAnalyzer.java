package com.company.app.ingest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 청크 품질 및 메타데이터 분석기
 * - 청크 중요도 점수 계산
 * - 키워드 추출 및 TF-IDF 계산
 * - 청크 품질 평가
 * - 문서 구조 분석
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChunkAnalyzer {
    
    private final TokenCounter tokenCounter;
    
    // 중요도 계산 가중치
    private static final double TITLE_WEIGHT = 0.3;
    private static final double LENGTH_WEIGHT = 0.2;
    private static final double KEYWORD_WEIGHT = 0.25;
    private static final double STRUCTURE_WEIGHT = 0.25;
    
    // 중요 키워드 패턴
    private static final Set<String> IMPORTANT_KEYWORDS = Set.of(
        "목적", "목표", "objective", "goal", "중요", "핵심", "주요", "key", "important", "critical",
        "결론", "conclusion", "요약", "summary", "개요", "overview", "특징", "feature",
        "방법", "method", "방식", "approach", "절차", "procedure", "과정", "process"
    );
    
    private static final Pattern CODE_PATTERN = Pattern.compile("```[\\s\\S]*?```|`[^`]+`");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[\\w\\.-]+");
    private static final Pattern EQUATION_PATTERN = Pattern.compile("\\$[^$]+\\$|\\\\\\([^)]+\\\\\\)");
    
    public record ChunkMetadata(
        double importance,
        List<String> keywords,
        Map<String, Double> tfIdfScores,
        int structureScore,
        String contentType,
        boolean hasCode,
        boolean hasEquations,
        boolean hasUrls,
        double readabilityScore
    ) {}
    
    /**
     * 청크의 메타데이터 분석
     */
    public ChunkMetadata analyzeChunk(String text, String sectionHint, int headerLevel, 
                                    List<String> documentKeywords) {
        
        // 1. 기본 정보 추출
        boolean hasCode = CODE_PATTERN.matcher(text).find();
        boolean hasEquations = EQUATION_PATTERN.matcher(text).find();
        boolean hasUrls = URL_PATTERN.matcher(text).find();
        
        // 2. 콘텐츠 타입 결정
        String contentType = determineContentType(text, hasCode, hasEquations);
        
        // 3. 키워드 추출
        List<String> keywords = extractKeywords(text);
        
        // 4. TF-IDF 점수 계산
        Map<String, Double> tfIdfScores = calculateTfIdf(text, documentKeywords);
        
        // 5. 중요도 점수 계산
        double importance = calculateImportance(text, sectionHint, headerLevel, keywords, contentType);
        
        // 6. 구조 점수 계산
        int structureScore = calculateStructureScore(text, sectionHint);
        
        // 7. 가독성 점수 계산
        double readabilityScore = calculateReadability(text);
        
        return new ChunkMetadata(
            importance,
            keywords,
            tfIdfScores,
            structureScore,
            contentType,
            hasCode,
            hasEquations,
            hasUrls,
            readabilityScore
        );
    }
    
    /**
     * 콘텐츠 타입 결정
     */
    private String determineContentType(String text, boolean hasCode, boolean hasEquations) {
        if (hasCode) return "CODE";
        if (hasEquations) return "MATH";
        if (text.contains("|") && text.contains("-")) return "TABLE";
        if (text.matches("^\\s*[-*+]\\s+.+")) return "LIST";
        return "TEXT";
    }
    
    /**
     * 중요도 점수 계산 (0.0 ~ 1.0)
     */
    private double calculateImportance(String text, String sectionHint, int headerLevel, 
                                     List<String> keywords, String contentType) {
        
        double score = 0.0;
        
        // 1. 제목/섹션 기반 점수
        double titleScore = calculateTitleScore(sectionHint, headerLevel);
        score += titleScore * TITLE_WEIGHT;
        
        // 2. 길이 기반 점수 (적절한 길이가 높은 점수)
        double lengthScore = calculateLengthScore(text);
        score += lengthScore * LENGTH_WEIGHT;
        
        // 3. 키워드 기반 점수
        double keywordScore = calculateKeywordScore(text, keywords);
        score += keywordScore * KEYWORD_WEIGHT;
        
        // 4. 구조적 요소 점수
        double structureScore = calculateStructuralScore(text, contentType);
        score += structureScore * STRUCTURE_WEIGHT;
        
        return Math.min(1.0, Math.max(0.0, score));
    }
    
    private double calculateTitleScore(String sectionHint, int headerLevel) {
        double score = 0.5; // 기본 점수
        
        // 헤더 레벨 (낮을수록 중요)
        score += (6 - Math.min(6, headerLevel)) * 0.1;
        
        // 섹션 제목 키워드
        String hint = sectionHint.toLowerCase();
        for (String keyword : IMPORTANT_KEYWORDS) {
            if (hint.contains(keyword)) {
                score += 0.1;
                break;
            }
        }
        
        return Math.min(1.0, score);
    }
    
    private double calculateLengthScore(String text) {
        int tokens = tokenCounter.countTokens(text);
        
        // 적절한 길이 범위에서 높은 점수
        if (tokens >= 150 && tokens <= 400) {
            return 1.0;
        } else if (tokens >= 100 && tokens <= 500) {
            return 0.8;
        } else if (tokens >= 50 && tokens <= 600) {
            return 0.6;
        } else {
            return 0.3;
        }
    }
    
    private double calculateKeywordScore(String text, List<String> keywords) {
        String lowerText = text.toLowerCase();
        int importantKeywordCount = 0;
        
        for (String keyword : IMPORTANT_KEYWORDS) {
            if (lowerText.contains(keyword)) {
                importantKeywordCount++;
            }
        }
        
        // 키워드 밀도
        double density = (double) keywords.size() / tokenCounter.countTokens(text);
        
        return Math.min(1.0, (importantKeywordCount * 0.2) + (density * 2.0));
    }
    
    private double calculateStructuralScore(String text, String contentType) {
        double score = 0.5;
        
        // 콘텐츠 타입별 가중치
        switch (contentType) {
            case "TABLE" -> score += 0.3; // 테이블은 중요한 정보
            case "CODE" -> score += 0.2;  // 코드는 구체적 정보
            case "LIST" -> score += 0.1;  // 리스트는 구조화된 정보
            case "MATH" -> score += 0.2;  // 수식은 핵심 정보
        }
        
        // 구조적 요소들
        if (text.contains("●") || text.contains("○") || text.contains("■")) {
            score += 0.1; // 불릿 포인트
        }
        
        if (text.matches(".*\\d+[.))]\\s+.*")) {
            score += 0.1; // 번호 목록
        }
        
        return Math.min(1.0, score);
    }
    
    /**
     * 키워드 추출 (개선된 버전)
     */
    private List<String> extractKeywords(String text) {
        // 1. 전처리
        String cleanText = text.toLowerCase()
            .replaceAll("[^가-힣a-zA-Z0-9\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        
        // 2. 단어 분리 및 필터링
        String[] words = cleanText.split("\\s+");
        Map<String, Integer> wordFreq = new HashMap<>();
        
        for (String word : words) {
            if (isValidKeyword(word)) {
                wordFreq.merge(word, 1, Integer::sum);
            }
        }
        
        // 3. 빈도 기반 정렬 및 상위 키워드 추출
        return wordFreq.entrySet().stream()
            .filter(entry -> entry.getValue() >= 2 || IMPORTANT_KEYWORDS.contains(entry.getKey()))
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(10)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    private boolean isValidKeyword(String word) {
        if (word.length() < 2) return false;
        
        // 불용어 제거
        Set<String> stopWords = Set.of(
            "이", "그", "저", "의", "을", "를", "에", "에서", "로", "으로", "와", "과", "도", "만",
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by"
        );
        
        return !stopWords.contains(word) && 
               !word.matches("\\d+") && // 순수 숫자 제외
               word.length() <= 20;     // 너무 긴 단어 제외
    }
    
    /**
     * TF-IDF 점수 계산
     */
    private Map<String, Double> calculateTfIdf(String text, List<String> documentKeywords) {
        Map<String, Double> tfIdf = new HashMap<>();
        List<String> textKeywords = extractKeywords(text);
        
        for (String keyword : textKeywords) {
            // TF (Term Frequency)
            long tf = Arrays.stream(text.toLowerCase().split("\\s+"))
                .filter(word -> word.equals(keyword))
                .count();
            
            // IDF (Inverse Document Frequency) - 간단한 근사
            double idf = Math.log(1.0 + documentKeywords.size() / 
                (1.0 + Collections.frequency(documentKeywords, keyword)));
            
            tfIdf.put(keyword, tf * idf);
        }
        
        return tfIdf;
    }
    
    /**
     * 구조 점수 계산 (1-10 점)
     */
    private int calculateStructureScore(String text, String sectionHint) {
        int score = 5; // 기본 점수
        
        // 제목이 있으면 가점
        if (sectionHint != null && !sectionHint.equals("FULL") && !sectionHint.equals("ROOT")) {
            score += 2;
        }
        
        // 문장 구조
        int sentenceCount = text.split("[.!?。！？]").length;
        if (sentenceCount >= 3 && sentenceCount <= 8) {
            score += 1; // 적절한 문장 수
        }
        
        // 특수 구조
        if (text.contains(":") || text.contains("：")) {
            score += 1; // 정의나 설명 구조
        }
        
        return Math.min(10, Math.max(1, score));
    }
    
    /**
     * 가독성 점수 계산 (0.0 ~ 1.0)
     */
    private double calculateReadability(String text) {
        if (text.trim().isEmpty()) return 0.0;
        
        // 문장 수와 단어 수 계산
        int sentences = text.split("[.!?。！？]").length;
        int words = text.split("\\s+").length;
        int characters = text.replaceAll("\\s", "").length();
        
        // 평균 문장 길이
        double avgSentenceLength = (double) words / Math.max(1, sentences);
        
        // 평균 단어 길이
        double avgWordLength = (double) characters / Math.max(1, words);
        
        // 가독성 점수 (낮을수록 읽기 쉬움)
        double readabilityScore = 0.5;
        
        // 문장 길이 패널티
        if (avgSentenceLength > 30) {
            readabilityScore -= 0.2;
        } else if (avgSentenceLength > 20) {
            readabilityScore -= 0.1;
        }
        
        // 단어 길이 고려 (한국어는 상대적으로 짧음)
        if (avgWordLength > 8) {
            readabilityScore -= 0.1;
        }
        
        // 구두점과 공백 비율
        long punctCount = text.chars().filter(ch -> ".,!?;:".indexOf(ch) >= 0).count();
        double punctRatio = (double) punctCount / text.length();
        
        if (punctRatio > 0.05 && punctRatio < 0.15) {
            readabilityScore += 0.2; // 적절한 구두점 사용
        }
        
        return Math.min(1.0, Math.max(0.0, readabilityScore));
    }
    
    /**
     * 문서 전체의 키워드 집합 생성
     */
    public List<String> extractDocumentKeywords(String fullText) {
        return extractKeywords(fullText);
    }
    
    /**
     * 청크 품질 평가
     */
    public double evaluateChunkQuality(ChunkMetadata metadata, String text) {
        double quality = 0.0;
        
        // 중요도 점수 (40%)
        quality += metadata.importance() * 0.4;
        
        // 가독성 점수 (20%)
        quality += metadata.readabilityScore() * 0.2;
        
        // 구조 점수 (20%)
        quality += (metadata.structureScore() / 10.0) * 0.2;
        
        // 길이 적절성 (10%)
        int tokens = tokenCounter.countTokens(text);
        double lengthQuality = tokens >= 100 && tokens <= 500 ? 1.0 : 0.5;
        quality += lengthQuality * 0.1;
        
        // 키워드 풍부도 (10%)
        double keywordDensity = Math.min(1.0, metadata.keywords().size() / 10.0);
        quality += keywordDensity * 0.1;
        
        return Math.min(1.0, quality);
    }
}
