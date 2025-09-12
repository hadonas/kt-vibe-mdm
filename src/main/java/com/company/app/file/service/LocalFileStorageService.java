package com.company.app.file.service;

import com.company.app.catalog.entity.CatalogNode;
import com.company.app.catalog.repository.CatalogNodeRepository;
import com.company.app.common.dto.Category;
import com.company.app.document.entity.DocumentEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 로컬 파일 시스템에 문서를 계층 구조로 저장하는 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LocalFileStorageService {
    
    @Value("${app.file.storage.path:/app/storage}")
    private String storageBasePath;
    
    @Value("${app.file.temp.path:/app/temp}")
    private String tempBasePath;
    
    private final FileAnalysisService fileAnalysisService;
    private final CatalogNodeRepository catalogNodeRepository;
    
    public String getStoragePath() {
        return storageBasePath;
    }
    
    public String getTempPath() {
        return tempBasePath;
    }
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    
    /**
     * 문서를 로컬 파일 시스템에 계층 구조로 저장
     */
    public String saveDocument(DocumentEntity document) {
        try {
            log.info("문서 로컬 저장 시작: {} - {}", document.getSerial().getFull(), document.getPurpose());
            
            // 1. 저장 경로 생성
            Path documentPath = createDocumentPath(document);
            
            // 2. 디렉토리 생성
            Files.createDirectories(documentPath);
            
            // 3. 파일 타입에 따른 저장 방식 결정
            if (isOriginalFileDocument(document)) {
                // 원본 파일 저장 (DOCX, XLSX 등)
                return saveOriginalFile(document, documentPath);
            } else {
                // MD 파일 생성 (GitHub 레포지토리 분석 결과 등)
                return saveMarkdownFile(document, documentPath);
            }
            
        } catch (Exception e) {
            log.error("문서 로컬 저장 중 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("문서 저장에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * 원본 파일 문서인지 확인 (DOCX, XLSX 등)
     */
    private boolean isOriginalFileDocument(DocumentEntity document) {
        return document.getSource() != null && 
               document.getSource().getType() == com.company.app.common.dto.Source.SourceType.PLAN &&
               document.getOriginalFileName() != null &&
               (document.getOriginalFileName().toLowerCase().endsWith(".docx") ||
                document.getOriginalFileName().toLowerCase().endsWith(".xlsx") ||
                document.getOriginalFileName().toLowerCase().endsWith(".pdf"));
    }
    
    /**
     * 원본 파일 저장 (DOCX, XLSX, PDF 등)
     */
    private String saveOriginalFile(DocumentEntity document, Path documentPath) throws IOException {
        // 원본 파일명에서 확장자 추출
        String originalFileName = document.getOriginalFileName();
        String extension = "";
        int lastDotIndex = originalFileName.lastIndexOf('.');
        if (lastDotIndex != -1) {
            extension = originalFileName.substring(lastDotIndex);
        }
        
        // 파일명 생성: (코드)_(제목).(원본확장자)
        String serial = document.getSerial().getFull();
        String title = sanitizeFileName(document.getPurpose());
        if (title.length() > 100) {
            title = title.substring(0, 97) + "...";
        }
        title = title.replaceAll("_+$", "");
        if (title.isEmpty()) {
            title = "document";
        }
        
        String fileName = String.format("%s_%s%s", serial, title, extension);
        Path filePath = documentPath.resolve(fileName);
        
        // 원본 파일을 임시 저장소에서 복사
        if (document.getSource() != null && document.getSource().getFiles() != null && !document.getSource().getFiles().isEmpty()) {
            String fileId = document.getSource().getFiles().get(0);
            try {
                // FileAnalysisService에서 원본 바이너리 파일을 가져와서 저장
                byte[] fileContent = fileAnalysisService.getTemporaryFile(fileId);
                Files.write(filePath, fileContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                
                // 임시 파일 삭제
                fileAnalysisService.deleteTemporaryFile(fileId);
            } catch (Exception e) {
                log.warn("원본 파일 복사 실패, 텍스트 내용으로 저장: {}", e.getMessage());
                // 원본 파일 복사 실패 시 텍스트 내용 저장
                String content = document.getContent();
                Files.write(filePath, content.getBytes("UTF-8"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } else {
            // 파일 ID가 없는 경우 텍스트 내용 저장
            String content = document.getContent();
            Files.write(filePath, content.getBytes("UTF-8"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
        
        log.info("원본 파일 로컬 저장 완료: {}", filePath);
        return filePath.toString();
    }
    
    /**
     * 마크다운 파일 저장 (GitHub 레포지토리 분석 결과 등)
     */
    private String saveMarkdownFile(DocumentEntity document, Path documentPath) throws IOException {
        // 파일명 생성 (코드번호_제목.md)
        String fileName = generateFileName(document);
        Path filePath = documentPath.resolve(fileName);
        
        // 파일 내용 생성
        String content = generateFileContent(document);
        
        // 파일 저장
        Files.write(filePath, content.getBytes("UTF-8"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        
        log.info("마크다운 파일 로컬 저장 완료: {}", filePath);
        return filePath.toString();
    }
    
    /**
     * GitHub 레포지토리 분석 결과를 로컬에 저장
     */
    public String saveRepositoryAnalysis(DocumentEntity document, String repoUrl, String analysisResult) {
        try {
            log.info("GitHub 레포지토리 분석 결과 로컬 저장 시작: {} - {}", document.getSerial().getFull(), repoUrl);
            
            // 1. 저장 경로 생성
            Path documentPath = createDocumentPath(document);
            
            // 2. 디렉토리 생성
            Files.createDirectories(documentPath);
            
            // 3. 파일명 생성 (코드번호_레포지토리명.md)
            String fileName = generateRepositoryFileName(document, repoUrl);
            Path filePath = documentPath.resolve(fileName);
            
            // 4. 파일 내용 생성
            String content = generateRepositoryFileContent(document, repoUrl, analysisResult);
            
            // 5. 파일 저장
            Files.write(filePath, content.getBytes("UTF-8"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            
            log.info("GitHub 레포지토리 분석 결과 로컬 저장 완료: {}", filePath);
            return filePath.toString();
            
        } catch (Exception e) {
            log.error("GitHub 레포지토리 분석 결과 로컬 저장 중 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("레포지토리 분석 결과 저장에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * 문서 저장 경로 생성 (계층 구조)
     */
    private Path createDocumentPath(DocumentEntity document) {
        Category category = document.getCategory();
        if (category == null) {
            throw new IllegalArgumentException("문서에 카테고리 정보가 없습니다.");
        }
        
        // 경로: /storage/대분류코드_대분류명/중분류코드_중분류명/소분류코드_소분류명/
        String majorDir = String.format("%s_%s", category.getMajorCode(), sanitizeFileName(category.getMajorName()));
        String midDir = String.format("%s_%s", category.getMidCode(), sanitizeFileName(category.getMidName()));
        String subDir = String.format("%s_%s", category.getSubCode(), sanitizeFileName(category.getSubName()));
        
        return Paths.get(storageBasePath, majorDir, midDir, subDir);
    }
    
    /**
     * 일반 문서 파일명 생성 - (코드)_(원본파일명).(파일형식) 형태
     */
    private String generateFileName(DocumentEntity document) {
        String serial = document.getSerial().getFull();
        
        // 원본 파일명이 있으면 사용, 없으면 purpose 사용
        String fileName;
        if (document.getOriginalFileName() != null && !document.getOriginalFileName().isEmpty()) {
            // 원본 파일명에서 확장자 제거
            String originalName = document.getOriginalFileName();
            int lastDotIndex = originalName.lastIndexOf('.');
            if (lastDotIndex != -1) {
                fileName = originalName.substring(0, lastDotIndex);
            } else {
                fileName = originalName;
            }
        } else {
            // 원본 파일명이 없으면 purpose 사용
            fileName = sanitizeFileName(document.getPurpose());
            if (fileName.isEmpty()) {
                fileName = "document";
            }
        }
        
        // 파일명이 너무 길면 잘라내기 (파일명 전체 길이 고려)
        if (fileName.length() > 100) {
            fileName = fileName.substring(0, 97) + "...";
        }
        
        // 파일명 끝의 언더스코어 제거
        fileName = fileName.replaceAll("_+$", "");
        if (fileName.isEmpty()) {
            fileName = "document";
        }
        
        // 원본 파일 확장자가 있으면 사용, 없으면 .md 사용
        String extension = ".md";
        if (document.getOriginalFileName() != null && !document.getOriginalFileName().isEmpty()) {
            String originalName = document.getOriginalFileName();
            int lastDotIndex = originalName.lastIndexOf('.');
            if (lastDotIndex != -1) {
                extension = originalName.substring(lastDotIndex);
            }
        }
        
        return String.format("%s_%s%s", serial, fileName, extension);
    }
    
    /**
     * GitHub 레포지토리 파일명 생성 - (코드)_(생성된 제목).(파일형식) 형태
     */
    private String generateRepositoryFileName(DocumentEntity document, String repoUrl) {
        String serial = document.getSerial().getFull();
        String title = sanitizeFileName(document.getPurpose());
        
        // purpose가 비어있거나 너무 길면 레포지토리명 사용
        if (title.isEmpty() || title.length() > 50) {
            title = extractRepositoryName(repoUrl);
        }
        
        // 제목이 여전히 너무 길면 더 짧게 잘라내기
        if (title.length() > 30) {
            title = title.substring(0, 27) + "...";
        }
        
        return String.format("%s_%s.md", serial, title);
    }
    
    /**
     * 일반 문서 파일 내용 생성
     */
    private String generateFileContent(DocumentEntity document) {
        StringBuilder content = new StringBuilder();
        
        // 헤더 정보
        content.append("# ").append(document.getSerial().getFull()).append(" - ").append(document.getPurpose()).append("\n\n");
        content.append("## 문서 정보\n");
        content.append("- **코드번호**: ").append(document.getSerial().getFull()).append("\n");
        content.append("- **제목**: ").append(document.getPurpose()).append("\n");
        content.append("- **분류**: ").append(document.getCategory().getMajorName())
                .append(" > ").append(document.getCategory().getMidName())
                .append(" > ").append(document.getCategory().getSubName()).append("\n");
        content.append("- **등록일**: ").append(document.getCreatedAt()).append("\n");
        content.append("- **승인일**: ").append(document.getApprovedAt() != null ? document.getApprovedAt() : "미승인").append("\n");
        content.append("- **등록자**: ").append(document.getOwnerId()).append("\n\n");
        
        // 소스 정보
        if (document.getSource() != null) {
            content.append("## 소스 정보\n");
            content.append("- **타입**: ").append(document.getSource().getType()).append("\n");
            if (document.getSource().getRepoUrl() != null) {
                content.append("- **레포지토리 URL**: ").append(document.getSource().getRepoUrl()).append("\n");
            }
            if (document.getSource().getFiles() != null && !document.getSource().getFiles().isEmpty()) {
                content.append("- **파일 목록**: ").append(String.join(", ", document.getSource().getFiles())).append("\n");
            }
            content.append("\n");
        }
        
        // 태그 정보
        if (document.getTags() != null && !document.getTags().isEmpty()) {
            content.append("## 태그\n");
            content.append("- ").append(String.join("\n- ", document.getTags())).append("\n\n");
        }
        
        // 문서 내용
        content.append("## 문서 내용\n");
        content.append(document.getContent() != null ? document.getContent() : "내용이 없습니다.");
        
        return content.toString();
    }
    
    /**
     * GitHub 레포지토리 파일 내용 생성
     */
    private String generateRepositoryFileContent(DocumentEntity document, String repoUrl, String analysisResult) {
        StringBuilder content = new StringBuilder();
        
        // 헤더 정보
        content.append("# ").append(document.getSerial().getFull()).append(" - ").append(document.getPurpose()).append("\n\n");
        content.append("## 문서 정보\n");
        content.append("- **코드번호**: ").append(document.getSerial().getFull()).append("\n");
        content.append("- **제목**: ").append(document.getPurpose()).append("\n");
        content.append("- **분류**: ").append(document.getCategory().getMajorName())
                .append(" > ").append(document.getCategory().getMidName())
                .append(" > ").append(document.getCategory().getSubName()).append("\n");
        content.append("- **등록일**: ").append(document.getCreatedAt()).append("\n");
        content.append("- **승인일**: ").append(document.getApprovedAt() != null ? document.getApprovedAt() : "미승인").append("\n");
        content.append("- **등록자**: ").append(document.getOwnerId()).append("\n\n");
        
        // GitHub 레포지토리 정보
        content.append("## GitHub 레포지토리 정보\n");
        content.append("- **URL**: ").append(repoUrl).append("\n");
        content.append("- **레포지토리명**: ").append(extractRepositoryName(repoUrl)).append("\n");
        content.append("- **분석일**: ").append(LocalDateTime.now().format(DATE_FORMATTER)).append("\n\n");
        
        // 태그 정보
        if (document.getTags() != null && !document.getTags().isEmpty()) {
            content.append("## 태그\n");
            content.append("- ").append(String.join("\n- ", document.getTags())).append("\n\n");
        }
        
        // 분석 결과
        content.append("## 레포지토리 분석 결과\n");
        content.append(analysisResult);
        
        return content.toString();
    }
    
    /**
     * 파일명에서 안전하지 않은 문자 제거
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "document";
        }
        
        // 안전하지 않은 문자 제거 및 정리
        String sanitized = fileName
            .replaceAll("[<>:\"/\\\\|?*]", "_")  // 안전하지 않은 문자 제거
            .replaceAll("\\s+", "_")             // 공백을 언더스코어로 변경
            .replaceAll("[\\p{Cntrl}]", "")      // 제어 문자 제거
            .replaceAll("_+", "_")               // 연속된 언더스코어를 하나로
            .replaceAll("^_+|_+$", "")           // 시작과 끝의 언더스코어 제거
            .trim();
        
        // 빈 문자열이면 기본값 반환
        if (sanitized.isEmpty()) {
            return "document";
        }
        
        // 너무 긴 파일명은 잘라내기
        if (sanitized.length() > 100) {
            sanitized = sanitized.substring(0, 97) + "...";
        }
        
        return sanitized;
    }
    
    /**
     * GitHub URL에서 레포지토리명 추출
     */
    private String extractRepositoryName(String repoUrl) {
        if (repoUrl == null) return "unknown_repo";
        
        try {
            // https://github.com/owner/repo 형태에서 repo 부분 추출
            String[] parts = repoUrl.split("/");
            if (parts.length >= 2) {
                String repoName = parts[parts.length - 1];
                // .git 확장자 제거
                if (repoName.endsWith(".git")) {
                    repoName = repoName.substring(0, repoName.length() - 4);
                }
                return sanitizeFileName(repoName);
            }
        } catch (Exception e) {
            log.warn("레포지토리명 추출 실패: {}", repoUrl, e);
        }
        
        return "unknown_repo";
    }
    
    /**
     * 저장된 파일 삭제
     */
    public boolean deleteDocument(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                Files.delete(path);
                log.info("문서 파일 삭제 완료: {}", filePath);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("문서 파일 삭제 중 오류 발생: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 저장소 통계 정보 조회
     */
    public Map<String, Object> getStorageStats() {
        try {
            Path basePath = Paths.get(storageBasePath);
            if (!Files.exists(basePath)) {
                return Map.of("totalFiles", 0, "totalSize", 0L, "categories", 0);
            }
            
            final int[] totalFiles = {0};
            final long[] totalSize = {0L};
            final int[] categories = {0};
            
            // 디렉토리 순회하여 통계 수집
            Files.walk(basePath)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        totalFiles[0]++;
                        totalSize[0] += Files.size(path);
                    } catch (IOException e) {
                        log.warn("파일 크기 조회 실패: {}", path, e);
                    }
                });
            
            // 카테고리 수 계산 (대분류 수)
            try {
                categories[0] = (int) Files.list(basePath)
                    .filter(Files::isDirectory)
                    .count();
            } catch (IOException e) {
                log.warn("카테고리 수 계산 실패", e);
            }
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalFiles", totalFiles[0]);
            stats.put("totalSize", totalSize[0]);
            stats.put("categories", categories[0]);
            stats.put("storagePath", storageBasePath);
            
            return stats;
            
        } catch (Exception e) {
            log.error("저장소 통계 조회 중 오류 발생: {}", e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }
    
    /**
     * 파일 계층 구조 조회
     */
    public Map<String, Object> getFileHierarchy() {
        try {
            Path basePath = Paths.get(storageBasePath);
            if (!Files.exists(basePath)) {
                return Map.of("hierarchy", Map.of(), "totalFiles", 0, "totalCategories", 0);
            }
            
            Map<String, Object> hierarchy = new HashMap<>();
            AtomicInteger totalFiles = new AtomicInteger(0);
            AtomicInteger totalCategories = new AtomicInteger(0);
            
            // 대분류 디렉토리 순회
            Files.list(basePath)
                .filter(Files::isDirectory)
                .forEach(majorDir -> {
                    try {
                        String majorName = majorDir.getFileName().toString();
                        Map<String, Object> majorData = new HashMap<>();
                        
                        // 중분류 디렉토리 순회
                        Files.list(majorDir)
                            .filter(Files::isDirectory)
                            .forEach(midDir -> {
                                try {
                                    String midName = midDir.getFileName().toString();
                                    Map<String, Object> midData = new HashMap<>();
                                    
                                    // 소분류 디렉토리 순회
                                    Files.list(midDir)
                                        .filter(Files::isDirectory)
                                        .forEach(subDir -> {
                                            try {
                                                String subName = subDir.getFileName().toString();
                                                Map<String, Object> subData = new HashMap<>();
                                                
                                                // 파일 목록 조회 (MD, DOCX, XLSX, PDF 파일 포함)
                                                List<Map<String, Object>> files = Files.list(subDir)
                                                    .filter(Files::isRegularFile)
                                                    .filter(path -> {
                                                        String fileName = path.toString().toLowerCase();
                                                        return fileName.endsWith(".md") || 
                                                               fileName.endsWith(".docx") || 
                                                               fileName.endsWith(".xlsx") || 
                                                               fileName.endsWith(".pdf");
                                                    })
                                                    .map(file -> {
                                                        Map<String, Object> fileData = new HashMap<>();
                                                        fileData.put("name", file.getFileName().toString());
                                                        fileData.put("path", file.toString());
                                                        try {
                                                            fileData.put("size", Files.size(file));
                                                            fileData.put("modified", Files.getLastModifiedTime(file).toString());
                                                        } catch (IOException e) {
                                                            log.warn("파일 정보 조회 실패: {}", file, e);
                                                        }
                                                        return fileData;
                                                    })
                                                    .collect(Collectors.toList());
                                                
                                                subData.put("files", files);
                                                subData.put("fileCount", files.size());
                                                midData.put(subName, subData);
                                                totalFiles.addAndGet(files.size());
                                                totalCategories.incrementAndGet();
                                                
                                            } catch (IOException e) {
                                                log.warn("소분류 디렉토리 조회 실패: {}", subDir, e);
                                            }
                                        });
                                    
                                    majorData.put(midName, midData);
                                    
                                } catch (IOException e) {
                                    log.warn("중분류 디렉토리 조회 실패: {}", midDir, e);
                                }
                            });
                        
                        hierarchy.put(majorName, majorData);
                        
                    } catch (IOException e) {
                        log.warn("대분류 디렉토리 조회 실패: {}", majorDir, e);
                    }
                });
            
            Map<String, Object> result = new HashMap<>();
            result.put("hierarchy", hierarchy);
            result.put("totalFiles", totalFiles.get());
            result.put("totalCategories", totalCategories.get());
            result.put("storagePath", storageBasePath);
            
            return result;
            
        } catch (Exception e) {
            log.error("파일 계층 구조 조회 중 오류 발생: {}", e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }
    
    /**
     * 특정 카테고리의 파일 목록 조회
     */
    public Map<String, Object> getFilesByCategory(String majorCode, String midCode, String subCode) {
        try {
            // 카테고리 경로 생성 - 이미 언더스코어가 포함된 경우 그대로 사용
            String majorDir = majorCode.contains("_") ? majorCode : majorCode + "_" + sanitizeFileName(getCategoryName(majorCode));
            String midDir = midCode.contains("_") ? midCode : midCode + "_" + sanitizeFileName(getCategoryName(midCode));
            String subDir = subCode.contains("_") ? subCode : subCode + "_" + sanitizeFileName(getCategoryName(subCode));
            
            Path categoryPath = Paths.get(storageBasePath, majorDir, midDir, subDir);
            
            log.info("카테고리 파일 조회: majorCode={}, midCode={}, subCode={}", majorCode, midCode, subCode);
            log.info("생성된 경로: {}", categoryPath);
            log.info("경로 존재 여부: {}", Files.exists(categoryPath));
            
            if (!Files.exists(categoryPath)) {
                return Map.of("files", List.of(), "fileCount", 0, "category", majorCode + "/" + midCode + "/" + subCode);
            }
            
            // 파일 목록 조회 (MD, DOCX, XLSX, PDF 파일 포함)
            List<Map<String, Object>> files = Files.list(categoryPath)
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String fileName = path.toString().toLowerCase();
                    return fileName.endsWith(".md") || 
                           fileName.endsWith(".docx") || 
                           fileName.endsWith(".xlsx") || 
                           fileName.endsWith(".pdf");
                })
                .map(file -> {
                    Map<String, Object> fileData = new HashMap<>();
                    fileData.put("name", file.getFileName().toString());
                    fileData.put("path", file.toString());
                    try {
                        fileData.put("size", Files.size(file));
                        fileData.put("modified", Files.getLastModifiedTime(file).toString());
                        
                        // 파일 내용 미리보기 (첫 200자)
                        String content = Files.readString(file, StandardCharsets.UTF_8);
                        if (content.length() > 200) {
                            content = content.substring(0, 200) + "...";
                        }
                        fileData.put("preview", content);
                        
                    } catch (IOException e) {
                        log.warn("파일 정보 조회 실패: {}", file, e);
                    }
                    return fileData;
                })
                .sorted((a, b) -> ((String) a.get("name")).compareTo((String) b.get("name")))
                .collect(Collectors.toList());
            
            Map<String, Object> result = new HashMap<>();
            result.put("files", files);
            result.put("fileCount", files.size());
            result.put("category", majorCode + "/" + midCode + "/" + subCode);
            result.put("path", categoryPath.toString());
            
            return result;
            
        } catch (Exception e) {
            log.error("카테고리별 파일 조회 중 오류 발생: {}", e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }
    
    /**
     * 카테고리 코드에서 이름 추출 (데이터베이스에서 조회)
     */
    private String getCategoryName(String code) {
        try {
            // 코드에서 언더스코어와 함께 온 경우 처리 (예: A_소프트웨어 -> A)
            String cleanCode = code;
            if (code.contains("_")) {
                cleanCode = code.split("_")[0];
            }
            
            // 데이터베이스에서 카테고리 조회
            return catalogNodeRepository.findByCodeAndActiveTrue(cleanCode)
                .map(CatalogNode::getName)
                .orElse(code); // 조회 실패 시 원본 코드 반환
                
        } catch (Exception e) {
            log.warn("카테고리 이름 조회 실패: {}, 원본 코드 반환: {}", code, e.getMessage());
            return code;
        }
    }
    
    /**
     * 문서의 파일 경로 조회 (파일 존재 여부와 관계없이 경로 반환)
     */
    public String getDocumentFilePath(DocumentEntity document) {
        try {
            if (document.getCategory() == null) {
                return null;
            }
            
            // 카테고리 경로 생성
            String majorDir = document.getCategory().getMajorCode() + "_" + sanitizeFileName(document.getCategory().getMajorName());
            String midDir = document.getCategory().getMidCode() + "_" + sanitizeFileName(document.getCategory().getMidName());
            String subDir = document.getCategory().getSubCode() + "_" + sanitizeFileName(document.getCategory().getSubName());
            
            // 파일명 생성
            String fileName = generateFileName(document);
            
            Path filePath = Paths.get(storageBasePath, majorDir, midDir, subDir, fileName);
            
            return filePath.toString();
            
        } catch (Exception e) {
            log.error("문서 파일 경로 조회 중 오류 발생: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 문서의 파일 경로 조회 (파일이 존재하는 경우만)
     */
    public String getExistingDocumentFilePath(DocumentEntity document) {
        try {
            if (document.getCategory() == null) {
                return null;
            }
            
            // 카테고리 경로 생성
            String majorDir = document.getCategory().getMajorCode() + "_" + sanitizeFileName(document.getCategory().getMajorName());
            String midDir = document.getCategory().getMidCode() + "_" + sanitizeFileName(document.getCategory().getMidName());
            String subDir = document.getCategory().getSubCode() + "_" + sanitizeFileName(document.getCategory().getSubName());
            
            // 파일명 생성
            String fileName = generateFileName(document);
            
            Path filePath = Paths.get(storageBasePath, majorDir, midDir, subDir, fileName);
            
            if (Files.exists(filePath)) {
                return filePath.toString();
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("문서 파일 경로 조회 중 오류 발생: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 문서 파일 삭제
     */
    public boolean deleteDocumentFile(DocumentEntity document) {
        try {
            if (document.getCategory() == null) {
                log.warn("문서 카테고리가 없어 파일 삭제 불가: {}", document.getId());
                return false;
            }
            
            // 카테고리 경로 생성
            String majorDir = document.getCategory().getMajorCode() + "_" + sanitizeFileName(document.getCategory().getMajorName());
            String midDir = document.getCategory().getMidCode() + "_" + sanitizeFileName(document.getCategory().getMidName());
            String subDir = document.getCategory().getSubCode() + "_" + sanitizeFileName(document.getCategory().getSubName());
            
            // 파일명 생성
            String fileName = generateFileName(document);
            
            Path filePath = Paths.get(storageBasePath, majorDir, midDir, subDir, fileName);
            
            if (Files.exists(filePath)) {
                boolean deleted = Files.deleteIfExists(filePath);
                if (deleted) {
                    log.info("파일 삭제 성공: {}", filePath);
                    return true;
                } else {
                    log.warn("파일 삭제 실패: {}", filePath);
                    return false;
                }
            } else {
                log.info("삭제할 파일이 존재하지 않음: {}", filePath);
                return true; // 파일이 없으면 삭제 성공으로 간주
            }
            
        } catch (Exception e) {
            log.error("문서 파일 삭제 중 오류 발생: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 승인 대기 중인 파일을 임시 저장소에 저장
     */
    public String saveTempFile(String requestId, String fileName, String content) {
        try {
            log.info("임시 파일 저장 시작: {} - {}", requestId, fileName);
            
            // 임시 디렉토리 생성
            Path tempDir = Paths.get(tempBasePath, "pending", requestId);
            Files.createDirectories(tempDir);
            
            // 파일명 생성 (타임스탬프 추가)
            String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
            String safeFileName = sanitizeFileName(fileName);
            String tempFileName = String.format("%s_%s", timestamp, safeFileName);
            
            // 파일 저장
            Path filePath = tempDir.resolve(tempFileName);
            Files.write(filePath, content.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
            
            log.info("임시 파일 저장 완료: {}", filePath);
            return filePath.toString();
            
        } catch (Exception e) {
            log.error("임시 파일 저장 중 오류 발생: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 승인 대기 중인 GitHub 레포지토리 분석 결과를 임시 저장소에 저장
     */
    public String saveTempRepositoryAnalysis(String requestId, String repoUrl, String analysisContent) {
        try {
            log.info("임시 레포지토리 분석 결과 저장 시작: {} - {}", requestId, repoUrl);
            
            // 임시 디렉토리 생성
            Path tempDir = Paths.get(tempBasePath, "pending", requestId);
            Files.createDirectories(tempDir);
            
            // 파일명 생성
            String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
            String repoName = extractRepoName(repoUrl);
            String fileName = String.format("%s_%s_analysis.md", timestamp, sanitizeFileName(repoName));
            
            // 분석 결과 저장
            Path filePath = tempDir.resolve(fileName);
            Files.write(filePath, analysisContent.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
            
            log.info("임시 레포지토리 분석 결과 저장 완료: {}", filePath);
            return filePath.toString();
            
        } catch (Exception e) {
            log.error("임시 레포지토리 분석 결과 저장 중 오류 발생: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 임시 파일을 최종 저장소로 이동
     */
    public String moveTempToFinal(String tempFilePath, DocumentEntity document) {
        try {
            log.info("임시 파일을 최종 저장소로 이동: {} -> {}", tempFilePath, document.getSerial().getFull());
            
            Path tempFile = Paths.get(tempFilePath);
            if (!Files.exists(tempFile)) {
                log.warn("임시 파일이 존재하지 않음: {}", tempFilePath);
                return null;
            }
            
            // 최종 저장 경로 생성
            String finalPath = saveDocument(document);
            if (finalPath == null) {
                log.error("최종 저장 경로 생성 실패");
                return null;
            }
            
            // 임시 파일 삭제
            Files.deleteIfExists(tempFile);
            
            log.info("임시 파일 이동 완료: {}", finalPath);
            return finalPath;
            
        } catch (Exception e) {
            log.error("임시 파일 이동 중 오류 발생: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 승인 거부된 임시 파일들 삭제
     */
    public void cleanupTempFiles(String requestId) {
        try {
            log.info("임시 파일 정리 시작: {}", requestId);
            
            Path tempDir = Paths.get(tempBasePath, "pending", requestId);
            if (Files.exists(tempDir)) {
                // 디렉토리와 모든 파일 삭제
                Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a)) // 역순으로 정렬하여 파일을 먼저 삭제
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.warn("파일 삭제 실패: {}", path, e);
                        }
                    });
                
                log.info("임시 파일 정리 완료: {}", requestId);
            }
            
        } catch (Exception e) {
            log.error("임시 파일 정리 중 오류 발생: {}", e.getMessage(), e);
        }
    }
    
    /**
     * GitHub URL에서 레포지토리 이름 추출
     */
    private String extractRepoName(String repoUrl) {
        try {
            // https://github.com/owner/repo 형식에서 repo 부분 추출
            String[] parts = repoUrl.split("/");
            if (parts.length >= 2) {
                return parts[parts.length - 1].replaceAll("\\.git$", "");
            }
            return "repository";
        } catch (Exception e) {
            log.warn("레포지토리 이름 추출 실패: {}", repoUrl, e);
            return "repository";
        }
    }
    
    /**
     * 코드로 파일시스템의 파일을 삭제 (벡터DB에 없는 파일용)
     */
    public boolean deleteFileByCode(String code) {
        try {
            log.info("코드로 파일 삭제 시도: {}", code);
            
            // 모든 카테고리 디렉토리를 순회하며 해당 코드의 파일을 찾아서 삭제
            Path storagePath = Paths.get(storageBasePath);
            if (!Files.exists(storagePath)) {
                log.warn("저장소 경로가 존재하지 않음: {}", storageBasePath);
                return false;
            }
            
            boolean deleted = false;
            
            // 대분류 디렉토리 순회
            try (DirectoryStream<Path> majorDirs = Files.newDirectoryStream(storagePath)) {
                for (Path majorDir : majorDirs) {
                    if (!Files.isDirectory(majorDir)) continue;
                    
                    // 중분류 디렉토리 순회
                    try (DirectoryStream<Path> midDirs = Files.newDirectoryStream(majorDir)) {
                        for (Path midDir : midDirs) {
                            if (!Files.isDirectory(midDir)) continue;
                            
                            // 소분류 디렉토리 순회
                            try (DirectoryStream<Path> subDirs = Files.newDirectoryStream(midDir)) {
                                for (Path subDir : subDirs) {
                                    if (!Files.isDirectory(subDir)) continue;
                                    
                                    // 해당 디렉토리에서 코드로 시작하는 파일 찾기
                                    try (DirectoryStream<Path> files = Files.newDirectoryStream(subDir)) {
                                        for (Path file : files) {
                                            if (Files.isRegularFile(file)) {
                                                String fileName = file.getFileName().toString();
                                                // 파일명이 코드로 시작하는지 확인
                                                if (fileName.startsWith(code + "_")) {
                                                    boolean fileDeleted = Files.deleteIfExists(file);
                                                    if (fileDeleted) {
                                                        log.info("파일 삭제 성공: {}", file);
                                                        deleted = true;
                                                    } else {
                                                        log.warn("파일 삭제 실패: {}", file);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            if (deleted) {
                log.info("코드 {}의 파일 삭제 완료", code);
            } else {
                log.warn("코드 {}에 해당하는 파일을 찾을 수 없음", code);
            }
            
            return deleted;
            
        } catch (Exception e) {
            log.error("코드로 파일 삭제 중 오류 발생: {}", e.getMessage(), e);
            return false;
        }
    }
}
