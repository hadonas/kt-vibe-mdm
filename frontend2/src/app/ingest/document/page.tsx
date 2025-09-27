'use client'

import { redirect } from 'next/navigation'
import { useState, useEffect, useMemo } from 'react'
import { useRouter } from 'next/navigation'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import FileUpload from '@/components/ui/FileUpload'
import { api, categoryApi } from '@/lib/api'
import { SingleIngestRequest, Category } from '@/types/api'
import { mockApi } from '@/lib/mock-api'
import { formatCategory } from '@/lib/utils'

// 가변 계층 카테고리 표시를 위한 유틸리티 함수
// (이전 inline 함수 제거하고 utils의 formatCategory 사용)

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
  // 전체 카테고리 (관리자 페이지와 동일한 구조를 재사용)
  interface FlatCatalogNode {
    code: string
    name: string
    level: number
    parentCode: string | null
  }
  const [allCategories, setAllCategories] = useState<FlatCatalogNode[]>([])
  const [categoryPathCodes, setCategoryPathCodes] = useState<string[]>([]) // 선택된 경로 코드들 (root->leaf)
  const [categoryLoading, setCategoryLoading] = useState(false)
  const [categoryError, setCategoryError] = useState<string | null>(null)
  const router = useRouter()

  // 카테고리 로딩
  useEffect(() => {
    const load = async () => {
      try {
        setCategoryLoading(true)
        const res = await categoryApi.getCategories()
        const cats = (res.data.categories || []).map((c: any) => ({
          code: c.code,
            name: c.name,
            level: c.level,
            parentCode: c.parentCode || null
        }))
        setAllCategories(cats)
      } catch (e:any) {
        setCategoryError(e.message || '카테고리를 불러오지 못했습니다.')
      } finally {
        setCategoryLoading(false)
      }
    }
    load()
  }, [])

  // 분석 결과가 있고 경로 미선택 시 자동 채움
  useEffect(() => {
    if (analysisResult?.proposedCategory && categoryPathCodes.length === 0) {
      const cat = analysisResult.proposedCategory
      const codes = cat.codes || cat.hierarchy?.map(h => h.code) || [cat.subCode, cat.midCode, cat.majorCode].filter(Boolean).reverse()
      if (codes && codes.length) setCategoryPathCodes(codes)
    }
  }, [analysisResult, categoryPathCodes.length])

  // 선택된 경로 기반 Category 객체 구성
  const selectedCategoryObject: Category | undefined = useMemo(() => {
    if (categoryPathCodes.length === 0) return undefined
    const nodes = categoryPathCodes.map(code => allCategories.find(c => c.code === code)).filter(Boolean) as FlatCatalogNode[]
    if (nodes.length === 0) return undefined
    const codes = nodes.map(n => n.code)
    const names = nodes.map(n => n.name)
    const depth = nodes.length
    const leaf = nodes[nodes.length - 1]
    // Backend expects legacy 3-level fields still present; map first three nodes accordingly
    const major = nodes[0]
    const mid = nodes[1]
    const sub = nodes[2] || nodes[nodes.length - 1] // fallback to leaf if fewer than 3
    return {
      majorCode: major?.code || '',
      majorName: major?.name || '',
      midCode: mid?.code || '',
      midName: mid?.name || '',
      subCode: sub?.code || '',
      subName: sub?.name || '',
      codes, names, depth, leafCode: leaf.code, leafName: leaf.name,
      hierarchy: nodes.map((n, idx) => ({ level: idx + 1, code: n.code, name: n.name })),
      fullCode: codes.join('-'),
      fullName: names.join(' > '),
      displayPath: names.join(' > ')
    }
  }, [categoryPathCodes, allCategories])

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
      // 직접 파일 업로드 방식 사용 (presign 방식 대신)
      console.log('Uploading file directly to /files/upload:', file.name)
      
      const uploadFormData = new FormData()
      uploadFormData.append('file', file)

      const uploadResponse = await api.post('/files/upload', uploadFormData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
        onUploadProgress: (progressEvent) => {
          if (progressEvent.total) {
            const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total)
            setUploadProgress(prev => ({ ...prev, [file.name]: percentCompleted }))
          }
        }
      })

      const uploadResult = uploadResponse.data
      const fileId = uploadResult.fileId

      setUploadProgress(prev => ({ ...prev, [file.name]: 100 }))
      return fileId

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
        proposedCategory: selectedCategoryObject || analysisResult?.proposedCategory,
        proposedTitle: analysisResult?.proposedTitle,
        originalFileName: files.length > 0 ? files[0].name : undefined
      }

      await api.post('/ingest/single', ingestRequest)
      
      setSuccess('문서 등록 요청이 성공적으로 제출되었습니다. 관리자의 승인을 기다려주세요.')
      
      // 3초 후 대시보드로 이동
      setTimeout(() => {
        router.replace('/dashboard')
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

  const renderCategory = () => {
    if (selectedCategoryObject) return formatCategory(selectedCategoryObject)
    if (analysisResult?.proposedCategory) return formatCategory(analysisResult.proposedCategory)
    return '분류 없음'
  }

  // 레벨별 선택 가능한 옵션 계산
  const levelOptions = useMemo(() => {
    const byParent: Record<string, FlatCatalogNode[]> = {}
    allCategories.forEach(c => {
      const key = c.parentCode || '__root__'
      if (!byParent[key]) byParent[key] = []
      byParent[key].push(c)
    })
    // 순서 보장
    Object.values(byParent).forEach(arr => arr.sort((a,b) => a.code.localeCompare(b.code)))

    const levels: FlatCatalogNode[][] = []
    // level 1
    levels.push(byParent['__root__'] || [])
    for (let i = 0; i < categoryPathCodes.length; i++) {
      const code = categoryPathCodes[i]
      const children = byParent[code]
      if (children && children.length > 0) {
        levels.push(children)
      }
    }
    return levels
  }, [allCategories, categoryPathCodes])

  const updatePath = (levelIndex: number, code: string) => {
    setCategoryPathCodes(prev => {
      const next = prev.slice(0, levelIndex)
      if (code) next[levelIndex] = code
      return next
    })
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
                        {renderCategory()}
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

            {/* 수동 카테고리 선택 (가변 계층) */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">카테고리 선택 (가변 계층)</label>
              {categoryLoading && (
                <div className="text-xs text-gray-500 mb-2">카테고리를 불러오는 중...</div>
              )}
              {categoryError && (
                <div className="text-xs text-red-500 mb-2">{categoryError}</div>
              )}
              <div className="flex flex-wrap gap-2">
                {levelOptions.map((options, idx) => (
                  <select
                    key={idx}
                    className="px-2 py-1 border border-gray-300 rounded text-sm bg-white"
                    value={categoryPathCodes[idx] || ''}
                    onChange={(e) => updatePath(idx, e.target.value)}
                  >
                    <option value="">{idx === 0 ? '1레벨 선택' : '하위 선택'}</option>
                    {options.map(o => (
                      <option key={o.code} value={o.code}>{o.name} ({o.code})</option>
                    ))}
                  </select>
                ))}
              </div>
              {selectedCategoryObject && (
                <div className="mt-2 text-xs text-gray-600">
                  선택된 경로: {selectedCategoryObject.displayPath}
                </div>
              )}
            </div>

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
