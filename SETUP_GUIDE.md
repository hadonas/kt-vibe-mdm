# MDM 시스템 설정 가이드

## 🚀 빠른 시작

### 1. 환경 변수 설정

#### 백엔드 환경 변수
```bash
# 루트 디렉토리에서
cp env.example .env

# 필요한 경우 .env 파일을 편집하여 설정 값 변경
# 특히 프로덕션 환경에서는 다음 값들을 반드시 변경하세요:
# - JWT_SECRET
# - ADMIN_PASSWORD
# - MONGO_PASSWORD
# - AI_API_KEY
```

#### 프론트엔드 환경 변수
```bash
# frontend2 디렉토리에서
cd frontend2
cp env.example .env.local

# 기본값으로도 작동하지만, 필요에 따라 API URL 변경 가능
# NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api
```

### 2. Docker Compose로 전체 시스템 실행

```bash
# 루트 디렉토리에서 전체 시스템 실행
docker-compose up -d

# 연결 상태 확인
./test-connection.sh
```

### 3. 접속

- **프론트엔드**: http://localhost:3000
- **백엔드 API**: http://localhost:8080/api
- **API 문서**: http://localhost:8080/api/swagger-ui.html

## 🔧 개발 환경 설정

### 로컬에서 프론트엔드만 개발하는 경우

```bash
# 백엔드와 MongoDB만 Docker로 실행
docker-compose up -d backend mongodb

# 프론트엔드는 로컬에서 실행
cd frontend2
npm install
npm run dev
```

### 완전 로컬 개발 환경

```bash
# 1. MongoDB 로컬 설치 및 실행
# 2. 백엔드 Spring Boot 애플리케이션 실행
./gradlew bootRun

# 3. 프론트엔드 Next.js 개발 서버 실행
cd frontend2
npm install
npm run dev
```

## 🌐 네트워크 설정 이해

### Docker Compose 환경에서의 네트워크

```
브라우저 → http://localhost:3000 → Next.js (Frontend Container)
브라우저 → http://localhost:8080/api → Spring Boot (Backend Container)

Container 간 통신:
Frontend Container → http://mdm-backend:8080/api → Backend Container
Backend Container → mongodb://mongodb:27017 → MongoDB Container
```

### 환경별 API URL 설정

| 환경 | 프론트엔드 | 백엔드 | API URL |
|------|------------|--------|---------|
| Docker Compose | Container | Container | `http://localhost:8080/api` |
| 로컬 개발 | 로컬 | 로컬 | `http://localhost:8080/api` |
| 프로덕션 | 서버 | 서버 | `https://api.yourdomain.com/api` |

## 🛠️ 트러블슈팅

### 연결 문제 해결

1. **컨테이너 상태 확인**
   ```bash
   docker-compose ps
   ```

2. **로그 확인**
   ```bash
   # 전체 로그
   docker-compose logs
   
   # 특정 서비스 로그
   docker-compose logs backend
   docker-compose logs frontend
   ```

3. **네트워크 연결 테스트**
   ```bash
   # 자동 테스트 스크립트 실행
   ./test-connection.sh
   
# 수동 테스트
curl http://localhost:8080/api/actuator/health
curl http://localhost:3000
   ```

4. **포트 충돌 확인**
   ```bash
   # macOS/Linux
   lsof -i :3000
   lsof -i :8080
   lsof -i :27017
   
   # Windows
   netstat -ano | findstr :3000
   ```

### 일반적인 문제들

#### 1. "Connection refused" 오류
- 백엔드 컨테이너가 완전히 시작되지 않았을 가능성
- `docker-compose logs backend`로 확인
- 몇 분 후 다시 시도

#### 2. CORS 오류
- 백엔드에서 프론트엔드 도메인이 허용되지 않은 경우
- `.env` 파일의 `CORS_ALLOWED_ORIGINS` 확인

#### 3. API 호출 실패
- 프론트엔드의 API URL 설정 확인
- `frontend2/.env.local` 파일의 `NEXT_PUBLIC_API_BASE_URL` 확인

#### 4. 컨테이너 빌드 실패
```bash
# 캐시 제거 후 다시 빌드
docker-compose down
docker system prune -f
docker-compose build --no-cache
docker-compose up -d
```

## 📝 환경 변수 참조

### 백엔드 주요 환경 변수

| 변수명 | 기본값 | 설명 |
|--------|--------|------|
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080/api` | 프론트엔드에서 사용할 API URL |
| `JWT_SECRET` | `change-this...` | JWT 토큰 서명 키 (필수 변경) |
| `ADMIN_PASSWORD` | `admin123` | 기본 관리자 비밀번호 |
| `MONGO_URI` | `mongodb://...` | MongoDB 연결 URI |
| `AI_API_KEY` | `` | OpenAI API 키 |

### 프론트엔드 주요 환경 변수

| 변수명 | 기본값 | 설명 |
|--------|--------|------|
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080/api` | 백엔드 API URL |
| `NODE_ENV` | `development` | 개발/프로덕션 환경 |

### API 엔드포인트 구조

| 카테고리 | 경로 | 설명 | 인증 필요 |
|----------|------|------|-----------|
| **인증** | `/api/auth/*` | 회원가입, 로그인, 사용자 정보 | 선택적 |
| **공개** | `/api/public/*` | 문서 수 조회, 파일 다운로드 | 선택적 |
| **관리자** | `/api/admin/*` | 문서 관리, 계층 구조 조회 | ADMIN 권한 |
| **승인** | `/api/approval/*` | 승인 요청 관리 | APPROVER/ADMIN 권한 |
| **파일** | `/api/files/*` | 파일 업로드, 분석, 다운로드 | 필요 |
| **검색** | `/api/search/*` | 벡터 검색, 인덱스 관리 | 필요 |
| **채팅** | `/api/chat/*` | RAG 채팅 | 필요 |

## 🔒 보안 설정

### 프로덕션 환경에서 반드시 변경해야 할 값들

1. **JWT_SECRET**: 강력한 랜덤 키로 변경
2. **ADMIN_PASSWORD**: 안전한 관리자 비밀번호
3. **MONGO_PASSWORD**: MongoDB 비밀번호
4. **AI_API_KEY**: 실제 OpenAI API 키

### 권장 보안 설정

```bash
# 강력한 JWT 시크릿 생성
openssl rand -base64 48

# 안전한 비밀번호 생성
openssl rand -base64 32
```

## 📊 모니터링

### 헬스체크 엔드포인트

- **백엔드**: http://localhost:8080/api/actuator/health
- **프론트엔드**: http://localhost:3000 (Next.js 기본 페이지)

### 로그 모니터링

```bash
# 실시간 로그 확인
docker-compose logs -f

# 특정 서비스 로그
docker-compose logs -f backend
docker-compose logs -f frontend
```

## 🚀 배포

### Docker 이미지 빌드

```bash
# 전체 시스템 빌드
docker-compose build

# 특정 서비스만 빌드
docker-compose build backend
docker-compose build frontend
```

### 프로덕션 배포

1. 환경 변수를 프로덕션 값으로 설정
2. HTTPS 설정 (Nginx, Let's Encrypt 등)
3. 데이터베이스 백업 설정
4. 모니터링 설정

---

문제가 발생하거나 추가 도움이 필요한 경우, 이슈를 생성하거나 개발팀에 문의하세요.
