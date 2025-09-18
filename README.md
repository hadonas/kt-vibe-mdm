# MDM (Monolithic Document Management)

모놀리식 문서 관리 시스템 POC - Spring Boot + Next.js + MongoDB Atlas/Vector Search

## 🎯 프로젝트 개요

대분류-중분류-소분류 기반 분류, 소분류코드-연번 일련번호 자동 부여, RAG 검색으로 중복 프로젝트 방지를 위한 문서 관리 시스템입니다.

### 주요 기능

- **📁 문서 분류**: 대/중/소 분류 체계 기반 자동 분류
- **🔢 일련번호 관리**: 소분류코드-연번 자동 부여
- **🔍 RAG 검색**: Vector Search를 통한 중복 프로젝트 방지
- **📄 레포지토리 분석**: GitHub 레포지토리 자동 개요 생성
- **📋 사업계획서 처리**: DOCX/XLSX 파일 텍스트 추출 및 분석
- **📦 벌크 등록**: ZIP/CSV 파일 일괄 처리
- **✅ 승인 워크플로우**: AI 제안 vs 수동 분류 승인 시스템
- **👥 협업 기능**: 문서 공유, 소유권 이관, 버전 관리

## 🏗️ 아키텍처

### 백엔드 (Spring Boot)
```
com.company.app
├── auth          # 인증 (JWT, 로컬 계정)
├── catalog       # 카탈로그 관리 (분류 체계)
├── ingest        # 문서 수집 (개별/벌크)
├── repo          # 레포지토리 분석
├── approval      # 승인 워크플로우
├── search        # Vector Search
├── document      # 문서 관리
├── chat          # RAG 챗봇
├── file          # 파일 처리
└── common        # 공통 기능
```

**Java 17** 기반으로 구축되었습니다.

### 프론트엔드 (Next.js)
- **Next.js 14** + **TypeScript** + **React 18**
- **App Router** (라우팅)
- **Tailwind CSS** (스타일링)
- **Axios** (HTTP 클라이언트)
- **Docker** (컨테이너화)

### 데이터베이스
- **MongoDB Atlas** (문서 저장)
- **Atlas Vector Search** (유사도 검색)
- **GridFS** (파일 저장)

## 🚀 빠른 시작

### 사전 요구사항

- Java 17+
- Node.js 18+
- MongoDB 7.0+
- Docker & Docker Compose (선택사항)

### 1. 저장소 클론

```bash
git clone <repository-url>
cd mdm
```

### 2. Docker Compose로 실행 (권장)

```bash
# 전체 스택 실행
docker-compose up -d

# 로그 확인
docker-compose logs -f

# 중지
docker-compose down
```

### 3. 수동 실행

#### 백엔드 실행

```bash
# 의존성 설치
./gradlew build

# 애플리케이션 실행
./gradlew bootRun

# 또는 JAR 실행
java -jar build/libs/mdm-0.0.1-SNAPSHOT.jar
```

#### 프론트엔드 실행

```bash
cd frontend2

# 의존성 설치
npm install

# 개발 서버 실행
npm run dev

# 빌드
npm run build
```

### 4. 접속

- **프론트엔드**: http://localhost:3000
- **백엔드 API**: http://localhost:8080/api
- **Swagger UI**: http://localhost:8080/api/swagger-ui.html
- **MongoDB**: mongodb://localhost:27017

### 5. 기본 계정

- **이메일**: admin@test.com
- **비밀번호**: admin123456

## 📊 데이터 모델

### 주요 컬렉션

- **users**: 사용자 정보
- **ingest_requests**: 등록 요청
- **documents**: 승인된 문서
- **catalog_nodes**: 카탈로그 트리
- **counters**: 일련번호 카운터

### Vector Search 인덱스

```json
{
  "fields": [
    {
      "type": "vector",
      "path": "vectors.purpose_768",
      "numDimensions": 768,
      "similarity": "cosine"
    },
    {
      "type": "filter",
      "path": "category.subCode"
    }
  ]
}
```

## 🔧 환경 설정

### 환경변수 설정

#### 1. 환경변수 파일 생성

```bash
# 백엔드 환경변수
cp env.example .env

# 프론트엔드 환경변수
cp frontend/env.example frontend/.env.local
```

#### 2. 주요 환경변수

