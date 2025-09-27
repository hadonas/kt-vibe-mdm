import axios from 'axios'

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api'

export const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Request interceptor to add auth token
api.interceptors.request.use((config) => {
  if (typeof window !== 'undefined') {
    const token = localStorage.getItem('accessToken')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
  }
  return config
})

// Response interceptor to handle auth errors and connection issues
api.interceptors.response.use(
  (response) => response,
  (error) => {
    // Handle auth errors
    if (error.response?.status === 401 && typeof window !== 'undefined') {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      window.location.href = '/login'
    }
    
    // Handle connection refused errors
    if (error.code === 'ECONNREFUSED' || error.message?.includes('CONNECTION_REFUSED')) {
      console.warn('Connection refused, retrying...', error.message)
      // You could implement retry logic here if needed
    }
    
    return Promise.reject(error)
  }
)

// Public API functions (no admin role required)
export const publicApi = {
  // Get document count
  getDocumentCount: () => 
    api.get('/public/documents/count'),
  
  // Download file by code
  downloadFileByCode: (code: string) => 
    api.get(`/public/files/download-by-code?code=${encodeURIComponent(code)}`, {
      responseType: 'blob'
    }),
  
  // Download file
  downloadFile: (filePath: string) => 
    api.get(`/public/files/download?filePath=${encodeURIComponent(filePath)}`, {
      responseType: 'blob'
    }),
  
  // Get my documents
  getMyDocuments: (page = 0, size = 20) => 
    api.get(`/public/my-documents?page=${page}&size=${size}`)
}

// Admin API functions (admin role required)
export const adminApi = {
  // Get all documents
  getDocuments: (page = 0, size = 20) => 
    api.get(`/admin/documents?page=${page}&size=${size}`),
  
  // Get document hierarchy (가변 계층 지원)
  getDocumentHierarchy: () => 
    api.get('/admin/documents/hierarchy'),
  
  // Get documents by category (가변 계층 지원)
  getDocumentsByCategory: (categoryCode: string, page = 0, size = 20) => 
    api.get(`/admin/documents/category/${encodeURIComponent(categoryCode)}?page=${page}&size=${size}`),
  
  // Get files by category (legacy 3-level support)
  getFilesByCategory: (majorCode: string, midCode: string, subCode: string) => 
    api.get(`/admin/files/category/${majorCode}/${midCode}/${subCode}`),
  
  // Get duplicate documents
  getDuplicateDocuments: () => 
    api.get('/admin/documents/duplicates'),
  
  // Delete document by code
  deleteDocumentByCode: (code: string) => 
    api.delete(`/admin/documents/${code}`),
  
  // Delete documents by category
  deleteDocumentsByCategory: (categoryCode: string) => 
    api.delete(`/admin/documents/category/${encodeURIComponent(categoryCode)}`),
}

// Category API functions (스마트 카테고리 관리)
export const categoryApi = {
  // Get all categories
  getCategories: () => 
    api.get('/categories'),
  
  // Get category by code
  getCategory: (code: string) => 
    api.get(`/categories/${code}`),
  
  // Create new category
  createCategory: (data: any) => 
    api.post('/categories', data),
  
  // Update category
  updateCategory: (code: string, data: any) => 
    api.put(`/categories/${code}`, data),
  
  // Search categories
  searchCategories: (query: string, limit = 10) => 
    api.get(`/categories/search?query=${encodeURIComponent(query)}&limit=${limit}`),
  
  // Classify document
  classifyDocument: (data: { title: string; summary: string }) => 
    api.post('/categories/classify', data),
  
  // Preview category metadata
  previewMetadata: (data: { name: string; code: string; parentCode?: string; level: number }) => 
    api.post('/categories/metadata/preview', data),
  
  // Auto-fill category fields
  autoFillCategory: (code: string) => 
    api.post(`/categories/${code}/auto-fill`),
  
  // Auto-fill all categories
  autoFillAllCategories: () => 
    api.post('/categories/auto-fill-all'),
  
  // Regenerate all embeddings
  regenerateAllEmbeddings: () => 
    api.post('/categories/embeddings/regenerate'),
  
  // Regenerate category embedding
  regenerateCategoryEmbedding: (code: string) => 
    api.post(`/categories/${code}/embedding`),
  
  // Reclassify all documents
  reclassifyAllDocuments: () => 
    api.post('/categories/reclassify/all'),
  
  // Reclassify documents by category
  reclassifyDocumentsByCategory: (categoryCode: string) => 
    api.post(`/categories/reclassify/category/${categoryCode}`),
  
  // Get document counts by category
  getDocumentCounts: () => 
    api.get('/categories/document-counts'),
  
  // Get documents by category
  getCategoryDocuments: (categoryCode: string, page: number = 0, size: number = 20) => 
    api.get(`/categories/${categoryCode}/documents?page=${page}&size=${size}`),
  
  // Force regenerate all category metadata with LLM
  forceRegenerateAllMetadata: () => 
    api.post('/categories/force-regenerate-metadata'),
}

// Search API
export const searchApi = {
  // Reindex all documents
  reindexDocuments: () =>
    api.post('/search/reindex'),
}

export default api
