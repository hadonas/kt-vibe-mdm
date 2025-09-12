package com.company.app.catalog.repository;

import com.company.app.catalog.entity.CatalogNode;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CatalogNodeRepository extends MongoRepository<CatalogNode, String> {
    
    List<CatalogNode> findByLevelAndActiveTrueOrderByOrder(Integer level);
    
    List<CatalogNode> findByParentCodeAndActiveTrueOrderByOrder(String parentCode);
    
    @Query("{'level': 1, 'active': true}")
    List<CatalogNode> findMajorCategories();
    
    @Query("{'level': 2, 'parentCode': ?0, 'active': true}")
    List<CatalogNode> findMidCategoriesByParentCode(String parentCode);
    
    @Query("{'level': 3, 'parentCode': ?0, 'active': true}")
    List<CatalogNode> findSubCategoriesByParentCode(String parentCode);
    
    Optional<CatalogNode> findByCodeAndActiveTrue(String code);
    
    @Query("{'code': ?0, 'level': ?1, 'active': true}")
    Optional<CatalogNode> findByCodeAndLevelAndActiveTrue(String code, Integer level);
}
