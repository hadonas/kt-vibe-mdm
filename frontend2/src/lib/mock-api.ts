import { ApprovalRequest, ChatQueryResponse, PresignResponse } from '@/types/api'

// 미구현 API들에 대한 Mock 데이터
export const mockApiResponses = {
  // Approval API Mock
  getApprovalRequests: (): ApprovalRequest[] => [
    {
      id: 'req-001',
      ownerId: 'user123',
      source: {
        type: 'REPO',
        repoUrl: 'https://github.com/user/sample-project',
      },
      extractedText: 'React를 사용한 웹 애플리케이션 프로젝트입니다. TypeScript와 TailwindCSS를 사용하여 개발되었습니다.',
      proposedCategory: {
        majorCode: 'A',
        majorName: '웹 개발',
        midCode: 'A01',
        midName: '프론트엔드',
        subCode: 'A0101',
        subName: 'React',
      },
      proposedPurpose: 'React 기반 관리자 대시보드',
      similarCandidates: [
        {
          docId: 'doc-001',
          score: 0.85,
          serial: 'A0101-001',
          purpose: 'React 웹 애플리케이션',
          snippet: '유사한 React 프로젝트로 TypeScript를 사용합니다.',
        }
      ],
      status: 'PENDING',
      requestedAt: new Date().toISOString(),
    },
    {
      id: 'req-002',
      ownerId: 'user456',
      source: {
        type: 'PLAN',
        files: ['project-plan.docx'],
      },
      extractedText: '2024년 신규 프로젝트 계획서입니다. AI 기반 문서 관리 시스템을 개발할 예정입니다.',
      proposedCategory: {
        majorCode: 'B',
        majorName: 'AI/ML',
        midCode: 'B01',
        midName: '문서 처리',
        subCode: 'B0101',
        subName: 'NLP',
      },
      proposedPurpose: 'AI 문서 관리 시스템 개발 계획',
      similarCandidates: [],
      status: 'PENDING',
      requestedAt: new Date(Date.now() - 86400000).toISOString(),
    }
  ],

  // Chat API Mock
  chatQuery: (query: string): ChatQueryResponse => ({
    answer: `죄송합니다. RAG 채팅 기능은 아직 구현되지 않았습니다. 

현재 질문: "${query}"

이 기능은 백엔드 개발이 완료되면 사용할 수 있습니다. 구현 예정 기능:
- 등록된 문서 검색
- 벡터 유사도 기반 문서 추천
- LLM 기반 답변 생성`,
    sources: [
      {
        docId: 'mock-doc-1',
        serial: 'MOCK-001',
        snippet: '이것은 모의 문서 소스입니다.',
      }
    ],
  }),

  // File API Mock
  presignUrl: (fileName: string): PresignResponse => ({
    fileId: `file-${Date.now()}`,
    uploadUrl: `https://mock-storage.example.com/upload/${fileName}`,
    expiresAt: new Date(Date.now() + 3600000).toISOString(), // 1시간 후
  }),
}

// 미구현 API 호출 시 사용할 에러 메시지
export const NOT_IMPLEMENTED_ERROR = {
  error: 'NOT_IMPLEMENTED',
  message: '이 기능은 아직 구현되지 않았습니다.',
  timestamp: new Date().toISOString(),
  path: '',
}

// Mock API 호출 함수들
export const mockApi = {
  // 승인 요청 목록 조회
  async getApprovalRequests() {
    await new Promise(resolve => setTimeout(resolve, 500)) // 네트워크 지연 시뮬레이션
    return mockApiResponses.getApprovalRequests()
  },

  // 승인 결정
  async approveRequest(id: string, decision: 'APPROVE' | 'REJECT') {
    await new Promise(resolve => setTimeout(resolve, 1000))
    return { 
      success: true, 
      message: `요청 ${id}가 ${decision === 'APPROVE' ? '승인' : '반려'}되었습니다.` 
    }
  },

  // 채팅 쿼리
  async chatQuery(query: string) {
    await new Promise(resolve => setTimeout(resolve, 1500))
    return mockApiResponses.chatQuery(query)
  },

  // 파일 업로드 URL 생성
  async getPresignedUrl(fileName: string) {
    await new Promise(resolve => setTimeout(resolve, 300))
    return mockApiResponses.presignUrl(fileName)
  },

  // 파일 업로드 시뮬레이션
  async uploadFile(url: string, file: File) {
    await new Promise(resolve => setTimeout(resolve, 2000))
    return { success: true, message: '파일이 성공적으로 업로드되었습니다.' }
  },
}
