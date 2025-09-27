# MDM (Monolithic Document Management)

모놀리식 문서 관리 시스템 - Spring Boot + Next.js + MongoDB + Elasticsearch

## 🎯 프로젝트 개요

가변 계층 분류 체계와 스마트 문서 분류, RAG 기반 검색으로 효율적인 문서 관리를 제공하는 시스템입니다.

### 주요 기능

- **📁 가변 계층 분류**: 1~N 레벨 분류 체계 지원 (기존 3단계 호환)
- **🤖 스마트 분류**: AI 기반 자동 문서 분류 및 카테고리 제안
- **🔢 일련번호 관리**: 분류별 자동 연번 부여
- **🔍 하이브리드 검색**: Vector + BM25 결합 검색 엔진
- **📄 레포지토리 분석**: GitHub 레포지토리 자동 개요 생성
- **📋 문서 처리**: DOCX/XLSX 파일 텍스트 추출 및 분석
- **� RAG 챗봇**: 문서 기반 질의응답 시스템
- **✅ 승인 워크플로우**: AI 제안 vs 수동 분류 승인 시스템
- **� 카테고리 통계**: 문서 사용량 추적 및 관리
- **🗂️ 통합 삭제**: 계층 인식 데이터 정리 시스템

## 🏗️ 아키텍처

### 백엔드 (Spring Boot)
```
com.company.app
├── auth          # 인증 (JWT, 로컬 계정)
├── catalog       # 카탈로그 관리 (가변 계층 분류 체계)
│   ├── entity    # CatalogNode (계층 구조 + 문서 카운트)
│   ├── service   # CategoryUsageService, SmartClassificationService
│   └── controller# 분류 생성/수정/삭제 API
├── ingest        # 문서 수집 (개별/벌크)
│   └── service   # 스마트 분류 통합
├── approval      # 승인 워크플로우
├── search        # 하이브리드 검색 (Elasticsearch 샤드 기반)
├── document      # 문서 관리
│   └── service   # DocumentDeletionService (통합 삭제)
├── chat          # RAG 챗봇
├── file          # 파일 처리 (텍스트 추출)
├── admin         # 관리자 기능 (문서/카테고리 관리)
└── common        # 공통 기능 (Category DTO 등)
```

**Java 17** 기반으로 구축되었습니다.

### 프론트엔드 (Next.js)
- **Next.js 14** + **TypeScript** + **React 18**
- **App Router** (라우팅)
- **Tailwind CSS** (스타일링)
- **Axios** (HTTP 클라이언트)
- **Docker** (컨테이너화)

### 데이터베이스
- **MongoDB** (문서/메타데이터, 가변 계층 카테고리)
- **Elasticsearch** (샤드 기반 하이브리드 벡터 + BM25 검색)
- **로컬 파일 시스템** (문서 원본 저장)

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
- **documents**: 승인된 문서 (가변 계층 카테고리 포함)
- **document_chunks**: 문서 청크 (검색용)
- **catalog_nodes**: 가변 계층 카탈로그 (문서 카운트 포함)
- **counters**: 일련번호 카운터

### 가변 계층 카테고리 구조

```json
{
  "majorCode": "A",           // 기존 호환성
  "majorName": "소프트웨어",
  "hierarchy": [              // 새로운 가변 구조
    {"level": 1, "code": "A", "name": "소프트웨어"},
    {"level": 2, "code": "A05", "name": "AI/머신러닝"},
    {"level": 3, "code": "A0501", "name": "자연어처리"}
  ],
  "fullCode": "A-A05-A0501",
  "fullName": "소프트웨어 > AI/머신러닝 > 자연어처리"
}

### 검색 아키텍처 (Shard-Aware Hybrid)

문서 청크 인덱스는 `mdm_document_chunks_shard_{0..N}` 형태로 분할되어 있으며 검색 시:
1) 임베딩 → 기준 샤드 해시 계산
2) 제한된 fanout 내 벡터 + 텍스트 동시 수행
3) RRF 결합 후 부족 시 전체 샤드 확장
4) fallback match 보강

카테고리 분류 후보도 동일한 하이브리드 로직(`CLASSIFICATION_CANDIDATE_COUNT * 2`)을 거쳐 상위 5개(기본값)를 반환합니다.

환경변수 요약:
```
VECTOR_SHARD_COUNT=4
VECTOR_SEARCH_FANOUT=1
VECTOR_SEARCH_MIN_SUFFICIENT_RATIO=0.5
CLASSIFICATION_CANDIDATE_COUNT=5
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

