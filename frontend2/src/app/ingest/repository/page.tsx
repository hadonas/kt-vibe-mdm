'use client'

import { redirect } from 'next/navigation'
import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import api from '@/lib/api'
import { SingleIngestRequest, IngestPreviewResponse } from '@/types/api'

export default function RepositoryIngestPage() {
  const [repoUrl, setRepoUrl] = useState('')
  const [accessToken, setAccessToken] = useState('')
  const [purpose, setPurpose] = useState('')
  const [tags, setTags] = useState('')
  const [isAnalyzing, setIsAnalyzing] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [preview, setPreview] = useState<IngestPreviewResponse | null>(null)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const router = useRouter()

  const handleAnalyze = async (e: React.FormEvent) => {
    e.preventDefault()
    
    if (!repoUrl.trim()) {
      setError('레포지토리 URL을 입력해주세요.')
      return
    }

    setIsAnalyzing(true)
    setError('')
    setPreview(null)

    try {
      const request: SingleIngestRequest = {
        repoUrl: repoUrl.trim(),
        accessToken: accessToken.trim() || undefined
      }

      const response = await api.post<IngestPreviewResponse>('/ingest/single', request)
      setPreview(response.data)
      
      // 제안된 목적이 있으면 자동으로 채움
      if (response.data.proposedPurpose && !purpose) {
        setPurpose(response.data.proposedPurpose)
      }

    } catch (error: unknown) {
      console.error('Repository analysis error:', error)
      const errorMessage = error instanceof Error && 'response' in error 
        ? (error as any).response?.data?.error 
        : '레포지토리 분석에 실패했습니다.'
      setError(errorMessage)
    } finally {
      setIsAnalyzing(false)
    }
  }

  const handleSubmit = async () => {
    if (!preview) return

    setIsSubmitting(true)
    setError('')

    try {
      const request = {
        repoUrl: repoUrl.trim(),
        accessToken: accessToken.trim() || undefined,
        purpose: purpose.trim() || undefined,
        tags: tags.trim() ? tags.split(',').map(tag => tag.trim()) : undefined,
        submit: true // 실제 등록 요청임을 명시
      }

      await api.post('/ingest/single', request)
      
      setSuccess('레포지토리 등록 요청이 성공적으로 제출되었습니다. 관리자의 승인을 기다려주세요.')
      
      setTimeout(() => {
        router.replace('/dashboard')
      }, 3000)

    } catch (error: unknown) {
      console.error('Repository ingest error:', error)
      const errorMessage = error instanceof Error && 'response' in error 
        ? (error as any).response?.data?.error 
        : '레포지토리 등록 요청에 실패했습니다.'
      setError(errorMessage)
    } finally {
      setIsSubmitting(false)
    }
  }

  const formatScore = (score: number) => {
    return (score * 100).toFixed(1) + '%'
  }

  return (
    <div className="max-w-4xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-gray-900">레포지토리 등록</h1>
        <p className="mt-2 text-gray-600">
          GitHub 레포지토리를 분석하여 문서 관리 시스템에 등록 요청을 제출하세요.
        </p>
      </div>

      {/* 레포지토리 정보 입력 */}
      <Card className="mb-6">
        <CardHeader>
          <CardTitle>레포지토리 정보</CardTitle>
          <CardDescription>
            분석할 GitHub 레포지토리의 URL을 입력하세요.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleAnalyze} className="space-y-4">
            <div>
              <label htmlFor="repoUrl" className="block text-sm font-medium text-gray-700 mb-1">
                레포지토리 URL *
              </label>
              <Input
                id="repoUrl"
                type="url"
                value={repoUrl}
                onChange={(e) => setRepoUrl(e.target.value)}
                placeholder="https://github.com/username/repository"
                className="w-full"
                required
              />
            </div>

            <div>
              <label htmlFor="accessToken" className="block text-sm font-medium text-gray-700 mb-1">
                GitHub Access Token (선택사항)
              </label>
              <Input
                id="accessToken"
                type="password"
                value={accessToken}
                onChange={(e) => setAccessToken(e.target.value)}
                placeholder="Private 레포지토리인 경우 필요"
                className="w-full"
              />
              <p className="text-xs text-gray-500 mt-1">
                Private 레포지토리에 접근하려면 GitHub Personal Access Token이 필요합니다.
              </p>
            </div>

            <div className="flex justify-end">
              <Button
                type="submit"
                disabled={isAnalyzing || !repoUrl.trim()}
              >
                {isAnalyzing ? '분석 중...' : '레포지토리 분석'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {/* 분석 결과 */}
      {preview && (
        <>
          <Card className="mb-6">
            <CardHeader>
              <CardTitle>분석 결과</CardTitle>
              <CardDescription>
                레포지토리 분석이 완료되었습니다. 아래 내용을 확인하고 등록을 진행하세요.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              {/* 제안된 카테고리 */}
              <div>
                <h4 className="font-medium text-gray-900 mb-2">제안된 카테고리</h4>
                <div className="bg-gray-50 p-3 rounded-md">
                  <p className="text-sm">
                    <span className="font-medium">대분류:</span> {preview.proposedCategory.majorName} ({preview.proposedCategory.majorCode})
                  </p>
                  <p className="text-sm">
                    <span className="font-medium">중분류:</span> {preview.proposedCategory.midName} ({preview.proposedCategory.midCode})
                  </p>
                  <p className="text-sm">
                    <span className="font-medium">소분류:</span> {preview.proposedCategory.subName} ({preview.proposedCategory.subCode})
                  </p>
                </div>
              </div>

              {/* 추출된 텍스트 */}
              <div>
                <h4 className="font-medium text-gray-900 mb-2">레포지토리 개요</h4>
                <div className="bg-gray-50 p-3 rounded-md max-h-40 overflow-y-auto">
                  <p className="text-sm text-gray-700 whitespace-pre-wrap">
                    {preview.extractedText}
                  </p>
                </div>
              </div>
            </CardContent>
          </Card>

          {/* 유사한 문서들 */}
          {preview.similarCandidates && preview.similarCandidates.length > 0 && (
            <Card className="mb-6">
              <CardHeader>
                <CardTitle className="text-lg text-orange-600">⚠️ 유사한 문서 발견</CardTitle>
                <CardDescription>
                  시스템에서 유사한 문서들을 발견했습니다. 중복 등록이 아닌지 확인해주세요.
                </CardDescription>
              </CardHeader>
              <CardContent>
                <div className="space-y-3">
                  {preview.similarCandidates.map((candidate, index) => (
                    <div key={index} className="border border-orange-200 bg-orange-50 p-3 rounded-md">
                      <div className="flex justify-between items-start mb-2">
                        <h5 className="font-medium text-gray-900">
                          {candidate.serial} - {candidate.purpose}
                        </h5>
                        <span className="text-sm text-orange-600 font-medium">
                          유사도: {formatScore(candidate.score)}
                        </span>
                      </div>
                      <p className="text-sm text-gray-600">
                        {candidate.snippet}
                      </p>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          )}

          {/* 추가 정보 입력 */}
          <Card className="mb-6">
            <CardHeader>
              <CardTitle>추가 정보</CardTitle>
              <CardDescription>
                필요에 따라 목적과 태그를 수정하거나 추가할 수 있습니다.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div>
                <label htmlFor="purpose" className="block text-sm font-medium text-gray-700 mb-1">
                  프로젝트 목적
                </label>
                <Input
                  id="purpose"
                  type="text"
                  value={purpose}
                  onChange={(e) => setPurpose(e.target.value)}
                  placeholder="이 프로젝트의 목적이나 용도를 설명해주세요"
                  className="w-full"
                />
              </div>

              <div>
                <label htmlFor="tags" className="block text-sm font-medium text-gray-700 mb-1">
                  태그 (선택사항)
                </label>
                <Input
                  id="tags"
                  type="text"
                  value={tags}
                  onChange={(e) => setTags(e.target.value)}
                  placeholder="태그1, 태그2, 태그3"
                  className="w-full"
                />
                <p className="text-xs text-gray-500 mt-1">
                  쉼표로 구분하여 여러 태그를 입력할 수 있습니다.
                </p>
              </div>
            </CardContent>
          </Card>
        </>
      )}

      {/* 에러 메시지 */}
      {error && (
        <div className="mb-6 bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-md text-sm">
          {error}
        </div>
      )}

      {/* 성공 메시지 */}
      {success && (
        <div className="mb-6 bg-green-50 border border-green-200 text-green-600 px-4 py-3 rounded-md text-sm">
          {success}
        </div>
      )}

      {/* 액션 버튼 */}
      {preview && !success && (
        <div className="flex justify-end space-x-4">
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              setPreview(null)
              setError('')
            }}
            disabled={isSubmitting}
          >
            다시 분석
          </Button>
          <Button
            type="button"
            onClick={handleSubmit}
            disabled={isSubmitting}
          >
            {isSubmitting ? '등록 중...' : '등록 요청 제출'}
          </Button>
        </div>
      )}

      {/* 안내 사항 */}
      <Card className="mt-6">
        <CardHeader>
          <CardTitle className="text-lg">레포지토리 등록 안내</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-3 text-sm text-gray-600">
            <div className="flex items-start space-x-2">
              <div className="w-6 h-6 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center text-xs font-semibold">1</div>
              <div>
                <p className="font-medium">레포지토리 분석</p>
                <p>README, 코드 구조, 설정 파일 등을 종합적으로 분석합니다.</p>
              </div>
            </div>
            <div className="flex items-start space-x-2">
              <div className="w-6 h-6 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center text-xs font-semibold">2</div>
              <div>
                <p className="font-medium">중복 검사</p>
                <p>기존 등록된 문서와의 유사도를 확인하여 중복을 방지합니다.</p>
              </div>
            </div>
            <div className="flex items-start space-x-2">
              <div className="w-6 h-6 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center text-xs font-semibold">3</div>
              <div>
                <p className="font-medium">관리자 승인</p>
                <p>관리자가 분석 결과를 검토하고 승인/반려를 결정합니다.</p>
              </div>
            </div>
            <div className="flex items-start space-x-2">
              <div className="w-6 h-6 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center text-xs font-semibold">4</div>
              <div>
                <p className="font-medium">시스템 등록</p>
                <p>승인된 레포지토리는 로컬 폴더에 클론되고 벡터 DB에 저장됩니다.</p>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
