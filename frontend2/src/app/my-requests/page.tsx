'use client'

import { redirect } from 'next/navigation'
import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { 
  ClipboardDocumentCheckIcon,
  ClockIcon,
  CheckCircleIcon,
  XCircleIcon,
  ExclamationTriangleIcon
} from '@heroicons/react/24/outline'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card'
import { useAuth } from '@/hooks/useAuth'

interface IngestRequest {
  id: string
  proposedTitle: string
  proposedPurpose: string
  originalFileName: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'COMPLETED' | 'FAILED'
  requestedAt: string
  approvedAt?: string
  approvalReason?: string
  proposedCategory?: {
    fullName: string
    fullCode: string
  }
}

export default function MyRequestsPage() {
  const { isAuthenticated, user, isLoading, token } = useAuth()
  const router = useRouter()
  const [requests, setRequests] = useState<IngestRequest[]>([])
  const [loading, setLoading] = useState(true)
  const [currentPage, setCurrentPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.replace('/login')
    }
  }, [isAuthenticated, isLoading, router])

  useEffect(() => {
    if (isAuthenticated && token) {
      loadMyRequests()
    }
  }, [isAuthenticated, token, currentPage])

  const loadMyRequests = async () => {
    try {
      setLoading(true)
      const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api'
      
      const response = await fetch(`${apiBaseUrl}/approval/my-requests?page=${currentPage}&size=10`, {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      })
      
      if (!response.ok) {
        throw new Error(`API failed: ${response.status}`)
      }
      
      const data = await response.json()
      setRequests(data.content || [])
      setTotalPages(data.totalPages || 0)
      setTotalElements(data.totalElements || 0)
    } catch (error) {
      console.error('내 요청 목록 로드 실패:', error)
    } finally {
      setLoading(false)
    }
  }

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'PENDING':
        return <ClockIcon className="h-5 w-5 text-yellow-500" />
      case 'APPROVED':
        return <CheckCircleIcon className="h-5 w-5 text-green-500" />
      case 'REJECTED':
        return <XCircleIcon className="h-5 w-5 text-red-500" />
      case 'COMPLETED':
        return <CheckCircleIcon className="h-5 w-5 text-blue-500" />
      case 'FAILED':
        return <ExclamationTriangleIcon className="h-5 w-5 text-orange-500" />
      default:
        return <ClockIcon className="h-5 w-5 text-gray-500" />
    }
  }

  const getStatusText = (status: string) => {
    switch (status) {
      case 'PENDING':
        return '대기 중'
      case 'APPROVED':
        return '승인됨'
      case 'REJECTED':
        return '반려됨'
      case 'COMPLETED':
        return '완료됨'
      case 'FAILED':
        return '실패'
      default:
        return status
    }
  }

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'PENDING':
        return 'bg-yellow-100 text-yellow-800'
      case 'APPROVED':
        return 'bg-green-100 text-green-800'
      case 'REJECTED':
        return 'bg-red-100 text-red-800'
      case 'COMPLETED':
        return 'bg-blue-100 text-blue-800'
      case 'FAILED':
        return 'bg-orange-100 text-orange-800'
      default:
        return 'bg-gray-100 text-gray-800'
    }
  }

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('ko-KR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })
  }

  if (isLoading) {
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
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-32 w-32 border-b-2 border-blue-600 mx-auto"></div>
          <p className="mt-4 text-gray-600">로그인 페이지로 이동 중...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="max-w-7xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-gray-900">내 요청 관리</h1>
        <p className="mt-2 text-gray-600">
          내가 제출한 문서 등록 요청들을 확인하고 관리할 수 있습니다.
        </p>
      </div>

      {loading ? (
        <div className="space-y-4">
          {[...Array(3)].map((_, i) => (
            <Card key={i} className="animate-pulse">
              <CardContent className="p-6">
                <div className="h-4 bg-gray-200 rounded w-1/4 mb-2"></div>
                <div className="h-3 bg-gray-200 rounded w-1/2 mb-4"></div>
                <div className="h-3 bg-gray-200 rounded w-3/4"></div>
              </CardContent>
            </Card>
          ))}
        </div>
      ) : requests.length === 0 ? (
        <Card>
          <CardContent className="p-12 text-center">
            <ClipboardDocumentCheckIcon className="h-12 w-12 text-gray-400 mx-auto mb-4" />
            <h3 className="text-lg font-medium text-gray-900 mb-2">요청이 없습니다</h3>
            <p className="text-gray-500">아직 제출한 문서 등록 요청이 없습니다.</p>
          </CardContent>
        </Card>
      ) : (
        <>
          <div className="mb-4">
            <p className="text-sm text-gray-600">
              총 {totalElements}개의 요청 중 {currentPage * 10 + 1}-{Math.min((currentPage + 1) * 10, totalElements)}개 표시
            </p>
          </div>

          <div className="space-y-4">
            {requests.map((request) => (
              <Card key={request.id} className="hover:shadow-lg transition-shadow">
                <CardHeader>
                  <div className="flex items-start justify-between">
                    <div className="flex-1">
                      <CardTitle className="text-lg mb-2">{request.proposedTitle}</CardTitle>
                      <div className="flex items-center space-x-4 text-sm text-gray-600">
                        <span>파일: {request.originalFileName}</span>
                        <span>카테고리: {request.proposedCategory?.fullName || '미분류'}</span>
                        <span>요청일: {formatDate(request.requestedAt)}</span>
                      </div>
                    </div>
                    <div className="flex items-center space-x-2">
                      {getStatusIcon(request.status)}
                      <span className={`px-2 py-1 rounded-full text-xs font-medium ${getStatusColor(request.status)}`}>
                        {getStatusText(request.status)}
                      </span>
                    </div>
                  </div>
                </CardHeader>
                <CardContent>
                  <CardDescription className="text-sm mb-4">
                    {request.proposedPurpose}
                  </CardDescription>
                  
                  {request.approvedAt && (
                    <div className="text-xs text-gray-500">
                      처리일: {formatDate(request.approvedAt)}
                    </div>
                  )}
                  
                  {request.approvalReason && (
                    <div className="mt-2 p-3 bg-gray-50 rounded-md">
                      <p className="text-sm font-medium text-gray-700 mb-1">처리 사유:</p>
                      <p className="text-sm text-gray-600">{request.approvalReason}</p>
                    </div>
                  )}
                </CardContent>
              </Card>
            ))}
          </div>

          {/* 페이지네이션 */}
          {totalPages > 1 && (
            <div className="mt-8 flex justify-center">
              <nav className="flex space-x-2">
                <button
                  onClick={() => setCurrentPage(0)}
                  disabled={currentPage === 0}
                  className="px-3 py-2 text-sm font-medium text-gray-500 bg-white border border-gray-300 rounded-l-md hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  처음
                </button>
                <button
                  onClick={() => setCurrentPage(currentPage - 1)}
                  disabled={currentPage === 0}
                  className="px-3 py-2 text-sm font-medium text-gray-500 bg-white border border-gray-300 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  이전
                </button>
                <span className="px-3 py-2 text-sm font-medium text-gray-700 bg-gray-100 border border-gray-300">
                  {currentPage + 1} / {totalPages}
                </span>
                <button
                  onClick={() => setCurrentPage(currentPage + 1)}
                  disabled={currentPage >= totalPages - 1}
                  className="px-3 py-2 text-sm font-medium text-gray-500 bg-white border border-gray-300 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  다음
                </button>
                <button
                  onClick={() => setCurrentPage(totalPages - 1)}
                  disabled={currentPage >= totalPages - 1}
                  className="px-3 py-2 text-sm font-medium text-gray-500 bg-white border border-gray-300 rounded-r-md hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  마지막
                </button>
              </nav>
            </div>
          )}
        </>
      )}
    </div>
  )
}
