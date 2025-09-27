# RAG 검색 시퀀스 다이어그램

## 일반 유저 - RAG 검색 시퀀스 다이어그램

```mermaid
sequenceDiagram
    participant U as 일반 유저
    participant F as 프론트엔드<br/>(Next.js)
    participant CS as Chat Service
    participant E as Elasticsearch
    participant M as MongoDB
    participant AI as AI 서비스<br/>(Ollama/OpenAI)

    Note over U,AI: 일반 유저 RAG 검색 프로세스 (하이브리드 검색)

    U->>F: 1. 검색 쿼리 입력<br/>"React 프로젝트에 대해 알려주세요"
    F->>CS: 2. POST /api/chat/query
    Note right of F: ChatQueryRequest<br/>{query: "React 프로젝트..."}

    CS->>AI: 3. 쿼리 임베딩 생성
    Note right of AI: 텍스트를 벡터로 변환
    AI-->>CS: 4. 임베딩 벡터 반환

    CS->>E: 5. 하이브리드 검색 수행
    Note right of E: BM25 + 벡터 검색<br/>샤드 기반 스코어링
    E-->>CS: 6. 상위 관련 청크들<br/>(documentId + content)

    CS->>M: 7. 문서 메타데이터 조회
    Note right of M: Document 컬렉션에서<br/>계층 카테고리 정보 포함
    M-->>CS: 8. 문서 정보 반환<br/>(제목, 카테고리, 메타데이터)

    CS->>CS: 9. 컨텍스트 구성
    Note right of CS: 검색된 청크들을<br/>가변 계층별로 조합

    CS->>AI: 10. RAG 쿼리 실행
    Note right of AI: 컨텍스트 + 사용자 쿼리로<br/>최종 답변 생성
    AI-->>CS: 11. 생성된 답변 반환

    CS->>CS: 12. 응답 포맷팅
    Note right of CS: 답변 + 참조 문서 정보<br/>+ 계층 카테고리 정보

    CS-->>F: 13. ChatQueryResponse
    Note right of CS: {response: "생성된 답변",<br/>sources: [참조문서들],<br/>categories: [계층정보]}

    F->>F: 14. UI 업데이트
    Note right of F: 채팅 인터페이스에<br/>답변과 계층별 참조 링크

    F-->>U: 15. 검색 결과 표시
    Note right of U: 답변 + 계층별 문서 링크<br/>+ 파일 다운로드 옵션

    U->>F: 17. 추가 질문
    F->>CS: 18. POST /api/chat/query
    Note right of F: 대화 컨텍스트 유지
    CS->>AI: 19. 추가 쿼리 처리
    AI-->>CS: 20. 추가 답변 생성
    CS-->>F: 21. 추가 응답
    F-->>U: 22. 연속 대화 결과
```

## 관리자 유저 - 시스템 관리 시퀀스 다이어그램

```mermaid
sequenceDiagram
    participant A as 관리자 유저
    participant F as 프론트엔드<br/>(Next.js)
    participant AS as Admin Service
    participant DDS as Document Deletion<br/>Service
    participant EIS as Elasticsearch Index<br/>Service
    participant M as MongoDB
    participant E as Elasticsearch
    participant AI as AI 서비스<br/>(Ollama/OpenAI)

    Note over A,AI: 관리자 시스템 관리 프로세스

    A->>F: 1. 관리자 대시보드 접근
    F->>AS: 2. GET /api/admin/documents
    AS->>M: 3. 가변 계층 문서 목록 조회
    M-->>AS: 4. 계층별 문서 목록 반환
    AS-->>F: 5. 가변 계층 문서 관리 목록

    A->>F: 6. 문서 계층 구조 조회
    F->>AS: 7. GET /api/admin/documents/hierarchy
    AS->>M: 8. CatalogNode 계층 구조 조회
    M-->>AS: 9. documentCount 포함 계층 데이터
    AS-->>F: 10. 사용량 통계 포함 계층 구조

    A->>F: 11. 카테고리별 문서 조회
    F->>AS: 12. GET /api/admin/documents/category/{path}
    AS->>M: 13. 특정 계층의 문서 조회
    M-->>AS: 14. 카테고리 문서 목록 반환
    AS-->>F: 15. 카테고리별 문서 목록

    A->>F: 16. 특정 문서 삭제
    F->>AS: 17. DELETE /api/admin/documents/{documentId}
    AS->>DDS: 18. 통합 삭제 서비스 호출
    DDS->>M: 19. MongoDB 문서 삭제
    DDS->>E: 20. Elasticsearch 청크 삭제
    DDS-->>AS: 21. 삭제 결과 반환
    AS-->>F: 22. 삭제 완료 응답
    F-->>A: 23. "문서가 삭제되었습니다" 메시지

    Note over A,AI: Elasticsearch 인덱스 관리

    A->>F: 24. 인덱스 재구성 요청
    F->>EIS: 25. POST /api/admin/elasticsearch/reindex
    EIS->>M: 26. 모든 승인된 문서 조회
    M-->>EIS: 27. 문서 목록 반환

    loop 각 문서에 대해
        EIS->>AI: 28. 문서 청크 임베딩 생성
        AI-->>EIS: 29. 임베딩 벡터 반환
        EIS->>E: 30. Elasticsearch에 인덱싱
    end

    EIS-->>F: 31. 인덱스 재구성 완료
    F-->>A: 32. "인덱스가 업데이트되었습니다" 메시지
```



