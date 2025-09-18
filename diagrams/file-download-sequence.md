# 파일 다운로드 시퀀스 다이어그램

## 일반 유저 - 파일 다운로드 시퀀스 다이어그램

```mermaid
sequenceDiagram
    participant U as 일반 유저
    participant F as 프론트엔드<br/>(Next.js)
    participant PS as Public Service
    participant FS as File Service
    participant M as MongoDB
    participant GridFS as GridFS

    Note over U,GridFS: 일반 유저 파일 다운로드 프로세스

    U->>F: 1. 채팅에서 파일 다운로드 요청
    Note right of U: 파일명: "A0101-001.md"
    F->>PS: 2. GET /api/public/files/download-by-code
    Note right of F: code: "A0101-001"

    PS->>M: 3. 문서 정보 조회
    Note right of M: serial로 Document 검색
    M-->>PS: 4. 문서 정보 반환<br/>(filePath 포함)

    PS->>FS: 5. 파일 다운로드 요청
    Note right of PS: filePath 전달
    FS->>GridFS: 6. 파일 조회
    GridFS-->>FS: 7. 파일 데이터 반환

    FS->>FS: 8. Content-Type 결정
    FS->>FS: 9. Content-Disposition 헤더 설정
    Note right of FS: filename="원본파일명.pdf"

    FS-->>PS: 10. 파일 리소스 반환
    PS-->>F: 11. 파일 다운로드 응답
    Note right of PS: Content-Disposition 헤더 포함

    F->>F: 12. 파일명 추출
    Note right of F: Content-Disposition에서<br/>실제 파일명 추출

    F-->>U: 13. 파일 다운로드 실행
    Note right of U: 올바른 파일명으로<br/>다운로드 완료
```

## 관리자 유저 - 파일 관리 시퀀스 다이어그램

```mermaid
sequenceDiagram
    participant A as 관리자 유저
    participant F as 프론트엔드<br/>(Next.js)
    participant AS as Admin Service
    participant FS as File Service
    participant M as MongoDB
    participant GridFS as GridFS

    Note over A,GridFS: 관리자 파일 관리 프로세스

    A->>F: 1. 관리자 파일 관리 페이지
    F->>AS: 2. GET /api/admin/files/hierarchy
    AS->>M: 3. 파일 계층 구조 조회
    M-->>AS: 4. 파일 목록 반환
    AS-->>F: 5. 파일 계층 구조

    A->>F: 6. 특정 파일 다운로드
    F->>AS: 7. GET /api/admin/files/download
    Note right of F: filePath 전달
    AS->>FS: 8. 파일 다운로드 요청
    FS->>GridFS: 9. 파일 조회
    GridFS-->>FS: 10. 파일 데이터 반환
    FS-->>AS: 11. 파일 리소스 반환
    AS-->>F: 12. 파일 다운로드 응답
    F-->>A: 13. 파일 다운로드 실행

    A->>F: 14. 카테고리별 파일 조회
    F->>AS: 15. GET /api/admin/files/category/{majorCode}/{midCode}/{subCode}
    AS->>M: 16. 카테고리별 파일 조회
    M-->>AS: 17. 파일 목록 반환
    AS-->>F: 18. 카테고리별 파일 목록
    F-->>A: 19. 파일 목록 표시
```



