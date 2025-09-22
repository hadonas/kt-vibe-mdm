package com.company.app.ingest.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class SemanticChunkerTest {
    private final SemanticChunker chunker = new SemanticChunker();

    @Test
    void chunk_basic() {
        String text = "# 제목\n이 문서는 테스트 입니다. 의미 기반 청킹을 검증합니다. 두번째 문장입니다." +
                " 세번째 문장입니다. 네번째 문장입니다.";
        var chunks = chunker.chunk(text);
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).text().length()).isGreaterThan(10);
    }
}
