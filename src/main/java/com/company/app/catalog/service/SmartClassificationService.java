package com.company.app.catalog.service;

import com.company.app.catalog.entity.CatalogNode;
import com.company.app.catalog.repository.CatalogNodeRepository;
import com.company.app.chat.service.LLMService;
import com.company.app.common.dto.Category;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 스마트 문서 분류 서비스
 * 하이브리드 검색 + LLM 기반 tie-breaking
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmartClassificationService {
    
    private final HybridCategorySearchService hybridSearchService;
    private final LLMService llmService;
    private final CatalogNodeRepository catalogNodeRepository;
    
    @Value("${classification.candidate-count:10}")
    private int candidateCount;
    
    @Value("${classification.confidence-threshold:0.8}")
    private double confidenceThreshold;
    
    /**
     * 문서 요약을 기반으로 스마트 분류 수행
     */
    public ClassificationResult classifyDocument(String documentSummary, String documentTitle) {
        try {
            log.info("스마트 문서 분류 시작: 제목={}, 요약={}자", documentTitle, documentSummary.length());
            
            // 1. 하이브리드 검색으로 후보 카테고리 검색 (더 많은 후보 조회)
            List<HybridCategorySearchService.CategorySearchResult> rawCandidates = 
                hybridSearchService.searchCategories(documentSummary, candidateCount * 2);
            
            if (rawCandidates.isEmpty()) {
                log.warn("후보 카테고리를 찾을 수 없음");
                return new ClassificationResult(null, 0.0, "후보 카테고리 없음", rawCandidates);
            }
            
            // 2. 구체성 기반 후보 필터링 및 재정렬
            List<HybridCategorySearchService.CategorySearchResult> candidates = 
                filterAndRerankCandidates(rawCandidates, candidateCount);
            
            log.info("후보 카테고리 {}개 발견", candidates.size());
            
            // 2. 최고 점수가 충분히 높으면 바로 선택
            HybridCategorySearchService.CategorySearchResult topCandidate = candidates.get(0);
            if (topCandidate.getHybridScore() > confidenceThreshold && 
                candidates.size() > 1 && 
                topCandidate.getHybridScore() > candidates.get(1).getHybridScore() * 1.5) {
                
                log.info("높은 신뢰도로 카테고리 자동 선택: {} (점수: {:.3f})", 
                    topCandidate.getCategory().getCode(), topCandidate.getHybridScore());
                
                Category selectedCategory = convertToCategory(topCandidate.getCategory());
                return new ClassificationResult(selectedCategory, topCandidate.getHybridScore(), 
                    "자동 선택 (높은 신뢰도)", candidates);
            }
            
            // 3. LLM을 사용한 tie-breaking
            Category selectedCategory = performLLMTieBreaking(documentSummary, documentTitle, candidates);
            
            if (selectedCategory != null) {
                double finalScore = candidates.stream()
                    .filter(c -> c.getCategory().getCode().equals(getCategoryCode(selectedCategory)))
                    .mapToDouble(HybridCategorySearchService.CategorySearchResult::getHybridScore)
                    .findFirst()
                    .orElse(0.0);
                
                return new ClassificationResult(selectedCategory, finalScore, "LLM tie-breaking", candidates);
            } else {
                log.warn("LLM 분류 실패, 최고 점수 카테고리 선택");
                Category fallbackCategory = convertToCategory(topCandidate.getCategory());
                return new ClassificationResult(fallbackCategory, topCandidate.getHybridScore(), 
                    "Fallback 선택", candidates);
            }
            
        } catch (Exception e) {
            log.error("스마트 문서 분류 중 오류", e);
            return new ClassificationResult(null, 0.0, "분류 오류: " + e.getMessage(), List.of());
        }
    }
    
    /**
     * 구체성 기반 후보 필터링 및 재정렬
     */
    private List<HybridCategorySearchService.CategorySearchResult> filterAndRerankCandidates(
            List<HybridCategorySearchService.CategorySearchResult> rawCandidates, int limit) {
        
        try {
            log.info("구체성 기반 후보 필터링 시작: {}개 → {}개", rawCandidates.size(), limit);
            
            // 1. 레벨별로 그룹화
            Map<Integer, List<HybridCategorySearchService.CategorySearchResult>> levelGroups = 
                rawCandidates.stream()
                    .collect(Collectors.groupingBy(c -> c.getCategory().getLevel()));
            
            List<HybridCategorySearchService.CategorySearchResult> filteredCandidates = new ArrayList<>();
            
            // 2. 하위 레벨부터 우선적으로 선택 (더 구체적인 카테고리 우선)
            for (int level = 5; level >= 1; level--) {
                if (levelGroups.containsKey(level)) {
                    List<HybridCategorySearchService.CategorySearchResult> levelCandidates = levelGroups.get(level);
                    
                    // 해당 레벨에서 상위 점수 후보들 선택
                    int remainingSlots = limit - filteredCandidates.size();
                    if (remainingSlots <= 0) break;
                    
                    // 레벨 3 이상(구체적인 카테고리)은 더 많이 포함
                    int levelLimit = level >= 3 ? Math.min(remainingSlots, levelCandidates.size()) 
                                                : Math.min(remainingSlots / 2, levelCandidates.size());
                    
                    filteredCandidates.addAll(levelCandidates.stream()
                        .limit(Math.max(1, levelLimit))
                        .collect(Collectors.toList()));
                }
            }
            
            // 3. 최종 정렬 (하이브리드 점수 기준)
            List<HybridCategorySearchService.CategorySearchResult> finalCandidates = filteredCandidates.stream()
                .sorted(Comparator.comparing(HybridCategorySearchService.CategorySearchResult::getHybridScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
            
            log.info("구체성 기반 필터링 완료: 최종 {}개 후보", finalCandidates.size());
            
            // 후보들의 레벨 분포 로깅
            Map<Integer, Long> levelDistribution = finalCandidates.stream()
                .collect(Collectors.groupingBy(c -> c.getCategory().getLevel(), Collectors.counting()));
            log.info("레벨별 분포: {}", levelDistribution);
            
            return finalCandidates;
            
        } catch (Exception e) {
            log.error("후보 필터링 중 오류, 원본 후보 반환", e);
            return rawCandidates.stream().limit(limit).collect(Collectors.toList());
        }
    }

    /**
     * LLM을 사용한 tie-breaking
     */
    private Category performLLMTieBreaking(String documentSummary, String documentTitle, 
                                         List<HybridCategorySearchService.CategorySearchResult> candidates) {
        try {
            log.info("LLM tie-breaking 시작: {}개 후보", candidates.size());
            
            // 상위 후보들만 선택 (최대 5개)
            List<HybridCategorySearchService.CategorySearchResult> topCandidates = 
                candidates.stream().limit(5).collect(Collectors.toList());
            
            // LLM 프롬프트 생성
            String prompt = buildClassificationPrompt(documentSummary, documentTitle, topCandidates);
            
            // LLM 호출
            String llmResponse = llmService.generateAnswer(prompt);
            
            // LLM 응답에서 카테고리 코드 추출
            String selectedCode = extractCategoryCodeFromLLMResponse(llmResponse, topCandidates);
            
            if (selectedCode != null) {
                Optional<HybridCategorySearchService.CategorySearchResult> selectedResult = 
                    topCandidates.stream()
                        .filter(c -> c.getCategory().getCode().equals(selectedCode))
                        .findFirst();
                
                if (selectedResult.isPresent()) {
                    log.info("LLM이 선택한 카테고리: {} (점수: {:.3f})", 
                        selectedCode, selectedResult.get().getHybridScore());
                    return convertToCategory(selectedResult.get().getCategory());
                }
            }
            
            log.warn("LLM 응답에서 유효한 카테고리 코드를 찾을 수 없음: {}", llmResponse);
            return null;
            
        } catch (Exception e) {
            log.error("LLM tie-breaking 중 오류", e);
            return null;
        }
    }
    
    /**
     * 분류용 LLM 프롬프트 생성
     */
    private String buildClassificationPrompt(String documentSummary, String documentTitle, 
                                           List<HybridCategorySearchService.CategorySearchResult> candidates) {
        
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("다음 문서를 가장 적절한 카테고리로 분류해주세요.\n\n");
        
        prompt.append("## 문서 정보\n");
        prompt.append("제목: ").append(documentTitle != null ? documentTitle : "제목 없음").append("\n");
        prompt.append("요약: ").append(documentSummary).append("\n\n");
        
        prompt.append("## 후보 카테고리들\n");
        for (int i = 0; i < candidates.size(); i++) {
            CatalogNode category = candidates.get(i).getCategory();
            double score = candidates.get(i).getHybridScore();
            
            prompt.append(String.format("%d. 코드: %s\n", i + 1, category.getCode()));
            prompt.append(String.format("   이름: %s\n", category.getName()));
            prompt.append(String.format("   점수: %.3f\n", score));
            
            if (category.getDescription() != null) {
                prompt.append(String.format("   설명: %s\n", category.getDescription()));
            }
            
            if (category.getAliases() != null && !category.getAliases().isEmpty()) {
                prompt.append(String.format("   동의어: %s\n", String.join(", ", category.getAliases())));
            }
            
            prompt.append("\n");
        }
        
        prompt.append("## 지시사항\n");
        prompt.append("1. 문서의 내용과 목적을 고려하여 **가장 구체적이고 세분화된** 카테고리를 선택하세요.\n");
        prompt.append("2. 여러 카테고리가 적용 가능한 경우, **더 구체적이고 하위 레벨의 카테고리**를 우선 선택하세요.\n");
        prompt.append("3. 예를 들어, 'AI/머신러닝'과 '소프트웨어' 중에서는 'AI/머신러닝'이 더 구체적이므로 우선합니다.\n");
        prompt.append("4. 단순히 점수가 높다고 선택하지 말고, 문서의 **핵심 주제와 가장 밀접한 관련이 있는** 카테고리를 선택하세요.\n");
        prompt.append("5. 응답 형식: 선택한 카테고리의 코드만 정확히 입력하세요.\n");
        prompt.append("6. 예시: A0503 (구체적인 하위 카테고리)\n");
        prompt.append("7. 설명이나 추가 텍스트 없이 코드만 반환하세요.\n\n");
        
        prompt.append("선택한 카테고리 코드:");
        
        return prompt.toString();
    }
    
    /**
     * LLM 응답에서 카테고리 코드 추출
     */
    private String extractCategoryCodeFromLLMResponse(String llmResponse, 
                                                    List<HybridCategorySearchService.CategorySearchResult> candidates) {
        if (llmResponse == null || llmResponse.trim().isEmpty()) {
            return null;
        }
        
        String cleanResponse = llmResponse.trim().toUpperCase();
        
        // 후보 카테고리 코드들과 매칭
        for (HybridCategorySearchService.CategorySearchResult candidate : candidates) {
            String code = candidate.getCategory().getCode();
            if (cleanResponse.contains(code)) {
                return code;
            }
        }
        
        return null;
    }
    
    /**
     * CatalogNode를 Category DTO로 변환 (가변 계층 지원)
     */
    private Category convertToCategory(CatalogNode node) {
        // 계층 구조를 따라 올라가며 전체 경로 구성
        List<CatalogNode> hierarchyNodes = buildHierarchy(node);
        
        // 가변 계층 정보 생성
        List<Category.CategoryLevel> hierarchy = hierarchyNodes.stream()
            .map(n -> new Category.CategoryLevel(n.getLevel(), n.getCode(), n.getName()))
            .collect(java.util.stream.Collectors.toList());
        
        // 전체 코드와 이름 생성
        String fullCode = hierarchyNodes.stream()
            .map(CatalogNode::getCode)
            .collect(java.util.stream.Collectors.joining("-"));
        
        String fullName = hierarchyNodes.stream()
            .map(CatalogNode::getName)
            .collect(java.util.stream.Collectors.joining(" > "));
        
        // 가변 계층 생성자 사용
        Category category = new Category(fullCode, fullName, hierarchy);
        
        // 기존 3단계 호환성을 위한 추가 설정
        if (hierarchyNodes.size() >= 1) {
            category.setMajorCode(hierarchyNodes.get(0).getCode());
            category.setMajorName(hierarchyNodes.get(0).getName());
        }
        if (hierarchyNodes.size() >= 2) {
            category.setMidCode(hierarchyNodes.get(1).getCode());
            category.setMidName(hierarchyNodes.get(1).getName());
        }
        if (hierarchyNodes.size() >= 3) {
            category.setSubCode(hierarchyNodes.get(2).getCode());
            category.setSubName(hierarchyNodes.get(2).getName());
        } else if (hierarchyNodes.size() == 2) {
            // 2레벨인 경우 중분류를 소분류로도 사용
            category.setSubCode(hierarchyNodes.get(1).getCode());
            category.setSubName(hierarchyNodes.get(1).getName());
        } else if (hierarchyNodes.size() == 1) {
            // 1레벨인 경우 대분류를 모든 레벨로 사용
            category.setMidCode(hierarchyNodes.get(0).getCode());
            category.setMidName(hierarchyNodes.get(0).getName());
            category.setSubCode(hierarchyNodes.get(0).getCode());
            category.setSubName(hierarchyNodes.get(0).getName());
        }
        
        return category;
    }
    
    /**
     * 카테고리 계층 구조 구성
     */
    private List<CatalogNode> buildHierarchy(CatalogNode node) {
        List<CatalogNode> hierarchy = new ArrayList<>();
        CatalogNode current = node;
        
        // 현재 노드부터 루트까지 역순으로 수집
        while (current != null) {
            hierarchy.add(0, current); // 앞에 삽입하여 루트부터 순서 유지
            
            if (current.getParentCode() != null) {
                current = catalogNodeRepository.findByCode(current.getParentCode()).orElse(null);
            } else {
                break;
            }
        }
        
        return hierarchy;
    }
    
    /**
     * Category DTO에서 코드 추출
     */
    private String getCategoryCode(Category category) {
        // 가장 구체적인 코드 반환 (소분류 > 중분류 > 대분류)
        if (category.getSubCode() != null && !category.getSubCode().equals(category.getMidCode())) {
            return category.getSubCode();
        } else if (category.getMidCode() != null && !category.getMidCode().equals(category.getMajorCode())) {
            return category.getMidCode();
        } else {
            return category.getMajorCode();
        }
    }
    
    /**
     * 분류 결과 클래스
     */
    public static class ClassificationResult {
        private final Category selectedCategory;
        private final double confidence;
        private final String reason;
        private final List<HybridCategorySearchService.CategorySearchResult> candidates;
        
        public ClassificationResult(Category selectedCategory, double confidence, String reason,
                                  List<HybridCategorySearchService.CategorySearchResult> candidates) {
            this.selectedCategory = selectedCategory;
            this.confidence = confidence;
            this.reason = reason;
            this.candidates = candidates;
        }
        
        public Category getSelectedCategory() { return selectedCategory; }
        public double getConfidence() { return confidence; }
        public String getReason() { return reason; }
        public List<HybridCategorySearchService.CategorySearchResult> getCandidates() { return candidates; }
        
        public boolean isSuccessful() { return selectedCategory != null; }
    }
}
