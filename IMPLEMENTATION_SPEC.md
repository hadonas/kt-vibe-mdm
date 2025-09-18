# MDM 시스템 구현명세서

## 📋 문서 정보

- **프로젝트명**: MDM 현대화
- **버전**: 1.0.0
- **작성일**: 2025년 9월 15일
- **작성자**: Cursor AI

## 🎯 프로젝트 개요

### 목적
대분류-중분류-소분류 기반 분류, 소분류코드-연번 일련번호 자동 부여, RAG 검색으로 중복 프로젝트 방지를 위한 문서 관리 시스템

### 주요 기능
- 📁 문서 분류 및 일련번호 관리
- 🔍 RAG 검색을 통한 중복 프로젝트 방지
- 📄 레포지토리 분석 및 문서 추출
- 📋 사업계획서 처리 (DOCX/XLSX)
- 📦 벌크 등록 (ZIP/CSV)
- ✅ 승인 워크플로우
- 👥 협업 기능 (문서 공유, 소유권 이관)

## 🏗️ 시스템 아키텍처

### 전체 구조
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Frontend      │    │    Backend      │    │   Database      │
│   (Next.js)     │◄──►│  (Spring Boot)  │◄──►│   (MongoDB)     │
│   Port: 3000    │    │   Port: 8080    │    │   Port: 27017   │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### 기술 스택

#### 백엔드
- **Framework**: Spring Boot 3.2
- **Language**: Java 17
- **Database**: MongoDB 7.0
- **Authentication**: JWT
- **Documentation**: OpenAPI 3.0 (Swagger)
- **Build Tool**: Gradle

#### 프론트엔드
- **Framework**: Next.js 14
- **Language**: TypeScript
- **UI Library**: React 18
- **Styling**: Tailwind CSS
- **HTTP Client**: Axios
- **Build Tool**: npm

#### 인프라
- **Containerization**: Docker & Docker Compose
- **Reverse Proxy**: Nginx (개발 환경)
- **File Storage**: GridFS (MongoDB)

## 📁 프로젝트 구조

### 백엔드 구조
```
src/main/java/com/company/app/
├── auth/                    # 인증 관리
│   ├── controller/         # AuthController
│   ├── service/           # AuthService
│   ├── entity/            # User
│   ├── repository/        # UserRepository
│   └── dto/               # DTOs
├── admin/                  # 관리자 기능
│   └── controller/        # AdminController
├── common/                 # 공통 기능
│   ├── controller/        # PublicController
│   ├── security/          # SecurityConfig, JWT
│   └── dto/               # ErrorResponse
├── approval/              # 승인 워크플로우
│   ├── controller/        # ApprovalController
│   ├── service/           # ApprovalService
│   └── dto/               # DTOs
├── ingest/                # 문서 수집
│   ├── controller/        # IngestController
│   ├── service/           # IngestService
│   └── dto/               # DTOs
├── file/                  # 파일 관리
│   ├── controller/        # FileController
│   └── service/           # LocalFileStorageService
├── search/                # 검색 기능
│   ├── controller/        # VectorSearchController
│   └── service/           # FaissVectorSearchService
├── chat/                  # RAG 채팅
│   ├── controller/        # ChatController
│   └── dto/               # DTOs
├── document/              # 문서 관리
│   ├── entity/            # DocumentEntity
│   └── repository/        # DocumentRepository
└── catalog/               # 카탈로그 관리
    └── repository/        # CatalogNodeRepository
```

### 프론트엔드 구조
```
frontend2/src/
├── app/                   # Next.js App Router
│   ├── page.tsx          # 메인 페이지
│   ├── layout.tsx        # 레이아웃
│   ├── globals.css       # 글로벌 스타일
│   ├── login/            # 로그인 페이지
│   ├── signup/           # 회원가입 페이지
│   ├── dashboard/        # 대시보드
│   ├── admin/            # 관리자 페이지
│   ├── approval/         # 승인 페이지
│   ├── chat/             # 채팅 페이지
│   ├── ingest/           # 문서 등록
│   └── my-requests/      # 내 요청
├── components/            # 재사용 컴포넌트
│   ├── layout/           # Navbar
│   └── ui/               # UI 컴포넌트
├── contexts/             # React Context
│   └── AuthContext.tsx   # 인증 컨텍스트
├── lib/                  # 유틸리티
│   ├── api.ts            # API 클라이언트
│   ├── auth.ts           # 인증 유틸리티
│   └── utils.ts          # 공통 유틸리티
└── types/                # TypeScript 타입
    └── api.ts            # API 타입 정의
```

