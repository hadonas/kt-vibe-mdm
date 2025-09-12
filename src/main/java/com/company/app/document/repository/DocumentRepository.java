package com.company.app.document.repository;

import com.company.app.document.entity.DocumentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends MongoRepository<DocumentEntity, String> {
    
    Optional<DocumentEntity> findBySerialFull(String serialFull);
    
    List<DocumentEntity> findByOwnerId(String ownerId);
    
    Page<DocumentEntity> findByOwnerId(String ownerId, Pageable pageable);
    
    @Query("{'acl.ownerId': ?0}")
    List<DocumentEntity> findByOwnerIdOrShared(String ownerId);
    
    @Query("{'acl.shared.userId': ?0}")
    List<DocumentEntity> findBySharedUserId(String userId);
    
    @Query("{'category.subCode': ?0}")
    List<DocumentEntity> findBySubCode(String subCode);
    
    @Query("{'purpose': {$regex: ?0, $options: 'i'}}")
    List<DocumentEntity> findByPurposeContaining(String purpose);
    
    @Query("{'purpose': {$regex: ?0, $options: 'i'}}")
    Page<DocumentEntity> findByPurposeContainingIgnoreCase(String purpose, Pageable pageable);
    
    @Query("{'content': {$regex: ?0, $options: 'i'}}")
    Page<DocumentEntity> findByContentContainingIgnoreCase(String content, Pageable pageable);
    
    @Query("{'tags': {$regex: ?0, $options: 'i'}}")
    Page<DocumentEntity> findByTagsContainingIgnoreCase(String tag, Pageable pageable);
    
    @Query("{'tags': {$in: ?0}}")
    List<DocumentEntity> findByTagsIn(List<String> tags);
    
    @Query("{'vectors.purpose_768': {$exists: true}}")
    List<DocumentEntity> findDocumentsWithVectors();
    
    @Query("{'vectors.purpose_768': {$exists: false}}")
    List<DocumentEntity> findDocumentsWithoutVectors();
}
