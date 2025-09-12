# 환경변수 설정 가이드

MDM (Monolithic Document Management) 시스템의 환경변수 설정 방법을 안내합니다.

## 📋 목차

1. [빠른 시작](#빠른-시작)
2. [환경변수 파일 생성](#환경변수-파일-생성)
3. [주요 설정 항목](#주요-설정-항목)
4. [환경별 설정](#환경별-설정)
5. [보안 고려사항](#보안-고려사항)
6. [문제 해결](#문제-해결)

## 🚀 빠른 시작

### 1. 환경변수 파일 생성

```bash
# 백엔드 환경변수 파일 생성
cp env.example .env

# 프론트엔드 환경변수 파일 생성
cp frontend/env.example frontend/.env.local
```

### 2. 기본 설정으로 실행

```bash
# Docker Compose로 전체 스택 실행
docker-compose up -d

# 또는 개별 실행
# MongoDB
docker-compose up -d mongodb

# 백엔드
./gradlew bootRun

# 프론트엔드
cd frontend && npm run dev
```

## 📁 환경변수 파일 생성

### 백엔드 환경변수

```bash
# env.example을 .env로 복사
cp env.example .env

# 필요한 값들 수정
nano .env
```

### 프론트엔드 환경변수

```bash
# frontend/env.example을 .env.local로 복사
cp frontend/env.example frontend/.env.local

# 필요한 값들 수정
nano frontend/.env.local
```

## ⚙️ 주요 설정 항목

### 🔐 필수 보안 설정

```bash
# JWT 비밀키 (반드시 변경 필요)
JWT_SECRET=your-super-secret-key-here

# 관리자 비밀번호 (반드시 변경 필요)
ADMIN_PASSWORD=your-secure-password

# MongoDB 비밀번호 (프로덕션에서 변경 필요)
MONGO_ROOT_PASSWORD=your-mongo-password
```

### 🗄️ 데이터베이스 설정

```bash
# 로컬 MongoDB
MONGO_URI=mongodb://localhost:27017/mdm

# MongoDB Atlas
MONGO_URI=mongodb+srv://username:password@cluster.mongodb.net/mdm

# 데이터베이스 이름
MONGO_DB=mdm
```

### 🤖 AI 서비스 설정

#### Ollama (로컬)
```bash
EMBEDDING_API_BASE=http://localhost:11434
EMBEDDING_MODEL=text-embed-3
LLM_API_BASE=http://localhost:11434
LLM_MODEL=llama3.2
```

#### OpenAI
```bash
EMBEDDING_API_BASE=https://api.openai.com/v1
EMBEDDING_MODEL=text-embedding-3-small
LLM_API_BASE=https://api.openai.com/v1
LLM_MODEL=gpt-4
AI_API_KEY=sk-your-openai-api-key
```

#### Azure OpenAI
```bash
EMBEDDING_API_BASE=https://your-resource.openai.azure.com
EMBEDDING_MODEL=text-embedding-3-small
LLM_API_BASE=https://your-resource.openai.azure.com
LLM_MODEL=gpt-4
AI_API_KEY=your-azure-api-key
```

### 📧 이메일 설정

```bash
# Gmail 사용 예시
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password
MAIL_FROM=noreply@yourdomain.com
```

### 📁 파일 저장소 설정

#### GridFS (기본값)
```bash
FILE_STORAGE_TYPE=gridfs
```

#### AWS S3
```bash
FILE_STORAGE_TYPE=s3
AWS_S3_BUCKET=your-bucket-name
AWS_S3_REGION=us-east-1
AWS_ACCESS_KEY_ID=your-access-key
AWS_SECRET_ACCESS_KEY=your-secret-key
```

#### 로컬 파일 시스템
```bash
FILE_STORAGE_TYPE=local
LOCAL_FILE_PATH=/app/files
```

## 🌍 환경별 설정

### 개발 환경 (.env.dev)

```bash
# 개발용 설정
SPRING_PROFILES_ACTIVE=dev
LOG_LEVEL=DEBUG
VITE_DEBUG_MODE=true
VITE_LOG_API_CALLS=true

# 로컬 서비스
MONGO_URI=mongodb://localhost:27017/mdm_dev
EMBEDDING_API_BASE=http://localhost:11434
LLM_API_BASE=http://localhost:11434
```

### 스테이징 환경 (.env.staging)

```bash
# 스테이징용 설정
SPRING_PROFILES_ACTIVE=staging
LOG_LEVEL=INFO
VITE_DEBUG_MODE=false

# 외부 서비스
MONGO_URI=mongodb+srv://user:pass@staging-cluster.mongodb.net/mdm
EMBEDDING_API_BASE=https://api.openai.com/v1
LLM_API_BASE=https://api.openai.com/v1
AI_API_KEY=sk-staging-key
```

### 프로덕션 환경 (.env.prod)

```bash
# 프로덕션용 설정
SPRING_PROFILES_ACTIVE=prod
LOG_LEVEL=WARN
VITE_DEBUG_MODE=false

# 강화된 보안
JWT_SECRET=super-secure-production-key
ADMIN_PASSWORD=ultra-secure-password
MONGO_ROOT_PASSWORD=production-mongo-password

# 프로덕션 서비스
MONGO_URI=mongodb+srv://user:pass@prod-cluster.mongodb.net/mdm
EMBEDDING_API_BASE=https://api.openai.com/v1
LLM_API_BASE=https://api.openai.com/v1
AI_API_KEY=sk-prod-key
```

## 🔒 보안 고려사항

### 1. 비밀키 생성

```bash
# JWT 비밀키 생성 (256비트)
openssl rand -base64 32

# 또는 Node.js로 생성
node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"
```

### 2. 환경변수 파일 권한 설정

```bash
# .env 파일 권한 제한
chmod 600 .env
chmod 600 frontend/.env.local

# 소유자만 읽기/쓰기 가능
chown $USER:$USER .env frontend/.env.local
```

### 3. .gitignore 설정

```bash
# .gitignore에 추가
echo ".env" >> .gitignore
echo ".env.*" >> .gitignore
echo "!.env.example" >> .gitignore
echo "frontend/.env.local" >> .gitignore
echo "frontend/.env.*" >> .gitignore
echo "!frontend/env.example" >> .gitignore
```

### 4. Docker Secrets 사용

```bash
# Docker Compose에서 secrets 사용
version: '3.8'
services:
  backend:
    environment:
      JWT_SECRET_FILE: /run/secrets/jwt_secret
    secrets:
      - jwt_secret

secrets:
  jwt_secret:
    file: ./secrets/jwt_secret.txt
```

## 🐛 문제 해결

### 1. 환경변수가 적용되지 않는 경우

```bash
# 환경변수 확인
printenv | grep MONGO
printenv | grep JWT

# Docker Compose에서 환경변수 확인
docker-compose config
```

### 2. MongoDB 연결 실패

```bash
# MongoDB 연결 테스트
mongosh "mongodb://localhost:27017/mdm"

# Docker 컨테이너 내부에서 확인
docker exec -it mdm-mongodb mongosh
```

### 3. 프론트엔드 API 연결 실패

```bash
# API 엔드포인트 확인
curl http://localhost:8080/api/actuator/health

# CORS 설정 확인
curl -H "Origin: http://localhost:3000" \
     -H "Access-Control-Request-Method: GET" \
     -H "Access-Control-Request-Headers: X-Requested-With" \
     -X OPTIONS \
     http://localhost:8080/api/auth/login
```

### 4. AI 서비스 연결 실패

```bash
# Ollama 서비스 확인
curl http://localhost:11434/api/tags

# OpenAI API 확인
curl -H "Authorization: Bearer $AI_API_KEY" \
     https://api.openai.com/v1/models
```

## 📚 추가 리소스

- [Spring Boot 외부화된 설정](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
- [Vite 환경변수](https://vitejs.dev/guide/env-and-mode.html)
- [Docker Compose 환경변수](https://docs.docker.com/compose/environment-variables/)
- [MongoDB Atlas 연결 문자열](https://docs.atlas.mongodb.com/driver-connection/)

## 💡 팁

1. **개발 시**: 기본값을 사용하여 빠르게 시작
2. **테스트 시**: 별도의 테스트 데이터베이스 사용
3. **프로덕션 시**: 모든 비밀키를 강력한 값으로 변경
4. **모니터링**: 환경변수 변경 시 로그 확인
5. **백업**: 중요한 설정은 별도로 백업

---

**문제가 있으시면 이슈를 생성해 주세요!** 🚀
