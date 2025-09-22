package com.company.app.ingest.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 의미 기반 문서 청킹.
 * 1) 제목/섹션 헤더(번호, 마크다운 스타일, 한글/영문 구분자) 기준 1차 분할
 * 2) 각 섹션을 토큰 길이(기본 500 토큰 상당) 단위로 의미 경계 문장 끝에서 재분할
 * 간단화를 위해 토큰 수는 문자 길이/4 로 근사.
 */
@Slf4j
@Component
public class SemanticChunker {

    private static final int DEFAULT_MAX_TOKENS = 500;        // 목표 최대 토큰
    private static final int MIN_SECTION_TOKENS = 120;        // 너무 짧은 섹션 병합 기준

    private static final Pattern SECTION_HEADER = Pattern.compile(
            "^(?:#{1,4}\\s+|[0-9]{1,2}\\.|[0-9]{1,2}\\)[)\\s]|[가-힣A-Za-z]{2,20}[:：]\s).*$",
            Pattern.MULTILINE);

    public record Chunk(String text, String sectionHint) {}

    public List<Chunk> chunk(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        // 1차: 섹션 헤더 추출
        List<Section> sections = splitBySection(raw);
        // 2차: 섹션별 길이 기반 재분할
        List<Chunk> results = new ArrayList<>();
        for (Section sec : sections) {
            results.addAll(splitSection(sec));
        }
        return results;
    }

    private List<Section> splitBySection(String text) {
        List<Section> list = new ArrayList<>();
        Matcher m = SECTION_HEADER.matcher(text);
        int lastStart = 0;
        String lastHeader = "ROOT";
        while (m.find()) {
            int start = m.start();
            if (start != 0) {
                String prev = text.substring(lastStart, start).trim();
                if (!prev.isEmpty()) {
                    list.add(new Section(lastHeader, prev));
                }
            }
            lastStart = start;
            lastHeader = m.group().strip().replaceAll("[#0-9.()]+", "").trim();
        }
        // tail
        String tail = text.substring(lastStart).trim();
        if (!tail.isEmpty()) list.add(new Section(lastHeader, tail));
        if (list.isEmpty()) list.add(new Section("FULL", text));
        return mergeShortSections(list);
    }

    private List<Section> mergeShortSections(List<Section> sections) {
        if (sections.size() < 2) return sections;
        List<Section> merged = new ArrayList<>();
        Section buffer = null;
        for (Section s : sections) {
            if (buffer == null) {
                buffer = s;
                continue;
            }
            if (approxTokens(buffer.content) < MIN_SECTION_TOKENS) {
                buffer = new Section(buffer.header + "/" + s.header, buffer.content + "\n" + s.content);
            } else {
                merged.add(buffer);
                buffer = s;
            }
        }
        if (buffer != null) merged.add(buffer);
        return merged;
    }

    private List<Chunk> splitSection(Section section) {
        List<Chunk> chunks = new ArrayList<>();
        String[] sentences = section.content.split("(?<=[.!?。！？])\\s+");
        StringBuilder buf = new StringBuilder();
        for (String sent : sentences) {
            if (sent.isBlank()) continue;
            if (approxTokens(buf) + approxTokens(sent) > DEFAULT_MAX_TOKENS && buf.length() > 0) {
                chunks.add(new Chunk(buf.toString().trim(), section.header));
                buf.setLength(0);
            }
            buf.append(sent.trim()).append(' ');
        }
        if (buf.length() > 0) {
            chunks.add(new Chunk(buf.toString().trim(), section.header));
        }
        return chunks;
    }

    private int approxTokens(CharSequence cs) { return cs.length() / 4; }

    private record Section(String header, String content) {}
}
