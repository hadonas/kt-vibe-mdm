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
  ChartBarIcon,
  CogIcon,
  ServerIcon,
  UserIcon
} from '@heroicons/react/24/outline'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card'
import { useAuth } from '@/hooks/useAuth'

interface DashboardStats {
  pendingRequests: number
  totalDocuments: number
  monthlyDocuments: number
  myPendingRequests: number
  myDocuments: number
}

export default function DashboardPage() {
  console.log('🎯 DashboardPage component is executing!')
  
  // 클라이언트 사이드에서만 실행되도록 보장
  const [mounted, setMounted] = useState(false)
  
  useEffect(() => {
    setMounted(true)
    console.log('🎯 DashboardPage mounted on client side')
  }, [])
  
  const { isAuthenticated, user, token, isLoading, logout, isAdmin, isApprover } = useAuth()
  const router = useRouter()
  const [stats, setStats] = useState<DashboardStats>({
    pendingRequests: 0,
    totalDocuments: 0,
    monthlyDocuments: 0,
    myPendingRequests: 0,
    myDocuments: 0
  })
  const [statsLoading, setStatsLoading] = useState(true)

  useEffect(() => {
    console.log('📊 Dashboard auth state:', { 
      isAuthenticated, 
      user: user?.name, 
      isLoading, 
      token: !!token,
      userRoles: user?.roles 
    })
    
    if (!isLoading && !isAuthenticated) {
      console.log('🔄 Redirecting to login...')
      router.push('/login')
    }
  }, [isAuthenticated, isLoading, router, user, token])

  // 컴포넌트가 마운트될 때 로그 추가
  useEffect(() => {
    console.log('🚀 Dashboard component mounted')
    return () => {
      console.log('🛑 Dashboard component unmounted')
    }
  }, [])

  // 클라이언트 사이드 라우팅 문제 해결을 위한 추가 로그
  useEffect(() => {
    console.log('🔄 Dashboard route change detected')
  }, [])

  // 통계 데이터 로드
  useEffect(() => {
    console.log('🔄 Dashboard useEffect triggered:', { isAuthenticated, token: !!token })
    if (isAuthenticated && token) {
      console.log('🚀 Calling loadDashboardStats...')
      loadDashboardStats()
    } else {
      console.log('⏸️ Not calling loadDashboardStats - missing auth or token')
    }
  }, [isAuthenticated, token])

  const loadDashboardStats = async () => {
    try {
      console.log('📊 Starting to load dashboard stats...')
      setStatsLoading(true)
      
      // 대기 중인 요청 수 조회 (사용자 역할에 따라 다른 API 사용)
      const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api'
      console.log('API Base URL:', apiBaseUrl)
      console.log('User roles:', user?.roles)
      console.log('Is admin:', isAdmin, 'Is approver:', isApprover)
      
      // API 호출에 타임아웃 추가
      const fetchWithTimeout = (url: string, options: RequestInit, timeout = 10000) => {
        return Promise.race([
          fetch(url, options),
          new Promise((_, reject) => 
            setTimeout(() => reject(new Error('Request timeout')), timeout)
          )
        ])
      }
      
      let pendingRequests = 0
      if (isAdmin || isApprover) {
        // 관리자/승인자는 전체 대기 중인 요청 조회
        console.log('🔍 Fetching pending requests for admin/approver...')
        const pendingResponse = await fetchWithTimeout(`${apiBaseUrl}/approval/requests?status=PENDING&size=1`, {
          headers: {
            'Authorization': `Bearer ${token}`
          }
        }) as Response
        
        if (!pendingResponse.ok) {
          throw new Error(`Pending requests API failed: ${pendingResponse.status}`)
        }
        
        const pendingData = await pendingResponse.json()
        pendingRequests = pendingData.totalElements || 0
        console.log('✅ Pending requests data:', pendingData)
      } else {
        // 일반 사용자는 자신의 대기 중인 요청만 조회
        console.log('🔍 Fetching my pending requests...')
        const myPendingResponse = await fetchWithTimeout(`${apiBaseUrl}/approval/my-requests?status=PENDING&size=1`, {
          headers: {
            'Authorization': `Bearer ${token}`
          }
        }) as Response
        
        if (!myPendingResponse.ok) {
          throw new Error(`My pending requests API failed: ${myPendingResponse.status}`)
        }
        
        const myPendingData = await myPendingResponse.json()
        pendingRequests = myPendingData.totalElements || 0
        console.log('✅ My pending requests data:', myPendingData)
      }

      // 전체 문서 수 조회 (사용자 역할에 따라 다른 API 사용)
      let totalDocuments = 0
      if (isAdmin || isApprover) {
        // 관리자/승인자는 파일 계층 구조에서 조회
        console.log('🔍 Fetching hierarchy for admin/approver...')
        const hierarchyResponse = await fetchWithTimeout(`${apiBaseUrl}/admin/files/hierarchy`, {
          headers: {
            'Authorization': `Bearer ${token}`
          }
        }) as Response
        
        if (!hierarchyResponse.ok) {
          throw new Error(`Hierarchy API failed: ${hierarchyResponse.status}`)
        }
        
        const hierarchyData = await hierarchyResponse.json()
        totalDocuments = hierarchyData.totalFiles || 0
        console.log('✅ Hierarchy data:', hierarchyData)
      } else {
        // 일반 사용자는 문서 수 API 사용
        console.log('🔍 Fetching document count...')
        const countResponse = await fetchWithTimeout(`${apiBaseUrl}/admin/documents/count`, {
          headers: {
            'Authorization': `Bearer ${token}`
          }
        }) as Response
        
        if (!countResponse.ok) {
          throw new Error(`Document count API failed: ${countResponse.status}`)
        }
        
        const countData = await countResponse.json()
        totalDocuments = countData.totalDocuments || 0
        console.log('✅ Document count data:', countData)
      }

      // 사용자별 데이터 조회
      console.log('🔍 Fetching my pending requests...')
      const myPendingResponse = await fetchWithTimeout(`${apiBaseUrl}/approval/my-requests?status=PENDING&size=1`, {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      }) as Response
      
      if (!myPendingResponse.ok) {
        throw new Error(`My pending requests API failed: ${myPendingResponse.status}`)
      }
      
      const myPendingData = await myPendingResponse.json()
      const myPendingRequests = myPendingData.totalElements || 0
      console.log('✅ My pending requests data:', myPendingData)

      // 사용자별 문서 수 조회
      console.log('🔍 Fetching my documents...')
      const myDocumentsResponse = await fetchWithTimeout(`${apiBaseUrl}/admin/my-documents?size=1`, {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      }) as Response
      
      if (!myDocumentsResponse.ok) {
        throw new Error(`My documents API failed: ${myDocumentsResponse.status}`)
      }
      
      const myDocumentsData = await myDocumentsResponse.json()
      const myDocuments = myDocumentsData.totalElements || 0
      console.log('✅ My documents data:', myDocumentsData)

      // 이번 달 문서 수 (현재는 전체 문서 수로 대체)
      const monthlyDocuments = totalDocuments

      console.log('📊 Setting final stats:', {
        pendingRequests,
        totalDocuments,
        monthlyDocuments,
        myPendingRequests,
        myDocuments
      })

      setStats({
        pendingRequests,
        totalDocuments,
        monthlyDocuments,
        myPendingRequests,
        myDocuments
      })
      
      console.log('✅ Dashboard stats loaded successfully!')
    } catch (error) {
      console.error('❌ 통계 데이터 로드 실패:', error)
    } finally {
      console.log('🏁 Setting statsLoading to false')
      setStatsLoading(false)
    }
  }

  // 서버 사이드 렌더링 중이거나 클라이언트가 마운트되지 않은 경우
  if (!mounted) {
    console.log('⏳ Dashboard is not mounted yet (SSR)')
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-32 w-32 border-b-2 border-blue-600 mx-auto"></div>
          <p className="mt-4 text-gray-600">페이지를 로딩하는 중...</p>
        </div>
      </div>
    )
  }

  // 로딩 중이거나 인증되지 않은 경우
  if (isLoading || !isAuthenticated || !user) {
    console.log('⏳ Dashboard loading state:', { isLoading, isAuthenticated, user: !!user, token: !!token })
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-32 w-32 border-b-2 border-blue-600 mx-auto"></div>
          <p className="mt-4 text-gray-600">
            {isLoading ? '인증 상태를 확인하는 중...' : 
             !isAuthenticated ? '로그인 페이지로 이동 중...' : 
             '사용자 정보를 불러오는 중...'}
          </p>
          <p className="mt-2 text-sm text-gray-500">
            Debug: isLoading={isLoading.toString()}, isAuthenticated={isAuthenticated.toString()}, user={!!user}
          </p>
        </div>
      </div>
    )
  }

  console.log('✅ Dashboard ready to render for user:', user.name)

  // 통계 데이터 로딩 중일 때
  if (statsLoading) {
    console.log('📊 Stats are loading...')
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-32 w-32 border-b-2 border-blue-600 mx-auto"></div>
          <p className="mt-4 text-gray-600">대시보드 데이터를 불러오는 중...</p>
        </div>
      </div>
    )
  }

  const quickActions = [
    {
      title: '단일 문서 등록',
      description: 'DOCX, XLSX, PDF 파일을 업로드하여 등록 요청',
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
      title: 'RAG 채팅',
      description: '등록된 문서들과 대화하며 정보 검색',
      icon: ChatBubbleLeftRightIcon,
      href: '/chat',
      color: 'bg-orange-500',
    },
    {
      title: '내 요청 관리',
      description: '내가 제출한 등록 요청들을 확인하고 관리',
      icon: UserIcon,
      href: '/my-requests',
      color: 'bg-purple-500',
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
        {isAdmin || isApprover ? (
          // 관리자/승인자용 통계
          <>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">대기 중인 요청</CardTitle>
                <ClipboardDocumentCheckIcon className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {statsLoading ? (
                    <div className="animate-pulse bg-gray-200 h-8 w-16 rounded"></div>
                  ) : (
                    stats.pendingRequests.toLocaleString()
                  )}
                </div>
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
                <div className="text-2xl font-bold">
                  {statsLoading ? (
                    <div className="animate-pulse bg-gray-200 h-8 w-16 rounded"></div>
                  ) : (
                    stats.totalDocuments.toLocaleString()
                  )}
                </div>
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
                <div className="text-2xl font-bold">
                  {statsLoading ? (
                    <div className="animate-pulse bg-gray-200 h-8 w-16 rounded"></div>
                  ) : (
                    stats.monthlyDocuments.toLocaleString()
                  )}
                </div>
                <p className="text-xs text-muted-foreground">
                  이번 달 새로 등록된 문서
                </p>
              </CardContent>
            </Card>
          </>
        ) : (
          // 일반 사용자용 통계
          <>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">나의 대기 중인 요청</CardTitle>
                <ClipboardDocumentCheckIcon className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {statsLoading ? (
                    <div className="animate-pulse bg-gray-200 h-8 w-16 rounded"></div>
                  ) : (
                    stats.myPendingRequests.toLocaleString()
                  )}
                </div>
                <p className="text-xs text-muted-foreground">
                  내가 제출한 승인 대기 중인 요청
                </p>
              </CardContent>
            </Card>
            
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">등록된 내 문서</CardTitle>
                <DocumentTextIcon className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {statsLoading ? (
                    <div className="animate-pulse bg-gray-200 h-8 w-16 rounded"></div>
                  ) : (
                    stats.myDocuments.toLocaleString()
                  )}
                </div>
                <p className="text-xs text-muted-foreground">
                  내가 등록한 문서 수
                </p>
              </CardContent>
            </Card>
            
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">전체 등록된 문서</CardTitle>
                <ChartBarIcon className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {statsLoading ? (
                    <div className="animate-pulse bg-gray-200 h-8 w-16 rounded"></div>
                  ) : (
                    stats.totalDocuments.toLocaleString()
                  )}
                </div>
                <p className="text-xs text-muted-foreground">
                  시스템에 등록된 총 문서 수
                </p>
              </CardContent>
            </Card>
          </>
        )}
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

      {/* 관리자 전용 섹션 */}
      {isAdmin && (
        <div className="mb-8">
          <h2 className="text-xl font-semibold text-gray-900 mb-4 flex items-center">
            <CogIcon className="h-6 w-6 mr-2 text-purple-600" />
            관리자 도구
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <Card className="hover:shadow-lg transition-shadow cursor-pointer border-purple-200">
              <Link href="/admin">
                <CardHeader>
                  <div className="flex items-center space-x-3">
                    <div className="p-2 rounded-lg bg-purple-500">
                      <ServerIcon className="h-5 w-5 text-white" />
                    </div>
                    <div>
                      <CardTitle className="text-lg text-purple-700">파일 관리</CardTitle>
                    </div>
                  </div>
                </CardHeader>
                <CardContent>
                  <CardDescription>
                    저장된 파일들의 계층 구조를 확인하고 관리합니다.
                  </CardDescription>
                </CardContent>
              </Link>
            </Card>
            
            <Card className="hover:shadow-lg transition-shadow cursor-pointer border-red-200">
              <Link href="/approval">
                <CardHeader>
                  <div className="flex items-center space-x-3">
                    <div className="p-2 rounded-lg bg-red-500">
                      <ClipboardDocumentCheckIcon className="h-5 w-5 text-white" />
                    </div>
                    <div>
                      <CardTitle className="text-lg text-red-700">승인 관리</CardTitle>
                    </div>
                  </div>
                </CardHeader>
                <CardContent>
                  <CardDescription>
                    등록 요청을 승인하거나 반려 처리합니다.
                  </CardDescription>
                </CardContent>
              </Link>
            </Card>
          </div>
        </div>
      )}

    </div>
  )
}
