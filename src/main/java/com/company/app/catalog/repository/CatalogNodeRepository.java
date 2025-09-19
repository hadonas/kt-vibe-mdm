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
    
    // 새로운 분류 시스템용 메서드들
    
    /**
     * 활성 카테고리 조회
     */
    List<CatalogNode> findByActiveTrue();
    
    /**
     * 코드로 카테고리 조회
     */
    Optional<CatalogNode> findByCode(String code);
    
    /**
     * 임베딩 벡터가 있는 활성 카테고리 조회
     */
    @Query("{ 'active': true, 'vector': { $exists: true, $ne: null, $not: { $size: 0 } } }")
    List<CatalogNode> findByActiveTrueAndVectorNotNull();
    
    /**
     * 텍스트 검색 (이름, 설명, 동의어, 예시문장)
     */
    @Query("{ $text: { $search: ?0 }, 'active': true }")
    List<CatalogNode> findByTextSearch(String searchText);
    
    /**
     * 키워드로 카테고리 검색
     */
    @Query("{ $or: [ " +
           "{ 'includeKeywords': { $in: ?0 } }, " +
           "{ 'aliases': { $in: ?0 } }, " +
           "{ 'name': { $regex: ?1, $options: 'i' } } " +
           "], 'active': true }")
    List<CatalogNode> findByKeywords(List<String> keywords, String namePattern);
    
    /**
     * 레벨과 부모 코드로 카테고리 조회
     */
    @Query("{ 'level': ?0, 'parentCode': ?1, 'active': true }")
    List<CatalogNode> findByLevelAndParentCodeAndActiveTrue(Integer level, String parentCode);
    
    /**
     * 레벨로 카테고리 조회
     */
    @Query("{ 'level': ?0, 'active': true }")
    List<CatalogNode> findByLevelAndActiveTrue(Integer level);
    
    /**
     * 부모 코드로 카테고리 조회
     */
    @Query("{ 'parentCode': ?0, 'active': true }")
    List<CatalogNode> findByParentCodeAndActiveTrue(String parentCode);
    
    /**
     * 특정 레벨의 카테고리 조회
     */
    @Query("{ 'level': { $gte: ?0 }, 'active': true }")
    List<CatalogNode> findByLevelGreaterThanEqualAndActiveTrue(int minLevel);
    
    /**
     * 최대 레벨 조회
     */
    @Query(value = "{}", sort = "{ 'level': -1 }")
    Optional<CatalogNode> findTopByOrderByLevelDesc();
}
