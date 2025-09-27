package com.company.app.ingest.service;

import com.company.app.document.entity.DocumentChunk;
import com.company.app.document.entity.DocumentEntity;
import com.company.app.document.repository.DocumentChunkRepository;
import com.company.app.search.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 문서 의미 기반 청킹 및 벡터 임베딩 저장 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkIngestionService {

    private final SemanticChunker semanticChunker;
    private final EmbeddingService embeddingService;
    private final DocumentChunkRepository chunkRepository;

    /**
     * DocumentEntity 의 content 를 의미 기반 청킹하고 MongoDB + (향후 ES) 색인 준비.
     */
    @Transactional
    public List<DocumentChunk> chunkAndPersist(DocumentEntity document) {
        if (document.getContent() == null || document.getContent().isBlank()) {
            log.warn("문서 내용 비어있어 청킹 생략: {}", document.getId());
            return List.of();
        }
        long start = System.currentTimeMillis();
        var raw = document.getContent();
        var chunks = semanticChunker.chunk(raw);
        List<String> texts = chunks.stream().map(SemanticChunker.Chunk::text).toList();
        // 배치 임베딩 생성
        List<List<Double>> embeddings = embeddingService.generateEmbeddings(texts);
        List<DocumentChunk> entities = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            var c = chunks.get(i);
            DocumentChunk dc = new DocumentChunk();
            dc.setDocumentId(document.getId());
            dc.setChunkIndex(i);
            dc.setContent(c.text());
            dc.setTokenCount(c.text().length() / 4); // 근사
            dc.setEmbedding(embeddings.get(i));
            dc.setSectionHint(c.sectionHint());
            dc.setCreatedAt(LocalDateTime.now());
            entities.add(dc);
        }
        chunkRepository.saveAll(entities);
        log.info("문서 청킹 완료 doc={} chunks={} in {}ms", document.getId(), entities.size(), (System.currentTimeMillis()-start));
        return entities;
    }
}
