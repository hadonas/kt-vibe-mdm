package com.company.app.document.repository;

import com.company.app.document.entity.DocumentChunk;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends MongoRepository<DocumentChunk, String> {
    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(String documentId);

    @Query(value = "{ 'documentId': ?0 }", count = true)
    long countByDocumentId(String documentId);

    void deleteByDocumentId(String documentId);
}
