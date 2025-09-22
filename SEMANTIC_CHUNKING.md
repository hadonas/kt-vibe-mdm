# Semantic Chunking

문서 본문을 의미 단위 청크로 분할하여 MongoDB(`document_chunks` 컬렉션)와 Elasticsearch(`mdm_document_chunks` 인덱스)에 저장합니다. 각 청크는 1536차원 임베딩을 포함하며 RAG / 검색 품질을 향상합니다.

## 구성 요소
- `DocumentChunk` 엔티티: 원본 문서 ID, chunkIndex, content, embedding 등 보관
- `SemanticChunker`: 섹션 헤더/문장 단위로 의미 기반 분할
- `ChunkIngestionService`: 배치 임베딩 생성 후 MongoDB 저장 + ES 색인 API 사용 가능
- `ElasticsearchIndexService`: `mdm_document_chunks` 인덱스 생성 및 bulk 색인
- `ChunkMigrationService`: 기존 문서를 재처리 (옵션)

## 설정
`application.yml` 또는 환경 변수
```
spring:
  semantic-chunk:
    enabled: ${SEMANTIC_CHUNK_ENABLED:true}
    migrate-on-start: ${SEMANTIC_CHUNK_MIGRATE_ON_START:false}
```

env.example 에 추가:
```
SEMANTIC_CHUNK_ENABLED=true
SEMANTIC_CHUNK_MIGRATE_ON_START=false
```

## 동작 흐름
1. 승인(Approval) 시 `ApprovalService` -> `ChunkIngestionService.chunkAndPersist` 호출
2. 생성된 청크 MongoDB 저장
3. `ElasticsearchIndexService.indexChunks` 로 ES에 dense_vector 포함 색인
4. (선택) 시작 시 migration 실행

## 마이그레이션 실행 수동 트리거
앱 기동 옵션:
```
SEMANTIC_CHUNK_ENABLED=true SEMANTIC_CHUNK_MIGRATE_ON_START=true ./gradlew bootRun
```
또는 운영 중 수동 스크립트(간단 REST 추가 고려 가능) 작성.

## 검색 활용
향후 검색 시:
1. 쿼리 임베딩 생성
2. `mdm_document_chunks` 인덱스 knn 쿼리 (topK)
3. 상위 청크들의 `documentId` 로 메타데이터 집계 후 응답

## 추가 개선 아이디어
- 토큰 길이 정확 측정(OpenAI tiktoken 등) 적용
- 청크 중요도 점수 계산(제목/표/목차 가중치)
- 하이브리드 BM25 + Vector rerank
- 청크 합성(summarization) 캐시
