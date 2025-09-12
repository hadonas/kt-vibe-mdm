// MongoDB 초기화 스크립트
// 컬렉션과 인덱스를 생성합니다.

// mdm 데이터베이스로 전환
db = db.getSiblingDB('mdm');

// 데이터베이스가 존재하는지 확인하고 생성
if (!db) {
    db = db.getSiblingDB('mdm');
}

// 사용자 컬렉션 생성
db.createCollection('users');
db.users.createIndex({ "email": 1 }, { unique: true });
db.users.createIndex({ "roles": 1 });

// 수집 요청 컬렉션 생성
db.createCollection('ingest_requests');
db.ingest_requests.createIndex({ "ownerId": 1 });
db.ingest_requests.createIndex({ "status": 1 });
db.ingest_requests.createIndex({ "requestedAt": -1 });
db.ingest_requests.createIndex({ "ownerId": 1, "status": 1 });

// 문서 컬렉션 생성
db.createCollection('documents');
db.documents.createIndex({ "serial.full": 1 }, { unique: true });
db.documents.createIndex({ "ownerId": 1 });
db.documents.createIndex({ "category.subCode": 1 });
db.documents.createIndex({ "purpose": 1 });
db.documents.createIndex({ "tags": 1 });
db.documents.createIndex({ "approvedAt": -1 });

// 카탈로그 노드 컬렉션 생성
db.createCollection('catalog_nodes');
db.catalog_nodes.createIndex({ "code": 1 }, { unique: true });
db.catalog_nodes.createIndex({ "level": 1, "active": 1 });
db.catalog_nodes.createIndex({ "parentCode": 1 });
db.catalog_nodes.createIndex({ "level": 1, "parentCode": 1, "order": 1 });

// 카운터 컬렉션 생성
db.createCollection('counters');
db.counters.createIndex({ "subCode": 1 }, { unique: true });

// 기본 카탈로그 데이터 삽입
db.catalog_nodes.insertMany([
  // 대분류
  { level: 1, code: "A", name: "소프트웨어", parentCode: null, active: true, order: 1 },
  { level: 1, code: "B", name: "하드웨어", parentCode: null, active: true, order: 2 },
  { level: 1, code: "C", name: "서비스", parentCode: null, active: true, order: 3 },
  
  // 중분류 - 소프트웨어
  { level: 2, code: "A01", name: "웹개발", parentCode: "A", active: true, order: 1 },
  { level: 2, code: "A02", name: "모바일앱", parentCode: "A", active: true, order: 2 },
  { level: 2, code: "A03", name: "데스크톱앱", parentCode: "A", active: true, order: 3 },
  
  // 소분류 - 웹개발
  { level: 3, code: "A0101", name: "프론트엔드", parentCode: "A01", active: true, order: 1 },
  { level: 3, code: "A0102", name: "백엔드", parentCode: "A01", active: true, order: 2 },
  { level: 3, code: "A0103", name: "풀스택", parentCode: "A01", active: true, order: 3 },
  
  // 소분류 - 모바일앱
  { level: 3, code: "A0201", name: "iOS", parentCode: "A02", active: true, order: 1 },
  { level: 3, code: "A0202", name: "Android", parentCode: "A02", active: true, order: 2 },
  { level: 3, code: "A0203", name: "크로스플랫폼", parentCode: "A02", active: true, order: 3 }
]);

// 기본 관리자 사용자 생성
db.users.insertOne({
  email: "admin@company.com",
  name: "관리자",
  passwordHash: "$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDi", // "admin123"
  roles: ["ADMIN", "APPROVER", "USER"],
  createdAt: new Date(),
  lastLoginAt: null
});

print("MongoDB 초기화 완료");
print("- 컬렉션 및 인덱스 생성");
print("- 기본 카탈로그 데이터 삽입");
print("- 관리자 계정 생성 (admin@company.com / admin123)");
