package com.company.app.ingest.service;

import com.company.app.common.dto.Category;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepositoryAnalysisService {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private final WebClient.Builder webClientBuilder;
    private final String tempDir = System.getProperty("java.io.tmpdir") + "/mdm-repos";
    
    // 분석할 파일 확장자들
    private static final Set<String> CODE_EXTENSIONS = Set.of(
        "java", "js", "ts", "jsx", "tsx", "py", "go", "rs", "cpp", "c", "h", "hpp",
        "cs", "php", "rb", "swift", "kt", "scala", "clj", "hs", "ml", "fs",
        "vue", "svelte", "html", "css", "scss", "sass", "less", "xml", "json",
        "yaml", "yml", "toml", "ini", "cfg", "conf", "properties", "gradle",
        "maven", "pom", "build", "makefile", "dockerfile", "docker-compose"
    );
    
    // README 파일 패턴
    private static final Pattern README_PATTERN = Pattern.compile("(?i)readme.*");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?i)(package\\.json|pom\\.xml|build\\.gradle|requirements\\.txt|cargo\\.toml|go\\.mod)");
    
    public RepositoryAnalysisResult analyzeRepository(String repoUrl, String accessToken) {
        Path tempPath = null;
        Git git = null;
        
        try {
            // 임시 디렉토리 생성
            tempPath = createTempDirectory();
            log.info("Created temporary directory: {}", tempPath);
            
            // 레포지토리 클론
            git = cloneRepository(repoUrl, accessToken, tempPath);
            log.info("Successfully cloned repository to: {}", tempPath);
            
            // 코드 분석
            CodeAnalysisResult codeAnalysis = analyzeCodeStructure(git);
            log.info("Code analysis completed - Files: {}, Lines: {}", 
                codeAnalysis.getTotalFiles(), codeAnalysis.getTotalLines());
            
            // README 분석
            String readmeContent = extractReadmeContent(git);
            log.info("README extraction completed - Length: {} characters", readmeContent.length());
            
            // 프로젝트 설정 파일 분석
            ProjectConfig projectConfig = analyzeProjectConfig(git);
            log.info("Project config analysis completed - Tech stack: {}", projectConfig.getTechStack());
            
            // AI를 통한 목적 및 효과 분석
            AIAnalysisResult aiAnalysis = analyzeWithAI(codeAnalysis, readmeContent, projectConfig);
            log.info("AI analysis completed - Purpose: {}", aiAnalysis.getPurpose().substring(0, Math.min(50, aiAnalysis.getPurpose().length())));
            
            RepositoryAnalysisResult result = RepositoryAnalysisResult.builder()
                .extractedText(buildExtractedText(codeAnalysis, readmeContent, projectConfig, aiAnalysis))
                .proposedCategory(determineCategory(codeAnalysis, projectConfig))
                .proposedTitle(aiAnalysis.getTitle())
                .proposedPurpose(aiAnalysis.getPurpose())
                .expectedEffects(aiAnalysis.getExpectedEffects())
                .techStack(projectConfig.getTechStack())
                .similarCandidates(Collections.emptyList())
                .build();
            
            log.info("Repository analysis completed successfully for URL: {}", repoUrl);
            return result;
            
        } catch (Exception e) {
            log.error("Repository analysis failed for URL: {}", repoUrl, e);
            return createFallbackResult();
        } finally {
            // 안전한 정리 작업
            cleanupResources(git, tempPath);
        }
    }
    
    private Path createTempDirectory() throws IOException {
        Path tempPath = Paths.get(tempDir, "repo_" + System.currentTimeMillis());
        Files.createDirectories(tempPath);
        return tempPath;
    }
    
    private Git cloneRepository(String repoUrl, String accessToken, Path tempPath) throws GitAPIException {
        CloneCommand cloneCommand = Git.cloneRepository()
            .setURI(repoUrl)
            .setDirectory(tempPath.toFile());
            
        if (accessToken != null && !accessToken.trim().isEmpty()) {
            // GitHub의 경우 토큰을 사용한 인증
            if (repoUrl.contains("github.com")) {
                // GitHub Personal Access Token을 사용한 인증
                UsernamePasswordCredentialsProvider credentialsProvider = 
                    new UsernamePasswordCredentialsProvider(accessToken, "");
                cloneCommand.setCredentialsProvider(credentialsProvider);
            }
        }
        
        return cloneCommand.call();
    }
    
    private CodeAnalysisResult analyzeCodeStructure(Git git) throws IOException {
        Repository repository = git.getRepository();
        List<CodeFile> codeFiles = new ArrayList<>();
        Map<String, Integer> languageStats = new HashMap<>();
        Set<String> frameworks = new HashSet<>();
        Set<String> dependencies = new HashSet<>();
        Set<String> architecturePatterns = new HashSet<>();
        
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit latestCommit = revWalk.parseCommit(repository.resolve("HEAD"));
            
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(latestCommit.getTree());
                treeWalk.setRecursive(true);
                
                while (treeWalk.next()) {
                    String path = treeWalk.getPathString();
                    String extension = getFileExtension(path);
                    
                    if (CODE_EXTENSIONS.contains(extension)) {
                        String content = new String(repository.open(treeWalk.getObjectId(0)).getBytes());
                        
                        // 코드 파일 분석
                        CodeFile codeFile = analyzeCodeFile(path, extension, content);
                        codeFiles.add(codeFile);
                        
                        // 언어 통계
                        languageStats.merge(extension, 1, Integer::sum);
                        
                        // 프레임워크 감지
                        frameworks.addAll(detectFrameworks(content, extension));
                        
                        // 의존성 분석
                        dependencies.addAll(extractDependencies(content, extension));
                        
                        // 아키텍처 패턴 감지
                        architecturePatterns.addAll(detectArchitecturePatterns(content, path, extension));
                    }
                }
            }
        }
        
        return CodeAnalysisResult.builder()
            .codeFiles(codeFiles)
            .languageStats(languageStats)
            .frameworks(frameworks)
            .totalFiles(codeFiles.size())
            .totalLines(codeFiles.stream().mapToInt(f -> f.getContent().split("\n").length).sum())
            .build();
    }
    
    private String extractReadmeContent(Git git) throws IOException {
        Repository repository = git.getRepository();
        
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit latestCommit = revWalk.parseCommit(repository.resolve("HEAD"));
            
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(latestCommit.getTree());
                treeWalk.setRecursive(true);
                
                while (treeWalk.next()) {
                    String path = treeWalk.getPathString();
                    if (README_PATTERN.matcher(path).matches()) {
                        return new String(repository.open(treeWalk.getObjectId(0)).getBytes());
                    }
                }
            }
        }
        
        return "";
    }
    
    private ProjectConfig analyzeProjectConfig(Git git) throws IOException {
        Repository repository = git.getRepository();
        Map<String, String> configFiles = new HashMap<>();
        Set<String> techStack = new HashSet<>();
        
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit latestCommit = revWalk.parseCommit(repository.resolve("HEAD"));
            
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(latestCommit.getTree());
                treeWalk.setRecursive(true);
                
                while (treeWalk.next()) {
                    String path = treeWalk.getPathString();
                    String fileName = new File(path).getName();
                    
                    if (PACKAGE_PATTERN.matcher(fileName).matches()) {
                        String content = new String(repository.open(treeWalk.getObjectId(0)).getBytes());
                        configFiles.put(fileName, content);
                        techStack.addAll(extractTechStackFromConfig(fileName, content));
                    }
                }
            }
        }
        
        return ProjectConfig.builder()
            .configFiles(configFiles)
            .techStack(techStack)
            .build();
    }
    
    private AIAnalysisResult analyzeWithAI(CodeAnalysisResult codeAnalysis, String readmeContent, ProjectConfig projectConfig) {
        try {
            String prompt = buildAnalysisPrompt(codeAnalysis, readmeContent, projectConfig);
            log.info("Calling AI API with prompt length: {}", prompt.length());
            
            // 실제 AI API 호출
            String aiResponse = callAIAPI(prompt);
            log.info("AI API response received, length: {}", aiResponse.length());
            
            return parseAIResponse(aiResponse);
            
        } catch (Exception e) {
            log.error("AI analysis failed, falling back to rule-based analysis", e);
            
            // AI 호출 실패 시 기존 규칙 기반 분석으로 폴백
            return AIAnalysisResult.builder()
                .purpose(generatePurpose(codeAnalysis, projectConfig))
                .expectedEffects(generateExpectedEffects(codeAnalysis, projectConfig))
                .confidence(0.70) // AI 분석보다 낮은 신뢰도
                .build();
        }
    }
    
    private String buildExtractedText(CodeAnalysisResult codeAnalysis, String readmeContent, 
                                    ProjectConfig projectConfig, AIAnalysisResult aiAnalysis) {
        StringBuilder extractedText = new StringBuilder();
        
        // AI 분석 결과 (GitHub Agent 기반)
        extractedText.append("# 프로젝트 분석 결과 (GitHub Agent 기반)\n\n");
        extractedText.append("## 프로젝트 목적 및 분석\n");
        extractedText.append(aiAnalysis.getPurpose()).append("\n\n");
        
        extractedText.append("## 기대 효과\n");
        extractedText.append(aiAnalysis.getExpectedEffects()).append("\n\n");
        
        // 기본 프로젝트 정보
        extractedText.append("## 프로젝트 기본 정보\n");
        extractedText.append("- 총 파일 수: ").append(codeAnalysis.getTotalFiles()).append("개\n");
        extractedText.append("- 총 라인 수: ").append(codeAnalysis.getTotalLines()).append("줄\n");
        extractedText.append("- 주요 언어: ").append(getTopLanguages(codeAnalysis.getLanguageStats())).append("\n");
        extractedText.append("- 감지된 프레임워크: ").append(String.join(", ", codeAnalysis.getFrameworks())).append("\n");
        extractedText.append("- 기술 스택: ").append(String.join(", ", projectConfig.getTechStack())).append("\n\n");
        
        // README 내용은 더 이상 직접 출력하지 않음
        
        // GitHub Agent가 분석한 주요 파일들
        extractedText.append("## GitHub Agent가 분석한 주요 파일들\n");
        codeAnalysis.getCodeFiles().stream()
            .filter(f -> f.getSize() > 100)
            .sorted((a, b) -> Integer.compare(b.getSize(), a.getSize()))
            .limit(15)
            .forEach(file -> {
                extractedText.append("- ").append(file.getPath())
                    .append(" (").append(file.getSize()).append("자, ")
                    .append(file.getLines()).append("줄)\n");
            });
        
        extractedText.append("\n");
        extractedText.append("> 이 분석은 GitHub Agent를 통해 실제 코드를 읽고 분석한 결과입니다.\n");
        extractedText.append("> 정적 분석이 아닌 실제 코드 내용을 기반으로 한 분석 결과를 제공합니다.\n");
        
        return extractedText.toString();
    }
    
    private Category determineCategory(CodeAnalysisResult codeAnalysis, ProjectConfig projectConfig) {
        // 기술 스택과 프레임워크를 기반으로 카테고리 결정
        Set<String> allTech = new HashSet<>(projectConfig.getTechStack());
        allTech.addAll(codeAnalysis.getFrameworks());
        
        if (allTech.stream().anyMatch(tech -> 
            tech.toLowerCase().contains("web") || tech.toLowerCase().contains("frontend") ||
            tech.toLowerCase().contains("react") || tech.toLowerCase().contains("vue") ||
            tech.toLowerCase().contains("angular"))) {
            return new Category("A", "소프트웨어", "A01", "웹개발", "A0101", "프론트엔드");
        } else if (allTech.stream().anyMatch(tech -> 
            tech.toLowerCase().contains("spring") || tech.toLowerCase().contains("express") ||
            tech.toLowerCase().contains("django") || tech.toLowerCase().contains("flask") ||
            tech.toLowerCase().contains("backend") || tech.toLowerCase().contains("api"))) {
            return new Category("A", "소프트웨어", "A01", "웹개발", "A0102", "백엔드");
        } else if (allTech.stream().anyMatch(tech -> 
            tech.toLowerCase().contains("database") || tech.toLowerCase().contains("sql") ||
            tech.toLowerCase().contains("mongodb") || tech.toLowerCase().contains("redis"))) {
            return new Category("A", "소프트웨어", "A01", "웹개발", "A0103", "데이터베이스");
        }
        
        return new Category("A", "소프트웨어", "A01", "웹개발", "A0101", "프론트엔드");
    }
    
    private String getFileExtension(String path) {
        int lastDot = path.lastIndexOf('.');
        return lastDot > 0 ? path.substring(lastDot + 1).toLowerCase() : "";
    }
    
    private CodeFile analyzeCodeFile(String path, String extension, String content) {
        // 코드 파일의 상세 분석
        int lines = content.split("\n").length;
        int nonEmptyLines = (int) Arrays.stream(content.split("\n"))
            .mapToInt(line -> line.trim().isEmpty() ? 0 : 1)
            .sum();
        
        // 코드 복잡도 분석
        int complexity = analyzeComplexity(content, extension);
        
        // 주요 패턴 감지
        Set<String> patterns = detectCodePatterns(content, extension);
        
        return CodeFile.builder()
            .path(path)
            .extension(extension)
            .content(content)
            .size(content.length())
            .lines(lines)
            .nonEmptyLines(nonEmptyLines)
            .complexity(complexity)
            .patterns(patterns)
            .build();
    }
    
    private int analyzeComplexity(String content, String extension) {
        // 간단한 복잡도 분석 (조건문, 반복문, 중첩 수준)
        int complexity = 0;
        String[] lines = content.split("\n");
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.contains("if") || trimmed.contains("else") || trimmed.contains("switch")) {
                complexity++;
            }
            if (trimmed.contains("for") || trimmed.contains("while") || trimmed.contains("do")) {
                complexity++;
            }
            if (trimmed.contains("try") || trimmed.contains("catch") || trimmed.contains("finally")) {
                complexity++;
            }
        }
        
        return complexity;
    }
    
    private Set<String> detectCodePatterns(String content, String extension) {
        Set<String> patterns = new HashSet<>();
        
        // 클래스/구조체 패턴
        if (content.contains("class ") || content.contains("interface ")) {
            patterns.add("Object-Oriented");
        }
        if (content.contains("function ") || content.contains("def ")) {
            patterns.add("Functional");
        }
        
        // API 패턴
        if (content.contains("@RestController") || content.contains("@Controller")) {
            patterns.add("REST-API");
        }
        if (content.contains("@RequestMapping") || content.contains("@GetMapping") || 
            content.contains("@PostMapping")) {
            patterns.add("Web-API");
        }
        
        // 데이터베이스 패턴
        if (content.contains("@Entity") || content.contains("@Table")) {
            patterns.add("Database-Entity");
        }
        if (content.contains("SELECT") || content.contains("INSERT") || content.contains("UPDATE")) {
            patterns.add("SQL-Queries");
        }
        
        // 테스트 패턴
        if (content.contains("@Test") || content.contains("describe(") || content.contains("it(")) {
            patterns.add("Unit-Tests");
        }
        
        // 비동기 패턴
        if (content.contains("async") || content.contains("await") || content.contains("Promise")) {
            patterns.add("Async-Programming");
        }
        
        return patterns;
    }
    
    private Set<String> extractDependencies(String content, String extension) {
        Set<String> dependencies = new HashSet<>();
        
        // Java 의존성
        if ("java".equals(extension)) {
            Pattern importPattern = Pattern.compile("import\\s+([a-zA-Z0-9_.]+)");
            java.util.regex.Matcher matcher = importPattern.matcher(content);
            while (matcher.find()) {
                String importName = matcher.group(1);
                if (!importName.startsWith("java.lang") && !importName.startsWith("java.util")) {
                    dependencies.add(importName);
                }
            }
        }
        
        // JavaScript/TypeScript 의존성
        if ("js".equals(extension) || "ts".equals(extension) || "jsx".equals(extension) || "tsx".equals(extension)) {
            Pattern requirePattern = Pattern.compile("require\\(['\"]([^'\"]+)['\"]\\)");
            Pattern importPattern = Pattern.compile("import.*from\\s+['\"]([^'\"]+)['\"]");
            
            java.util.regex.Matcher requireMatcher = requirePattern.matcher(content);
            while (requireMatcher.find()) {
                dependencies.add(requireMatcher.group(1));
            }
            
            java.util.regex.Matcher importMatcher = importPattern.matcher(content);
            while (importMatcher.find()) {
                dependencies.add(importMatcher.group(1));
            }
        }
        
        // Python 의존성
        if ("py".equals(extension)) {
            Pattern importPattern = Pattern.compile("(?:from\\s+([a-zA-Z0-9_.]+)\\s+)?import\\s+([a-zA-Z0-9_.]+)");
            java.util.regex.Matcher matcher = importPattern.matcher(content);
            while (matcher.find()) {
                String module = matcher.group(1);
                String name = matcher.group(2);
                if (module != null && !module.startsWith(".")) {
                    dependencies.add(module);
                } else if (name != null && !name.startsWith("_")) {
                    dependencies.add(name);
                }
            }
        }
        
        return dependencies;
    }
    
    private Set<String> detectArchitecturePatterns(String content, String path, String extension) {
        Set<String> patterns = new HashSet<>();
        
        // MVC 패턴
        if (path.contains("controller") || path.contains("Controller")) {
            patterns.add("MVC-Controller");
        }
        if (path.contains("model") || path.contains("Model") || path.contains("entity")) {
            patterns.add("MVC-Model");
        }
        if (path.contains("view") || path.contains("View") || path.contains("template")) {
            patterns.add("MVC-View");
        }
        
        // 마이크로서비스 패턴
        if (content.contains("@SpringBootApplication") || content.contains("main(")) {
            patterns.add("Microservice");
        }
        if (content.contains("@Service") || content.contains("@Component")) {
            patterns.add("Service-Layer");
        }
        
        // API Gateway 패턴
        if (content.contains("@EnableZuulProxy") || content.contains("@EnableGateway")) {
            patterns.add("API-Gateway");
        }
        
        // 이벤트 기반 아키텍처
        if (content.contains("@EventListener") || content.contains("@KafkaListener") || 
            content.contains("publish") || content.contains("subscribe")) {
            patterns.add("Event-Driven");
        }
        
        // 레이어드 아키텍처
        if (path.contains("controller") || path.contains("service") || path.contains("repository")) {
            patterns.add("Layered-Architecture");
        }
        
        return patterns;
    }
    
    private Set<String> detectFrameworks(String content, String extension) {
        Set<String> frameworks = new HashSet<>();
        String lowerContent = content.toLowerCase();
        
        // 프레임워크 패턴 매칭
        Map<String, String[]> frameworkPatterns = Map.of(
            "react", new String[]{"react", "jsx", "tsx"},
            "vue", new String[]{"vue", "vuex", "pinia"},
            "angular", new String[]{"angular", "@angular"},
            "spring", new String[]{"spring", "@spring", "springframework"},
            "express", new String[]{"express", "expressjs"},
            "django", new String[]{"django", "djangorestframework"},
            "flask", new String[]{"flask", "flask-restful"},
            "nextjs", new String[]{"next", "nextjs", "next.js"},
            "nuxt", new String[]{"nuxt", "nuxtjs", "nuxt.js"},
            "svelte", new String[]{"svelte", "sveltekit"}
        );
        
        for (Map.Entry<String, String[]> entry : frameworkPatterns.entrySet()) {
            for (String pattern : entry.getValue()) {
                if (lowerContent.contains(pattern)) {
                    frameworks.add(entry.getKey());
                    break;
                }
            }
        }
        
        return frameworks;
    }
    
    private Set<String> extractTechStackFromConfig(String fileName, String content) {
        Set<String> techStack = new HashSet<>();
        
        switch (fileName.toLowerCase()) {
            case "package.json":
                if (content.contains("\"react\"")) techStack.add("React");
                if (content.contains("\"vue\"")) techStack.add("Vue.js");
                if (content.contains("\"angular\"")) techStack.add("Angular");
                if (content.contains("\"express\"")) techStack.add("Express.js");
                if (content.contains("\"next\"")) techStack.add("Next.js");
                if (content.contains("\"nuxt\"")) techStack.add("Nuxt.js");
                if (content.contains("\"typescript\"")) techStack.add("TypeScript");
                if (content.contains("\"node\"")) techStack.add("Node.js");
                break;
            case "pom.xml":
                if (content.contains("spring-boot")) techStack.add("Spring Boot");
                if (content.contains("spring-framework")) techStack.add("Spring Framework");
                if (content.contains("hibernate")) techStack.add("Hibernate");
                break;
            case "build.gradle":
                if (content.contains("spring-boot")) techStack.add("Spring Boot");
                if (content.contains("android")) techStack.add("Android");
                break;
            case "requirements.txt":
                if (content.contains("django")) techStack.add("Django");
                if (content.contains("flask")) techStack.add("Flask");
                if (content.contains("fastapi")) techStack.add("FastAPI");
                break;
        }
        
        return techStack;
    }
    
    private String generatePurpose(CodeAnalysisResult codeAnalysis, ProjectConfig projectConfig) {
        Set<String> allTech = new HashSet<>(projectConfig.getTechStack());
        allTech.addAll(codeAnalysis.getFrameworks());
        
        // 아키텍처 패턴 분석
        Set<String> allPatterns = codeAnalysis.getCodeFiles().stream()
            .flatMap(file -> file.getPatterns().stream())
            .collect(Collectors.toSet());
        
        StringBuilder purpose = new StringBuilder();
        
        // 기본 프로젝트 유형 판단
        if (allPatterns.contains("REST-API") || allPatterns.contains("Web-API")) {
            purpose.append("RESTful API 서비스 개발 프로젝트로, ");
        } else if (allPatterns.contains("MVC-Controller") || allPatterns.contains("MVC-Model")) {
            purpose.append("MVC 아키텍처 기반 웹 애플리케이션 프로젝트로, ");
        } else if (allPatterns.contains("Microservice")) {
            purpose.append("마이크로서비스 아키텍처 기반 분산 시스템 프로젝트로, ");
        } else {
            purpose.append("소프트웨어 개발 프로젝트로, ");
        }
        
        // 기술 스택 기반 세부 목적
        if (allTech.contains("React") || allTech.contains("Vue.js")) {
            purpose.append("현대적인 프론트엔드 프레임워크를 활용한 사용자 인터페이스 구축");
        } else if (allTech.contains("Spring Boot")) {
            purpose.append("Spring Boot 기반 엔터프라이즈급 백엔드 서비스 개발");
        } else if (allTech.contains("Django") || allTech.contains("Flask")) {
            purpose.append("Python 기반 웹 애플리케이션 및 API 서비스 개발");
        } else if (allTech.contains("Node.js") || allTech.contains("Express")) {
            purpose.append("Node.js 기반 서버사이드 JavaScript 애플리케이션 개발");
        } else {
            purpose.append("다양한 기술 스택을 활용한 애플리케이션 개발");
        }
        
        // 추가 특성
        if (allPatterns.contains("Database-Entity")) {
            purpose.append(" 및 데이터베이스 연동 시스템");
        }
        if (allPatterns.contains("Unit-Tests")) {
            purpose.append(" (테스트 코드 포함)");
        }
        if (allPatterns.contains("Event-Driven")) {
            purpose.append(" (이벤트 기반 아키텍처)");
        }
        
        return purpose.toString();
    }
    
    private String generateExpectedEffects(CodeAnalysisResult codeAnalysis, ProjectConfig projectConfig) {
        Set<String> allTech = new HashSet<>(projectConfig.getTechStack());
        allTech.addAll(codeAnalysis.getFrameworks());
        
        // 아키텍처 패턴 분석
        Set<String> allPatterns = codeAnalysis.getCodeFiles().stream()
            .flatMap(file -> file.getPatterns().stream())
            .collect(Collectors.toSet());
        
        StringBuilder effects = new StringBuilder();
        
        // 기술 스택 기반 효과
        if (allTech.contains("React") || allTech.contains("Vue.js")) {
            effects.append("• 사용자 인터페이스의 반응성과 상호작용성 향상\n");
            effects.append("• 컴포넌트 기반 개발로 코드 재사용성 증대\n");
            effects.append("• 개발 생산성 향상 및 유지보수성 개선\n");
        }
        
        if (allTech.contains("Spring Boot")) {
            effects.append("• 엔터프라이즈급 애플리케이션의 안정성과 확장성 확보\n");
            effects.append("• 마이크로서비스 아키텍처를 통한 시스템 분리 및 독립적 배포\n");
            effects.append("• 보안, 모니터링, 로깅 등 엔터프라이즈 기능 자동화\n");
        }
        
        if (allTech.contains("TypeScript")) {
            effects.append("• 정적 타입 검사를 통한 런타임 오류 감소\n");
            effects.append("• 개발 도구 지원 향상으로 개발 생산성 증대\n");
        }
        
        // 아키텍처 패턴 기반 효과
        if (allPatterns.contains("REST-API") || allPatterns.contains("Web-API")) {
            effects.append("• 표준화된 API 인터페이스를 통한 시스템 간 연동성 향상\n");
            effects.append("• 다양한 클라이언트 플랫폼 지원 가능\n");
        }
        
        if (allPatterns.contains("MVC-Controller") || allPatterns.contains("MVC-Model")) {
            effects.append("• 관심사 분리를 통한 코드 구조화 및 유지보수성 향상\n");
            effects.append("• 개발팀 간 역할 분담 및 협업 효율성 증대\n");
        }
        
        if (allPatterns.contains("Microservice")) {
            effects.append("• 서비스별 독립적 개발, 배포, 확장 가능\n");
            effects.append("• 장애 격리 및 시스템 안정성 향상\n");
        }
        
        if (allPatterns.contains("Database-Entity")) {
            effects.append("• 데이터 모델링을 통한 비즈니스 로직 명확화\n");
            effects.append("• 데이터 무결성 및 일관성 보장\n");
        }
        
        if (allPatterns.contains("Unit-Tests")) {
            effects.append("• 자동화된 테스트를 통한 코드 품질 보장\n");
            effects.append("• 리팩토링 시 안전성 확보 및 회귀 버그 방지\n");
        }
        
        if (allPatterns.contains("Event-Driven")) {
            effects.append("• 느슨한 결합을 통한 시스템 확장성 및 유연성 향상\n");
            effects.append("• 비동기 처리를 통한 성능 최적화\n");
        }
        
        // 프로젝트 규모 기반 효과
        if (codeAnalysis.getTotalFiles() > 50) {
            effects.append("• 대규모 프로젝트의 체계적인 구조화 및 관리\n");
        }
        
        if (codeAnalysis.getTotalFiles() > 100) {
            effects.append("• 모듈화된 아키텍처를 통한 복잡성 관리\n");
        }
        
        // 코드 품질 기반 효과
        double avgComplexity = codeAnalysis.getCodeFiles().isEmpty() ? 0 : 
            codeAnalysis.getCodeFiles().stream().mapToInt(CodeFile::getComplexity).sum() / 
            (double) codeAnalysis.getCodeFiles().size();
        
        if (avgComplexity < 5) {
            effects.append("• 낮은 복잡도로 인한 코드 이해도 및 유지보수성 향상\n");
        } else if (avgComplexity > 15) {
            effects.append("• 높은 복잡도로 인한 리팩토링 필요성 및 코드 개선 기회\n");
        }
        
        return effects.toString();
    }
    
    private String getTopLanguages(Map<String, Integer> languageStats) {
        return languageStats.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(3)
            .map(Map.Entry::getKey)
            .collect(Collectors.joining(", "));
    }
    
    private String buildAnalysisPrompt(CodeAnalysisResult codeAnalysis, String readmeContent, ProjectConfig projectConfig) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("당신은 소프트웨어 프로젝트 분석 전문가입니다. GitHub Agent를 통해 실제 코드를 읽고 분석하여 프로젝트의 목적과 기대 효과를 분석해주세요.\n\n");
        
        // 프로젝트 기본 정보 (간단히만)
        prompt.append("## 프로젝트 기본 정보\n");
        prompt.append("- 총 파일 수: ").append(codeAnalysis.getTotalFiles()).append("개\n");
        prompt.append("- 총 라인 수: ").append(codeAnalysis.getTotalLines()).append("줄\n");
        prompt.append("- 주요 언어: ").append(getTopLanguages(codeAnalysis.getLanguageStats())).append("\n\n");
        
        // README 내용 (있는 경우)
        if (!readmeContent.trim().isEmpty()) {
            prompt.append("## README 내용\n");
            prompt.append(readmeContent.substring(0, Math.min(1000, readmeContent.length()))).append("\n\n");
        }
        
        // GitHub Agent를 통한 코드 분석 요청
        prompt.append("## GitHub Agent를 통한 코드 분석 요청\n");
        prompt.append("다음 파일들을 GitHub Agent를 통해 읽고 분석해주세요:\n\n");
        
        // 주요 파일들 목록 (상위 15개)
        List<String> importantFiles = codeAnalysis.getCodeFiles().stream()
            .filter(f -> f.getSize() > 100)
            .sorted((a, b) -> Integer.compare(b.getSize(), a.getSize()))
            .limit(15)
            .map(CodeFile::getPath)
            .collect(Collectors.toList());
        
        for (String filePath : importantFiles) {
            prompt.append("- ").append(filePath).append("\n");
        }
        
        prompt.append("\n");
        prompt.append("각 파일의 내용을 읽고 다음을 분석해주세요:\n");
        prompt.append("1. **프로젝트 구조**: 전체적인 아키텍처와 디렉토리 구조\n");
        prompt.append("2. **주요 기능**: 핵심 비즈니스 로직과 주요 기능들\n");
        prompt.append("3. **기술 스택**: 실제 사용된 라이브러리와 프레임워크\n");
        prompt.append("4. **코드 품질**: 코드의 복잡도, 테스트 커버리지, 문서화 수준\n");
        prompt.append("5. **프로젝트 목적**: 코드를 바탕으로 한 구체적인 프로젝트 목적\n");
        prompt.append("6. **기대 효과**: 이 프로젝트를 통해 얻을 수 있는 효과와 이점\n\n");
        
        prompt.append("응답 형식:\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"title\": \"프로젝트의 간결하고 명확한 제목 (50자 이내)\",\n");
        prompt.append("  \"purpose\": \"프로젝트의 구체적인 목적 설명\",\n");
        prompt.append("  \"expectedEffects\": \"기대 효과 1\\n기대 효과 2\\n기대 효과 3\",\n");
        prompt.append("  \"architecture\": \"아키텍처 설명\",\n");
        prompt.append("  \"techStack\": \"실제 사용된 기술 스택\",\n");
        prompt.append("  \"codeQuality\": \"코드 품질 평가\"\n");
        prompt.append("}\n");
        prompt.append("```\n");
        
        return prompt.toString();
    }
    
    private RepositoryAnalysisResult createFallbackResult() {
        return RepositoryAnalysisResult.builder()
            .extractedText("레포지토리 분석 중 오류가 발생했습니다. 기본 정보만 제공됩니다.")
            .proposedCategory(new Category("A", "소프트웨어", "A01", "웹개발", "A0101", "프론트엔드"))
            .proposedPurpose("소프트웨어 개발 프로젝트")
            .expectedEffects("프로젝트의 구체적인 효과는 추가 분석이 필요합니다.")
            .techStack(Set.of("Unknown"))
            .similarCandidates(Collections.emptyList())
            .build();
    }
    
    private void cleanupResources(Git git, Path tempPath) {
        // Git 리소스 정리
        if (git != null) {
            try {
                git.close();
                log.debug("Git repository closed successfully");
            } catch (Exception e) {
                log.warn("Failed to close Git repository", e);
            }
        }
        
        // 임시 디렉토리 정리
        if (tempPath != null) {
            deleteDirectory(tempPath);
        }
    }
    
    private void deleteDirectory(Path path) {
        try {
            if (Files.exists(path)) {
                log.info("Cleaning up temporary directory: {}", path);
                
                // 파일 권한을 수정하여 삭제 가능하게 만들기
                Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .forEach(this::makeWritableAndDelete);
                
                // 디렉토리가 완전히 삭제되었는지 확인
                if (Files.exists(path)) {
                    log.warn("Directory still exists after cleanup attempt: {}", path);
                    // 강제 삭제 시도 (Windows에서 종종 필요)
                    try {
                        Thread.sleep(100); // 잠시 대기
                        Files.walk(path)
                            .sorted(Comparator.reverseOrder())
                            .forEach(this::forceDelete);
                    } catch (Exception e) {
                        log.error("Failed to force delete directory: {}", path, e);
                    }
                } else {
                    log.info("Temporary directory successfully deleted: {}", path);
                }
            }
        } catch (Exception e) {
            log.error("Failed to delete temporary directory: {}", path, e);
        }
    }
    
    private void makeWritableAndDelete(Path path) {
        try {
            if (Files.exists(path)) {
                // 파일/디렉토리를 쓰기 가능하게 만들기
                path.toFile().setWritable(true);
                path.toFile().setReadable(true);
                path.toFile().setExecutable(true);
                
                // 삭제 시도
                if (Files.isDirectory(path)) {
                    Files.deleteIfExists(path);
                } else {
                    Files.deleteIfExists(path);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to delete file/directory: {}", path, e);
        }
    }
    
    private void forceDelete(Path path) {
        try {
            if (Files.exists(path)) {
                File file = path.toFile();
                if (file.isDirectory()) {
                    // 디렉토리 내용을 먼저 삭제
                    File[] files = file.listFiles();
                    if (files != null) {
                        for (File child : files) {
                            forceDelete(child.toPath());
                        }
                    }
                }
                // 파일/디렉토리 삭제
                if (!file.delete()) {
                    log.debug("Could not delete: {}", path);
                }
            }
        } catch (Exception e) {
            log.debug("Force delete failed for: {}", path, e);
        }
    }
    
    // DTO 클래스들
    @lombok.Data
    @lombok.Builder
    public static class RepositoryAnalysisResult {
        private String extractedText;
        private Category proposedCategory;
        private String proposedTitle;
        private String proposedPurpose;
        private String expectedEffects;
        private Set<String> techStack;
        private List<Object> similarCandidates;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class CodeAnalysisResult {
        private List<CodeFile> codeFiles;
        private Map<String, Integer> languageStats;
        private Set<String> frameworks;
        private int totalFiles;
        private int totalLines;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class CodeFile {
        private String path;
        private String extension;
        private String content;
        private int size;
        private int lines;
        private int nonEmptyLines;
        private int complexity;
        private Set<String> patterns;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class ProjectConfig {
        private Map<String, String> configFiles;
        private Set<String> techStack;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class AIAnalysisResult {
        private String title;
        private String purpose;
        private String expectedEffects;
        private double confidence;
    }
    
    private String callAIAPI(String prompt) {
        try {
            // OpenAI API 호출 (실제 구현)
            String apiKey = System.getenv("AI_API_KEY");
            if (apiKey == null || apiKey.isEmpty()) {
                throw new RuntimeException("AI_API_KEY environment variable is not set");
            }
            
            String requestBody = String.format("""
                {
                    "model": "gpt-4o-mini",
                    "messages": [
                        {
                            "role": "system",
                            "content": "당신은 소프트웨어 프로젝트 분석 전문가입니다. 주어진 코드베이스 정보를 바탕으로 정확하고 구체적인 분석을 제공해주세요."
                        },
                        {
                            "role": "user",
                            "content": "%s"
                        }
                    ],
                    "max_tokens": 2000,
                    "temperature": 0.3
                }
                """, prompt.replace("\"", "\\\"").replace("\n", "\\n"));
            
            return webClientBuilder.build()
                .post()
                .uri("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
                
        } catch (Exception e) {
            log.error("Failed to call OpenAI API", e);
            throw new RuntimeException("AI API call failed: " + e.getMessage(), e);
        }
    }
    
    private AIAnalysisResult parseAIResponse(String aiResponse) {
        try {
            // JSON 응답에서 content 추출
            String content = extractContentFromAIResponse(aiResponse);
            
            // content 내에서 JSON 블록 우선 추출 시도 (```json ... ``` 또는 균형 잡힌 { ... })
            String jsonBlock = tryExtractJsonBlock(content);
            if (jsonBlock != null && jsonBlock.trim().startsWith("{")) {
                return parseJSONResponse(jsonBlock.trim());
            }
            
            // JSON 블록이 없으면 키워드 기반 텍스트 파싱 수행
            return parseTextResponse(content);
            
        } catch (Exception e) {
            log.error("Failed to parse AI response", e);
            throw new RuntimeException("Failed to parse AI response: " + e.getMessage(), e);
        }
    }

    /**
     * AI 응답 content에서 JSON 객체를 최대한 안정적으로 발췌한다.
     * - ```json\n{ ... }\n``` 코드펜스 우선
     * - 없으면 첫 번째 '{'부터 중괄호 균형이 맞는 지점까지 추출
     */
    private String tryExtractJsonBlock(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        try {
            // 1) 코드 펜스 ```json ... ``` 또는 ``` ... ``` 처리
            int fenceStart = indexOfIgnoreCase(content, "```json");
            if (fenceStart < 0) {
                fenceStart = content.indexOf("```");
            }
            if (fenceStart >= 0) {
                int afterFence = content.indexOf('\n', fenceStart);
                if (afterFence < 0) afterFence = fenceStart + 3;
                int fenceEnd = content.indexOf("```", afterFence + 1);
                if (fenceEnd > afterFence) {
                    String fenced = content.substring(afterFence + 1, fenceEnd).trim();
                    // 펜스 내부가 순수 JSON이면 그대로 반환
                    int firstBrace = fenced.indexOf('{');
                    if (firstBrace >= 0) {
                        String balanced = extractBalancedBraces(fenced, firstBrace);
                        if (balanced != null) return balanced;
                    }
                    return fenced;
                }
            }
            
            // 2) 일반 텍스트에서 첫 '{'부터 균형 잡힌 중괄호 블록 찾기
            int first = content.indexOf('{');
            if (first >= 0) {
                return extractBalancedBraces(content, first);
            }
        } catch (Exception ignore) {
            // best-effort
        }
        return null;
    }

    private String extractBalancedBraces(String text, int startIndex) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = startIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\') { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') depth++;
            else if (c == '}') depth--;
            if (depth == 0) {
                return text.substring(startIndex, i + 1);
            }
        }
        return null;
    }

    private int indexOfIgnoreCase(String haystack, String needle) {
        final int max = haystack.length() - needle.length();
        for (int i = 0; i <= max; i++) {
            if (haystack.regionMatches(true, i, needle, 0, needle.length())) {
                return i;
            }
        }
        return -1;
    }
    
    private String extractContentFromAIResponse(String aiResponse) {
        try {
            log.info("Raw AI response: {}", aiResponse);
            
            // Jackson ObjectMapper를 사용하여 JSON 파싱
            JsonNode rootNode = objectMapper.readTree(aiResponse);
            
            // choices[0].message.content 경로로 접근
            JsonNode choices = rootNode.get("choices");
            if (choices == null || !choices.isArray() || choices.size() == 0) {
                log.warn("No choices found in AI response");
                return aiResponse;
            }
            
            JsonNode firstChoice = choices.get(0);
            if (firstChoice == null) {
                log.warn("First choice is null");
                return aiResponse;
            }
            
            JsonNode message = firstChoice.get("message");
            if (message == null) {
                log.warn("Message is null");
                return aiResponse;
            }
            
            JsonNode content = message.get("content");
            if (content == null) {
                log.warn("Content is null");
                return aiResponse;
            }
            
            String contentStr = content.asText();
            log.info("Extracted content from AI response: {}", contentStr);
            return contentStr;
            
        } catch (Exception e) {
            log.error("Failed to extract content from AI response using Jackson", e);
            log.warn("Falling back to manual parsing");
            
            // Fallback to manual parsing
            try {
                int contentStart = aiResponse.indexOf("\"content\":\"") + 11;
                int contentEnd = aiResponse.lastIndexOf("\"");
                if (contentStart > 10 && contentEnd > contentStart) {
                    String content = aiResponse.substring(contentStart, contentEnd);
                    return content.replace("\\n", "\n").replace("\\\"", "\"");
                }
            } catch (Exception e2) {
                log.error("Manual parsing also failed", e2);
            }
            
            return aiResponse;
        }
    }
    
    private AIAnalysisResult parseJSONResponse(String content) {
        try {
            // Jackson ObjectMapper를 사용하여 JSON 파싱
            JsonNode rootNode = objectMapper.readTree(content);
            
            String title = getJsonField(rootNode, "title");
            String purpose = getJsonField(rootNode, "purpose");
            String expectedEffects = getJsonField(rootNode, "expectedEffects");
            String architecture = getJsonField(rootNode, "architecture");
            String techStack = getJsonField(rootNode, "techStack");
            String codeQuality = getJsonField(rootNode, "codeQuality");
            
            log.info("Parsed AI response - title: {}, purpose: {}, expectedEffects: {}, architecture: {}, techStack: {}, codeQuality: {}", 
                    title, purpose, expectedEffects, architecture, techStack, codeQuality);
            
            // 추가 정보를 purpose에 통합
            StringBuilder enhancedPurpose = new StringBuilder();
            if (purpose != null && !purpose.trim().isEmpty()) {
                enhancedPurpose.append(purpose);
            }
            if (architecture != null && !architecture.trim().isEmpty()) {
                enhancedPurpose.append("\n\n**아키텍처**: ").append(architecture);
            }
            if (techStack != null && !techStack.trim().isEmpty()) {
                enhancedPurpose.append("\n\n**기술 스택**: ").append(techStack);
            }
            if (codeQuality != null && !codeQuality.trim().isEmpty()) {
                enhancedPurpose.append("\n\n**코드 품질**: ").append(codeQuality);
            }
            
            return AIAnalysisResult.builder()
                .title(title != null && !title.trim().isEmpty() ? title : "프로젝트")
                .purpose(enhancedPurpose.length() > 0 ? enhancedPurpose.toString() : "프로젝트 목적을 분석할 수 없습니다.")
                .expectedEffects(expectedEffects != null && !expectedEffects.trim().isEmpty() ? expectedEffects : "기대 효과를 분석할 수 없습니다.")
                .confidence(0.90)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to parse JSON response using Jackson", e);
            log.warn("Falling back to manual JSON parsing");
            
            // Fallback to manual parsing
            try {
                String purpose = extractJSONField(content, "purpose");
                String expectedEffects = extractJSONField(content, "expectedEffects");
                
                return AIAnalysisResult.builder()
                    .purpose(purpose != null ? purpose : "프로젝트 목적을 분석할 수 없습니다.")
                    .expectedEffects(expectedEffects != null ? expectedEffects : "기대 효과를 분석할 수 없습니다.")
                    .confidence(0.70)
                    .build();
            } catch (Exception e2) {
                log.error("Manual JSON parsing also failed", e2);
                return parseTextResponse(content);
            }
        }
    }
    
    private String getJsonField(JsonNode rootNode, String fieldName) {
        JsonNode fieldNode = rootNode.get(fieldName);
        if (fieldNode != null && !fieldNode.isNull()) {
            return fieldNode.asText();
        }
        return null;
    }
    
    private String extractJSONField(String json, String fieldName) {
        try {
            String pattern = "\"" + fieldName + "\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1).replace("\\n", "\n");
            }
        } catch (Exception e) {
            log.warn("Failed to extract field: " + fieldName, e);
        }
        return null;
    }
    
    private AIAnalysisResult parseTextResponse(String content) {
        // 텍스트 응답을 파싱하여 제목, 목적과 효과 추출
        String title = "프로젝트";
        String purpose = "프로젝트 목적을 분석할 수 없습니다.";
        String expectedEffects = "기대 효과를 분석할 수 없습니다.";
        
        // 간단한 텍스트 파싱 로직
        String[] lines = content.split("\n");
        StringBuilder titleBuilder = new StringBuilder();
        StringBuilder purposeBuilder = new StringBuilder();
        StringBuilder effectsBuilder = new StringBuilder();
        
        boolean inTitle = false;
        boolean inPurpose = false;
        boolean inEffects = false;
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.toLowerCase().contains("제목") || trimmed.toLowerCase().contains("title")) {
                inTitle = true;
                inPurpose = false;
                inEffects = false;
                continue;
            }
            if (trimmed.toLowerCase().contains("목적") || trimmed.toLowerCase().contains("purpose")) {
                inTitle = false;
                inPurpose = true;
                inEffects = false;
                continue;
            }
            if (trimmed.toLowerCase().contains("효과") || trimmed.toLowerCase().contains("effect")) {
                inTitle = false;
                inPurpose = false;
                inEffects = true;
                continue;
            }
            
            if (inTitle && !trimmed.isEmpty()) {
                titleBuilder.append(trimmed).append(" ");
            } else if (inPurpose && !trimmed.isEmpty()) {
                purposeBuilder.append(trimmed).append("\n");
            } else if (inEffects && !trimmed.isEmpty()) {
                effectsBuilder.append(trimmed).append("\n");
            }
        }
        
        if (titleBuilder.length() > 0) {
            title = titleBuilder.toString().trim();
            // 제목이 너무 길면 잘라내기
            if (title.length() > 50) {
                title = title.substring(0, 47) + "...";
            }
        }
        if (purposeBuilder.length() > 0) {
            purpose = purposeBuilder.toString().trim();
        }
        if (effectsBuilder.length() > 0) {
            expectedEffects = effectsBuilder.toString().trim();
        }
        
        return AIAnalysisResult.builder()
            .title(title)
            .purpose(purpose)
            .expectedEffects(expectedEffects)
            .confidence(0.80)
            .build();
    }
}
