package com.company.app.file.service;

import com.company.app.catalog.service.SmartClassificationService;
import com.company.app.chat.service.LLMService;
import com.company.app.common.dto.Category;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileAnalysisService {
    
    private final LLMService llmService;
    private final SmartClassificationService smartClassificationService;
    private final ObjectMapper objectMapper;
    
    // 임시 파일 저장소 (메모리)
    private final Map<String, TempFileInfo> tempFiles = new ConcurrentHashMap<>();
    
    // 임시 파일 정보 클래스
    private static class TempFileInfo {
        private final String fileName;
        private final String filePath;
        private final long fileSize;
        private final long uploadTime;
        
        public TempFileInfo(String fileName, String filePath, long fileSize) {
            this.fileName = fileName;
            this.filePath = filePath;
            this.fileSize = fileSize;
            this.uploadTime = System.currentTimeMillis();
        }
        
        public String getFileName() { return fileName; }
        public String getFilePath() { return filePath; }
        public long getFileSize() { return fileSize; }
        public long getUploadTime() { return uploadTime; }
    }
    
    public FileAnalysisResult analyzeFile(MultipartFile file) throws IOException {
        // 파일에서 텍스트 추출
        String extractedText = extractTextFromFile(file);
        
        if (extractedText.trim().isEmpty()) {
            throw new IllegalArgumentException("파일에서 텍스트를 추출할 수 없습니다.");
        }
        
        // Elasticsearch 기반 스마트 분류 사용
        return analyzeWithSmartClassification(extractedText, file.getOriginalFilename());
    }
    
    private FileAnalysisResult analyzeWithSmartClassification(String extractedText, String fileName) {
        try {
            log.info("스마트 분류를 통한 파일 분석 시작: {}", fileName);
            
            // 1. LLM으로 목적과 제목 생성
            String proposedPurpose = generatePurpose(extractedText);
            String proposedTitle = generateTitle(fileName, extractedText);
            
            // 2. 스마트 분류로 카테고리 결정 (문서 요약과 제목 사용)
            var classificationResult = smartClassificationService.classifyDocument(proposedPurpose, proposedTitle);
            Category proposedCategory = classificationResult.getSelectedCategory();
            
            log.info("스마트 분류 완료: {} -> {}", fileName, proposedCategory.getFullName());
            
            FileAnalysisResult result = new FileAnalysisResult();
            result.setProposedCategory(proposedCategory);
            result.setProposedPurpose(proposedPurpose);
            result.setProposedTitle(proposedTitle);
            return result;
            
        } catch (Exception e) {
            log.warn("스마트 분류 실패, LLM 분석으로 fallback: {}", e.getMessage());
            return analyzeWithLLM(extractedText, fileName);
        }
    }
    
    private String generatePurpose(String extractedText) {
        try {
            String prompt = String.format("""
                다음 문서의 목적이나 용도를 한 줄로 간단히 설명해주세요:
                
                %s
                
                응답은 간단한 텍스트로만 해주세요 (JSON 형식 말고).
                """, extractedText.length() > 1000 ? extractedText.substring(0, 1000) + "..." : extractedText);
            
            return llmService.generateText(prompt).trim();
        } catch (Exception e) {
            log.warn("목적 생성 실패: {}", e.getMessage());
            return "문서 목적 분석 중";
        }
    }
    
    private String generateTitle(String fileName, String extractedText) {
        try {
            String baseTitle = removeFileExtension(fileName);
            
            // 파일명이 의미있는 경우 그대로 사용
            if (baseTitle.length() > 3 && !baseTitle.matches(".*\\d{4,}.*")) {
                return baseTitle;
            }
            
            // 파일명이 의미없는 경우 내용에서 제목 생성
            String prompt = String.format("""
                다음 문서 내용에 적합한 제목을 생성해주세요:
                
                %s
                
                응답은 간단한 제목만 해주세요 (JSON 형식 말고).
                """, extractedText.length() > 500 ? extractedText.substring(0, 500) + "..." : extractedText);
            
            return llmService.generateText(prompt).trim();
        } catch (Exception e) {
            log.warn("제목 생성 실패: {}", e.getMessage());
            return removeFileExtension(fileName);
        }
    }
    
    private String extractTextFromFile(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new IllegalArgumentException("파일명이 없습니다.");
        }
        
        String extension = getFileExtension(fileName).toLowerCase();
        
        try (InputStream inputStream = file.getInputStream()) {
            switch (extension) {
                case "docx":
                    return extractTextFromDocx(inputStream);
                case "xlsx":
                    return extractTextFromXlsx(inputStream);
                case "pdf":
                    return extractTextFromPdf(inputStream);
                default:
                    throw new IllegalArgumentException("지원되지 않는 파일 형식입니다: " + extension);
            }
        }
    }
    
    private String extractTextFromDocx(InputStream inputStream) throws IOException {
        StringBuilder text = new StringBuilder();
        
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String paragraphText = paragraph.getText();
                if (paragraphText != null && !paragraphText.trim().isEmpty()) {
                    text.append(paragraphText).append("\n");
                }
            }
        }
        
        return text.toString();
    }
    
    private String extractTextFromXlsx(InputStream inputStream) throws IOException {
        StringBuilder text = new StringBuilder();
        
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                text.append("시트: ").append(sheet.getSheetName()).append("\n");
                
                for (Row row : sheet) {
                    List<String> rowData = new ArrayList<>();
                    for (Cell cell : row) {
                        String cellValue = getCellValueAsString(cell);
                        if (cellValue != null && !cellValue.trim().isEmpty()) {
                            rowData.add(cellValue);
                        }
                    }
                    if (!rowData.isEmpty()) {
                        text.append(String.join("\t", rowData)).append("\n");
                    }
                }
                text.append("\n");
            }
        }
        
        return text.toString();
    }
    
    private String extractTextFromPdf(InputStream inputStream) throws IOException {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (Exception e) {
            log.warn("PDF 텍스트 추출 실패: {}", e.getMessage());
            throw new IOException("PDF 파일 텍스트 추출에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }
    
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return fileName.substring(lastDotIndex + 1);
    }
    
    private FileAnalysisResult analyzeWithLLM(String extractedText, String fileName) {
        try {
            String prompt = buildAnalysisPrompt(extractedText, fileName);
            String response = llmService.generateText(prompt);
            
            return parseAnalysisResponse(response);
        } catch (Exception e) {
            log.error("LLM 분석 실패", e);
            return createDefaultAnalysisResult(fileName);
        }
    }
    
    private String buildAnalysisPrompt(String extractedText, String fileName) {
        String titleWithoutExtension = removeFileExtension(fileName);
        return String.format("""
            다음은 '%s' 파일에서 추출한 내용입니다. 이 문서를 분석하여 적절한 카테고리와 목적을 제안해주세요.
            
            추출된 내용:
            %s
            
            다음 JSON 형식으로 응답해주세요:
            {
              "proposedCategory": {
                "majorCode": "A",
                "majorName": "소프트웨어",
                "midCode": "A01",
                "midName": "웹개발",
                "subCode": "A0101",
                "subName": "프론트엔드"
              },
              "proposedPurpose": "문서의 목적이나 용도를 간단히 설명",
              "proposedTitle": "%s"
            }
            
            카테고리 코드는 다음 규칙을 따르세요:
            - 대분류: A(소프트웨어), B(하드웨어), C(데이터), D(기타)
            - 중분류: A01(웹개발), A02(모바일), A03(데스크톱), A04(기타)
            - 소분류: A0101(프론트엔드), A0102(백엔드), A0103(풀스택), A0104(기타)
            
            proposedTitle은 원본 파일명(확장자 제외)을 그대로 사용하세요: "%s"
            
            응답은 반드시 유효한 JSON 형식이어야 합니다.
            """, fileName, extractedText.length() > 2000 ? extractedText.substring(0, 2000) + "..." : extractedText, titleWithoutExtension, titleWithoutExtension);
    }
    
    private FileAnalysisResult parseAnalysisResponse(String response) {
        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            
            FileAnalysisResult result = new FileAnalysisResult();
            
            // 카테고리 파싱
            JsonNode categoryNode = jsonNode.get("proposedCategory");
            if (categoryNode != null) {
                Category category = new Category();
                category.setMajorCode(categoryNode.get("majorCode").asText());
                category.setMajorName(categoryNode.get("majorName").asText());
                category.setMidCode(categoryNode.get("midCode").asText());
                category.setMidName(categoryNode.get("midName").asText());
                category.setSubCode(categoryNode.get("subCode").asText());
                category.setSubName(categoryNode.get("subName").asText());
                result.setProposedCategory(category);
            }
            
            // 목적 파싱
            if (jsonNode.has("proposedPurpose")) {
                result.setProposedPurpose(jsonNode.get("proposedPurpose").asText());
            }
            
            // 제목 파싱
            if (jsonNode.has("proposedTitle")) {
                result.setProposedTitle(jsonNode.get("proposedTitle").asText());
            }
            
            return result;
        } catch (Exception e) {
            log.error("LLM 응답 파싱 실패: {}", response, e);
            return createDefaultAnalysisResult("문서");
        }
    }
    
    private FileAnalysisResult createDefaultAnalysisResult(String fileName) {
        FileAnalysisResult result = new FileAnalysisResult();
        
        // 기본 카테고리 설정
        Category defaultCategory = new Category();
        defaultCategory.setMajorCode("D");
        defaultCategory.setMajorName("기타");
        defaultCategory.setMidCode("D01");
        defaultCategory.setMidName("문서");
        defaultCategory.setSubCode("D0101");
        defaultCategory.setSubName("일반문서");
        result.setProposedCategory(defaultCategory);
        
        result.setProposedPurpose("문서의 목적을 확인해주세요");
        // 원본 파일명을 제목으로 사용 (확장자 제거)
        String titleWithoutExtension = removeFileExtension(fileName);
        result.setProposedTitle(titleWithoutExtension);
        
        return result;
    }
    
    private String removeFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return fileName;
        }
        return fileName.substring(0, lastDotIndex);
    }
    
    public static class FileAnalysisResult {
        private Category proposedCategory;
        private String proposedPurpose;
        private String proposedTitle;
        
        // Getters and Setters
        public Category getProposedCategory() {
            return proposedCategory;
        }
        
        public void setProposedCategory(Category proposedCategory) {
            this.proposedCategory = proposedCategory;
        }
        
        public String getProposedPurpose() {
            return proposedPurpose;
        }
        
        public void setProposedPurpose(String proposedPurpose) {
            this.proposedPurpose = proposedPurpose;
        }
        
        public String getProposedTitle() {
            return proposedTitle;
        }
        
        public void setProposedTitle(String proposedTitle) {
            this.proposedTitle = proposedTitle;
        }
    }
    
    /**
     * 임시 파일 저장
     */
    public String saveTemporaryFile(MultipartFile file) throws IOException {
        String fileId = UUID.randomUUID().toString();
        String originalFileName = file.getOriginalFilename();
        
        // 임시 디렉토리에 파일 저장
        Path tempDir = Paths.get("/app/temp");
        Files.createDirectories(tempDir);
        
        String fileName = fileId + "_" + originalFileName;
        Path filePath = tempDir.resolve(fileName);
        
        // 파일 저장
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        
        // 메모리에 파일 정보 저장
        tempFiles.put(fileId, new TempFileInfo(originalFileName, filePath.toString(), file.getSize()));
        
        log.info("임시 파일 저장 완료: {} -> {}", originalFileName, filePath);
        return fileId;
    }
    
    /**
     * 임시 파일 조회
     */
    public byte[] getTemporaryFile(String fileId) throws IOException {
        TempFileInfo fileInfo = tempFiles.get(fileId);
        if (fileInfo == null) {
            throw new IOException("파일을 찾을 수 없습니다: " + fileId);
        }
        
        Path filePath = Paths.get(fileInfo.getFilePath());
        if (!Files.exists(filePath)) {
            throw new IOException("파일이 존재하지 않습니다: " + filePath);
        }
        
        return Files.readAllBytes(filePath);
    }
    
    /**
     * 임시 파일명 조회
     */
    public String getTemporaryFileName(String fileId) {
        TempFileInfo fileInfo = tempFiles.get(fileId);
        return fileInfo != null ? fileInfo.getFileName() : null;
    }
    
    /**
     * 임시 파일 삭제
     */
    public void deleteTemporaryFile(String fileId) {
        TempFileInfo fileInfo = tempFiles.remove(fileId);
        if (fileInfo != null) {
            try {
                Files.deleteIfExists(Paths.get(fileInfo.getFilePath()));
                log.info("임시 파일 삭제 완료: {}", fileInfo.getFilePath());
            } catch (IOException e) {
                log.warn("임시 파일 삭제 실패: {}", fileInfo.getFilePath(), e);
            }
        }
    }
    
    /**
     * 오래된 임시 파일 정리 (24시간 이상 된 파일)
     */
    public void cleanupOldTempFiles() {
        long cutoffTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000); // 24시간

        tempFiles.entrySet().removeIf(entry -> {
            if (entry.getValue().getUploadTime() < cutoffTime) {
                try {
                    Files.deleteIfExists(Paths.get(entry.getValue().getFilePath()));
                    log.info("오래된 임시 파일 삭제: {}", entry.getValue().getFilePath());
                } catch (IOException e) {
                    log.warn("오래된 임시 파일 삭제 실패: {}", entry.getValue().getFilePath(), e);
                }
                return true;
            }
            return false;
        });
    }

    public String extractTextFromTemporaryFile(String fileId) throws IOException {
        TempFileInfo fileInfo = tempFiles.get(fileId);
        if (fileInfo == null) {
            throw new IOException("파일을 찾을 수 없습니다: " + fileId);
        }

        Path filePath = Paths.get(fileInfo.getFilePath());
        if (!Files.exists(filePath)) {
            throw new IOException("파일이 존재하지 않습니다: " + filePath);
        }

        // 파일 확장자에 따라 텍스트 추출
        String fileName = fileInfo.getFileName();
        String extension = getFileExtension(fileName).toLowerCase();
        
        try (InputStream is = Files.newInputStream(filePath)) {
            if ("docx".equals(extension)) {
                XWPFDocument document = new XWPFDocument(is);
                StringBuilder text = new StringBuilder();
                for (XWPFParagraph p : document.getParagraphs()) {
                    text.append(p.getText()).append("\n");
                }
                return text.toString();
            } else if ("xlsx".equals(extension)) {
                XSSFWorkbook workbook = new XSSFWorkbook(is);
                StringBuilder text = new StringBuilder();
                for (Sheet sheet : workbook) {
                    for (Row row : sheet) {
                        for (Cell cell : row) {
                            text.append(cell.toString()).append(" ");
                        }
                        text.append("\n");
                    }
                }
                return text.toString();
            } else if ("pdf".equals(extension)) {
                try (PDDocument document = PDDocument.load(is)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    return stripper.getText(document);
                } catch (Exception e) {
                    log.warn("PDF 텍스트 추출 실패: {}", e.getMessage());
                    return "PDF 파일 텍스트 추출에 실패했습니다.";
                }
            } else {
                // 기타 파일은 텍스트로 읽기
                return Files.readString(filePath, StandardCharsets.UTF_8);
            }
        }
    }
}
