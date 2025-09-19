// Auth Types
export interface LoginRequest {
  email: string
  password: string
}

export interface SignupRequest {
  email: string
  password: string
  name: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  user: User
}

export interface User {
  id: string
  email: string
  name: string
  roles: ('USER' | 'APPROVER' | 'ADMIN')[]
  createdAt: string
  lastLoginAt: string
}

// Ingest Types
export interface SingleIngestRequest {
  repoUrl?: string
  accessToken?: string
  fileIds?: string[]
  purpose?: string
  tags?: string[]
  proposedCategory?: Category
  proposedTitle?: string
  originalFileName?: string
}

export interface IngestPreviewResponse {
  extractedText: string
  proposedCategory: Category
  proposedPurpose: string
  similarCandidates: SimilarCandidate[]
}

export interface IngestRequestResponse {
  id: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'IMPORTED'
  requestedAt: string
}


// Category Types
export interface Category {
  majorCode: string
  majorName: string
  midCode: string
  midName: string
  subCode: string
  subName: string
  fullCode?: string // 가변 계층 지원을 위한 전체 코드
  fullName?: string // 가변 계층 지원을 위한 전체 이름
  hierarchy?: CategoryLevel[] // 계층 구조 정보
}

export interface CategoryLevel {
  level: number
  code: string
  name: string
}

export interface SimilarCandidate {
  docId: string
  score: number
  serial: string
  purpose: string
  snippet: string
}

// Approval Types
export interface ApprovalRequest {
  id: string
  ownerId: string
  source: Source
  extractedText: string
  proposedCategory?: Category
  proposedTitle?: string
  proposedPurpose: string
  similarCandidates: SimilarCandidate[]
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'IMPORTED'
  requestedAt: string
  approvedAt?: string
  approvedBy?: string
}

export interface ApprovalDecision {
  decision: 'APPROVE' | 'REJECT'
  category?: Category
  purpose?: string
  reason?: string
}

export interface Source {
  type: 'REPO' | 'PLAN'
  repoUrl?: string
  files?: string[]
}

// Chat Types
export interface ChatQueryRequest {
  query: string
  context?: string[]
}

export interface ChatQueryResponse {
  answer: string
  sources: ChatSource[]
}

export interface ChatSource {
  docId: string
  serial: string
  snippet: string
}

// File Types
export interface PresignRequest {
  fileName: string
  contentType?: string
  size?: number
}

export interface PresignResponse {
  fileId: string
  uploadUrl: string
  expiresAt: string
}

// Document Types
export interface Document {
  id: string
  ownerId: string
  serial: Serial
  category: Category
  purpose: string
  content: string
  source: Source
  requestedAt: string
  approvedAt?: string
  version: number
  acl: ACL
  transferHistory?: TransferRecord[]
}

export interface Serial {
  subCode: string
  number: number
  full: string
}

export interface ACL {
  ownerId: string
  shared: SharedUser[]
}

export interface SharedUser {
  userId: string
  permission: 'READ' | 'WRITE'
}

export interface TransferRecord {
  fromUserId: string
  toUserId: string
  transferredAt: string
  reason: string
}

// Search Types
export interface SearchQueryRequest {
  query: string
  mode?: 'hybrid' | 'vector' | 'keyword'
  filters?: Record<string, unknown>
  topK?: number
}

export interface SearchQueryResponse {
  results: SearchResult[]
  total: number
}

export interface SearchResult {
  docId: string
  serial: Serial
  purpose: string
  snippet: string
  score: number
}
