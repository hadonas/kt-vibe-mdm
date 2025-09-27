# MDM 시스템 아키텍처 다이어그램

## 통합 시스템 아키텍처 다이어그램 (서비스별)

```mermaid
graph TB
    subgraph "User Layer"
        U[일반 유저]
        A[관리자 유저]
    end

    subgraph "Frontend Layer"
        F[Next.js App<br/>가변 계층 UI]
        UI[React Components<br/>다단계 카테고리 선택]
    end

    subgraph "API Gateway Layer"
        API[REST API Controllers]
    end

    subgraph "Service Layer"
        AUTH[Auth Service]
        FS[File Service<br/>텍스트 추출]
        IS[Ingest Service<br/>+ 스마트 분류]
        AS[Approval Service]
        CS[Chat Service<br/>RAG]
        SCS[Smart Classification<br/>Service]
        CUS[Category Usage<br/>Service]
        DDS[Document Deletion<br/>Service]
        EIS[Elasticsearch Index<br/>Service]
    end

    subgraph "Data Layer"
        M[(MongoDB<br/>가변 계층 문서)]
        E[(Elasticsearch<br/>샤드 기반 검색)]
        LocalFS[로컬 파일<br/>시스템]
    end

    subgraph "External Services"
        AI[AI Service<br/>Ollama/OpenAI<br/>분류 + 임베딩]
    end

    U --> F
    A --> F
    F --> UI
    UI --> API
    
    API --> AUTH
    API --> FS
    API --> IS
    API --> AS
    API --> CS
    API --> SCS
    API --> CUS
    API --> DDS
    API --> EIS
    
    AUTH --> M
    FS --> LocalFS
    IS --> M
    IS --> CUS
    IS --> EIS
    IS --> AI
    AS --> M
    CS --> E
    CS --> AI
    SCS --> AI
    SCS --> M
    CUS --> M
    DDS --> M
    DDS --> E
    DDS --> LocalFS
    DDS --> CUS
    EIS --> E
    
    AI --> E
```

## 전체 시스템 구조

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Frontend      │    │    Backend      │    │   Database      │
│   (Next.js)     │◄──►│  (Spring Boot)  │◄──►│   (MongoDB)     │
│   Port: 3000    │    │   Port: 8080    │    │   Port: 27017   │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## 기술 스택

### 백엔드
- **Framework**: Spring Boot 3.2
- **Language**: Java 17
- **Database**: MongoDB 7.0
- **Authentication**: JWT
- **Documentation**: OpenAPI 3.0 (Swagger)
- **Build Tool**: Gradle

### 프론트엔드
- **Framework**: Next.js 14
- **Language**: TypeScript
- **UI Library**: React 18
- **Styling**: Tailwind CSS
- **HTTP Client**: Axios
- **Build Tool**: npm

### 인프라
- **Containerization**: Docker & Docker Compose
- **Reverse Proxy**: Nginx (개발 환경)
- **File Storage**: GridFS (MongoDB)



