# MDM Frontend 2.0

Modern Next.js frontend for the MDM (Monolithic Document Management) system.

## Features

### ✅ 구현 완료
- **Modern UI**: Built with Next.js 15, React 19, and TailwindCSS
- **Authentication**: JWT-based login/signup system with role-based access
- **Document Upload**: Single document upload with file validation (DOCX, XLSX, PDF)
- **Repository Registration**: GitHub repository analysis and registration
- **Bulk Upload**: ZIP and CSV bulk upload support
- **Admin Dashboard**: Overview with statistics and quick actions
- **Responsive Design**: Mobile-friendly interface

### 🚧 백엔드 연동 대기 중
- **Approval System**: Admin approval workflow for document registration
- **RAG Chat**: Chat interface with document search capabilities
- **File Management**: Presigned URL file uploads
- **Document Search**: Vector and hybrid search functionality
- **Catalog Management**: Category tree management

## Tech Stack

- **Framework**: Next.js 15 with App Router
- **UI**: TailwindCSS + Headless UI
- **Icons**: Heroicons
- **HTTP Client**: Axios
- **TypeScript**: Full type safety

## Getting Started

1. **Install dependencies**:
   ```bash
   npm install
   ```

2. **Environment setup**:
   ```bash
   cp .env.example .env.local
   ```
   
   Update the API base URL in `.env.local`:
   ```
   NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api
   ```

3. **Run development server**:
   ```bash
   npm run dev
   ```
   
   The application will be available at [http://localhost:3001](http://localhost:3001)

## Project Structure

```
src/
├── app/                    # Next.js App Router pages
│   ├── approval/          # Admin approval system (UI ready)
│   ├── chat/              # RAG chat interface (UI ready)
│   ├── dashboard/         # Main dashboard
│   ├── ingest/            # Document/repository upload
│   ├── login/             # Authentication
│   └── signup/
├── components/
│   ├── layout/            # Navigation and layout
│   └── ui/                # Reusable UI components
├── lib/                   # Utilities and services
│   ├── api.ts             # API client configuration
│   ├── auth.ts            # Authentication service
│   └── utils.ts           # Utility functions
└── types/
    └── api.ts             # TypeScript type definitions
```

## Key Pages

### Authentication
- `/login` - User login ✅
- `/signup` - User registration ✅

### Main Features
- `/dashboard` - Main dashboard with statistics ✅
- `/ingest/document` - Single document upload ✅
- `/ingest/repository` - GitHub repository registration ✅
- `/ingest/bulk` - Bulk upload (ZIP/CSV) ✅
- `/approval` - Admin approval system (UI 완료, API 연동 대기)
- `/chat` - RAG chat interface (UI 완료, API 연동 대기)

## Backend API Status

### ✅ 구현 완료된 API
- **Authentication**: `/auth/login`, `/auth/signup`, `/auth/password/forgot`
- **Document Ingest**: `/ingest/single`, `/ingest/bulk`, `/ingest/{id}/resubmit`

### 🚧 미구현 API (프론트엔드 UI는 준비 완료)
- **Approval**: `/approval/requests`, `/approval/requests/{id}/decide`
- **File Upload**: `/files/presign`, `/files/{id}`
- **Chat**: `/chat/query`
- **Search**: `/search/similar`, `/search/query`
- **Document**: `/documents/{id}`
- **Catalog**: `/catalog/tree`

## User Roles

- **USER**: Basic user can submit registration requests
- **APPROVER**: Can approve/reject registration requests (UI ready)
- **ADMIN**: Full system access including user management (UI ready)

## Document Registration Flow

1. **User uploads documents or provides repository URL** ✅
2. **System analyzes content and suggests categories** ✅
3. **Checks for similar existing documents** ✅
4. **Creates registration request** ✅
5. **Admin reviews and approves/rejects** (UI 완료, API 대기)
6. **Approved documents stored in MongoDB vector DB** (백엔드 대기)

## Development

### Building for Production
```bash
npm run build
npm start
```

### Code Quality
```bash
npm run lint
```

## Environment Variables

- `NEXT_PUBLIC_API_BASE_URL`: Backend API base URL (default: http://localhost:8080/api)
- `NODE_ENV`: Environment (development/production)

## API Integration Status

### ✅ Working Endpoints
```typescript
// Authentication
POST /auth/login
POST /auth/signup  
POST /auth/password/forgot

// Document Ingest
POST /ingest/single        // Repository preview or file upload
POST /ingest/bulk         // ZIP/CSV bulk upload
POST /ingest/{id}/resubmit // Resubmit with changes
```

### 🚧 Frontend Ready, Backend Pending
```typescript
// These endpoints have full UI implementation
// but are waiting for backend implementation

POST /approval/requests/{id}/decide
GET  /approval/requests
POST /chat/query
POST /files/presign
GET  /files/{id}
POST /search/similar
POST /search/query
GET  /documents/{id}
GET  /catalog/tree
```

## Notes

- All frontend components are fully implemented and styled
- Authentication and document upload flows are working
- Approval system and chat interface have complete UI but need backend APIs
- Error handling and loading states are implemented throughout
- Responsive design works on all screen sizes
- TypeScript types are defined for all API endpoints

## License

Private project - All rights reserved.