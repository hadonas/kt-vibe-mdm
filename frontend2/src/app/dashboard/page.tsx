'use client'

import { useEffect, useState, useMemo, useRef } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { 
  DocumentTextIcon, FolderIcon, ChatBubbleLeftRightIcon, ClipboardDocumentCheckIcon,
  UserGroupIcon, ChartBarIcon, CogIcon, ServerIcon, UserIcon
} from '@heroicons/react/24/outline'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card'
import { useAuth } from '@/contexts/AuthContexts'

interface DashboardStats {
  pendingRequests: number
  totalDocuments: number
  monthlyDocuments: number
  myPendingRequests: number
  myDocuments: number
}

const DashboardPage = () => {
  // 클라이언트 사이드에서만 실행되도록 보장
  const { hasInitialized, isAuthenticated, user, token, isLoading, logout, isAdmin, isApprover } = useAuth()
  const router = useRouter()
  const [stats, setStats] = useState<DashboardStats>({
    pendingRequests: 0, totalDocuments: 0, monthlyDocuments: 0, myPendingRequests: 0, myDocuments: 0
  })
  const [statsLoading, setStatsLoading] = useState(true)
  const lastFetchKeyRef = useRef<string | null>(null)

  useEffect(() => {
    if (!hasInitialized) return; // 복원 전엔 아무 것도 하지 않음
    if (!isAuthenticated) {
      router.replace('/login');
    }
  }, [hasInitialized, isAuthenticated, router]);

  // 2) 대시보드 데이터 로드
  useEffect(() => {
    if (!hasInitialized || !isAuthenticated || !token) return

    const fetchKey = `${token}|${Number(isAdmin)}|${Number(isApprover)}`

    // 같은 키로 이미 실행했다면 재실행하지 않음 (StrictMode 이중 실행, 미세한 리렌더 차단)
    if (lastFetchKeyRef.current === fetchKey) {
      return
    }
    lastFetchKeyRef.current = fetchKey
  
    const controller = new AbortController()
    const timeout = setTimeout(() => controller.abort(), 10000)

    const fetchWithTimeout = (url: string, options: RequestInit = {}) =>
      fetch(url, { ...options, signal: controller.signal })

    const load = async () => {
      try {
        setStatsLoading(true)
        const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api'

        // 승인대기 개수
        let pendingRequests = 0
        if (isAdmin || isApprover) {
          const r = await fetchWithTimeout(`${apiBaseUrl}/approval/requests?status=PENDING&size=1`, {
            headers: { Authorization: `Bearer ${token}` },
          })
          const j = r.ok ? await r.json() : { totalElements: 0 }
          pendingRequests = j.totalElements ?? 0
        } else {
          const r = await fetchWithTimeout(`${apiBaseUrl}/approval/my-requests?status=PENDING&size=1`, {
            headers: { Authorization: `Bearer ${token}` },
          })
          const j = r.ok ? await r.json() : { totalElements: 0 }
          pendingRequests = j.totalElements ?? 0
        }

        // 문서수
        let totalDocuments = 0
        if (isAdmin || isApprover) {
          const r = await fetchWithTimeout(`${apiBaseUrl}/admin/files/hierarchy`, {
            headers: { Authorization: `Bearer ${token}` },
          })
          const j = r.ok ? await r.json() : { totalFiles: 0 }
          totalDocuments = j.totalFiles ?? 0
        } else {
          const r = await fetchWithTimeout(`${apiBaseUrl}/public/documents/count`, {
            headers: { Authorization: `Bearer ${token}` },
          })
          const j = r.ok ? await r.json() : { totalDocuments: 0 }
          totalDocuments = j.totalDocuments ?? 0
        }

        // 내 대기 / 내 문서
        const myPendingRes = await fetchWithTimeout(`${apiBaseUrl}/approval/my-requests?status=PENDING&size=1`, {
          headers: { Authorization: `Bearer ${token}` },
        })
        const myPending = myPendingRes.ok ? (await myPendingRes.json()).totalElements ?? 0 : 0

        const myDocsRes = await fetchWithTimeout(`${apiBaseUrl}/public/my-documents?size=1`, {
          headers: { Authorization: `Bearer ${token}` },
        })
        const myDocs = myDocsRes.ok ? (await myDocsRes.json()).totalElements ?? 0 : 0

        setStats({
          pendingRequests,
          totalDocuments,
          monthlyDocuments: totalDocuments, // TODO: 서버에서 month filter 제공되면 교체
          myPendingRequests: myPending,
          myDocuments: myDocs,
        })
      } catch (e) {
        // 실패해도 안전하게 화면은 유지
        console.error('❌ 통계 데이터 로드 실패:', e)
      } finally {
        setStatsLoading(false)
      }
    }

    load()
    return () => {
      clearTimeout(timeout)
    }
  }, [hasInitialized, isAuthenticated, token, isAdmin, isApprover])

  // 이후 렌더 (사용자 존재 가정)
  const quickActions = useMemo(() => {
    const base = [
      { title: '단일 문서 등록', description: 'DOCX, XLSX, PDF 파일을 업로드하여 등록 요청', icon: DocumentTextIcon, href: '/ingest/document', color: 'bg-blue-500' },
      { title: '레포지토리 등록', description: 'GitHub 레포지토리를 분석하여 등록 요청', icon: FolderIcon, href: '/ingest/repository', color: 'bg-green-500' },
      { title: 'RAG 채팅', description: '등록된 문서들과 대화하며 정보 검색', icon: ChatBubbleLeftRightIcon, href: '/chat', color: 'bg-orange-500' },
      { title: '내 요청 관리', description: '내가 제출한 등록 요청들을 확인하고 관리', icon: UserIcon, href: '/my-requests', color: 'bg-purple-500' },
    ]
    if (isApprover) {
      base.push({ title: '승인 관리', description: '등록 요청 승인/반려 처리', icon: UserGroupIcon, href: '/approval', color: 'bg-red-500' })
    }
    return base
  }, [isApprover])

  // 3) 로딩/리다이렉트 UX
  if (!hasInitialized || isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-32 w-32 border-b-2 border-blue-600 mx-auto"></div>
          <p className="mt-4 text-gray-600">인증 상태를 확인하는 중...</p>
        </div>
      </div>
    )
  }
  if (!isAuthenticated) {
    // replace가 실행될 때까지의 안전한 빈 화면
    return null
  }

  return (
    <div className="max-w-7xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-gray-900">
          안녕하세요, {user?.name}님!
        </h1>
        <p className="mt-2 text-gray-600">
          문서 관리 시스템에 오신 것을 환영합니다. 
          {isAdmin && ' (관리자 권한)'}
          {isApprover && !isAdmin && ' (승인자 권한)'}
        </p>
      </div>

      {/* 통계 카드 */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
        {(isAdmin || isApprover) ? (
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

export default DashboardPage