# Shard-Aware Vector Search
VECTOR_SHARD_COUNT=4
VECTOR_SEARCH_FANOUT=1
VECTOR_SEARCH_MIN_SUFFICIENT_RATIO=0.5
CLASSIFICATION_CANDIDATE_COUNT=5

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
- `GET /api/admin/documents/hierarchy` - 가변 계층 문서 구조 조회
- `DELETE /api/admin/documents/category/{categoryCode}` - 카테고리별 문서 삭제
- `DELETE /api/admin/documents/{code}` - 코드로 문서 삭제

#### 카테고리 API (Categories)
- `GET /api/categories` - 모든 카테고리 조회 (평면 목록)
- `GET /api/categories/tree` - 계층 트리 구조 조회
- `POST /api/categories` - 새 카테고리 생성
- `PUT /api/categories/{code}` - 카테고리 수정
- `DELETE /api/categories/{code}` - 카테고리 삭제

#### 스마트 분류 API
- `POST /api/categories/classify` - 텍스트 기반 자동 분류
- `GET /api/categories/search` - 카테고리 검색

#### 문서 수집 API (Ingest)
- `POST /api/ingest/single` - 단일 문서/레포지토리 등록
- `POST /api/ingest/{id}/resubmit` - 재등록/수정

#### 파일 처리 API
- `POST /api/files/upload` - 파일 업로드
- `POST /api/files/analyze` - 파일 분석 (텍스트 추출 + 분류 제안)
- `GET /api/files/download/{id}` - 파일 다운로드

#### RAG 챗봇 API
- `POST /api/chat/query` - 문서 기반 질의응답
- `GET /api/chat/history` - 채팅 히스토리
- `GET /api/admin/documents/category/{majorCode}/{midCode}/{subCode}` - 카테고리별 문서 조회
- `GET /api/admin/files/hierarchy` - 파일 계층 구조 조회
- `GET /api/admin/files/category/{majorCode}/{midCode}/{subCode}` - 카테고리별 파일 조회
- `DELETE /api/admin/documents/{code}` - 문서 삭제
#### 승인 관리 (Approval)
- `GET /api/approval/requests` - 승인 요청 목록 조회
- `GET /api/approval/requests/{id}` - 승인 요청 상세 조회
- `POST /api/approval/requests/{id}/decide` - 승인/반려 결정

### Swagger UI 접속

자세한 API 문서는 **http://localhost:8080/api/swagger-ui.html** 에서 확인할 수 있습니다.

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
# 전체 스택 빌드 및 실행
docker-compose up -d --build

# 개별 이미지 빌드
docker build -f Dockerfile.backend -t mdm-backend .
docker build -f frontend2/Dockerfile -t mdm-frontend ./frontend2
```

## 🔧 유지보수

### 로그 확인

```bash
# 전체 로그
docker-compose logs -f

# 특정 서비스 로그
docker-compose logs -f backend
docker-compose logs -f frontend
```

### 데이터 백업

```bash
# MongoDB 백업
docker exec mdm-mongodb mongodump --db mdm --out /backup

# Elasticsearch 백업 (스냅샷)
curl -X PUT "localhost:9200/_snapshot/backup_repo" -H 'Content-Type: application/json' -d '{
  "type": "fs",
  "settings": {"location": "/backup/elasticsearch"}
}'
```

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
