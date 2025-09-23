package com.company.app.ingest.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 정확한 토큰 수 계산을 위한 유틸리티 클래스
 * OpenAI GPT 계열 모델의 토큰 수를 근사적으로 계산합니다.
 */
@Slf4j
@Component
public class TokenCounter {
    
    // 한국어 조사, 어미 등 자주 나타나는 형태소
    private static final Set<String> KOREAN_PARTICLES = new HashSet<>(Arrays.asList(
        "은", "는", "이", "가", "을", "를", "에", "에서", "로", "으로", "와", "과", "의", "도", "만", "부터", "까지"
    ));
    
    // 영어 고빈도 단어들 (토큰 1개로 간주)
    private static final Set<String> ENGLISH_COMMON_WORDS = new HashSet<>(Arrays.asList(
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by"
    ));
    
    // 특수 문자 패턴
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern PUNCTUATION = Pattern.compile("[\\p{Punct}]+");
    private static final Pattern KOREAN = Pattern.compile("[가-힣]+");
    private static final Pattern ENGLISH = Pattern.compile("[a-zA-Z]+");
    private static final Pattern NUMBERS = Pattern.compile("\\d+");
    
    /**
     * 텍스트의 예상 토큰 수를 계산합니다.
     * GPT 계열 모델의 토큰화 규칙을 근사적으로 적용합니다.
     */
    public int countTokens(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        
        text = text.trim();
        int tokenCount = 0;
        
        // 1. 공백으로 단어 분리
        String[] words = WHITESPACE.split(text);
        
        for (String word : words) {
            if (word.isEmpty()) continue;
            
            tokenCount += countWordTokens(word);
        }
        
        return Math.max(1, tokenCount); // 최소 1개 토큰
    }
    
    /**
     * 개별 단어의 토큰 수를 계산합니다.
     */
    private int countWordTokens(String word) {
        if (word.isEmpty()) return 0;
        
        // 구두점 제거한 순수 단어
        String cleanWord = PUNCTUATION.matcher(word).replaceAll("");
        
        // 구두점 토큰 수 추가
        int punctTokens = word.length() - cleanWord.length();
        
        if (cleanWord.isEmpty()) {
            return Math.max(1, punctTokens); // 구두점만 있는 경우
        }
        
        // 한국어 처리
        if (KOREAN.matcher(cleanWord).matches()) {
            return countKoreanTokens(cleanWord) + punctTokens;
        }
        
        // 영어 처리
        if (ENGLISH.matcher(cleanWord).matches()) {
            return countEnglishTokens(cleanWord) + punctTokens;
        }
        
        // 숫자 처리
        if (NUMBERS.matcher(cleanWord).matches()) {
            return Math.max(1, cleanWord.length() / 3) + punctTokens; // 숫자는 3자리당 대략 1토큰
        }
        
        // 혼합된 경우 - 보수적으로 계산
        return Math.max(1, cleanWord.length() / 3) + punctTokens;
    }
    
    /**
     * 한국어 단어의 토큰 수를 계산합니다.
     */
    public int countKoreanTokens(String koreanWord) {
        if (koreanWord.length() <= 1) return 1;
        
        // 조사나 어미인 경우 1토큰
        if (KOREAN_PARTICLES.contains(koreanWord)) {
            return 1;
        }
        
        // 일반적으로 한국어는 2-3글자당 1토큰 정도
        if (koreanWord.length() <= 3) return 1;
        if (koreanWord.length() <= 6) return 2;
        
        // 긴 단어는 3글자당 1토큰으로 근사
        return Math.max(2, koreanWord.length() / 3);
    }
    
    /**
     * 영어 단어의 토큰 수를 계산합니다.
     */
    public int countEnglishTokens(String englishWord) {
        String lowerWord = englishWord.toLowerCase();
        
        // 고빈도 단어는 1토큰
        if (ENGLISH_COMMON_WORDS.contains(lowerWord)) {
            return 1;
        }
        
        // 짧은 단어는 1토큰
        if (englishWord.length() <= 4) return 1;
        
        // 중간 길이 단어는 대부분 1토큰
        if (englishWord.length() <= 8) return 1;
        
        // 긴 단어는 subword로 분할될 가능성이 높음
        return Math.max(1, englishWord.length() / 6);
    }
    
    /**
     * 토큰 수 기반으로 최적 청크 크기인지 판단합니다.
     */
    public boolean isOptimalChunkSize(String text, int minTokens, int maxTokens) {
        int tokens = countTokens(text);
        return tokens >= minTokens && tokens <= maxTokens;
    }
    
    /**
     * 텍스트가 너무 길어서 분할이 필요한지 판단합니다.
     */
    public boolean needsSplitting(String text, int maxTokens) {
        return countTokens(text) > maxTokens;
    }
}
