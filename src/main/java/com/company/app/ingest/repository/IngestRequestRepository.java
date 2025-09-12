package com.company.app.ingest.repository;

import com.company.app.ingest.entity.IngestRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface IngestRequestRepository extends MongoRepository<IngestRequest, String> {
    
    List<IngestRequest> findByStatus(IngestRequest.Status status);
    
    Page<IngestRequest> findByStatus(IngestRequest.Status status, Pageable pageable);
    
    List<IngestRequest> findByOwnerId(String ownerId);
    
    Page<IngestRequest> findByOwnerId(String ownerId, Pageable pageable);
    
    @Query("{'ownerId': ?0, 'status': ?1}")
    Page<IngestRequest> findByOwnerIdAndStatus(String ownerId, IngestRequest.Status status, Pageable pageable);
    
    @Query("{'status': ?0, 'requestedAt': {'$gte': ?1, '$lte': ?2}}")
    List<IngestRequest> findByStatusAndRequestedAtBetween(
            IngestRequest.Status status, 
            LocalDateTime start, 
            LocalDateTime end);
    
    @Query("{'ownerId': ?0, 'status': ?1}")
    List<IngestRequest> findByOwnerIdAndStatus(String ownerId, IngestRequest.Status status);
}
