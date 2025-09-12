'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import FileUpload from '@/components/ui/FileUpload'
import api from '@/lib/api'
import { PresignRequest, SingleIngestRequest, Category } from '@/types/api'
import { mockApi } from '@/lib/mock-api'
import { isApiImplemented } from '@/lib/api-status'

export default function DocumentIngestPage() {
  const [files, setFiles] = useState<File[]>([])
  const [purpose, setPurpose] = useState('')
  const [tags, setTags] = useState('')
  const [isUploading, setIsUploading] = useState(false)
  const [isAnalyzing, setIsAnalyzing] = useState(false)
  const [uploadProgress, setUploadProgress] = useState<{ [key: string]: number }>({})
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [analysisResult, setAnalysisResult] = useState<{
    proposedCategory?: Category
    proposedPurpose?: string
    proposedTitle?: string
  } | null>(null)
  const [uploadedFileId, setUploadedFileId] = useState<string | null>(null)
  const router = useRouter()

  const analyzeFile = async (file: File) => {
    try {
      setIsAnalyzing(true)
      setError('')

      // 1. 파일 업로드
      const uploadFormData = new FormData()
      uploadFormData.append('file', file)

      const uploadResponse = await api.post('/files/upload', uploadFormData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      })

      const uploadResult = uploadResponse.data
      const fileId = uploadResult.fileId

      // 2. 파일 분석
      const analyzeFormData = new FormData()
      analyzeFormData.append('file', file)

      const analyzeResponse = await api.post('/files/analyze', analyzeFormData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      })

      const result = analyzeResponse.data
      setAnalysisResult({
        proposedCategory: result.proposedCategory,
        proposedPurpose: result.proposedPurpose,
        proposedTitle: result.proposedTitle
      })
      
      // 분석 결과를 폼에 자동으로 채우기
      if (result.proposedPurpose) {
        setPurpose(result.proposedPurpose)
      }
      
      // 제목은 원본 파일명을 사용하도록 설정 (확장자 제거)
      if (result.proposedTitle) {
        const titleWithoutExtension = result.proposedTitle.replace(/\.[^/.]+$/, "")
        setAnalysisResult(prev => ({
          ...prev,
          proposedTitle: titleWithoutExtension
        }))
      }

      // 업로드된 파일 ID 저장
      setUploadedFileId(fileId)
      
    } catch (error: unknown) {
      console.error('파일 분석 실패:', error)
      const errorMessage = error instanceof Error && 'response' in error 
        ? (error as any).response?.data?.error 
        : '파일 분석에 실패했습니다.'
      setError(errorMessage)
    } finally {
      setIsAnalyzing(false)
    }
  }

  const uploadFile = async (file: File): Promise<string> => {
    try {
      if (isApiImplemented('/files/presign')) {
        // 1. Presigned URL 요청
        const presignRequest: PresignRequest = {
          fileName: file.name,
          contentType: file.type,
          size: file.size
        }

        const presignResponse = await api.post('/files/presign', presignRequest)
        const { fileId, uploadUrl } = presignResponse.data

        // 2. 파일 업로드
        const uploadResponse = await fetch(uploadUrl, {
          method: 'PUT',
          body: file,
          headers: {
            'Content-Type': file.type,
          },
        })

        if (!uploadResponse.ok) {
          throw new Error('파일 업로드에 실패했습니다.')
        }

        setUploadProgress(prev => ({ ...prev, [file.name]: 100 }))
        return fileId
      } else {
        // Mock API 사용
        console.warn('Using mock API for file upload - backend not implemented yet')
        
        const mockPresignResponse = await mockApi.getPresignedUrl(file.name)
        const { fileId, uploadUrl } = mockPresignResponse

        // Mock 업로드 시뮬레이션
        await mockApi.uploadFile(uploadUrl, file)

        setUploadProgress(prev => ({ ...prev, [file.name]: 100 }))
        return fileId
      }

    } catch (error) {
      console.error('File upload error:', error)
      
      // Mock API로 폴백
      try {
        console.warn('Falling back to mock file upload')
        const mockPresignResponse = await mockApi.getPresignedUrl(file.name)
        await mockApi.uploadFile(mockPresignResponse.uploadUrl, file)
        setUploadProgress(prev => ({ ...prev, [file.name]: 100 }))
        return mockPresignResponse.fileId
      } catch (mockError) {
        throw new Error(`${file.name} 업로드 실패`)
      }
    }
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (files.length === 0) {
      setError('업로드할 파일을 선택해주세요.')
      return
    }

    setIsUploading(true)
    setError('')
    setSuccess('')
    setUploadProgress({})

    try {
      // 파일들 업로드
      const fileIds: string[] = []
      for (const file of files) {
        setUploadProgress(prev => ({ ...prev, [file.name]: 0 }))
        const fileId = await uploadFile(file)
        fileIds.push(fileId)
      }

      // 등록 요청 생성
      const ingestRequest: SingleIngestRequest = {
        fileIds: uploadedFileId ? [uploadedFileId] : fileIds,
        purpose: purpose.trim() || undefined,
        tags: tags.trim() ? tags.split(',').map(tag => tag.trim()) : undefined,
        proposedCategory: analysisResult?.proposedCategory,
        proposedTitle: analysisResult?.proposedTitle,
        originalFileName: files.length > 0 ? files[0].name : undefined
      }

      await api.post('/ingest/single', ingestRequest)
      
      setSuccess('문서 등록 요청이 성공적으로 제출되었습니다. 관리자의 승인을 기다려주세요.')
      
      // 3초 후 대시보드로 이동
      setTimeout(() => {
        router.push('/dashboard')
      }, 3000)

    } catch (error: unknown) {
      console.error('Document ingest error:', error)
      const errorMessage = error instanceof Error && 'response' in error 
        ? (error as any).response?.data?.error 
        : '문서 등록 요청에 실패했습니다.'
      setError(errorMessage)
    } finally {
      setIsUploading(false)
    }
  }

  const handleFilesChange = async (newFiles: File[]) => {
    setFiles(newFiles)
    setError('')
    setAnalysisResult(null)
    
    // 첫 번째 파일이 있으면 자동으로 분석
    if (newFiles.length > 0) {
      await analyzeFile(newFiles[0])
    }
  }

  return (
    <div className="max-w-4xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-gray-900">단일 문서 등록</h1>
        <p className="mt-2 text-gray-600">
          DOCX, XLSX 파일을 업로드하여 문서 관리 시스템에 등록 요청을 제출하세요.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>문서 업로드</CardTitle>
          <CardDescription>
            업로드된 파일은 관리자의 승인 후 시스템에 등록됩니다.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-6">
            {/* 파일 업로드 */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                문서 파일 *
              </label>
              <FileUpload
                onFilesChange={handleFilesChange}
                accept=".docx,.xlsx,.pdf"
                multiple={true}
                maxSize={50 * 1024 * 1024} // 50MB
              />
            </div>

            {/* 파일 분석 결과 */}
            {isAnalyzing && (
              <div className="bg-blue-50 border border-blue-200 text-blue-600 px-4 py-3 rounded-md text-sm">
                <div className="flex items-center">
                  <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-blue-600 mr-2"></div>
                  AI가 파일을 분석하고 있습니다...
                </div>
              </div>
            )}

            {analysisResult && (
              <div className="bg-green-50 border border-green-200 rounded-md p-4">
                <h4 className="font-medium text-green-800 mb-3">AI 분석 결과</h4>
                <div className="space-y-2 text-sm">
                  {analysisResult.proposedTitle && (
                    <div>
                      <span className="font-medium text-green-700">제안된 제목:</span>
                      <span className="ml-2 text-green-600">{analysisResult.proposedTitle}</span>
                    </div>
                  )}
                  {analysisResult.proposedCategory && (
                    <div>
                      <span className="font-medium text-green-700">제안된 카테고리:</span>
                      <span className="ml-2 text-green-600">
                        {analysisResult.proposedCategory.majorName} &gt; {analysisResult.proposedCategory.midName} &gt; {analysisResult.proposedCategory.subName}
                      </span>
                    </div>
                  )}
                  {analysisResult.proposedPurpose && (
                    <div>
                      <span className="font-medium text-green-700">제안된 목적:</span>
                      <span className="ml-2 text-green-600">{analysisResult.proposedPurpose}</span>
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* 목적 */}
            <div>
              <label htmlFor="purpose" className="block text-sm font-medium text-gray-700 mb-1">
                문서 목적 (선택사항)
              </label>
              <Input
                id="purpose"
                type="text"
                value={purpose}
                onChange={(e) => setPurpose(e.target.value)}
                placeholder="이 문서의 목적이나 용도를 설명해주세요"
                className="w-full"
              />
              <p className="text-xs text-gray-500 mt-1">
                문서의 목적을 명시하면 더 정확한 카테고리 분류가 가능합니다.
              </p>
            </div>

            {/* 태그 */}
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

            {/* 업로드 진행상황 */}
            {isUploading && Object.keys(uploadProgress).length > 0 && (
              <div className="space-y-2">
                <h4 className="text-sm font-medium text-gray-900">업로드 진행상황:</h4>
                {Object.entries(uploadProgress).map(([fileName, progress]) => (
                  <div key={fileName} className="space-y-1">
                    <div className="flex justify-between text-xs">
                      <span className="truncate">{fileName}</span>
                      <span>{progress}%</span>
                    </div>
                    <div className="w-full bg-gray-200 rounded-full h-2">
                      <div
                        className="bg-blue-600 h-2 rounded-full transition-all duration-300"
                        style={{ width: `${progress}%` }}
                      ></div>
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* 에러 메시지 */}
            {error && (
              <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-md text-sm">
                {error}
              </div>
            )}

            {/* 성공 메시지 */}
            {success && (
              <div className="bg-green-50 border border-green-200 text-green-600 px-4 py-3 rounded-md text-sm">
                {success}
              </div>
            )}

            {/* 제출 버튼 */}
            <div className="flex justify-end space-x-4">
              <Button
                type="button"
                variant="outline"
                onClick={() => router.back()}
                disabled={isUploading}
              >
                취소
              </Button>
              <Button
                type="submit"
                disabled={isUploading || files.length === 0}
              >
                {isUploading ? '업로드 중...' : '등록 요청 제출'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {/* 안내 사항 */}
      <Card className="mt-6">
        <CardHeader>
          <CardTitle className="text-lg">등록 절차 안내</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-3 text-sm text-gray-600">
            <div className="flex items-start space-x-2">
              <div className="w-6 h-6 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center text-xs font-semibold">1</div>
              <div>
                <p className="font-medium">파일 업로드</p>
                <p>DOCX, XLSX, PDF 파일을 업로드합니다.</p>
              </div>
            </div>
            <div className="flex items-start space-x-2">
              <div className="w-6 h-6 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center text-xs font-semibold">2</div>
              <div>
                <p className="font-medium">자동 분석</p>
                <p>시스템이 문서 내용을 분석하고 카테고리를 제안합니다.</p>
              </div>
            </div>
            <div className="flex items-start space-x-2">
              <div className="w-6 h-6 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center text-xs font-semibold">3</div>
              <div>
                <p className="font-medium">관리자 승인</p>
                <p>관리자가 등록 요청을 검토하고 승인/반려를 결정합니다.</p>
              </div>
            </div>
            <div className="flex items-start space-x-2">
              <div className="w-6 h-6 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center text-xs font-semibold">4</div>
              <div>
                <p className="font-medium">시스템 등록</p>
                <p>승인된 문서는 MongoDB 벡터 DB에 저장되어 RAG 검색이 가능해집니다.</p>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
