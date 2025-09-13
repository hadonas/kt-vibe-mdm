'use client'

import { useState, useEffect } from 'react'
import AuthService from '@/lib/auth'
import { User } from '@/types/api'

export function useAuth() {
  const [hasInitialized, setHasInitialized] = useState(false)
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(false)
  const [user, setUser] = useState<User | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [token, setToken] = useState<string | null>(null)

  useEffect(() => {
    let mounted = true

    const checkAuth = async () => {
      try {
        // 1) 로컬 토큰 동기 확인 (빠르게 화면 깜빡임 줄임)
        const localAuthenticated = await AuthService.isAuthenticated()
        const localToken = await AuthService.getToken()
        if (mounted) {
          setIsAuthenticated(localAuthenticated)
          setToken(localToken)
        }

        if (!localAuthenticated || !localToken) {
          if (mounted) {
            setUser(null)
            setIsLoading(false)
          }
          return
        }

        // 2) 서버에서 현재 유저 조회
        const currentUser = await AuthService.getCurrentUserFromServer()
        if (mounted) {
          if (currentUser) {
            setUser(currentUser)
            setIsAuthenticated(true)
          } else {
            AuthService.logout()
            setUser(null)
            setIsAuthenticated(false)
            setToken(null)
          }
        }
      } catch (e) {
        if (mounted) {
          AuthService.logout()
          setUser(null)
          setIsAuthenticated(false)
          setToken(null)
        }
      } finally {
        if (mounted) {
          setIsLoading(false)
          setHasInitialized(true)
        }
      }
    }

    checkAuth()
    return () => { mounted = false }
  }, [])

  const login = async (credentials: { email: string; password: string }) => {
    setIsLoading(true)
    try {
      const res = await AuthService.login(credentials)
      setIsAuthenticated(true)
      setUser(res.user)
      setToken(res.accessToken)
      return res
    } finally {
      setIsLoading(false)
    }
  }

  const logout = () => {
    AuthService.logout()
    setIsAuthenticated(false)
    setUser(null)
    setToken(null)
    setIsLoading(false)
  }

  return {
    hasInitialized,
    isAuthenticated,
    user,
    token,
    isLoading,
    login,
    logout,
    isAdmin: user?.roles?.includes('ADMIN') ?? false,
    isApprover: (user?.roles?.includes('APPROVER') || user?.roles?.includes('ADMIN')) ?? false,
  }
}