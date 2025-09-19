'use client';

import { useState, useEffect } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { useAuth } from '@/contexts/AuthContexts';
import { 
  ArrowDownTrayIcon, 
  TrashIcon, 
  XMarkIcon, 
  PlusIcon, 
  FolderIcon,
  DocumentIcon,
  ChevronRightIcon,
  ChevronDownIcon,
  SparklesIcon,
  CogIcon
} from '@heroicons/react/24/outline';
import { adminApi, categoryApi } from '@/lib/api';

// 스마트 카테고리 관리 인터페이스들
interface CatalogNode {
  id: string;
  level: number;
  code: string;
  name: string;
  parentCode: string | null;
  active: boolean;
  order: number;
  description: string | null;
  aliases: string[] | null;
  includeKeywords: string[] | null;
  excludeKeywords: string[] | null;
  examplePhrases: string[] | null;
  vector: number[] | null;
  scoreWeights: {
    keyword: number;
    bm25: number;
    vector: number;
    xencoder: number;
  } | null;
  lastVectorUpdate: string | null;
  children: CatalogNode[] | null;
}

interface CategoryCreateRequest {
  level: number;
  code: string;
  name: string;
  parentCode?: string;
  description?: string;
  aliases?: string[];
  includeKeywords?: string[];
  excludeKeywords?: string[];
  examplePhrases?: string[];
  order?: number;
  autoGenerate?: boolean;
}

interface ReclassificationResult {
  totalDocuments: number;
  processedCount: number;
  successCount: number;
  unchangedCount: number;
  failureCount: number;
  errors: string[];
  startTime: string;
  endTime: string;
  successRate: number;
  durationSeconds: number;
}

