# MDM 시스템 아키텍처 다이어그램

## 통합 시스템 아키텍처 다이어그램 (서비스별)

```mermaid
graph TB
    subgraph "User Layer"
        U[일반 유저]
        A[관리자 유저]
    end

    subgraph "Frontend Layer"
        F[Next.js App]
        UI[React Components]
    end

    subgraph "API Gateway Layer"
        API[REST API Controllers]
    end

    subgraph "Service Layer"
        AUTH[Auth Service]
        FS[File Service]
        IS[Ingest Service]
        AS[Approval Service]
        CS[Chat Service]
        VSS[Vector Search Service]
        PS[Public Service]
    end

    subgraph "Data Layer"
        M[(MongoDB)]
        GridFS[GridFS]
        FAISS[FAISS Vector Store]
    end

    subgraph "External Services"
        AI[AI Service<br/>Ollama/OpenAI]
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
    API --> VSS
    API --> PS
    
    AUTH --> M
    FS --> GridFS
    IS --> M
    IS --> GridFS
    AS --> M
    CS --> VSS
    CS --> AI
    VSS --> FAISS
    VSS --> M
    PS --> M
    PS --> FS
    
    AI --> FAISS
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



