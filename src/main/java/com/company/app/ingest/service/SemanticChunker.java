package com.company.app.ingest.service;

import com.company.app.search.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 진짜 시맨틱 청킹 서비스
 * - 문장별 임베딩 생성 및 코사인 유사도 계산
 * - 유사도 기반 문장 그룹핑
 * - 표, 리스트, 코드 블록 등 구조적 요소 보존
 * - 동적 유사도 임계값 조정
 * - 토큰 수 기반 청크 크기 제한
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SemanticChunker {

    private final TokenCounter tokenCounter;
    private final ChunkQualityValidator qualityValidator;
    private final EmbeddingService embeddingService;

    // 기본 설정값들
    private static final int DEFAULT_MAX_TOKENS = 500;
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.75;
    private static final int MAX_SINGLE_BLOCK_TOKENS = 800;
    private static final int MIN_SENTENCE_LENGTH = 10;

    // 구조적 요소 패턴들
    private static final Pattern TABLE_PATTERN = Pattern.compile(
        "(?:" +
        "\\|[^\\n]*\\|[\\s]*\\n[\\s]*\\|[-\\s|:]*\\|" +  // 마크다운 테이블
        "|(?:^[^\\n]*\\t[^\\n]*\\t[^\\n]*$)" +            // 탭 구분 테이블
        "|(?:^\\s*(?:[^\\n]*\\s{3,}){2,}[^\\n]*$)" +     // 공백 구분 테이블
        ")", Pattern.MULTILINE
    );

    private static final Pattern LIST_PATTERN = Pattern.compile(
        "(?:" +
        "^\\s*(?:[-*+•◦▪▫]|\\d+[.)]|[가-힣][.)]|[a-zA-Z][.)])\\s+" + // 불릿/번호 리스트
        "|^\\s*(?:□|☐|■|☑|✓)\\s+" +                                    // 체크박스
        ")", Pattern.MULTILINE
    );

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
        "(?:```[\\s\\S]*?```|`[^`\\n]+`|(?:^    [^\\n]*\\n)+)", Pattern.MULTILINE
    );

    private static final Pattern HEADER_PATTERN = Pattern.compile(
        "^\\s*(?:" +
        "(?:\\d+(?:\\.\\d+)*[.))]\\s*)" +         // 1.2.3) 또는 1.2.3.
        "|(?:[IVX]+[.))]\\s*)" +                  // I), II), III)
        "|(?:[가-힣]+[.))]\\s*)" +                // 가), 나), 다)
        "|(?:#{1,6}\\s+)" +                       // # 마크다운 헤더
        "|(?:={3,}|[-]{3,})" +                    // === 또는 ---
        ")", Pattern.MULTILINE
    );

    // 문장 분리 패턴 (개선된 버전)
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile(
        "(?<=[.!?。！？])\\s+(?=[A-Z가-힣\"'\\(\\[])|" +     // 일반 문장 끝
        "(?<=[.!?。！？]\\s)\\n+|" +                          // 문장 끝 + 개행
        "(?<=\\n)\\s*(?=\\d+[.))]\\s)|" +                     // 번호 목록 앞
        "(?<=\\n)\\s*(?=[가-힣][.))]\\s)|" +                  // 한글 번호 앞
        "(?<=\\n)\\s*(?=[-*+•]\\s)"                           // 불릿 포인트 앞
    );

    public enum DocumentType {
        DOCX, PDF, MARKDOWN, TEXT
    }

    public record Chunk(String text, String sectionHint, DocumentStructure structure) {}
    
    public record DocumentStructure(
        int headerLevel,
        String contentType,
        boolean isStructuralBlock,
        Map<String, Object> metadata
    ) {}

    public record SemanticSegment(
        String text,
        List<Double> embedding,
        String type,  // TEXT, TABLE, LIST, CODE, HEADER
        boolean isStructural,
        int tokenCount
    ) {}

    /**
     * 메인 청킹 메서드 - 시맨틱 유사도 기반
     */
    public List<String> chunkText(String text) {
        return chunkText(text, DocumentType.TEXT, DEFAULT_MAX_TOKENS, DEFAULT_SIMILARITY_THRESHOLD, true);
    }

    public List<String> chunkText(String text, DocumentType docType) {
        return chunkText(text, docType, DEFAULT_MAX_TOKENS, DEFAULT_SIMILARITY_THRESHOLD, true);
    }

    public List<String> chunkText(String text, boolean withQualityValidation) {
        return chunkText(text, DocumentType.TEXT, DEFAULT_MAX_TOKENS, DEFAULT_SIMILARITY_THRESHOLD, withQualityValidation);
    }

    /**
     * 완전한 시맨틱 청킹 - 임베딩 기반 유사도 계산
     */
    public List<String> chunkText(String text, DocumentType docType, int maxTokens, 
                                double similarityThreshold, boolean withQualityValidation) {
        
        log.info("시맨틱 청킹 시작: {} 문자, 타입: {}, 최대 {}토큰, 유사도임계값: {:.3f}", 
                text.length(), docType, maxTokens, similarityThreshold);

        if (text == null || text.trim().isEmpty()) {
            return List.of();
        }

        try {
            // 1. 구조적 요소 및 문장 분리
            List<SemanticSegment> segments = extractSemanticSegments(text, docType);
            log.info("세그먼트 추출 완료: {}개 (구조적 요소 포함)", segments.size());

            // 2. 임베딩 생성 (배치 처리로 효율성 향상)
            List<SemanticSegment> embeddedSegments = generateEmbeddingsForSegments(segments);
            log.info("임베딩 생성 완료: {}개 세그먼트", embeddedSegments.size());

            // 3. 유사도 기반 청킹
            List<List<SemanticSegment>> semanticChunks = performSemanticClustering(
                embeddedSegments, maxTokens, similarityThreshold);
            log.info("시맨틱 클러스터링 완료: {}개 청크", semanticChunks.size());

            // 4. 청크를 텍스트로 변환
            List<String> chunkTexts = semanticChunks.stream()
                .map(this::mergeSegmentsToText)
                .collect(Collectors.toList());

            if (!withQualityValidation) {
                return chunkTexts;
            }

            // 5. 품질 검증 및 최적화
            ChunkQualityValidator.ChunkValidationResult result = 
                qualityValidator.validateAndOptimizeChunks(chunkTexts, text);

            log.info("시맨틱 청킹 완료: 총 {}개 청크, 평균 품질 {:.3f}", 
                    result.validatedChunks().size(), result.statistics().averageQuality());

            return result.validatedChunks().stream()
                .map(ChunkQualityValidator.ProcessedChunk::content)
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("시맨틱 청킹 중 오류 발생, 기본 청킹으로 폴백", e);
            return fallbackToBasicChunking(text, maxTokens);
        }
    }

    /**
     * 구조적 요소 및 문장 단위로 세그먼트 추출
     */
    private List<SemanticSegment> extractSemanticSegments(String text, DocumentType docType) {
        List<SemanticSegment> segments = new ArrayList<>();
        
        // 구조적 요소들을 먼저 식별하고 위치 정보 수집
        List<StructuralElement> structuralElements = identifyStructuralElements(text);
        
        // 구조적 요소들을 위치순 정렬
        structuralElements.sort(Comparator.comparingInt(StructuralElement::start));
        
        int lastEnd = 0;
        for (StructuralElement element : structuralElements) {
            // 이전 구조적 요소와 현재 요소 사이의 텍스트를 문장 단위로 분리
            if (element.start() > lastEnd) {
                String betweenText = text.substring(lastEnd, element.start()).trim();
                if (!betweenText.isEmpty()) {
                    segments.addAll(extractSentenceSegments(betweenText));
                }
            }
            
            // 현재 구조적 요소 추가
            segments.add(new SemanticSegment(
                element.content(),
                null, // 임베딩은 나중에 생성
                element.type(),
                true,
                tokenCounter.countTokens(element.content())
            ));
            
            lastEnd = element.end();
        }
        
        // 마지막 구조적 요소 이후 남은 텍스트 처리
        if (lastEnd < text.length()) {
            String remainingText = text.substring(lastEnd).trim();
            if (!remainingText.isEmpty()) {
                segments.addAll(extractSentenceSegments(remainingText));
            }
        }
        
        return segments;
    }

    /**
     * 구조적 요소 식별 (표, 리스트, 코드, 헤더)
     */
    private List<StructuralElement> identifyStructuralElements(String text) {
        List<StructuralElement> elements = new ArrayList<>();
        
        // 표 감지
        Matcher tableMatcher = TABLE_PATTERN.matcher(text);
        while (tableMatcher.find()) {
            elements.add(new StructuralElement(
                tableMatcher.start(), tableMatcher.end(), 
                tableMatcher.group(), "TABLE"
            ));
        }
        
        // 코드 블록 감지
        Matcher codeMatcher = CODE_BLOCK_PATTERN.matcher(text);
        while (codeMatcher.find()) {
            elements.add(new StructuralElement(
                codeMatcher.start(), codeMatcher.end(),
                codeMatcher.group(), "CODE"
            ));
        }
        
        // 리스트 감지 (연속된 리스트 항목들을 하나로 묶기)
        elements.addAll(identifyListBlocks(text));
        
        // 헤더 감지
        Matcher headerMatcher = HEADER_PATTERN.matcher(text);
        while (headerMatcher.find()) {
            elements.add(new StructuralElement(
                headerMatcher.start(), headerMatcher.end(),
                headerMatcher.group(), "HEADER"
            ));
        }
        
        return elements;
    }

    /**
     * 리스트 블록 식별 (연속된 리스트 항목들을 하나로 묶기)
     */
    private List<StructuralElement> identifyListBlocks(String text) {
        List<StructuralElement> listElements = new ArrayList<>();
        String[] lines = text.split("\n");
        
        StringBuilder currentList = new StringBuilder();
        int listStartPos = -1;
        boolean inList = false;
        int currentPos = 0;
        
        for (String line : lines) {
            if (LIST_PATTERN.matcher(line).find()) {
                if (!inList) {
                    listStartPos = text.indexOf(line, currentPos);
                    inList = true;
                }
                currentList.append(line).append("\n");
            } else if (inList && (line.trim().isEmpty() || line.startsWith("  ") || line.startsWith("\t"))) {
                // 리스트 항목의 연속 또는 들여쓰기된 내용
                currentList.append(line).append("\n");
            } else if (inList) {
                // 리스트 종료
                String listContent = currentList.toString().trim();
                listElements.add(new StructuralElement(
                    listStartPos, listStartPos + listContent.length(),
                    listContent, "LIST"
                ));
                currentList = new StringBuilder();
                inList = false;
            }
            
            currentPos = text.indexOf(line, currentPos) + line.length() + 1;
        }
        
        // 마지막 리스트 처리
        if (inList && currentList.length() > 0) {
            String listContent = currentList.toString().trim();
            listElements.add(new StructuralElement(
                listStartPos, listStartPos + listContent.length(),
                listContent, "LIST"
            ));
        }
        
        return listElements;
    }

    /**
     * 일반 텍스트를 문장 단위로 분리
     */
    private List<SemanticSegment> extractSentenceSegments(String text) {
        List<SemanticSegment> segments = new ArrayList<>();
        
        // 문장 분리
        String[] sentences = SENTENCE_BOUNDARY.split(text);
        
        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.length() >= MIN_SENTENCE_LENGTH) {
                segments.add(new SemanticSegment(
                    sentence,
                    null, // 임베딩은 나중에 생성
                    "TEXT",
                    false,
                    tokenCounter.countTokens(sentence)
                ));
            }
        }
        
        // 분리되지 않은 긴 텍스트 처리
        if (segments.isEmpty() && text.length() >= MIN_SENTENCE_LENGTH) {
            segments.add(new SemanticSegment(
                text,
                null,
                "TEXT",
                false,
                tokenCounter.countTokens(text)
            ));
        }
        
        return segments;
    }

    /**
     * 세그먼트들에 대한 임베딩 생성 (배치 처리)
     */
    private List<SemanticSegment> generateEmbeddingsForSegments(List<SemanticSegment> segments) {
        // 임베딩이 필요한 텍스트들 추출
        List<String> textsToEmbed = segments.stream()
            .map(SemanticSegment::text)
            .collect(Collectors.toList());
        
        // 배치로 임베딩 생성
        List<List<Double>> embeddings = embeddingService.generateEmbeddings(textsToEmbed);
        
        // 임베딩을 세그먼트에 할당
        List<SemanticSegment> embeddedSegments = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            SemanticSegment original = segments.get(i);
            embeddedSegments.add(new SemanticSegment(
                original.text(),
                embeddings.get(i),
                original.type(),
                original.isStructural(),
                original.tokenCount()
            ));
        }
        
        return embeddedSegments;
    }

    /**
     * 시맨틱 유사도 기반 클러스터링
     */
    private List<List<SemanticSegment>> performSemanticClustering(
            List<SemanticSegment> segments, int maxTokens, double similarityThreshold) {
        
        List<List<SemanticSegment>> clusters = new ArrayList<>();
        List<SemanticSegment> currentCluster = new ArrayList<>();
        int currentTokens = 0;
        
        for (int i = 0; i < segments.size(); i++) {
            SemanticSegment currentSegment = segments.get(i);
            
            // 구조적 요소는 특별 처리
            if (currentSegment.isStructural()) {
                // 현재 클러스터가 있으면 먼저 완성
                if (!currentCluster.isEmpty()) {
                    clusters.add(new ArrayList<>(currentCluster));
                    currentCluster.clear();
                    currentTokens = 0;
                }
                
                // 구조적 요소 크기 확인
                if (currentSegment.tokenCount() <= MAX_SINGLE_BLOCK_TOKENS) {
                    // 단독 클러스터로 추가
                    clusters.add(List.of(currentSegment));
                } else {
                    // 너무 큰 구조적 요소는 분할
                    clusters.addAll(splitLargeStructuralElement(currentSegment, maxTokens));
                }
                continue;
            }
            
            // 첫 번째 세그먼트이거나 현재 클러스터가 비어있는 경우
            if (currentCluster.isEmpty()) {
                currentCluster.add(currentSegment);
                currentTokens = currentSegment.tokenCount();
                continue;
            }
            
            // 토큰 수 제한 확인
            int newTokenCount = currentTokens + currentSegment.tokenCount();
            if (newTokenCount > maxTokens) {
                // 현재 클러스터 완성하고 새 클러스터 시작
                clusters.add(new ArrayList<>(currentCluster));
                currentCluster.clear();
                currentCluster.add(currentSegment);
                currentTokens = currentSegment.tokenCount();
                continue;
            }
            
            // 시맨틱 유사도 계산
            double maxSimilarity = calculateMaxSimilarityWithCluster(currentSegment, currentCluster);
            
            if (maxSimilarity >= similarityThreshold) {
                // 유사도가 높으면 현재 클러스터에 추가
                currentCluster.add(currentSegment);
                currentTokens = newTokenCount;
            } else {
                // 유사도가 낮으면 현재 클러스터 완성하고 새 클러스터 시작
                clusters.add(new ArrayList<>(currentCluster));
                currentCluster.clear();
                currentCluster.add(currentSegment);
                currentTokens = currentSegment.tokenCount();
            }
        }
        
        // 마지막 클러스터 추가
        if (!currentCluster.isEmpty()) {
            clusters.add(currentCluster);
        }
        
        return clusters;
    }

    /**
     * 세그먼트와 클러스터 내 세그먼트들 간의 최대 유사도 계산
     */
    private double calculateMaxSimilarityWithCluster(SemanticSegment segment, List<SemanticSegment> cluster) {
        double maxSimilarity = 0.0;
        
        for (SemanticSegment clusterSegment : cluster) {
            if (!clusterSegment.isStructural() && segment.embedding() != null && clusterSegment.embedding() != null) {
                double similarity = embeddingService.cosineSimilarity(segment.embedding(), clusterSegment.embedding());
                maxSimilarity = Math.max(maxSimilarity, similarity);
            }
        }
        
        return maxSimilarity;
    }

    /**
     * 큰 구조적 요소 분할
     */
    private List<List<SemanticSegment>> splitLargeStructuralElement(SemanticSegment element, int maxTokens) {
        List<List<SemanticSegment>> parts = new ArrayList<>();
        
        if (element.type().equals("TABLE")) {
            // 테이블은 행 단위로 분할
            parts.addAll(splitTableByRows(element, maxTokens));
        } else if (element.type().equals("LIST")) {
            // 리스트는 항목 단위로 분할
            parts.addAll(splitListByItems(element, maxTokens));
        } else {
            // 기타는 단순 텍스트 분할
            parts.add(List.of(element));
        }
        
        return parts;
    }

    /**
     * 테이블 행 단위 분할
     */
    private List<List<SemanticSegment>> splitTableByRows(SemanticSegment tableSegment, int maxTokens) {
        List<List<SemanticSegment>> parts = new ArrayList<>();
        String[] rows = tableSegment.text().split("\n");
        
        StringBuilder currentPart = new StringBuilder();
        int currentTokens = 0;
        
        for (String row : rows) {
            int rowTokens = tokenCounter.countTokens(row);
            
            if (currentTokens + rowTokens > maxTokens && currentPart.length() > 0) {
                // 현재 부분 완성
                parts.add(List.of(new SemanticSegment(
                    currentPart.toString().trim(),
                    null,
                    "TABLE_PART",
                    true,
                    currentTokens
                )));
                currentPart = new StringBuilder(row + "\n");
                currentTokens = rowTokens;
            } else {
                currentPart.append(row).append("\n");
                currentTokens += rowTokens;
            }
        }
        
        if (currentPart.length() > 0) {
            parts.add(List.of(new SemanticSegment(
                currentPart.toString().trim(),
                null,
                "TABLE_PART",
                true,
                currentTokens
            )));
        }
        
        return parts;
    }

    /**
     * 리스트 항목 단위 분할
     */
    private List<List<SemanticSegment>> splitListByItems(SemanticSegment listSegment, int maxTokens) {
        List<List<SemanticSegment>> parts = new ArrayList<>();
        String[] lines = listSegment.text().split("\n");
        
        StringBuilder currentPart = new StringBuilder();
        int currentTokens = 0;
        
        for (String line : lines) {
            int lineTokens = tokenCounter.countTokens(line);
            
            if (currentTokens + lineTokens > maxTokens && currentPart.length() > 0) {
                parts.add(List.of(new SemanticSegment(
                    currentPart.toString().trim(),
                    null,
                    "LIST_PART",
                    true,
                    currentTokens
                )));
                currentPart = new StringBuilder(line + "\n");
                currentTokens = lineTokens;
            } else {
                currentPart.append(line).append("\n");
                currentTokens += lineTokens;
            }
        }
        
        if (currentPart.length() > 0) {
            parts.add(List.of(new SemanticSegment(
                currentPart.toString().trim(),
                null,
                "LIST_PART",
                true,
                currentTokens
            )));
        }
        
        return parts;
    }

    /**
     * 세그먼트들을 하나의 텍스트로 병합
     */
    private String mergeSegmentsToText(List<SemanticSegment> segments) {
        return segments.stream()
            .map(SemanticSegment::text)
            .collect(Collectors.joining("\n\n"));
    }

    /**
     * 오류 시 기본 청킹으로 폴백
     */
    private List<String> fallbackToBasicChunking(String text, int maxTokens) {
        log.warn("기본 청킹으로 폴백");
        
        List<String> chunks = new ArrayList<>();
        String[] sentences = SENTENCE_BOUNDARY.split(text);
        
        StringBuilder currentChunk = new StringBuilder();
        int currentTokens = 0;
        
        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty()) continue;
            
            int sentenceTokens = tokenCounter.countTokens(sentence);
            
            if (currentTokens + sentenceTokens > maxTokens && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder(sentence);
                currentTokens = sentenceTokens;
            } else {
                if (currentChunk.length() > 0) {
                    currentChunk.append(" ");
                }
                currentChunk.append(sentence);
                currentTokens += sentenceTokens;
            }
        }
        
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }
        
        return chunks;
    }

    /**
     * 레거시 지원을 위한 메서드
     */
    public List<Chunk> chunk(String raw) {
        List<String> chunkTexts = chunkText(raw, DocumentType.TEXT);
        return chunkTexts.stream()
            .map(text -> new Chunk(
                text, 
                "SEMANTIC",
                new DocumentStructure(0, "TEXT", false, Map.of())
            ))
            .collect(Collectors.toList());
    }

    // 헬퍼 클래스들
    private record StructuralElement(int start, int end, String content, String type) {}
}