```bash
# MongoDB
MONGO_URI=mongodb://localhost:27017/mdm
MONGO_DB=mdm

# JWT (보안을 위해 반드시 변경)
JWT_SECRET=mySecretKey
JWT_ISSUER=app.local

# Vector Search
ATLAS_VECTOR_INDEX_PURPOSE=purpose_idx

# AI Services
EMBEDDING_API_BASE=http://localhost:11434
EMBEDDING_MODEL=text-embed-3
LLM_API_BASE=http://localhost:11434
LLM_MODEL=llama3.2

# 관리자 계정
ADMIN_PASSWORD=admin123
ADMIN_EMAIL=admin@company.com
```

#### 3. 상세 설정 가이드

자세한 환경변수 설정 방법은 [ENVIRONMENT_SETUP.md](ENVIRONMENT_SETUP.md)를 참조하세요.

- 🔐 **보안 설정**: JWT 비밀키, 관리자 비밀번호
- 🗄️ **데이터베이스**: MongoDB 로컬/Atlas 설정
- 🤖 **AI 서비스**: Ollama, OpenAI, Azure OpenAI
- 📧 **이메일**: SMTP 설정
- 📁 **파일 저장소**: GridFS, S3, 로컬

### MongoDB Atlas 설정

1. Atlas 클러스터 생성
2. Vector Search 인덱스 생성
3. 환경변수 업데이트

## 📚 API 문서

### 주요 엔드포인트

#### 인증 (Authentication)
- `POST /api/auth/signup` - 회원가입
- `POST /api/auth/login` - 로그인
- `POST /api/auth/password/forgot` - 비밀번호 재설정
- `GET /api/auth/me` - 현재 사용자 정보 조회

#### 공개 API (Public)
- `GET /api/public/documents/count` - 전체 문서 수 조회
- `GET /api/public/files/download-by-code` - 코드로 파일 다운로드
- `GET /api/public/files/download` - 파일 다운로드
- `GET /api/public/my-documents` - 사용자별 문서 조회

#### 관리자 API (Admin)
- `GET /api/admin/documents` - 모든 문서 목록 조회
- `GET /api/admin/documents/hierarchy` - 문서 계층 구조 조회
- `GET /api/admin/documents/codes` - 코드별 문서 조회
- `GET /api/admin/documents/category/{majorCode}/{midCode}/{subCode}` - 카테고리별 문서 조회
- `GET /api/admin/files/hierarchy` - 파일 계층 구조 조회
- `GET /api/admin/files/category/{majorCode}/{midCode}/{subCode}` - 카테고리별 파일 조회
- `DELETE /api/admin/documents/{code}` - 문서 삭제
- `GET /api/admin/documents/duplicates` - 중복 문서 조회

#### 승인 관리 (Approval)
- `GET /api/approval/requests` - 승인 요청 목록 조회
- `GET /api/approval/requests/{id}` - 승인 요청 상세 조회
- `POST /api/approval/requests/{id}/decide` - 승인/반려 결정
- `GET /api/approval/my-requests` - 사용자별 승인 요청 조회
- `GET /api/approval/stats` - 승인 요청 통계 조회

#### 문서 수집 (Ingest)
- `POST /api/ingest/single` - 개별 등록 (레포 URL 또는 파일)
- `POST /api/ingest/{id}/resubmit` - 재등록/수정 (버전업)

#### 파일 관리 (File)
- `POST /api/files/analyze` - 파일 분석 (카테고리 및 목적 추출)
- `POST /api/files/upload` - 파일 업로드
- `GET /api/files/download/{fileId}` - 파일 다운로드

#### 검색 및 채팅 (Search & Chat)
- `POST /api/search/reindex` - 벡터 인덱스 재구성
- `POST /api/chat/query` - RAG 채팅 쿼리

자세한 API 문서는 Swagger UI에서 확인할 수 있습니다.

## 🧪 테스트

### 백엔드 테스트

```bash
# 단위 테스트
./gradlew test

# 통합 테스트
./gradlew integrationTest

# 테스트 커버리지
./gradlew jacocoTestReport
```

### 프론트엔드 테스트

```bash
cd frontend2

# 린팅
npm run lint

# 타입 체크
npm run type-check

# 빌드 테스트
npm run build
```

## 🚀 배포

### Docker 이미지 빌드

```bash
# 백엔드
docker build -f Dockerfile.backend -t mdm-backend .

# 프론트엔드
docker build -f frontend2/Dockerfile -t mdm-frontend ./frontend2
```

### Kubernetes 배포

```bash
# 네임스페이스 생성
kubectl create namespace mdm

# 배포
kubectl apply -f k8s/
```

## 🤝 기여하기

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📝 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다. 자세한 내용은 `LICENSE` 파일을 참조하세요.

## 📞 지원

문제가 발생하거나 질문이 있으시면 이슈를 생성해 주세요.

---

**MDM Team** - 문서 관리의 새로운 표준
