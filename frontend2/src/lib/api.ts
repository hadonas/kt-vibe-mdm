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

// Admin API functions
export const adminApi = {
  // Get all documents
  getDocuments: (page = 0, size = 20) => 
    api.get(`/admin/documents?page=${page}&size=${size}`),
  
  // Get document hierarchy
  getDocumentHierarchy: () => 
    api.get('/admin/documents/hierarchy'),
  
  // Get files by category
  getFilesByCategory: (majorCode: string, midCode: string, subCode: string) => 
    api.get(`/admin/files/category/${majorCode}/${midCode}/${subCode}`),
  
  // Get duplicate documents
  getDuplicateDocuments: () => 
    api.get('/admin/documents/duplicates'),
  
  // Delete document by code
  deleteDocumentByCode: (code: string) => 
    api.delete(`/admin/documents/${code}`),
  
  // Download file
  downloadFile: (filePath: string) => 
    api.get(`/admin/files/download?filePath=${encodeURIComponent(filePath)}`, {
      responseType: 'blob'
    })
}

export default api
