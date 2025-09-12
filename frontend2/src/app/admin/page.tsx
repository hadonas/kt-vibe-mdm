'use client';

import { useState, useEffect } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { useAuth } from '@/hooks/useAuth';
import { ArrowDownTrayIcon, TrashIcon, XMarkIcon } from '@heroicons/react/24/outline';
import { adminApi } from '@/lib/api';

interface FileInfo {
  name: string;
  path: string;
  size: number;
  modified: string;
  preview?: string;
}

interface CategoryInfo {
  files: FileInfo[];
  fileCount: number;
}

interface HierarchyData {
  [majorCategory: string]: {
    [midCategory: string]: {
      [subCategory: string]: CategoryInfo;
    };
  };
}

interface HierarchyResponse {
  hierarchy: HierarchyData;
  totalFiles: number;
  totalCategories: number;
  storagePath: string;
}

interface Document {
  id: string;
  serial: {
    full: string;
  };
  purpose: string;
  createdAt: string;
  approvedAt: string;
}

interface DocumentsResponse {
  content: Document[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  size: number;
}

export default function AdminPage() {
  const { user, token } = useAuth();
  const [hierarchy, setHierarchy] = useState<HierarchyResponse | null>(null);
  const [selectedCategory, setSelectedCategory] = useState<{
    major: string;
    mid: string;
    sub: string;
  } | null>(null);
  const [categoryFiles, setCategoryFiles] = useState<FileInfo[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  // Document management states
  const [documents, setDocuments] = useState<Document[]>([]);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [deleteCode, setDeleteCode] = useState('');
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    if (token) {
      fetchHierarchy();
      fetchDocuments(0);
    }
  }, [token]);

