'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@/contexts/AuthContexts'

export default function Home() {
  const { hasInitialized, isAuthenticated, isLoading } = useAuth()
  const router = useRouter()

  useEffect(() => {
    console.log('🏠 Home - hasInitialized:', hasInitialized, 'isAuthenticated:', isAuthenticated, 'isLoading:', isLoading)
    if (!hasInitialized) return
    
    // 인증 상태에 따라 적절한 페이지로 리다이렉트
    if (isAuthenticated) {
      console.log('🏠 Home - 대시보드로 리다이렉트')
      router.replace('/dashboard')
    } else {
      console.log('🏠 Home - 로그인 페이지로 리다이렉트')
      router.replace('/login')
    }
  }, [hasInitialized, isAuthenticated, router])

  // 초기화가 완료되지 않았거나 로딩 중일 때만 로딩 화면 표시
  if (!hasInitialized || isLoading) {
    console.log('🏠 Home - 로딩 중')
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-32 w-32 border-b-2 border-blue-600 mx-auto"></div>
          <p className="mt-4 text-gray-600">로딩 중...</p>
        </div>
      </div>
    )
  }

  console.log('🏠 Home - 페이지를 준비 중입니다...')
  // 이 부분은 일반적으로 도달하지 않지만, 안전을 위해 추가
  return (
    <div className="min-h-screen flex items-center justify-center">
      <div className="text-center">
        <div className="animate-spin rounded-full h-32 w-32 border-b-2 border-blue-600 mx-auto"></div>
        <p className="mt-4 text-gray-600">페이지를 준비 중입니다...</p>
      </div>
    </div>
  )
}