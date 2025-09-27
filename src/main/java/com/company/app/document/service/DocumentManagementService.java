package com.company.app.document.service;

import com.company.app.document.repository.DocumentRepository;
import com.company.app.document.repository.DocumentChunkRepository;
import com.company.app.search.service.ElasticsearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentManagementService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final ElasticsearchIndexService elasticsearchIndexService;

    @Transactional
    public boolean deleteDocumentAndChunks(String documentId) {
        return documentRepository.findById(documentId).map(doc -> {
            log.info("문서 삭제 시작: {}", documentId);
            // 1. 청크 삭제 (Mongo)
            chunkRepository.deleteByDocumentId(documentId);
            // 2. ES 문서 제거
            elasticsearchIndexService.deleteDocumentFromIndex(documentId);
            // 3. ES 청크 제거 (delete_by_query 추후 구현)
            elasticsearchIndexService.deleteChunksByDocumentId(documentId);
            // 4. 원본 문서 삭제
            documentRepository.deleteById(documentId);
            log.info("문서 및 청크 삭제 완료: {}", documentId);
            return true; }).orElse(false);
    }
}