  const fetchHierarchy = async () => {
    try {
      setLoading(true);
      setError(null);
      
      console.log('🔍 Fetching hierarchy with token:', token ? 'Token present' : 'No token');
      
      const response = await fetch('/api/admin/files/hierarchy', {
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      });

      console.log('🔍 Response status:', response.status);
      console.log('🔍 Response headers:', Object.fromEntries(response.headers.entries()));

      if (!response.ok) {
        const errorText = await response.text();
        console.error('🔍 Error response:', errorText);
        throw new Error(`파일 계층 구조를 가져오는데 실패했습니다. (${response.status})`);
      }

      const data = await response.json();
      console.log('🔍 Hierarchy data received:', data);
      setHierarchy(data);
    } catch (err) {
      console.error('🔍 Fetch hierarchy error:', err);
      setError(err instanceof Error ? err.message : '알 수 없는 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const fetchCategoryFiles = async (major: string, mid: string, sub: string) => {
    try {
      setLoading(true);
      setError(null);
      
      const response = await fetch(`/api/admin/files/category/${major}/${mid}/${sub}`, {
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      });

      if (!response.ok) {
        throw new Error('카테고리 파일을 가져오는데 실패했습니다.');
      }

      const data = await response.json();
      setCategoryFiles(data.files || []);
      setSelectedCategory({ major, mid, sub });
    } catch (err) {
      setError(err instanceof Error ? err.message : '알 수 없는 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const formatFileSize = (bytes: number) => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString('ko-KR');
  };

  const downloadFile = async (filePath: string, fileName: string) => {
    try {
      if (!token) {
        setError('로그인이 필요합니다.');
        return;
      }

      const response = await fetch(`/api/admin/files/download?filePath=${encodeURIComponent(filePath)}`, {
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      });

      if (!response.ok) {
        throw new Error('파일 다운로드에 실패했습니다.');
      }

      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = fileName;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    } catch (err) {
      console.error('Download error:', err);
      setError('파일 다운로드 중 오류가 발생했습니다.');
    }
  };

  // Document management functions
  const fetchDocuments = async (page = 0) => {
    try {
      setLoading(true);
      setError(null);
      
      const response = await adminApi.getDocuments(page, 20);
      const data: DocumentsResponse = response.data;
      
      setDocuments(data.content);
      setCurrentPage(data.currentPage);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (err) {
      console.error('Fetch documents error:', err);
      setError('문서 목록을 가져오는데 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteDocument = async () => {
    if (!deleteCode.trim()) {
      setError('삭제할 문서의 코드를 입력해주세요.');
      return;
    }

    try {
      setDeleting(true);
      setError(null);
      
      const response = await adminApi.deleteDocumentByCode(deleteCode.trim());
      
      alert(`문서가 성공적으로 삭제되었습니다.\n삭제된 문서: ${response.data.deletedDocuments}개\n삭제된 파일: ${response.data.deletedFiles}개`);
      
      // Refresh documents list
      await fetchDocuments(currentPage);
      
      // Close modal
      setShowDeleteModal(false);
      setDeleteCode('');
    } catch (err: any) {
      console.error('Delete document error:', err);
      setError(err.response?.data?.message || '문서 삭제에 실패했습니다.');
    } finally {
      setDeleting(false);
    }
  };

  const openDeleteModal = () => {
    setShowDeleteModal(true);
    setDeleteCode('');
    setError(null);
  };

  const closeDeleteModal = () => {
    setShowDeleteModal(false);
    setDeleteCode('');
    setError(null);
  };

  if (!user || !user.roles.includes('ADMIN')) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <Card className="p-8 text-center">
          <h1 className="text-2xl font-bold text-red-600 mb-4">접근 권한 없음</h1>
          <p className="text-gray-600">관리자만 접근할 수 있는 페이지입니다.</p>
        </Card>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 p-6">
      <div className="max-w-7xl mx-auto">
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-gray-900 mb-2">관리자 페이지</h1>
          <p className="text-gray-600">문서 파일 계층 구조 관리</p>
        </div>

        {error && (
          <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg">
            <p className="text-red-600">{error}</p>
            <Button 
              onClick={fetchHierarchy}
              className="mt-2"
              variant="outline"
            >
              다시 시도
            </Button>
          </div>
        )}

        {/* Document Management Section */}
        <Card className="p-6 mb-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-xl font-semibold">문서 관리</h2>
            <div className="flex gap-2">
              <Button onClick={() => fetchDocuments(0)} disabled={loading}>
                {loading ? '로딩...' : '문서 목록 새로고침'}
              </Button>
              <Button onClick={openDeleteModal} variant="outline" className="text-red-600 hover:text-red-700">
                <TrashIcon className="w-4 h-4 mr-2" />
                문서 삭제
              </Button>
            </div>
          </div>

          {documents.length > 0 && (
            <div className="space-y-2">
              <div className="text-sm text-gray-600 mb-4">
                총 {totalElements}개 문서 (페이지 {currentPage + 1}/{totalPages})
              </div>
              
              <div className="max-h-96 overflow-y-auto">
                <div className="space-y-2">
                  {documents.map((doc) => (
                    <div key={doc.id} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                      <div className="flex-1">
                        <div className="font-medium text-gray-900">
                          {doc.serial?.full || 'N/A'}
                        </div>
                        <div className="text-sm text-gray-600 truncate">
                          {doc.purpose || '제목 없음'}
                        </div>
                        <div className="text-xs text-gray-500">
                          생성: {formatDate(doc.createdAt)} | 
                          승인: {doc.approvedAt ? formatDate(doc.approvedAt) : '미승인'}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Pagination */}
              {totalPages > 1 && (
                <div className="flex justify-center mt-4 space-x-2">
                  <Button
                    onClick={() => fetchDocuments(currentPage - 1)}
                    disabled={currentPage === 0 || loading}
                    variant="outline"
                    size="sm"
                  >
                    이전
                  </Button>
                  <span className="px-3 py-2 text-sm text-gray-600">
                    {currentPage + 1} / {totalPages}
                  </span>
                  <Button
                    onClick={() => fetchDocuments(currentPage + 1)}
                    disabled={currentPage >= totalPages - 1 || loading}
                    variant="outline"
                    size="sm"
                  >
                    다음
                  </Button>
                </div>
              )}
            </div>
          )}

          {documents.length === 0 && !loading && (
            <div className="text-center py-8 text-gray-500">
              등록된 문서가 없습니다.
            </div>
          )}
        </Card>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* 계층 구조 트리 */}
          <Card className="p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-xl font-semibold">파일 계층 구조</h2>
              <Button onClick={fetchHierarchy} disabled={loading}>
                {loading ? '로딩...' : '새로고침'}
              </Button>
            </div>

            {hierarchy && (
              <div className="space-y-2">
                <div className="text-sm text-gray-600 mb-4">
                  총 {hierarchy.totalFiles}개 파일, {hierarchy.totalCategories}개 카테고리
                </div>
                
                <div className="space-y-2">
                  {Object.entries(hierarchy.hierarchy).map(([majorName, majorData]) => (
                    <div key={majorName} className="border rounded-lg p-3">
                      <div className="font-medium text-gray-900 mb-2">{majorName}</div>
                      <div className="ml-4 space-y-2">
                        {Object.entries(majorData).map(([midName, midData]) => (
                          <div key={midName} className="border rounded p-2">
                            <div className="font-medium text-gray-700 mb-1">{midName}</div>
                            <div className="ml-4 space-y-1">
                              {Object.entries(midData).map(([subName, subData]) => (
                                <div key={subName} className="flex items-center justify-between">
                                  <span className="text-sm text-gray-600">{subName}</span>
                                  <Button
                                    size="sm"
                                    variant="outline"
                                    onClick={() => {
                                      const majorCode = majorName.split('_')[0];
                                      const midCode = midName.split('_')[0];
                                      const subCode = subName.split('_')[0];
                                      fetchCategoryFiles(majorCode, midCode, subCode);
                                    }}
                                  >
                                    {subData.fileCount}개 파일 보기
                                  </Button>
                                </div>
                              ))}
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </Card>

          {/* 선택된 카테고리의 파일 목록 */}
          <Card className="p-6">
            <h2 className="text-xl font-semibold mb-4">
              {selectedCategory 
                ? `${selectedCategory.major}/${selectedCategory.mid}/${selectedCategory.sub} 파일 목록`
                : '카테고리를 선택하세요'
              }
            </h2>

            {selectedCategory && (
              <div className="space-y-3">
                {categoryFiles.length === 0 ? (
                  <p className="text-gray-500 text-center py-8">파일이 없습니다.</p>
                ) : (
                  categoryFiles.map((file, index) => (
                    <div key={index} className="border rounded-lg p-4 hover:bg-gray-50">
                      <div className="flex items-start justify-between mb-2">
                        <h3 className="font-medium text-gray-900 truncate">{file.name}</h3>
                        <div className="flex items-center space-x-2">
                          <span className="text-sm text-gray-500">{formatFileSize(file.size)}</span>
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => downloadFile(file.path, file.name)}
                            className="h-8 px-3"
                          >
                            <ArrowDownTrayIcon className="h-4 w-4 mr-1" />
                            다운로드
                          </Button>
                        </div>
                      </div>
                      <div className="text-sm text-gray-600 mb-2">
                        수정일: {formatDate(file.modified)}
                      </div>
                      {file.preview && (
                        <div className="text-sm text-gray-500 bg-gray-100 p-2 rounded">
                          <div className="font-medium mb-1">미리보기:</div>
                          <div className="whitespace-pre-wrap">{file.preview}</div>
                        </div>
                      )}
                    </div>
                  ))
                )}
              </div>
            )}
          </Card>
        </div>
      </div>

      {/* Delete Document Modal */}
      {showDeleteModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 w-full max-w-md mx-4">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold text-red-600">문서 삭제</h3>
              <button
                onClick={closeDeleteModal}
                className="text-gray-400 hover:text-gray-600"
              >
                <XMarkIcon className="w-6 h-6" />
              </button>
            </div>

            <div className="mb-4">
              <p className="text-gray-600 mb-4">
                삭제할 문서의 코드를 입력하세요. 이 작업은 되돌릴 수 없습니다.
              </p>
              <div className="space-y-2">
                <label className="block text-sm font-medium text-gray-700">
                  문서 코드
                </label>
                <input
                  type="text"
                  value={deleteCode}
                  onChange={(e) => setDeleteCode(e.target.value)}
                  placeholder="예: A0101-1234"
                  className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-red-500"
                  autoFocus
                />
              </div>
            </div>

            {error && (
              <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-md">
                <p className="text-red-600 text-sm">{error}</p>
              </div>
            )}

            <div className="flex justify-end space-x-3">
              <Button
                onClick={closeDeleteModal}
                variant="outline"
                disabled={deleting}
              >
                취소
              </Button>
              <Button
                onClick={handleDeleteDocument}
                disabled={deleting || !deleteCode.trim()}
                className="bg-red-600 hover:bg-red-700 text-white"
              >
                {deleting ? '삭제 중...' : '삭제'}
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
