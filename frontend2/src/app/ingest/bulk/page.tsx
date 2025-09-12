'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import FileUpload from '@/components/ui/FileUpload'
import api from '@/lib/api'
import { BulkIngestResponse } from '@/types/api'

type BulkType = 'ZIP' | 'CSV'

export default function BulkIngestPage() {
  const [files, setFiles] = useState<File[]>([])
  const [bulkType, setBulkType] = useState<BulkType>('ZIP')
  const [isUploading, setIsUploading] = useState(false)
  const [result, setResult] = useState<BulkIngestResponse | null>(null)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const router = useRouter()

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    
    if (files.length === 0) {
      setError('업로드할 파일을 선택해주세요.')
      return
    }

    setIsUploading(true)
    setError('')
    setResult(null)
    setSuccess('')

    try {
      const formData = new FormData()
      formData.append('file', files[0])
      formData.append('type', bulkType)

      const response = await api.post<BulkIngestResponse>('/ingest/bulk', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      })

      setResult(response.data)
      
      if (response.data.errorCount === 0) {
        setSuccess(`벌크 등록이 성공적으로 완료되었습니다. 총 ${response.data.successCount}개의 항목이 처리되었습니다.`)
      } else {
        setSuccess(`벌크 등록이 완료되었습니다. 성공: ${response.data.successCount}개, 실패: ${response.data.errorCount}개`)
      }

    } catch (error: unknown) {
      console.error('Bulk ingest error:', error)
      const errorMessage = error instanceof Error && 'response' in error 
        ? (error as any).response?.data?.error 
        : '벌크 등록에 실패했습니다.'
      setError(errorMessage)
    } finally {
      setIsUploading(false)
    }
  }

  const handleFilesChange = (newFiles: File[]) => {
    setFiles(newFiles)
    setError('')
    setResult(null)
  }

  const getAcceptedFileTypes = () => {
    return bulkType === 'ZIP' ? '.zip' : '.csv'
  }

  const getFileDescription = () => {
    if (bulkType === 'ZIP') {
      return 'ZIP 파일 내부에 여러 문서 파일(DOCX, XLSX, PDF)이 포함되어야 합니다.'
    } else {
      return 'CSV 파일에는 문서 정보와 레포지토리 URL이 포함되어야 합니다.'
    }
  }

  return (
    <div className="max-w-4xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-gray-900">벌크 등록</h1>
        <p className="mt-2 text-gray-600">
          ZIP 파일 또는 CSV 파일을 사용하여 여러 문서를 한 번에 등록할 수 있습니다.
        </p>
      </div>

      <Card className="mb-6">
        <CardHeader>
          <CardTitle>벌크 등록 유형 선택</CardTitle>
          <CardDescription>
            등록할 파일의 유형을 선택하세요.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
            <div
              className={`p-4 border-2 rounded-lg cursor-pointer transition-colors ${
                bulkType === 'ZIP'
                  ? 'border-blue-500 bg-blue-50'
                  : 'border-gray-300 hover:border-gray-400'
              }`}
              onClick={() => setBulkType('ZIP')}
            >
              <div className="flex items-center space-x-2 mb-2">
                <input
                  type="radio"
                  id="zip"
                  name="bulkType"
                  value="ZIP"
                  checked={bulkType === 'ZIP'}
                  onChange={() => setBulkType('ZIP')}
                  className="text-blue-600"
                />
                <label htmlFor="zip" className="font-medium text-gray-900">
                  ZIP 파일
                </label>
              </div>
              <p className="text-sm text-gray-600">
                여러 문서 파일을 ZIP으로 압축하여 업로드
              </p>
            </div>

            <div
              className={`p-4 border-2 rounded-lg cursor-pointer transition-colors ${
                bulkType === 'CSV'
                  ? 'border-blue-500 bg-blue-50'
                  : 'border-gray-300 hover:border-gray-400'
              }`}
              onClick={() => setBulkType('CSV')}
            >
              <div className="flex items-center space-x-2 mb-2">
                <input
                  type="radio"
                  id="csv"
                  name="bulkType"
                  value="CSV"
                  checked={bulkType === 'CSV'}
                  onChange={() => setBulkType('CSV')}
                  className="text-blue-600"
                />
                <label htmlFor="csv" className="font-medium text-gray-900">
                  CSV 파일
                </label>
              </div>
              <p className="text-sm text-gray-600">
                문서 정보와 레포지토리 URL을 CSV 형태로 업로드
              </p>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card className="mb-6">
        <CardHeader>
          <CardTitle>파일 업로드</CardTitle>
          <CardDescription>
            {getFileDescription()}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-6">
            <FileUpload
              onFilesChange={handleFilesChange}
              accept={getAcceptedFileTypes()}
              multiple={false}
              maxSize={100 * 1024 * 1024} // 100MB
            />

            {error && (
              <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-md text-sm">
                {error}
              </div>
            )}

            {success && (
              <div className="bg-green-50 border border-green-200 text-green-600 px-4 py-3 rounded-md text-sm">
                {success}
              </div>
            )}

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
                {isUploading ? '업로드 중...' : '벌크 등록 시작'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {/* 처리 결과 */}
      {result && (
        <Card className="mb-6">
          <CardHeader>
            <CardTitle>처리 결과</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
              <div className="text-center p-4 bg-gray-50 rounded-lg">
                <div className="text-2xl font-bold text-gray-900">{result.totalProcessed}</div>
                <div className="text-sm text-gray-600">총 처리</div>
              </div>
              <div className="text-center p-4 bg-green-50 rounded-lg">
                <div className="text-2xl font-bold text-green-600">{result.successCount}</div>
                <div className="text-sm text-gray-600">성공</div>
              </div>
              <div className="text-center p-4 bg-red-50 rounded-lg">
                <div className="text-2xl font-bold text-red-600">{result.errorCount}</div>
                <div className="text-sm text-gray-600">실패</div>
              </div>
            </div>

            {result.errors && result.errors.length > 0 && (
              <div>
                <h4 className="font-medium text-gray-900 mb-2">오류 상세:</h4>
                <div className="bg-red-50 border border-red-200 rounded-md p-3 max-h-60 overflow-y-auto">
                  {result.errors.map((error, index) => (
                    <div key={index} className="text-sm text-red-600 mb-1">
                      <span className="font-medium">행 {error.row}:</span> {error.error}
                    </div>
                  ))}
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* 파일 형식 안내 */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">ZIP 파일 형식</CardTitle>
          </CardHeader>
          <CardContent className="text-sm text-gray-600 space-y-2">
            <p><strong>구조:</strong></p>
            <pre className="bg-gray-50 p-2 rounded text-xs">
{`documents.zip
├── folder1/
│   ├── document1.docx
│   └── document2.xlsx
├── folder2/
│   └── document3.pdf
└── document4.docx`}
            </pre>
            <p><strong>지원 파일:</strong> DOCX, XLSX, PDF</p>
            <p><strong>최대 크기:</strong> 100MB</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-lg">CSV 파일 형식</CardTitle>
          </CardHeader>
          <CardContent className="text-sm text-gray-600 space-y-2">
            <p><strong>필수 컬럼:</strong></p>
            <pre className="bg-gray-50 p-2 rounded text-xs">
{`type,url,purpose,tags
REPO,https://github.com/user/repo1,웹 애플리케이션,web;react
REPO,https://github.com/user/repo2,API 서버,api;node.js`}
            </pre>
            <p><strong>type:</strong> REPO (레포지토리)</p>
            <p><strong>url:</strong> GitHub 레포지토리 URL</p>
            <p><strong>purpose:</strong> 프로젝트 목적</p>
            <p><strong>tags:</strong> 세미콜론(;)으로 구분된 태그</p>
          </CardContent>
        </Card>
      </div>

      {/* 처리 절차 안내 */}
      <Card className="mt-6">
        <CardHeader>
          <CardTitle className="text-lg">벌크 등록 절차</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-3 text-sm text-gray-600">
            <div className="flex items-start space-x-2">
              <div className="w-6 h-6 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center text-xs font-semibold">1</div>
              <div>
                <p className="font-medium">파일 업로드</p>
                <p>ZIP 또는 CSV 파일을 업로드합니다.</p>
              </div>
            </div>
            <div className="flex items-start space-x-2">
              <div className="w-6 h-6 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center text-xs font-semibold">2</div>
              <div>
                <p className="font-medium">자동 처리</p>
                <p>시스템이 각 항목을 분석하고 등록 요청을 생성합니다.</p>
              </div>
            </div>
            <div className="flex items-start space-x-2">
              <div className="w-6 h-6 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center text-xs font-semibold">3</div>
              <div>
                <p className="font-medium">관리자 승인</p>
                <p>생성된 모든 등록 요청은 관리자의 승인을 거쳐야 합니다.</p>
              </div>
            </div>
            <div className="flex items-start space-x-2">
              <div className="w-6 h-6 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center text-xs font-semibold">4</div>
              <div>
                <p className="font-medium">시스템 등록</p>
                <p>승인된 항목들은 MongoDB 벡터 DB에 저장됩니다.</p>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
