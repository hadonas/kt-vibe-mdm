'use client'

import React, { createContext, useContext, useEffect, useState, useCallback } from 'react'
import AuthService from '@/lib/auth'
import { User } from '@/types/api'

type AuthContextValue = {
  hasInitialized: boolean
  isAuthenticated: boolean
  user: User | null
  token: string | null
  isLoading: boolean
  login: (cred: { email: string; password: string }) => Promise<any>
  logout: () => void
  isAdmin: boolean
  isApprover: boolean
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [hasInitialized, setHasInitialized] = useState(false)
  const [isAuthenticated, setIsAuthenticated] = useState(false)
  const [user, setUser] = useState<User | null>(null)
  const [token, setToken] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  const hydrate = useCallback(async () => {
    try {
      const authed = await AuthService.isAuthenticated()
      const t = await AuthService.getToken()
      setIsAuthenticated(!!authed)
      setToken(t ?? null)

      if (authed && t) {
        const u = await AuthService.getCurrentUserFromServer()
        if (u) {
          setUser(u)
          setIsAuthenticated(true)
        } else {
          AuthService.logout()
          setUser(null)
          setIsAuthenticated(false)
          setToken(null)
        }
      } else {
        setUser(null)
      }
    } catch {
      AuthService.logout()
      setUser(null)
      setIsAuthenticated(false)
      setToken(null)
    } finally {
      setIsLoading(false)
      setHasInitialized(true)
    }
  }, [])

  useEffect(() => {
    let mounted = true
    ;(async () => { if (mounted) await hydrate() })()

    // 다른 탭/윈도우 동기화
    const onStorage = (e: StorageEvent) => {
      if (e.key === 'accessToken') hydrate()
    }
    window.addEventListener('storage', onStorage)

    // 같은 탭 내 컴포넌트 간 즉시 동기화
    const onAuthChanged = () => hydrate()
    window.addEventListener('auth:changed', onAuthChanged as any)

    return () => {
      mounted = false
      window.removeEventListener('storage', onStorage)
      window.removeEventListener('auth:changed', onAuthChanged as any)
    }
  }, [hydrate])

  const login = useCallback(async (credentials: { email: string; password: string }) => {
    setIsLoading(true)
    try {
      const res = await AuthService.login(credentials)
      setIsAuthenticated(true)
      setUser(res.user)
      setToken(res.accessToken)
      // 같은 탭/다른 탭에 상태 변경 알림
      try { window.dispatchEvent(new Event('auth:changed')) } catch {}
      return res
    } finally {
      setIsLoading(false)
      setHasInitialized(true)
    }
  }, [])

  const logout = useCallback(() => {
    AuthService.logout()
    setIsAuthenticated(false)
    setUser(null)
    setToken(null)
    setHasInitialized(true)
    try { window.dispatchEvent(new Event('auth:changed')) } catch {}
  }, [])

  const value: AuthContextValue = {
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

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within <AuthProvider>')
  return ctx
}