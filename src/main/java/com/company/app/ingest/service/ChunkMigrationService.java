package com.company.app.ingest.service;

import com.company.app.document.entity.DocumentChunk;
import com.company.app.document.entity.DocumentEntity;
import com.company.app.document.repository.DocumentChunkRepository;
import com.company.app.document.repository.DocumentRepository;
import com.company.app.search.service.ElasticsearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 기존 문서들을 의미 기반 청킹으로 재처리하는 마이그레이션 러너.
 * spring.semantic-chunk.migrate-on-start=true 일 때 한 번 실행.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChunkMigrationService implements ApplicationRunner {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final ChunkIngestionService chunkIngestionService;
    private final ElasticsearchIndexService elasticsearchIndexService;

    @Value("${spring.semantic-chunk.enabled:true}")
    private boolean semanticChunkEnabled;

    @Value("${spring.semantic-chunk.migrate-on-start:false}")
    private boolean migrateOnStart;

    @Override
    public void run(ApplicationArguments args) {
        if (!semanticChunkEnabled || !migrateOnStart) {
            return;
        }
        runMigration();
    }

    /**
     * 수동으로 마이그레이션 실행
     */
    public void runMigration() {
        log.info("[ChunkMigration] 시작");
        List<DocumentEntity> docs = documentRepository.findAll();
        int processed = 0;
        for (DocumentEntity d : docs) {
            try {
                long existing = chunkRepository.countByDocumentId(d.getId());
                if (existing > 0) {
                    log.debug("문서 {}는 이미 청킹됨, 스킵", d.getId());
                    continue; // 이미 처리됨
                }
                log.info("문서 {} 청킹 시작", d.getId());
                List<DocumentChunk> chunks = chunkIngestionService.chunkAndPersist(d);
                elasticsearchIndexService.indexChunks(chunks);
                processed++;
                log.info("문서 {} 청킹 완료, {}개 청크 생성", d.getId(), chunks.size());
            } catch (Exception e) {
                log.error("[ChunkMigration] 실패 doc={}", d.getId(), e);
            }
        }
        log.info("[ChunkMigration] 완료 processed={} total={}", processed, docs.size());
    }
}
