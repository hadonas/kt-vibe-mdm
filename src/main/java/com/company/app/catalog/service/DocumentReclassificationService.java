package com.company.app.catalog.service;

import com.company.app.catalog.repository.CatalogNodeRepository;
import com.company.app.common.dto.Category;
import com.company.app.common.dto.Serial;
import com.company.app.document.entity.DocumentEntity;
import com.company.app.document.repository.DocumentRepository;
import com.company.app.file.service.LocalFileStorageService;
import com.company.app.ingest.service.RepositoryAnalysisService;
import com.company.app.search.service.ElasticsearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 기존 문서 스마트 재분류 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentReclassificationService {
    
    private final DocumentRepository documentRepository;
    private final SmartClassificationService smartClassificationService;
    private final LocalFileStorageService fileStorageService;
    @SuppressWarnings("unused")
    private final CatalogNodeRepository catalogNodeRepository;
    private final RepositoryAnalysisService repositoryAnalysisService;
    private final ElasticsearchIndexService elasticsearchIndexService;
    
    /**
     * 모든 문서를 스마트 분류로 재분류
     */
    public ReclassificationResult reclassifyAllDocuments() {
        try {
            log.info("모든 문서 스마트 재분류 시작");
            
            // 전체 문서 수 조회
            long totalDocuments = documentRepository.count();
            log.info("재분류 대상 문서 수: {}개", totalDocuments);
            
            ReclassificationResult result = new ReclassificationResult();
            result.setTotalDocuments((int) totalDocuments);
            result.setStartTime(LocalDateTime.now());
            
            AtomicInteger processedCount = new AtomicInteger(0);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            AtomicInteger unchangedCount = new AtomicInteger(0);
            List<String> errors = new ArrayList<>();
            
            // 페이지별로 문서 처리
            int pageSize = 50; // 한 번에 50개씩 처리
            int totalPages = (int) Math.ceil((double) totalDocuments / pageSize);
            
            for (int page = 0; page < totalPages; page++) {
                try {
                    Pageable pageable = PageRequest.of(page, pageSize);
                    Page<DocumentEntity> documentPage = documentRepository.findAll(pageable);
                    
                    log.info("페이지 {}/{} 처리 중... ({}개 문서)", page + 1, totalPages, documentPage.getContent().size());
                    
                    for (DocumentEntity document : documentPage.getContent()) {
                        try {
                            processedCount.incrementAndGet();
                            
                            ReclassificationStatus status = reclassifyDocument(document);
                            
                            switch (status) {
                                case SUCCESS:
                                    successCount.incrementAndGet();
                                    break;
                                case UNCHANGED:
                                    unchangedCount.incrementAndGet();
                                    break;
                                case FAILED:
                                    failureCount.incrementAndGet();
                                    break;
                            }
                            
                            if (processedCount.get() % 100 == 0) {
                                log.info("진행 상황: {}/{}개 처리 완료", processedCount.get(), totalDocuments);
                            }
                            
                        } catch (Exception e) {
                            failureCount.incrementAndGet();
                            String error = String.format("문서 %s 재분류 실패: %s", 
                                document.getSerial().getFull(), e.getMessage());
                            errors.add(error);
                            log.error(error, e);
                        }
                    }
                    
                } catch (Exception e) {
                    String error = String.format("페이지 %d 처리 중 오류: %s", page, e.getMessage());
                    errors.add(error);
                    log.error(error, e);
                }
            }
            
            result.setProcessedCount(processedCount.get());
            result.setSuccessCount(successCount.get());
            result.setUnchangedCount(unchangedCount.get());
            result.setFailureCount(failureCount.get());
            result.setErrors(errors);
            result.setEndTime(LocalDateTime.now());
            
            log.info("모든 문서 스마트 재분류 완료: 처리 {}개, 성공 {}개, 변경없음 {}개, 실패 {}개", 
                processedCount.get(), successCount.get(), unchangedCount.get(), failureCount.get());
            
            return result;
            
        } catch (Exception e) {
            log.error("문서 재분류 중 전체 오류", e);
            throw new RuntimeException("문서 재분류 실패", e);
        }
    }
    
    /**
     * 개별 문서 재분류
     */
    public ReclassificationStatus reclassifyDocument(DocumentEntity document) {
        try {
            log.debug("문서 재분류 시작: {} - {}", document.getSerial().getFull(), document.getPurpose());
            
            // 현재 카테고리 백업
            Category originalCategory = document.getCategory();
            
            // 깃헙 레포의 경우 코드를 다시 읽어서 새로운 요약 생성
            String documentContent = document.getContent();
            String documentTitle = document.getPurpose();
            
            if (isGitHubRepository(document)) {
                try {
                    log.info("깃헙 레포 재분석 시작: {}", document.getSource().getRepoUrl());
                    
                    // 레포지토리 재분석
                    RepositoryAnalysisService.RepositoryAnalysisResult reanalysisResult = 
                        repositoryAnalysisService.analyzeRepository(document.getSource().getRepoUrl(), null);
                    
                    // 새로운 분석 결과로 내용 업데이트
                    documentContent = reanalysisResult.getExtractedText();
                    documentTitle = reanalysisResult.getProposedTitle() != null ? 
                        reanalysisResult.getProposedTitle() : document.getPurpose();
                    
                    // 문서 내용도 업데이트
                    document.setContent(documentContent);
                    if (reanalysisResult.getProposedTitle() != null) {
                        document.setPurpose(documentTitle);
                    }
                    
                    log.info("깃헙 레포 재분석 완료: {} ({}자)", 
                        document.getSource().getRepoUrl(), documentContent.length());
                    
                } catch (Exception e) {
                    log.warn("깃헙 레포 재분석 실패, 기존 내용 사용: {}", e.getMessage());
                }
            }
            
            // 스마트 분류 실행
            SmartClassificationService.ClassificationResult classificationResult = 
                smartClassificationService.classifyDocument(documentContent, documentTitle);
            
            if (!classificationResult.isSuccessful()) {
                log.warn("문서 재분류 실패: {} - {}", document.getSerial().getFull(), classificationResult.getReason());
                return ReclassificationStatus.FAILED;
            }
            
            Category newCategory = classificationResult.getSelectedCategory();
            
            // 카테고리가 변경되었는지 확인
            if (isSameCategory(originalCategory, newCategory)) {
                log.debug("카테고리 변경 없음: {}", document.getSerial().getFull());
                return ReclassificationStatus.UNCHANGED;
            }
            
            log.info("카테고리 변경 감지: {} - {} -> {}", 
                document.getSerial().getFull(), originalCategory.getFullCode(), newCategory.getFullCode());
            
            // 새로운 일련번호 생성
            String oldSerial = document.getSerial().getFull();
            Serial newSerial = generateNewSerial(newCategory);
            
            // 파일 이동 및 이름 변경 (기존 경로 -> 새 경로, 기존 일련번호 -> 새 일련번호)
            boolean fileMoved = moveAndRenameDocumentFile(document, originalCategory, newCategory, newSerial);
            
            if (fileMoved) {
                // 문서 카테고리 및 일련번호 업데이트
                document.setCategory(newCategory);
                document.setSerial(newSerial);
                document.setUpdatedAt(LocalDateTime.now());
                DocumentEntity savedDocument = documentRepository.save(document);
                
                // Elasticsearch 인덱스도 업데이트
                try {
                    elasticsearchIndexService.updateDocumentInIndex(savedDocument);
                    log.info("Elasticsearch 문서 인덱스 업데이트 완료: {}", savedDocument.getId());
                } catch (Exception e) {
                    log.warn("Elasticsearch 문서 인덱스 업데이트 실패: {}", savedDocument.getId(), e);
                    // Elasticsearch 실패는 전체 실패로 처리하지 않음
                }
                
                log.info("문서 재분류 성공: {} -> {} (카테고리: {} -> {})", 
                    oldSerial, newSerial.getFull(), originalCategory.getFullCode(), newCategory.getFullCode());
                
                return ReclassificationStatus.SUCCESS;
            } else {
                log.warn("파일 이동 실패로 재분류 취소: {}", document.getSerial().getFull());
                return ReclassificationStatus.FAILED;
            }
            
        } catch (Exception e) {
            log.error("문서 재분류 중 오류: {}", document.getSerial().getFull(), e);
            return ReclassificationStatus.FAILED;
        }
    }
    
    /**
     * 새로운 일련번호 생성
     */
    private Serial generateNewSerial(Category newCategory) {
        try {
            // 새 카테고리의 서브코드 추출
            String subCode;
            if (newCategory.getFullCode() != null) {
                // 가변 계층 구조: 마지막 레벨 코드를 서브코드로 사용
                String[] codes = newCategory.getFullCode().split("-");
                subCode = codes[codes.length - 1];
            } else {
                // 기존 3레벨 구조
                subCode = newCategory.getSubCode();
            }
            
            // 해당 서브코드의 다음 번호 조회
            int nextNumber = getNextSerialNumber(subCode);
            
            return Serial.of(subCode, nextNumber);
            
        } catch (Exception e) {
            log.error("새 일련번호 생성 실패: {}", newCategory.getFullCode(), e);
            // Fallback: 현재 시간 기반 번호
            int fallbackNumber = (int) (System.currentTimeMillis() % 10000);
            String subCode = newCategory.getSubCode() != null ? newCategory.getSubCode() : "A01";
            return Serial.of(subCode, fallbackNumber);
        }
    }
    
    /**
     * 서브코드별 다음 일련번호 조회
     */
    private int getNextSerialNumber(String subCode) {
        try {
            // 해당 서브코드로 시작하는 마지막 일련번호 조회
            List<DocumentEntity> documents = documentRepository.findAll();
            int maxNumber = documents.stream()
                .filter(doc -> doc.getSerial() != null && doc.getSerial().getFull() != null)
                .filter(doc -> doc.getSerial().getFull().startsWith(subCode + "-"))
                .mapToInt(doc -> {
                    try {
                        String[] parts = doc.getSerial().getFull().split("-");
                        return Integer.parseInt(parts[1]);
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .max()
                .orElse(0);
            
            return maxNumber + 1;
            
        } catch (Exception e) {
            log.warn("일련번호 조회 실패, 랜덤 번호 사용: {}", subCode, e);
            return (int) (Math.random() * 9000) + 1000; // 1000-9999 범위
        }
    }

    /**
     * 문서 파일을 새로운 카테고리 경로로 이동 및 일련번호 변경
     */
    private boolean moveAndRenameDocumentFile(DocumentEntity document, Category oldCategory, Category newCategory, Serial newSerial) {
        try {
            // 기존 파일 경로 찾기
            String oldFilePath = fileStorageService.getExistingDocumentFilePath(document);
            if (oldFilePath == null) {
                log.warn("기존 파일을 찾을 수 없음: {}", document.getSerial().getFull());
                return true; // 파일이 없으면 이동 성공으로 간주
            }
            
            // 새로운 카테고리와 일련번호로 임시 설정하여 새 경로 생성
            Category backupCategory = document.getCategory();
            Serial backupSerial = document.getSerial();
            
            document.setCategory(newCategory);
            document.setSerial(newSerial);
            String newFilePath = fileStorageService.getDocumentFilePath(document);
            
            // 원복
            document.setCategory(backupCategory);
            document.setSerial(backupSerial);
            
            if (newFilePath == null) {
                log.error("새 파일 경로 생성 실패: {}", newSerial.getFull());
                return false;
            }
            
            Path oldPath = Paths.get(oldFilePath);
            Path newPath = Paths.get(newFilePath);
            
            // 같은 경로면 이동할 필요 없음
            if (oldPath.equals(newPath)) {
                log.debug("파일 경로 변경 없음: {}", document.getSerial().getFull());
                return true;
            }
            
            // 새 디렉토리 생성
            Files.createDirectories(newPath.getParent());
            
            // 파일 이동
            Files.move(oldPath, newPath);
            
            log.info("파일 이동 및 이름 변경 성공: {} -> {}", oldPath, newPath);
            return true;
            
        } catch (Exception e) {
            log.error("파일 이동 및 이름 변경 중 오류: {}", document.getSerial().getFull(), e);
            return false;
        }
    }

    /**
     * 문서 파일을 새로운 카테고리 경로로 이동 (기존 메서드 - 하위 호환성)
     */
    private boolean moveDocumentFile(DocumentEntity document, Category oldCategory, Category newCategory) {
        try {
            // 기존 파일 경로 찾기
            String oldFilePath = fileStorageService.getExistingDocumentFilePath(document);
            if (oldFilePath == null) {
                log.warn("기존 파일을 찾을 수 없음: {}", document.getSerial().getFull());
                return true; // 파일이 없으면 이동 성공으로 간주
            }
            
            // 새로운 카테고리로 임시 설정하여 새 경로 생성
            Category backupCategory = document.getCategory();
            document.setCategory(newCategory);
            String newFilePath = fileStorageService.getDocumentFilePath(document);
            document.setCategory(backupCategory); // 원복
            
            if (newFilePath == null) {
                log.error("새 파일 경로 생성 실패: {}", document.getSerial().getFull());
                return false;
            }
            
            Path oldPath = Paths.get(oldFilePath);
            Path newPath = Paths.get(newFilePath);
            
            // 같은 경로면 이동할 필요 없음
            if (oldPath.equals(newPath)) {
                log.debug("파일 경로 변경 없음: {}", document.getSerial().getFull());
                return true;
            }
            
            // 새 디렉토리 생성
            Files.createDirectories(newPath.getParent());
            
            // 파일 이동
            Files.move(oldPath, newPath);
            
            log.info("파일 이동 성공: {} -> {}", oldPath, newPath);
            return true;
            
        } catch (Exception e) {
            log.error("파일 이동 중 오류: {}", document.getSerial().getFull(), e);
            return false;
        }
    }
    
    /**
     * 두 카테고리가 같은지 비교
     */
    private boolean isSameCategory(Category cat1, Category cat2) {
        if (cat1 == null || cat2 == null) {
            return cat1 == cat2;
        }
        
        // fullCode 비교 (가장 정확한 방법)
        if (cat1.getFullCode() != null && cat2.getFullCode() != null) {
            return cat1.getFullCode().equals(cat2.getFullCode());
        }
        
        // 기존 방식 비교 (Fallback)
        return cat1.getMajorCode().equals(cat2.getMajorCode()) &&
               cat1.getMidCode().equals(cat2.getMidCode()) &&
               cat1.getSubCode().equals(cat2.getSubCode());
    }
    
    /**
     * 특정 카테고리의 문서들만 재분류
     */
    public ReclassificationResult reclassifyDocumentsByCategory(String categoryCode) {
        try {
            log.info("카테고리별 문서 재분류 시작: {}", categoryCode);
            
            // 해당 카테고리의 문서들 조회
            List<DocumentEntity> documents = findDocumentsByCategory(categoryCode);
            log.info("재분류 대상 문서 수: {}개 (카테고리: {})", documents.size(), categoryCode);
            
            ReclassificationResult result = new ReclassificationResult();
            result.setTotalDocuments(documents.size());
            result.setStartTime(LocalDateTime.now());
            
            AtomicInteger processedCount = new AtomicInteger(0);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            AtomicInteger unchangedCount = new AtomicInteger(0);
            List<String> errors = new ArrayList<>();
            
            for (DocumentEntity document : documents) {
                try {
                    processedCount.incrementAndGet();
                    
                    ReclassificationStatus status = reclassifyDocument(document);
                    
                    switch (status) {
                        case SUCCESS:
                            successCount.incrementAndGet();
                            break;
                        case UNCHANGED:
                            unchangedCount.incrementAndGet();
                            break;
                        case FAILED:
                            failureCount.incrementAndGet();
                            break;
                    }
                    
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    String error = String.format("문서 %s 재분류 실패: %s", 
                        document.getSerial().getFull(), e.getMessage());
                    errors.add(error);
                    log.error(error, e);
                }
            }
            
            result.setProcessedCount(processedCount.get());
            result.setSuccessCount(successCount.get());
            result.setUnchangedCount(unchangedCount.get());
            result.setFailureCount(failureCount.get());
            result.setErrors(errors);
            result.setEndTime(LocalDateTime.now());
            
            log.info("카테고리별 문서 재분류 완료: 처리 {}개, 성공 {}개, 변경없음 {}개, 실패 {}개", 
                processedCount.get(), successCount.get(), unchangedCount.get(), failureCount.get());
            
            return result;
            
        } catch (Exception e) {
            log.error("카테고리별 문서 재분류 중 오류", e);
            throw new RuntimeException("카테고리별 문서 재분류 실패", e);
        }
    }
    
    /**
     * 특정 카테고리에 속한 문서들 조회
     */
    private List<DocumentEntity> findDocumentsByCategory(String categoryCode) {
        List<DocumentEntity> result = new ArrayList<>();
        
        // 대분류 코드로 검색
        List<DocumentEntity> majorDocs = documentRepository.findByCategoryMajorCode(categoryCode);
        result.addAll(majorDocs);
        
        // 중분류 코드로 검색
        List<DocumentEntity> midDocs = documentRepository.findByCategoryMidCode(categoryCode);
        result.addAll(midDocs);
        
        // 소분류 코드로 검색
        List<DocumentEntity> subDocs = documentRepository.findByCategorySubCode(categoryCode);
        result.addAll(subDocs);
        
        // 중복 제거
        return result.stream()
            .distinct()
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 재분류 상태 확인
     */
    public ReclassificationStatus checkReclassificationStatus() {
        // 현재는 단순 구현, 추후 Redis나 DB를 사용한 상태 관리 가능
        return ReclassificationStatus.SUCCESS;
    }
    
    /**
     * 재분류 결과 클래스
     */
    public static class ReclassificationResult {
        private int totalDocuments;
        private int processedCount;
        private int successCount;
        private int unchangedCount;
        private int failureCount;
        private List<String> errors;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        
        public ReclassificationResult() {
            this.errors = new ArrayList<>();
        }
        
        // Getters and Setters
        public int getTotalDocuments() { return totalDocuments; }
        public void setTotalDocuments(int totalDocuments) { this.totalDocuments = totalDocuments; }
        public int getProcessedCount() { return processedCount; }
        public void setProcessedCount(int processedCount) { this.processedCount = processedCount; }
        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int successCount) { this.successCount = successCount; }
        public int getUnchangedCount() { return unchangedCount; }
        public void setUnchangedCount(int unchangedCount) { this.unchangedCount = unchangedCount; }
        public int getFailureCount() { return failureCount; }
        public void setFailureCount(int failureCount) { this.failureCount = failureCount; }
        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        
        public long getDurationSeconds() {
            if (startTime != null && endTime != null) {
                return java.time.Duration.between(startTime, endTime).getSeconds();
            }
            return 0;
        }
        
        public double getSuccessRate() {
            if (processedCount == 0) return 0.0;
            return (double) successCount / processedCount * 100.0;
        }
    }
    
    /**
     * 재분류 상태 열거형
     */
    public enum ReclassificationStatus {
        SUCCESS,    // 재분류 성공 (카테고리 변경됨)
        UNCHANGED,  // 카테고리 변경 없음
        FAILED      // 재분류 실패
    }
    
    /**
     * 깃헙 레포지토리인지 확인
     */
    private boolean isGitHubRepository(DocumentEntity document) {
        return document.getSource() != null && 
               document.getSource().getRepoUrl() != null && 
               !document.getSource().getRepoUrl().isEmpty();
    }
}
