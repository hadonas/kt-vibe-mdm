// API 구현 상태 관리
export const API_STATUS = {
  // 구현 완료된 API들
  IMPLEMENTED: {
    // Authentication
    '/auth/login': true,
    '/auth/signup': true,
    '/auth/password/forgot': true,
    '/auth/me': true,
    
    // Ingest
    '/ingest/single': true,
    '/ingest/bulk': true,
    '/ingest/{id}/resubmit': true,
    
    // Approval
    '/approval/requests': true,
    '/approval/requests/{id}/decide': true,
    
    // Chat
    '/chat/query': true,
  },
  
  // 미구현 API들 (501 Not Implemented 반환 예상)
  NOT_IMPLEMENTED: {
    // File
    '/files/presign': true,
    '/files/{id}': true,
    
    // Search
    '/search/similar': true,
    '/search/query': true,
    
    // Document
    '/documents/{id}': true,
    
    // Catalog
    '/catalog/tree': true,
  }
}

export function isApiImplemented(endpoint: string): boolean {
  return API_STATUS.IMPLEMENTED[endpoint] || false
}

export function isApiNotImplemented(endpoint: string): boolean {
  return API_STATUS.NOT_IMPLEMENTED[endpoint] || false
}
