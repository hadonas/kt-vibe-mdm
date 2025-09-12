import api from './api'
import { LoginRequest, SignupRequest, AuthResponse, User } from '@/types/api'

export class AuthService {
  static async login(credentials: LoginRequest): Promise<AuthResponse> {
    const response = await api.post<AuthResponse>('/auth/login', credentials)
    const { accessToken, refreshToken, user } = response.data
    
    if (typeof window !== 'undefined') {
      localStorage.setItem('accessToken', accessToken)
      localStorage.setItem('refreshToken', refreshToken)
      localStorage.setItem('user', JSON.stringify(user))
    }
    
    return response.data
  }

  static async signup(userData: SignupRequest): Promise<AuthResponse> {
    const response = await api.post<AuthResponse>('/auth/signup', userData)
    const { accessToken, refreshToken, user } = response.data
    
    if (typeof window !== 'undefined') {
      localStorage.setItem('accessToken', accessToken)
      localStorage.setItem('refreshToken', refreshToken)
      localStorage.setItem('user', JSON.stringify(user))
    }
    
    return response.data
  }

  static logout(): void {
    if (typeof window === 'undefined') return
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('user')
  }

  static getCurrentUser(): User | null {
    if (typeof window === 'undefined') return null
    try {
      const userStr = localStorage.getItem('user')
      return userStr ? JSON.parse(userStr) : null
    } catch {
      return null
    }
  }

  static async getCurrentUserFromServer(): Promise<User | null> {
    if (typeof window === 'undefined') return null
    try {
      const response = await api.get<User>('/auth/me')
      const user = response.data
      localStorage.setItem('user', JSON.stringify(user))
      return user
    } catch {
      return null
    }
  }

  static isAuthenticated(): boolean {
    if (typeof window === 'undefined') return false
    return !!localStorage.getItem('accessToken')
  }

  static hasRole(role: 'USER' | 'APPROVER' | 'ADMIN'): boolean {
    const user = this.getCurrentUser()
    return user?.roles.includes(role) ?? false
  }

  static isAdmin(): boolean {
    return this.hasRole('ADMIN')
  }

  static isApprover(): boolean {
    return this.hasRole('APPROVER') || this.hasRole('ADMIN')
  }
}

export default AuthService
