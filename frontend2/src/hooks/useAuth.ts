'use client'

import { useState, useEffect } from 'react'
import AuthService from '@/lib/auth'
import { User } from '@/types/api'

export function useAuth() {
  const [isAuthenticated, setIsAuthenticated] = useState(false)
  const [user, setUser] = useState<User | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [token, setToken] = useState<string | null>(null)

  useEffect(() => {
    const checkAuth = async () => {
      try {
        console.log('🔍 Checking authentication status...')
        const authenticated = AuthService.isAuthenticated()
        
        if (authenticated) {
          console.log('🔍 Token found, fetching user data from server...')
          const currentToken = AuthService.getToken()
          const currentUser = await AuthService.getCurrentUserFromServer()
          
          if (currentUser && currentToken) {
            console.log('🔍 Auth check results:', { authenticated: true, user: currentUser })
            setIsAuthenticated(true)
            setUser(currentUser)
            setToken(currentToken)
          } else {
            console.log('🔍 Failed to get user from server, clearing auth')
            AuthService.logout()
            setIsAuthenticated(false)
            setUser(null)
            setToken(null)
          }
        } else {
          console.log('🔍 No token found')
          setIsAuthenticated(false)
          setUser(null)
          setToken(null)
        }
        
        setIsLoading(false)
      } catch (error) {
        console.error('❌ Error checking auth:', error)
        AuthService.logout()
        setIsAuthenticated(false)
        setUser(null)
        setToken(null)
        setIsLoading(false)
      }
    }

    // 클라이언트 사이드에서만 실행되도록 보장
    if (typeof window !== 'undefined') {
      checkAuth()
    } else {
      // 서버 사이드에서는 로딩 상태를 즉시 해제
      setIsLoading(false)
    }
  }, [])

  const logout = () => {
    AuthService.logout()
    setIsAuthenticated(false)
    setUser(null)
    setToken(null)
  }

  return {
    isAuthenticated,
    user,
    token,
    isLoading,
    logout,
    isAdmin: user?.roles.includes('ADMIN') ?? false,
    isApprover: (user?.roles.includes('APPROVER') || user?.roles.includes('ADMIN')) ?? false,
  }
}