## 🔧 상세 구현 사항

### 1. 인증 시스템

#### 백엔드 구현
```java
@RestController
@RequestMapping("/auth")
public class AuthController {
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request)
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request)
    
    @PostMapping("/password/forgot")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request)
    
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication)
}
```

#### 프론트엔드 구현
```typescript
// AuthContext.tsx
export const AuthProvider = ({ children }: { children: React.ReactNode }) => {
  const [isAuthenticated, setIsAuthenticated] = useState(false)
  const [user, setUser] = useState<User | null>(null)
  const [token, setToken] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [hasInitialized, setHasInitialized] = useState(false)
  
  const login = async (credentials: { email: string; password: string }) => {
    // 로그인 로직
  }
  
  const logout = () => {
    // 로그아웃 로직
  }
}
```

#### 보안 설정
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        return http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/signup", "/auth/login", "/auth/password/forgot").permitAll()
                .requestMatchers("/public/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .build()
    }
}
```

### 2. API 엔드포인트 구조

#### 공개 API (`/api/public/*`)
- `GET /documents/count` - 전체 문서 수 조회
- `GET /files/download-by-code` - 코드로 파일 다운로드
- `GET /files/download` - 파일 다운로드
- `GET /my-documents` - 사용자별 문서 조회

#### 관리자 API (`/api/admin/*`)
- `GET /documents` - 모든 문서 목록 조회
- `GET /documents/hierarchy` - 문서 계층 구조 조회
- `GET /documents/codes` - 코드별 문서 조회
- `GET /documents/category/{majorCode}/{midCode}/{subCode}` - 카테고리별 문서 조회
- `GET /files/hierarchy` - 파일 계층 구조 조회
- `DELETE /documents/{code}` - 문서 삭제
- `GET /documents/duplicates` - 중복 문서 조회

#### 승인 API (`/api/approval/*`)
- `GET /requests` - 승인 요청 목록 조회
- `GET /requests/{id}` - 승인 요청 상세 조회
- `POST /requests/{id}/decide` - 승인/반려 결정
- `GET /my-requests` - 사용자별 승인 요청 조회
- `GET /stats` - 승인 요청 통계 조회

#### 파일 API (`/api/files/*`)
- `POST /analyze` - 파일 분석 (카테고리 및 목적 추출)
- `POST /upload` - 파일 업로드
- `GET /download/{fileId}` - 파일 다운로드

#### 검색 및 채팅 API
- `POST /search/reindex` - 벡터 인덱스 재구성
- `POST /chat/query` - RAG 채팅 쿼리

### 3. 데이터 모델

#### User 엔티티
```java
@Document(collection = "users")
public class User {
    @Id
    private String id
    private String email
    private String name
    private String passwordHash
    private List<Role> roles
    private LocalDateTime createdAt
    private LocalDateTime lastLoginAt
}
```

#### Document 엔티티
```java
@Document(collection = "documents")
public class DocumentEntity {
    @Id
    private String id
    private String serial
    private String title
    private String purpose
    private Category category
    private Status status
    private String ownerId
    private LocalDateTime createdAt
    private LocalDateTime updatedAt
    private String filePath
    private List<String> tags
}
```

#### Category 구조
```java
public class Category {
    private String majorCode
    private String majorName
    private String midCode
    private String midName
    private String subCode
    private String subName
}
```

### 4. 파일 관리 시스템

#### 파일 업로드
```java
@PostMapping("/upload")
public ResponseEntity<Map<String, Object>> uploadFile(
    @RequestParam("file") MultipartFile file,
    Authentication authentication) {
    
    String fileId = UUID.randomUUID().toString()
    String fileName = file.getOriginalFilename()
    String filePath = localFileStorageService.storeFile(file, fileId)
    
    return ResponseEntity.ok(Map.of(
        "fileId", fileId,
        "fileName", fileName,
        "filePath", filePath
    ))
}
```

#### 파일 다운로드
```java
@GetMapping("/files/download")
public ResponseEntity<Resource> downloadFile(@RequestParam String filePath) {
    Path path = Paths.get(filePath)
    Resource resource = new UrlResource(path.toUri())
    
    if (resource.exists()) {
        String contentType = determineContentType(filePath)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, 
                "attachment; filename=\"" + resource.getFilename() + "\"")
            .body(resource)
    }
    return ResponseEntity.notFound().build()
}
```

### 5. 승인 워크플로우

#### 승인 요청 생성
```java
@PostMapping("/single")
public ResponseEntity<?> singleIngest(@Valid @RequestBody SingleIngestRequest request,
                                    Authentication authentication) {
    String userId = getUserId(authentication)
    
    if (request.getRepoUrl() != null) {
        // 레포지토리 등록 요청
        var response = ingestService.createRepoIngestRequest(userId, request)
        return ResponseEntity.status(201).body(response)
    } else if (request.getFileIds() != null) {
        // 파일 기반 등록 요청
        var response = ingestService.createFileIngestRequest(userId, request)
        return ResponseEntity.status(201).body(response)
    }
    
    return ResponseEntity.badRequest()
        .body(Map.of("error", "레포 URL 또는 파일 ID가 필요합니다."))
}
```

#### 승인/반려 결정
```java
@PostMapping("/requests/{id}/decide")
@PreAuthorize("hasRole('APPROVER') or hasRole('ADMIN')")
public ResponseEntity<Map<String, Object>> decideRequest(
    @PathVariable String id,
    @RequestBody Map<String, Object> request,
    Authentication authentication) {
    
    String decision = (String) request.get("decision")
    String comment = (String) request.get("comment")
    String approverId = getUserId(authentication)
    
    var result = approvalService.decideRequest(id, decision, comment, approverId)
    return ResponseEntity.ok(result)
}
```

### 6. 검색 및 RAG 시스템

#### 벡터 검색
```java
@PostMapping("/reindex")
public ResponseEntity<Map<String, String>> reindexAllDocuments() {
    try {
        faissVectorSearchService.reindexAllDocuments()
        return ResponseEntity.ok(Map.of("message", "인덱스 재구성이 완료되었습니다."))
    } catch (Exception e) {
        return ResponseEntity.status(500)
            .body(Map.of("error", "인덱스 재구성 중 오류가 발생했습니다."))
    }
}
```

#### RAG 채팅
```java
@PostMapping("/query")
public ResponseEntity<ChatQueryResponse> query(
    @Valid @RequestBody ChatQueryRequest request,
    Authentication authentication) {
    
    String userId = getUserId(authentication)
    var response = chatService.processQuery(request.getQuery(), userId)
    return ResponseEntity.ok(response)
}
```

### 7. 프론트엔드 구현

#### 인증 컨텍스트
```typescript
// contexts/AuthContext.tsx
export const AuthProvider = ({ children }: { children: React.ReactNode }) => {
  const [isAuthenticated, setIsAuthenticated] = useState(false)
  const [user, setUser] = useState<User | null>(null)
  const [token, setToken] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [hasInitialized, setHasInitialized] = useState(false)

  const checkAuth = async () => {
    const localToken = localStorage.getItem('accessToken')
    const localAuthenticated = localStorage.getItem('isAuthenticated') === 'true'
    
    if (localToken && localAuthenticated) {
      try {
        const response = await api.get('/auth/me')
        setUser(response.data)
        setToken(localToken)
        setIsAuthenticated(true)
      } catch (error) {
        localStorage.removeItem('accessToken')
        localStorage.removeItem('isAuthenticated')
        setIsAuthenticated(false)
        setUser(null)
        setToken(null)
      }
    } else {
      setIsAuthenticated(false)
      setUser(null)
      setToken(null)
    }
    
    setIsLoading(false)
    setHasInitialized(true)
  }

  useEffect(() => {
    checkAuth()
  }, [])

  const login = async (credentials: { email: string; password: string }) => {
    setIsLoading(true)
    try {
      const res = await AuthService.login(credentials)
      setIsAuthenticated(true)
      setUser(res.user)
      setToken(res.accessToken)
      setHasInitialized(true)
      return res
    } finally {
      setIsLoading(false)
    }
  }

  const logout = () => {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('isAuthenticated')
    setIsAuthenticated(false)
    setUser(null)
    setToken(null)
    setIsLoading(false)
    setHasInitialized(true)
  }

  return (
    <AuthContext.Provider value={{
      isAuthenticated,
      user,
      token,
      isLoading,
      hasInitialized,
      login,
      logout
    }}>
      {children}
    </AuthContext.Provider>
  )
}
```

#### API 클라이언트
```typescript
// lib/api.ts
const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api',
  timeout: 10000,
})

// Request interceptor
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// Response interceptor
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('isAuthenticated')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export const publicApi = {
  getDocumentCount: () => api.get('/public/documents/count'),
  downloadFileByCode: (code: string) => 
    api.get(`/public/files/download-by-code?code=${encodeURIComponent(code)}`, {
      responseType: 'blob'
    }),
  downloadFile: (filePath: string) => 
    api.get(`/public/files/download?filePath=${encodeURIComponent(filePath)}`, {
      responseType: 'blob'
    }),
  getMyDocuments: (page = 0, size = 20) => 
    api.get(`/public/my-documents?page=${page}&size=${size}`)
}

export const adminApi = {
  getAllDocuments: (page = 0, size = 20) => 
    api.get(`/admin/documents?page=${page}&size=${size}`),
  getDocumentHierarchy: () => api.get('/admin/documents/hierarchy'),
  getDocumentsByCode: (code: string) => 
    api.get(`/admin/documents/codes?code=${encodeURIComponent(code)}`),
  deleteDocument: (code: string) => api.delete(`/admin/documents/${code}`),
  getDuplicateDocuments: () => api.get('/admin/documents/duplicates')
}
```

### 8. Docker 설정

#### Docker Compose
```yaml
version: '3.8'
services:
  mongodb:
    image: mongo:7.0
    container_name: mdm-mongodb
    ports:
      - "27017:27017"
    environment:
      MONGO_INITDB_ROOT_USERNAME: admin
      MONGO_INITDB_ROOT_PASSWORD: password
    volumes:
      - mongodb_data:/data/db
      - ./scripts/mongo-init.js:/docker-entrypoint-initdb.d/mongo-init.js:ro

  backend:
    build:
      context: .
      dockerfile: Dockerfile.backend
    container_name: mdm-backend
    ports:
      - "8080:8080"
    environment:
      MONGO_URI: mongodb://admin:password@mongodb:27017/mdm?authSource=admin
      JWT_SECRET: your-secret-key
      ADMIN_EMAIL: admin@company.com
      ADMIN_PASSWORD: admin123
    depends_on:
      - mongodb
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/api/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  frontend:
    build:
      context: ./frontend2
      dockerfile: Dockerfile
    container_name: mdm-frontend
    ports:
      - "3000:3000"
    environment:
      NEXT_PUBLIC_API_BASE_URL: http://localhost:8080/api
    depends_on:
      - backend
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost:3000"]
      interval: 30s
      timeout: 10s
      retries: 3

volumes:
  mongodb_data:
```

#### 백엔드 Dockerfile
```dockerfile
FROM openjdk:17-jdk-slim

WORKDIR /app

COPY build/libs/*.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
```

#### 프론트엔드 Dockerfile
```dockerfile
FROM node:18-alpine AS builder

WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production

COPY . .
RUN npm run build

FROM node:18-alpine AS runner

WORKDIR /app

ENV NODE_ENV production

RUN addgroup --system --gid 1001 nodejs
RUN adduser --system --uid 1001 nextjs

COPY --from=builder /app/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs

EXPOSE 3000

ENV PORT 3000

CMD ["node", "server.js"]
```

## 🔒 보안 구현

### 1. 인증 및 인가
- JWT 기반 토큰 인증
- 역할 기반 접근 제어 (RBAC)
- API 엔드포인트별 권한 설정

### 2. CORS 설정
```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration()
    configuration.setAllowedOriginPatterns(Arrays.asList("*"))
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"))
    configuration.setAllowedHeaders(Arrays.asList("*"))
    configuration.setExposedHeaders(Arrays.asList("Content-Disposition", "Content-Type", "Content-Length"))
    configuration.setAllowCredentials(true)
    
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource()
    source.registerCorsConfiguration("/**", configuration)
    return source
}
```

### 3. 파일 업로드 보안
- 파일 타입 검증
- 파일 크기 제한
- 안전한 파일명 생성

## 📊 성능 최적화

### 1. 데이터베이스 최적화
- MongoDB 인덱스 설정
- 페이지네이션 구현
- 벡터 검색 인덱스

### 2. 프론트엔드 최적화
- Next.js App Router 사용
- 컴포넌트 최적화
- API 호출 최적화

### 3. 캐싱 전략
- JWT 토큰 캐싱
- API 응답 캐싱
- 정적 파일 캐싱

## 🧪 테스트 전략

### 1. 백엔드 테스트
- 단위 테스트 (JUnit 5)
- 통합 테스트 (TestContainers)
- API 테스트 (MockMvc)

### 2. 프론트엔드 테스트
- 컴포넌트 테스트 (Jest, React Testing Library)
- E2E 테스트 (Playwright)
- API 모킹 (MSW)

## 🚀 배포 전략

### 1. 개발 환경
- Docker Compose를 통한 로컬 개발
- Hot Reload 지원
- 개발용 데이터베이스

### 2. 프로덕션 환경
- Docker 컨테이너 배포
- Kubernetes 오케스트레이션
- 로드 밸런싱
- 모니터링 및 로깅

## 📈 모니터링 및 로깅

### 1. 애플리케이션 모니터링
- Spring Boot Actuator
- 헬스체크 엔드포인트
- 메트릭 수집

### 2. 로깅
- 구조화된 로깅 (JSON)
- 로그 레벨 관리
- 중앙 집중식 로그 관리

## 🔄 CI/CD 파이프라인

### 1. 빌드 프로세스
- Gradle 빌드
- npm 빌드
- Docker 이미지 빌드

### 2. 배포 프로세스
- 자동화된 배포
- 롤백 전략
- 환경별 설정 관리

## 📝 API 문서화

### 1. OpenAPI 3.0 스펙
- 자동 생성된 API 문서
- Swagger UI 인터페이스
- 요청/응답 스키마 정의

### 2. 사용자 가이드
- API 사용 예제
- 인증 방법
- 에러 코드 설명

## 🛠️ 개발 도구

### 1. 백엔드 개발
- IntelliJ IDEA
- Gradle
- Spring Boot DevTools
- MongoDB Compass

### 2. 프론트엔드 개발
- Visual Studio Code
- Next.js DevTools
- Tailwind CSS
- TypeScript

### 3. 공통 도구
- Git
- Docker
- Postman (API 테스트)
- MongoDB Atlas (클라우드 데이터베이스)

## 📋 향후 개선 사항

### 1. 기능 개선
- 실시간 알림 시스템
- 고급 검색 필터
- 문서 버전 관리
- 사용자 권한 세분화

### 2. 기술 개선
- 마이크로서비스 아키텍처 전환
- Redis 캐싱 도입
- Elasticsearch 검색 엔진
- GraphQL API 도입

### 3. 성능 개선
- CDN 도입
- 이미지 최적화
- 코드 스플리팅
- 서버사이드 렌더링 최적화

---

**문서 버전**: 1.0.0  
**최종 수정일**: 2024년 12월  
**다음 검토일**: 2025년 1월