export default function AdminPage() {
  const { user, token, isLoading } = useAuth();
  
  // 카테고리 관리 상태
  const [categories, setCategories] = useState<CatalogNode[]>([]);
  const [expandedCategories, setExpandedCategories] = useState<Set<string>>(new Set());
  const [selectedCategory, setSelectedCategory] = useState<CatalogNode | null>(null);
  const [documentCounts, setDocumentCounts] = useState<Record<string, number>>({});
  const [categoryDocuments, setCategoryDocuments] = useState<any[]>([]);
  const [showDocuments, setShowDocuments] = useState(false);
  const [documentsLoading, setDocumentsLoading] = useState(false);
  
  // 카테고리 생성 상태
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [createForm, setCreateForm] = useState<CategoryCreateRequest>({
    level: 1,
    code: '',
    name: '',
    autoGenerate: true
  });
  
  // 재분류 상태
  const [reclassificationStatus, setReclassificationStatus] = useState<'idle' | 'running' | 'completed'>('idle');
  const [reclassificationResult, setReclassificationResult] = useState<ReclassificationResult | null>(null);
  
  // 공통 상태
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (token) {
      fetchCategories();
      fetchDocumentCounts();
    }
  }, [token]);

  const fetchCategories = async () => {
    try {
      setLoading(true);
      setError(null);
      
      const response = await categoryApi.getCategories();
      setCategories(response.data.categories || []);
    } catch (err) {
      console.error('Fetch categories error:', err);
      setError(err instanceof Error ? err.message : '알 수 없는 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  };

  // 부모 카테고리 선택에 따른 레벨 자동 계산
  const calculateLevelFromParent = (parentCode: string | null) => {
    if (!parentCode) return 1; // 부모가 없으면 최상위(1레벨)
    
    const parent = categories.find(cat => cat.code === parentCode);
    return parent ? parent.level + 1 : 1;
  };

  const fetchDocumentCounts = async () => {
    try {
      const response = await categoryApi.getDocumentCounts();
      setDocumentCounts(response.data.documentCounts || {});
    } catch (err) {
      console.error('Fetch document counts error:', err);
      // 문서 개수 조회 실패는 치명적이지 않으므로 에러 상태를 설정하지 않음
    }
  };

  const fetchCategoryDocuments = async (categoryCode: string) => {
    try {
      setDocumentsLoading(true);
      const response = await categoryApi.getCategoryDocuments(categoryCode);
      setCategoryDocuments(response.data.documents || []);
      setShowDocuments(true);
    } catch (err) {
      console.error('Fetch category documents error:', err);
      setError('문서 목록을 불러오는데 실패했습니다.');
    } finally {
      setDocumentsLoading(false);
    }
  };

  const createCategory = async () => {
    try {
      setLoading(true);
      setError(null);
      
      const response = await categoryApi.createCategory(createForm);
      const result = response.data;
      
      alert(`카테고리가 성공적으로 생성되었습니다!\n코드: ${result.code}\n이름: ${result.name}`);
      
      // 목록 새로고침 및 모달 닫기
      await fetchCategories();
      setShowCreateModal(false);
      setCreateForm({
        level: 1,
        code: '',
        name: '',
        autoGenerate: true
      });
      
    } catch (err: any) {
      console.error('Create category error:', err);
      setError(err.response?.data?.error || err.message || '카테고리 생성 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const startReclassification = async () => {
    try {
      setReclassificationStatus('running');
      setError(null);
      
      const response = await categoryApi.reclassifyAllDocuments();
      const result = response.data;
      
      alert(`모든 문서 재분류가 시작되었습니다!\n${result.message}\n\n처리 시간이 오래 걸릴 수 있습니다.`);
      
      // 상태 모니터링 (실제로는 WebSocket이나 폴링으로 구현)
      setTimeout(() => {
        setReclassificationStatus('completed');
      }, 5000);
      
    } catch (err: any) {
      console.error('Reclassification error:', err);
      setError(err.response?.data?.error || err.message || '재분류 시작 중 오류가 발생했습니다.');
      setReclassificationStatus('idle');
    }
  };

  const toggleCategoryExpansion = (categoryCode: string) => {
    const newExpanded = new Set(expandedCategories);
    if (newExpanded.has(categoryCode)) {
      newExpanded.delete(categoryCode);
    } else {
      newExpanded.add(categoryCode);
    }
    setExpandedCategories(newExpanded);
  };

  const renderCategoryTree = (parentCode: string | null = null, level: number = 1) => {
    const childCategories = categories.filter(cat => cat.parentCode === parentCode && cat.level === level);
    
    return childCategories.map(category => {
      const hasChildren = categories.some(cat => cat.parentCode === category.code);
      const isExpanded = expandedCategories.has(category.code);
      
      return (
        <div key={category.code} className="mb-2">
          <div 
            className={`flex items-center p-3 rounded-lg cursor-pointer transition-colors ${
              selectedCategory?.code === category.code 
                ? 'bg-blue-50 border-2 border-blue-200' 
                : 'bg-gray-50 hover:bg-gray-100'
            }`}
            onClick={() => {
              setSelectedCategory(category);
              if (documentCounts[category.code] > 0) {
                fetchCategoryDocuments(category.code);
              }
            }}
          >
            <div className="flex items-center flex-1" style={{ marginLeft: `${(level - 1) * 20}px` }}>
              {hasChildren && (
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    toggleCategoryExpansion(category.code);
                  }}
                  className="mr-2 p-1 hover:bg-gray-200 rounded"
                >
                  {isExpanded ? (
                    <ChevronDownIcon className="w-4 h-4" />
                  ) : (
                    <ChevronRightIcon className="w-4 h-4" />
                  )}
                </button>
              )}
              
              <div className="flex items-center">
                {hasChildren ? (
                  <FolderIcon className="w-5 h-5 text-blue-500 mr-2" />
                ) : (
                  <DocumentIcon className="w-5 h-5 text-gray-500 mr-2" />
                )}
                
                <div className="flex-1">
                  <div className="font-medium text-gray-900 flex items-center justify-between">
                    <span>{category.name} ({category.code})</span>
                    <span className="text-sm bg-blue-100 text-blue-800 px-2 py-1 rounded-full ml-2">
                      {documentCounts[category.code] || 0}개
                    </span>
                  </div>
                  {category.description && (
                    <div className="text-sm text-gray-500">
                      {category.description}
                    </div>
                  )}
                </div>
              </div>
            </div>
            
            <div className="flex items-center space-x-2">
              <span className="text-xs bg-gray-200 px-2 py-1 rounded">
                Level {category.level}
              </span>
              {category.vector && (
                <SparklesIcon className="w-4 h-4 text-green-500" title="임베딩 있음" />
              )}
            </div>
          </div>
          
          {hasChildren && isExpanded && (
            <div className="ml-4">
              {renderCategoryTree(category.code, level + 1)}
            </div>
          )}
        </div>
      );
    });
  };

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <Card className="p-8 text-center">
          <h1 className="text-2xl font-bold text-blue-600 mb-4">로딩 중...</h1>
          <p className="text-gray-600">사용자 정보를 확인하고 있습니다.</p>
        </Card>
      </div>
    );
  }

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
          <h1 className="text-3xl font-bold text-gray-900 mb-2">스마트 카테고리 관리</h1>
          <p className="text-gray-600">가변 계층 구조 카테고리 관리 및 문서 재분류</p>
        </div>

        {error && (
          <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg">
            <p className="text-red-600">{error}</p>
            <Button 
              onClick={() => {
                setError(null);
                fetchCategories();
              }}
              className="mt-2"
              variant="outline"
            >
              다시 시도
            </Button>
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* 카테고리 트리 */}
          <div className="lg:col-span-2">
            <Card className="p-6">
              <div className="flex items-center justify-between mb-6">
                <h2 className="text-xl font-semibold">카테고리 계층 구조</h2>
                <div className="flex space-x-2">
                  <Button onClick={fetchCategories} disabled={loading} size="sm">
                    {loading ? '로딩...' : '새로고침'}
                  </Button>
                  <Button 
                    onClick={() => setShowCreateModal(true)} 
                    size="sm"
                    className="bg-green-600 hover:bg-green-700"
                  >
                    <PlusIcon className="w-4 h-4 mr-2" />
                    카테고리 추가
                  </Button>
                </div>
              </div>

              <div className="space-y-2 max-h-96 overflow-y-auto">
                {categories.length === 0 ? (
                  <div className="text-center py-8 text-gray-500">
                    카테고리가 없습니다.
                  </div>
                ) : (
                  renderCategoryTree()
                )}
              </div>
            </Card>

            {/* 선택된 카테고리의 문서 목록 */}
            {selectedCategory && documentCounts[selectedCategory.code] > 0 && (
              <Card className="p-6 mt-6">
                <div className="flex items-center justify-between mb-4">
                  <h3 className="text-lg font-semibold">
                    📁 {selectedCategory.name} 카테고리 문서 목록 ({documentCounts[selectedCategory.code]}개)
                  </h3>
                  <div className="flex space-x-2">
                    <Button
                      onClick={() => fetchCategoryDocuments(selectedCategory.code)}
                      disabled={documentsLoading}
                      size="sm"
                      variant="outline"
                    >
                      {documentsLoading ? '로딩 중...' : '새로고침'}
                    </Button>
                    <Button
                      onClick={async () => {
                        if (window.confirm(`"${selectedCategory.name}" 카테고리의 모든 문서(${documentCounts[selectedCategory.code]}개)를 삭제하시겠습니까?\n이 작업은 되돌릴 수 없습니다.`)) {
                          try {
                            await adminApi.deleteDocumentsByCategory(selectedCategory.code);
                            alert(`"${selectedCategory.name}" 카테고리의 모든 문서가 삭제되었습니다.`);
                            // 문서 목록 및 카운트 새로고침
                            fetchDocumentCounts();
                            setCategoryDocuments([]);
                            setShowDocuments(false);
                          } catch (err) {
                            console.error('카테고리 문서 삭제 오류:', err);
                            alert('카테고리 문서 삭제 중 오류가 발생했습니다.');
                          }
                        }
                      }}
                      size="sm"
                      variant="outline"
                      className="text-red-600 hover:text-red-700 hover:bg-red-50"
                    >
                      <TrashIcon className="w-4 h-4 mr-1" />
                      전체 삭제
                    </Button>
                  </div>
                </div>

                {documentsLoading ? (
                  <div className="text-center py-8 text-gray-500">
                    문서 목록을 불러오는 중...
                  </div>
                ) : categoryDocuments.length > 0 ? (
                  <div className="space-y-3 max-h-64 overflow-y-auto">
                    {categoryDocuments.map((doc, index) => (
                      <div key={doc.id} className="border rounded-lg p-3 hover:bg-gray-50">
                        <div className="flex items-start justify-between">
                          <div className="flex-1">
                            <div className="flex items-center space-x-2 mb-1">
                              <span className="text-sm font-medium text-blue-600">
                                {doc.serial}
                              </span>
                              <span className="text-xs bg-gray-100 px-2 py-1 rounded">
                                {doc.sourceType === 'repository' ? '레포지토리' : '파일'}
                              </span>
                            </div>
                            <h4 className="font-medium text-gray-900 text-sm mb-1">
                              {doc.purpose}
                            </h4>
                            <div className="text-xs text-gray-600 mb-1">
                              작성일: {new Date(doc.createdAt).toLocaleDateString('ko-KR')}
                            </div>
                            {doc.tags && doc.tags.length > 0 && (
                              <div className="flex flex-wrap gap-1">
                                {doc.tags.slice(0, 3).map((tag: string, tagIndex: number) => (
                                  <span key={tagIndex} className="text-xs bg-gray-200 text-gray-700 px-1 py-0.5 rounded">
                                    {tag}
                                  </span>
                                ))}
                                {doc.tags.length > 3 && (
                                  <span className="text-xs text-gray-500">+{doc.tags.length - 3}개</span>
                                )}
                              </div>
                            )}
                          </div>
                          <div className="flex space-x-2 ml-2">
                            <Button
                              onClick={() => {
                                window.open(`http://localhost:8080/api/public/files/download-by-code?code=${encodeURIComponent(doc.serial)}`, '_blank');
                              }}
                              variant="outline"
                              size="sm"
                            >
                              <ArrowDownTrayIcon className="w-4 h-4" />
                            </Button>
                            <Button
                              onClick={async () => {
                                if (window.confirm(`문서 "${doc.serial}"을(를) 삭제하시겠습니까?\n이 작업은 되돌릴 수 없습니다.`)) {
                                  try {
                                    await adminApi.deleteDocumentByCode(doc.serial);
                                    alert('문서가 성공적으로 삭제되었습니다.');
                                    // 문서 목록 새로고침
                                    fetchCategoryDocuments(selectedCategory.code);
                                    fetchDocumentCounts();
                                  } catch (err) {
                                    console.error('문서 삭제 오류:', err);
                                    alert('문서 삭제 중 오류가 발생했습니다.');
                                  }
                                }
                              }}
                              variant="outline"
                              size="sm"
                              className="text-red-600 hover:text-red-700 hover:bg-red-50"
                            >
                              <TrashIcon className="w-4 h-4" />
                            </Button>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="text-center py-6 text-gray-500">
                    이 카테고리에는 문서가 없습니다.
                  </div>
                )}
              </Card>
            )}
          </div>

          {/* 카테고리 상세 정보 및 관리 기능 */}
          <div>
            <Card className="p-6 mb-6">
              <h2 className="text-xl font-semibold mb-4">카테고리 상세 정보</h2>
              
              {selectedCategory ? (
                <div className="space-y-4">
                  <div>
                    <h3 className="font-medium text-gray-900 mb-2">
                      {selectedCategory.name} ({selectedCategory.code})
                    </h3>
                    <div className="text-sm text-gray-600 space-y-1">
                      <div>레벨: {selectedCategory.level}</div>
                      <div>상위: {selectedCategory.parentCode || '없음'}</div>
                      <div>순서: {selectedCategory.order}</div>
                      <div>활성: {selectedCategory.active ? '예' : '아니오'}</div>
                    </div>
                  </div>
                  
                  {selectedCategory.description && (
                    <div>
                      <div className="font-medium text-gray-700 mb-1">설명</div>
                      <div className="text-sm text-gray-600 bg-gray-50 p-2 rounded">
                        {selectedCategory.description}
                      </div>
                    </div>
                  )}
                  
                  {selectedCategory.aliases && selectedCategory.aliases.length > 0 && (
                    <div>
                      <div className="font-medium text-gray-700 mb-1">동의어</div>
                      <div className="flex flex-wrap gap-1">
                        {selectedCategory.aliases.map((alias, idx) => (
                          <span key={idx} className="text-xs bg-blue-100 text-blue-800 px-2 py-1 rounded">
                            {alias}
                          </span>
                        ))}
                      </div>
                    </div>
                  )}
                  
                  {selectedCategory.includeKeywords && selectedCategory.includeKeywords.length > 0 && (
                    <div>
                      <div className="font-medium text-gray-700 mb-1">포함 키워드</div>
                      <div className="flex flex-wrap gap-1">
                        {selectedCategory.includeKeywords.map((keyword, idx) => (
                          <span key={idx} className="text-xs bg-green-100 text-green-800 px-2 py-1 rounded">
                            {keyword}
                          </span>
                        ))}
                      </div>
                    </div>
                  )}
                  
                  {selectedCategory.vector && (
                    <div>
                      <div className="font-medium text-gray-700 mb-1">임베딩 정보</div>
                      <div className="text-sm text-gray-600">
                        <div className="flex items-center">
                          <SparklesIcon className="w-4 h-4 text-green-500 mr-1" />
                          {selectedCategory.vector.length}차원 벡터
                        </div>
                        <div className="text-xs text-gray-500">
                          마지막 업데이트: {selectedCategory.lastVectorUpdate ? 
                            new Date(selectedCategory.lastVectorUpdate).toLocaleString('ko-KR') : 
                            '없음'
                          }
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              ) : (
                <div className="text-center py-8 text-gray-500">
                  카테고리를 선택하세요
                </div>
              )}
            </Card>


            {/* 스마트 재분류 섹션 */}
            <Card className="p-6">
              <h2 className="text-xl font-semibold mb-4">문서 스마트 재분류</h2>
              
              <div className="space-y-4">
                <div className="text-sm text-gray-600">
                  기존 문서들을 새로운 스마트 분류 시스템으로 재분류하고, 카테고리 메타데이터를 LLM으로 재생성합니다.
                </div>
                
                {/* LLM 메타데이터 강제 재생성 */}
                <div className="border-t pt-4">
                  <div className="flex items-center justify-between mb-3">
                    <h3 className="font-medium text-gray-900">카테고리 메타데이터 재생성</h3>
                    <Button
                      onClick={async () => {
                        try {
                          setLoading(true);
                          setError(null);
                          
                          const response = await categoryApi.forceRegenerateAllMetadata();
                          const result = response.data;
                          
                          alert(`카테고리 메타데이터 및 임베딩 재생성 완료!\n성공: ${result.successCount}개\n실패: ${result.failureCount}개`);
                          
                          // 카테고리 목록 새로고침
                          await fetchCategories();
                          
                        } catch (err: any) {
                          console.error('Force regenerate metadata error:', err);
                          setError(err.response?.data?.error || '메타데이터 재생성 중 오류가 발생했습니다.');
                        } finally {
                          setLoading(false);
                        }
                      }}
                      variant="outline"
                      disabled={loading}
                      className="text-sm"
                    >
                      <SparklesIcon className="w-4 h-4 mr-1" />
                      LLM으로 메타데이터 재생성
                    </Button>
                  </div>
                  <div className="text-xs text-gray-500">
                    모든 카테고리의 설명, 동의어, 키워드, 예시문장을 LLM으로 다시 생성합니다.
                  </div>
                </div>
                
                {reclassificationStatus === 'idle' && (
                  <Button 
                    onClick={startReclassification}
                    className="w-full bg-purple-600 hover:bg-purple-700"
                    disabled={loading}
                  >
                    <CogIcon className="w-4 h-4 mr-2" />
                    모든 문서 재분류 시작
                  </Button>
                )}
                
                {reclassificationStatus === 'running' && (
                  <div className="text-center py-4">
                    <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-purple-600 mx-auto mb-2"></div>
                    <div className="text-sm text-gray-600">재분류 진행 중...</div>
                  </div>
                )}
                
                {reclassificationStatus === 'completed' && reclassificationResult && (
                  <div className="bg-green-50 p-4 rounded-lg">
                    <div className="font-medium text-green-800 mb-2">재분류 완료!</div>
                    <div className="text-sm text-green-700 space-y-1">
                      <div>처리: {reclassificationResult.processedCount}개</div>
                      <div>성공: {reclassificationResult.successCount}개</div>
                      <div>변경없음: {reclassificationResult.unchangedCount}개</div>
                      <div>실패: {reclassificationResult.failureCount}개</div>
                      <div>성공률: {reclassificationResult.successRate.toFixed(1)}%</div>
                    </div>
                  </div>
                )}
              </div>
            </Card>
          </div>
        </div>
      </div>

      {/* 카테고리 생성 모달 */}
      {showCreateModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 w-full max-w-lg mx-4 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold text-gray-900">새 카테고리 생성</h3>
              <button
                onClick={() => setShowCreateModal(false)}
                className="text-gray-400 hover:text-gray-600"
              >
                <XMarkIcon className="w-6 h-6" />
              </button>
            </div>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  상위 카테고리 선택
                </label>
                <select
                  value={createForm.parentCode || ''}
                  onChange={(e) => {
                    const parentCode = e.target.value || null;
                    const newLevel = calculateLevelFromParent(parentCode);
                    setCreateForm({
                      ...createForm, 
                      parentCode: parentCode,
                      level: newLevel
                    });
                  }}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="">최상위 카테고리 생성 (1레벨)</option>
                  {categories.map(cat => (
                    <option key={cat.code} value={cat.code}>
                      {'  '.repeat(cat.level - 1)}{cat.name} ({cat.code}) - {cat.level}레벨 하위로 생성
                    </option>
                  ))}
                </select>
                <div className="text-xs text-gray-500 mt-1">
                  부모를 선택하면 자동으로 {createForm.parentCode ? calculateLevelFromParent(createForm.parentCode) : 1}레벨로 설정됩니다
                </div>
              </div>
              
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  자동 계산된 레벨
                </label>
                <div className="px-3 py-2 bg-gray-100 border border-gray-300 rounded-md text-gray-700">
                  레벨 {createForm.level} {createForm.level === 1 ? '(최상위)' : `(${createForm.parentCode ? categories.find(c => c.code === createForm.parentCode)?.name : ''} 하위)`}
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  카테고리 코드 *
                </label>
                <Input
                  type="text"
                  value={createForm.code}
                  onChange={(e) => setCreateForm({...createForm, code: e.target.value})}
                  placeholder="예: A05, A0501, A050101"
                  required
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  카테고리명 *
                </label>
                <Input
                  type="text"
                  value={createForm.name}
                  onChange={(e) => setCreateForm({...createForm, name: e.target.value})}
                  placeholder="예: 인공지능, 머신러닝, 딥러닝"
                  required
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  순서
                </label>
                <Input
                  type="number"
                  value={createForm.order || 0}
                  onChange={(e) => setCreateForm({...createForm, order: parseInt(e.target.value)})}
                  placeholder="0"
                />
              </div>

              <div className="flex items-center">
                <input
                  type="checkbox"
                  id="autoGenerate"
                  checked={createForm.autoGenerate || false}
                  onChange={(e) => setCreateForm({...createForm, autoGenerate: e.target.checked})}
                  className="mr-2"
                />
                <label htmlFor="autoGenerate" className="text-sm text-gray-700">
                  LLM으로 설명/키워드/동의어 자동 생성
                </label>
              </div>

              {!createForm.autoGenerate && (
                <>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      설명
                    </label>
                    <textarea
                      value={createForm.description || ''}
                      onChange={(e) => setCreateForm({...createForm, description: e.target.value})}
                      placeholder="카테고리에 대한 설명을 입력하세요"
                      className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                      rows={3}
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      동의어 (쉼표로 구분)
                    </label>
                    <Input
                      type="text"
                      value={createForm.aliases?.join(', ') || ''}
                      onChange={(e) => setCreateForm({
                        ...createForm, 
                        aliases: e.target.value.split(',').map(s => s.trim()).filter(s => s)
                      })}
                      placeholder="예: AI, 인공지능, 기계학습"
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      포함 키워드 (쉼표로 구분)
                    </label>
                    <Input
                      type="text"
                      value={createForm.includeKeywords?.join(', ') || ''}
                      onChange={(e) => setCreateForm({
                        ...createForm, 
                        includeKeywords: e.target.value.split(',').map(s => s.trim()).filter(s => s)
                      })}
                      placeholder="예: 머신러닝, 딥러닝, 신경망"
                    />
                  </div>
                </>
              )}
            </div>

            {error && (
              <div className="mt-4 p-3 bg-red-50 border border-red-200 rounded-md">
                <p className="text-red-600 text-sm">{error}</p>
              </div>
            )}

            <div className="flex justify-end space-x-3 mt-6">
              <Button
                onClick={() => setShowCreateModal(false)}
                variant="outline"
                disabled={loading}
              >
                취소
              </Button>
              <Button
                onClick={createCategory}
                disabled={loading || !createForm.code || !createForm.name}
                className="bg-blue-600 hover:bg-blue-700"
              >
                {loading ? '생성 중...' : '생성'}
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}