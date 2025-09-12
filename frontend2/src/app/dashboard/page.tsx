'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { 
  DocumentTextIcon,
  FolderIcon,
  ChatBubbleLeftRightIcon,
  ClipboardDocumentCheckIcon,
  UserGroupIcon,
  ChartBarIcon
} from '@heroicons/react/24/outline'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card'
import { useAuth } from '@/hooks/useAuth'

export default function DashboardPage() {
  const { isAuthenticated, user, isLoading, isAdmin, isApprover } = useAuth()
  const router = useRouter()

  useEffect(() => {
    console.log('📊 Dashboard auth state:', { isAuthenticated, user, isLoading })
    
    if (!isLoading && !isAuthenticated) {
      console.log('🔄 Redirecting to login...')
      router.push('/login')
    }
  }, [isAuthenticated, isLoading, router])

  // 로딩 중일 때
  if (isLoading) {
    console.log('⏳ Dashboard is loading...')
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-32 w-32 border-b-2 border-blue-600 mx-auto"></div>
          <p className="mt-4 text-gray-600">인증 상태를 확인하는 중...</p>
        </div>
      </div>
    )
  }

  // 인증되지 않은 경우 (리다이렉트 전까지의 짧은 시간)
  if (!isAuthenticated) {
    console.log('🚫 Not authenticated, should redirect...')
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-32 w-32 border-b-2 border-blue-600 mx-auto"></div>
          <p className="mt-4 text-gray-600">로그인 페이지로 이동 중...</p>
        </div>
      </div>
    )
  }

  // 사용자 정보가 없는 경우
  if (!user) {
    console.log('👤 No user data available')
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-32 w-32 border-b-2 border-blue-600 mx-auto"></div>
          <p className="mt-4 text-gray-600">사용자 정보를 불러오는 중...</p>
        </div>
      </div>
    )
  }

  console.log('✅ Dashboard ready to render for user:', user.name)

  const quickActions = [
    {
      title: '단일 문서 등록',
      description: 'DOCX, XLSX 파일을 업로드하여 등록 요청',
      icon: DocumentTextIcon,
      href: '/ingest/document',
      color: 'bg-blue-500',
    },
    {
      title: '레포지토리 등록',
      description: 'GitHub 레포지토리를 분석하여 등록 요청',
      icon: FolderIcon,
      href: '/ingest/repository',
      color: 'bg-green-500',
    },
    {
      title: '벌크 등록',
      description: 'CSV 또는 ZIP 파일로 대량 등록',
      icon: ClipboardDocumentCheckIcon,
      href: '/ingest/bulk',
      color: 'bg-purple-500',
    },
    {
      title: 'RAG 채팅',
      description: '등록된 문서들과 대화하며 정보 검색',
      icon: ChatBubbleLeftRightIcon,
      href: '/chat',
      color: 'bg-orange-500',
    },
  ]

  if (isApprover) {
    quickActions.push({
      title: '승인 관리',
      description: '등록 요청 승인/반려 처리',
      icon: UserGroupIcon,
      href: '/approval',
      color: 'bg-red-500',
    })
  }

  return (
    <div className="max-w-7xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-gray-900">
          안녕하세요, {user.name}님!
        </h1>
        <p className="mt-2 text-gray-600">
          문서 관리 시스템에 오신 것을 환영합니다. 
          {isAdmin && ' (관리자 권한)'}
          {isApprover && !isAdmin && ' (승인자 권한)'}
        </p>
      </div>

      {/* 통계 카드 */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">대기 중인 요청</CardTitle>
            <ClipboardDocumentCheckIcon className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">12</div>
            <p className="text-xs text-muted-foreground">
              승인 대기 중인 등록 요청
            </p>
          </CardContent>
        </Card>
        
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">등록된 문서</CardTitle>
            <DocumentTextIcon className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">1,234</div>
            <p className="text-xs text-muted-foreground">
              시스템에 등록된 총 문서 수
            </p>
          </CardContent>
        </Card>
        
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">이번 달 등록</CardTitle>
            <ChartBarIcon className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">89</div>
            <p className="text-xs text-muted-foreground">
              이번 달 새로 등록된 문서
            </p>
          </CardContent>
        </Card>
      </div>

      {/* 빠른 작업 */}
      <div className="mb-8">
        <h2 className="text-xl font-semibold text-gray-900 mb-4">빠른 작업</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {quickActions.map((action) => (
            <Card key={action.title} className="hover:shadow-lg transition-shadow cursor-pointer">
              <Link href={action.href}>
                <CardHeader>
                  <div className="flex items-center space-x-3">
                    <div className={`p-2 rounded-lg ${action.color}`}>
                      <action.icon className="h-5 w-5 text-white" />
                    </div>
                    <div>
                      <CardTitle className="text-lg">{action.title}</CardTitle>
                    </div>
                  </div>
                </CardHeader>
                <CardContent>
                  <CardDescription>{action.description}</CardDescription>
                </CardContent>
              </Link>
            </Card>
          ))}
        </div>
      </div>

      {/* 최근 활동 */}
      <div>
        <h2 className="text-xl font-semibold text-gray-900 mb-4">최근 활동</h2>
        <Card>
          <CardContent className="p-6">
            <div className="space-y-4">
              <div className="flex items-center space-x-3">
                <div className="w-2 h-2 bg-green-500 rounded-full"></div>
                <div className="flex-1">
                  <p className="text-sm font-medium">문서 &quot;프로젝트 계획서&quot;가 승인되었습니다.</p>
                  <p className="text-xs text-gray-500">2시간 전</p>
                </div>
              </div>
              <div className="flex items-center space-x-3">
                <div className="w-2 h-2 bg-blue-500 rounded-full"></div>
                <div className="flex-1">
                  <p className="text-sm font-medium">새로운 레포지토리 등록 요청이 제출되었습니다.</p>
                  <p className="text-xs text-gray-500">5시간 전</p>
                </div>
              </div>
              <div className="flex items-center space-x-3">
                <div className="w-2 h-2 bg-orange-500 rounded-full"></div>
                <div className="flex-1">
                  <p className="text-sm font-medium">벌크 등록 작업이 완료되었습니다. (성공: 45, 실패: 2)</p>
                  <p className="text-xs text-gray-500">1일 전</p>
                </div>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
