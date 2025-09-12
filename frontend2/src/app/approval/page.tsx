'use client'

import { useState, useEffect, useCallback } from 'react'
import { useRouter } from 'next/navigation'
import { 
  CheckCircleIcon, 
  XCircleIcon,
  DocumentTextIcon,
  FolderIcon
} from '@heroicons/react/24/outline'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import AuthService from '@/lib/auth'
import api from '@/lib/api'
import { ApprovalRequest, ApprovalDecision } from '@/types/api'
import { formatDate } from '@/lib/utils'
import { mockApi, NOT_IMPLEMENTED_ERROR } from '@/lib/mock-api'
import { isApiImplemented } from '@/lib/api-status'

type StatusFilter = 'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'COMPLETED'

export default function ApprovalPage() {
  const [requests, setRequests] = useState<ApprovalRequest[]>([])
  const [filteredRequests, setFilteredRequests] = useState<ApprovalRequest[]>([])
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('PENDING')
  const [searchTerm, setSearchTerm] = useState('')
  const [selectedRequest, setSelectedRequest] = useState<ApprovalRequest | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isProcessing, setIsProcessing] = useState(false)
  const [error, setError] = useState('')
  const router = useRouter()

  const fetchRequests = async () => {
    try {
      setIsLoading(true)
      
      if (isApiImplemented('/approval/requests')) {
        const response = await api.get<{content: ApprovalRequest[]}>('/approval/requests')
        setRequests(response.data.content || [])
      } else {
        // Mock API 사용
        console.warn('Using mock API for /approval/requests - backend not implemented yet')
        const mockData = await mockApi.getApprovalRequests()
        setRequests(mockData)
      }
    } catch (error: unknown) {
      console.error('Failed to fetch approval requests:', error)
      
      // 백엔드 미구현 시 Mock 데이터 사용
      try {
        console.warn('Falling back to mock API data')
        const mockData = await mockApi.getApprovalRequests()
        setRequests(mockData)
        setError('백엔드 API가 구현되지 않아 Mock 데이터를 표시합니다.')
      } catch (mockError) {
        setError('승인 요청 목록을 불러오는데 실패했습니다.')
      }
    } finally {
      setIsLoading(false)
    }
  }

  const filterRequests = useCallback(() => {
    let filtered = requests

    if (statusFilter !== 'ALL') {
      filtered = filtered.filter(req => req.status === statusFilter)
    }

    if (searchTerm.trim()) {
      const term = searchTerm.toLowerCase()
      filtered = filtered.filter(req => 
        req.proposedPurpose.toLowerCase().includes(term) ||
        (req.proposedTitle && req.proposedTitle.toLowerCase().includes(term)) ||
        (req.proposedCategory?.majorName && req.proposedCategory.majorName.toLowerCase().includes(term)) ||
        (req.proposedCategory?.midName && req.proposedCategory.midName.toLowerCase().includes(term)) ||
        (req.proposedCategory?.subName && req.proposedCategory.subName.toLowerCase().includes(term))
      )
    }

    setFilteredRequests(filtered)
  }, [requests, statusFilter, searchTerm])

  useEffect(() => {
    if (!AuthService.isApprover()) {
      router.push('/dashboard')
      return
    }
    fetchRequests()
  }, [router])

  useEffect(() => {
    filterRequests()
  }, [filterRequests])

  const handleDecision = async (requestId: string, decision: 'APPROVE' | 'REJECT', reason?: string) => {
    try {
      setIsProcessing(true)
      
      if (isApiImplemented('/approval/requests/{id}/decide')) {
        const decisionData: ApprovalDecision = {
          decision,
          reason
        }
        await api.post(`/approval/requests/${requestId}/decide`, decisionData)
      } else {
        // Mock API 사용
        console.warn('Using mock API for approval decision - backend not implemented yet')
        await mockApi.approveRequest(requestId, decision)
        
        // Mock 데이터 업데이트
        setRequests(prev => prev.map(req => 
          req.id === requestId 
            ? { ...req, status: decision === 'APPROVE' ? 'APPROVED' : 'REJECTED' as any }
            : req
        ))
      }
      
      // 요청 목록 새로고침
      await fetchRequests()
      setSelectedRequest(null)
      
    } catch (error: unknown) {
      console.error('Failed to process decision:', error)
      
      // Mock API로 폴백
      try {
        console.warn('Falling back to mock approval')
        await mockApi.approveRequest(requestId, decision)
        setRequests(prev => prev.map(req => 
          req.id === requestId 
            ? { ...req, status: decision === 'APPROVE' ? 'APPROVED' : 'REJECTED' as any }
            : req
        ))
        setSelectedRequest(null)
        setError('백엔드 API가 구현되지 않아 Mock 처리되었습니다.')
      } catch (mockError) {
        setError('승인 처리에 실패했습니다.')
      }
    } finally {
      setIsProcessing(false)
    }
  }

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'PENDING':
        return 'text-yellow-600 bg-yellow-100'
      case 'APPROVED':
        return 'text-green-600 bg-green-100'
      case 'REJECTED':
        return 'text-red-600 bg-red-100'
      case 'COMPLETED':
        return 'text-blue-600 bg-blue-100'
      default:
        return 'text-gray-600 bg-gray-100'
    }
  }

  const getStatusText = (status: string) => {
    switch (status) {
      case 'PENDING':
        return '대기중'
      case 'APPROVED':
        return '승인됨'
      case 'REJECTED':
        return '반려됨'
      case 'COMPLETED':
        return '등록완료'
      default:
        return status
    }
  }

  const getSourceIcon = (type: string) => {
    return type === 'REPO' ? FolderIcon : DocumentTextIcon
  }

  const formatScore = (score: number) => {
    return (score * 100).toFixed(1) + '%'
  }

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-32 w-32 border-b-2 border-blue-600"></div>
      </div>
    )
  }

  return (
    <div className="max-w-7xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-gray-900">승인 관리</h1>
        <p className="mt-2 text-gray-600">
          등록 요청을 검토하고 승인/반려를 결정하세요.
        </p>
      </div>

      {/* 필터 및 검색 */}
      <Card className="mb-6">
        <CardContent className="p-6">
          <div className="flex flex-col md:flex-row md:items-center md:justify-between space-y-4 md:space-y-0">
            <div className="flex flex-wrap gap-2">
              {(['ALL', 'PENDING', 'APPROVED', 'REJECTED', 'COMPLETED'] as StatusFilter[]).map((status) => (
                <Button
                  key={status}
                  variant={statusFilter === status ? 'default' : 'outline'}
                  size="sm"
                  onClick={() => setStatusFilter(status)}
                >
                  {status === 'ALL' ? '전체' : getStatusText(status)}
                  {status !== 'ALL' && (
                    <span className="ml-1 text-xs">
                      ({requests.filter(req => req.status === status).length})
                    </span>
                  )}
                </Button>
              ))}
            </div>
            <div className="w-full md:w-64">
              <Input
                type="text"
                placeholder="검색..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
              />
            </div>
          </div>
        </CardContent>
      </Card>

      {error && (
        <div className="mb-6 bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-md text-sm">
          {error}
        </div>
      )}

      {/* 요청 목록 */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="space-y-4">
          <h2 className="text-xl font-semibold text-gray-900">
            승인 요청 목록 ({filteredRequests.length})
          </h2>
          
          {filteredRequests.length === 0 ? (
            <Card>
              <CardContent className="p-6 text-center text-gray-500">
                조건에 맞는 승인 요청이 없습니다.
              </CardContent>
            </Card>
          ) : (
            <div className="space-y-3 max-h-screen overflow-y-auto">
              {filteredRequests.map((request) => {
                const SourceIcon = getSourceIcon(request.source.type)
                return (
                  <Card 
                    key={request.id} 
                    className={`cursor-pointer transition-colors ${
                      selectedRequest?.id === request.id 
                        ? 'border-blue-500 bg-blue-50' 
                        : 'hover:bg-gray-50'
                    }`}
                    onClick={() => setSelectedRequest(request)}
                  >
                    <CardContent className="p-4">
                      <div className="flex items-start justify-between mb-2">
                        <div className="flex items-center space-x-2">
                          <SourceIcon className="h-5 w-5 text-gray-500" />
                          <span className={`px-2 py-1 rounded-full text-xs font-medium ${getStatusColor(request.status)}`}>
                            {getStatusText(request.status)}
                          </span>
                        </div>
                        <span className="text-xs text-gray-500">
                          {formatDate(request.requestedAt)}
                        </span>
                      </div>
                      
                      <h3 className="font-medium text-gray-900 mb-1 line-clamp-2">
                        {request.proposedTitle || request.proposedPurpose}
                      </h3>
                      
                      <p className="text-sm text-gray-600 mb-2">
                        {request.proposedCategory ? 
                          `${request.proposedCategory.majorName} > ${request.proposedCategory.midName} > ${request.proposedCategory.subName}` :
                          '카테고리 미분류'
                        }
                      </p>
                      
                      {request.similarCandidates && request.similarCandidates.length > 0 && (
                        <div className="text-xs text-orange-600 bg-orange-50 px-2 py-1 rounded">
                          ⚠️ 유사 문서 {request.similarCandidates.length}개 발견
                        </div>
                      )}
                    </CardContent>
                  </Card>
                )
              })}
            </div>
          )}
        </div>

        {/* 상세 정보 */}
        <div className="sticky top-8">
          {selectedRequest ? (
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <CardTitle className="text-lg">요청 상세 정보</CardTitle>
                  <span className={`px-3 py-1 rounded-full text-sm font-medium ${getStatusColor(selectedRequest.status)}`}>
                    {getStatusText(selectedRequest.status)}
                  </span>
                </div>
                <CardDescription>
                  요청자: {selectedRequest.ownerId} | {formatDate(selectedRequest.requestedAt)}
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                {/* 소스 정보 */}
                <div>
                  <h4 className="font-medium text-gray-900 mb-2">소스 정보</h4>
                  <div className="bg-gray-50 p-3 rounded-md">
                    <p className="text-sm">
                      <span className="font-medium">타입:</span> {selectedRequest.source.type === 'REPO' ? '레포지토리' : '문서'}
                    </p>
                    {selectedRequest.source.repoUrl && (
                      <p className="text-sm">
                        <span className="font-medium">URL:</span> 
                        <a 
                          href={selectedRequest.source.repoUrl} 
                          target="_blank" 
                          rel="noopener noreferrer"
                          className="text-blue-600 hover:underline ml-1"
                        >
                          {selectedRequest.source.repoUrl}
                        </a>
                      </p>
                    )}
                    {selectedRequest.source.files && selectedRequest.source.files.length > 0 && (
                      <p className="text-sm">
                        <span className="font-medium">파일:</span> {selectedRequest.source.files.join(', ')}
                      </p>
                    )}
                  </div>
                </div>

                {/* 제안된 카테고리 */}
                <div>
                  <h4 className="font-medium text-gray-900 mb-2">제안된 카테고리</h4>
                  <div className="bg-gray-50 p-3 rounded-md">
                    {selectedRequest.proposedCategory ? (
                      <>
                        <p className="text-sm">
                          <span className="font-medium">대분류:</span> {selectedRequest.proposedCategory.majorName} ({selectedRequest.proposedCategory.majorCode})
                        </p>
                        <p className="text-sm">
                          <span className="font-medium">중분류:</span> {selectedRequest.proposedCategory.midName} ({selectedRequest.proposedCategory.midCode})
                        </p>
                        <p className="text-sm">
                          <span className="font-medium">소분류:</span> {selectedRequest.proposedCategory.subName} ({selectedRequest.proposedCategory.subCode})
                        </p>
                      </>
                    ) : (
                      <p className="text-sm text-gray-500">카테고리가 제안되지 않았습니다.</p>
                    )}
                  </div>
                </div>

                {/* 제목 */}
                {selectedRequest.proposedTitle && (
                  <div>
                    <h4 className="font-medium text-gray-900 mb-2">AI 생성 제목</h4>
                    <div className="bg-blue-50 p-3 rounded-md">
                      <p className="text-sm text-blue-700 font-medium">
                        {selectedRequest.proposedTitle}
                      </p>
                    </div>
                  </div>
                )}

                {/* 목적 */}
                <div>
                  <h4 className="font-medium text-gray-900 mb-2">목적</h4>
                  <div className="bg-gray-50 p-3 rounded-md">
                    <p className="text-sm text-gray-700">
                      {selectedRequest.proposedPurpose}
                    </p>
                  </div>
                </div>

                {/* 추출된 텍스트 */}
                <div>
                  <h4 className="font-medium text-gray-900 mb-2">추출된 내용</h4>
                  <div className="bg-gray-50 p-3 rounded-md max-h-40 overflow-y-auto">
                    <p className="text-sm text-gray-700 whitespace-pre-wrap">
                      {selectedRequest.extractedText}
                    </p>
                  </div>
                </div>

                {/* 유사한 문서들 */}
                {selectedRequest.similarCandidates && selectedRequest.similarCandidates.length > 0 && (
                  <div>
                    <h4 className="font-medium text-gray-900 mb-2 text-orange-600">
                      ⚠️ 유사한 문서들
                    </h4>
                    <div className="space-y-2 max-h-40 overflow-y-auto">
                      {selectedRequest.similarCandidates.map((candidate, index) => (
                        <div key={index} className="border border-orange-200 bg-orange-50 p-2 rounded-md">
                          <div className="flex justify-between items-start mb-1">
                            <span className="text-sm font-medium">
                              {candidate.serial} - {candidate.purpose}
                            </span>
                            <span className="text-xs text-orange-600">
                              {formatScore(candidate.score)}
                            </span>
                          </div>
                          <p className="text-xs text-gray-600">
                            {candidate.snippet}
                          </p>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* 승인/반려 버튼 */}
                {selectedRequest.status === 'PENDING' && (
                  <div className="flex space-x-3 pt-4 border-t">
                    <Button
                      onClick={() => handleDecision(selectedRequest.id, 'REJECT')}
                      variant="destructive"
                      disabled={isProcessing}
                      className="flex-1"
                    >
                      <XCircleIcon className="h-4 w-4 mr-2" />
                      반려
                    </Button>
                    <Button
                      onClick={() => handleDecision(selectedRequest.id, 'APPROVE')}
                      disabled={isProcessing}
                      className="flex-1"
                    >
                      <CheckCircleIcon className="h-4 w-4 mr-2" />
                      승인
                    </Button>
                  </div>
                )}

                {selectedRequest.approvedAt && selectedRequest.approvedBy && (
                  <div className="pt-4 border-t text-sm text-gray-600">
                    <p>
                      승인자: {selectedRequest.approvedBy} | {formatDate(selectedRequest.approvedAt)}
                    </p>
                  </div>
                )}
              </CardContent>
            </Card>
          ) : (
            <Card>
              <CardContent className="p-6 text-center text-gray-500">
                승인 요청을 선택하여 상세 정보를 확인하세요.
              </CardContent>
            </Card>
          )}
        </div>
      </div>
    </div>
  )
}
