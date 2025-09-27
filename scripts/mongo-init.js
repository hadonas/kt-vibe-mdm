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

// 카탈로그 노드 컬렉션 생성 (스마트 분류 시스템)
db.createCollection('catalog_nodes');
db.catalog_nodes.createIndex({ "code": 1 }, { unique: true });
db.catalog_nodes.createIndex({ "level": 1, "active": 1 });
db.catalog_nodes.createIndex({ "parentCode": 1 });
db.catalog_nodes.createIndex({ "level": 1, "parentCode": 1, "order": 1 });

// 스마트 분류용 추가 인덱스
db.catalog_nodes.createIndex({ "active": 1, "vector": 1 }); // 벡터 검색용
db.catalog_nodes.createIndex({ "includeKeywords": 1 }); // 키워드 검색용
db.catalog_nodes.createIndex({ "aliases": 1 }); // 동의어 검색용
db.catalog_nodes.createIndex({ "lastVectorUpdate": -1 }); // 임베딩 업데이트 추적용

// 텍스트 검색 인덱스 (이름, 설명, 동의어, 예시문장)
db.catalog_nodes.createIndex({ 
  "name": "text", 
  "description": "text", 
  "aliases": "text", 
  "examplePhrases": "text" 
}, { name: "category_text_index" });

// 카운터 컬렉션 생성
db.createCollection('counters');
db.counters.createIndex({ "subCode": 1 }, { unique: true });

// 기본 카탈로그 데이터 삽입 (스마트 분류 시스템)
db.catalog_nodes.insertMany([
  // 대분류
  { 
    level: 1, code: "A", name: "소프트웨어", parentCode: null, active: true, order: 1,
    description: "소프트웨어 개발 및 프로그래밍 관련 프로젝트",
    aliases: ["SW", "Software", "프로그램", "앱", "애플리케이션"],
    includeKeywords: ["개발", "프로그래밍", "코딩", "소프트웨어", "앱"],
    excludeKeywords: ["하드웨어", "물리적", "기계"],
    examplePhrases: ["웹 애플리케이션 개발", "모바일 앱 제작", "소프트웨어 아키텍처 설계"],
    scoreWeights: { keyword: 1.0, bm25: 1.0, vector: 1.0, xencoder: 1.0 }
  },
  { 
    level: 1, code: "B", name: "하드웨어", parentCode: null, active: true, order: 2,
    description: "하드웨어 설계 및 물리적 시스템 관련 프로젝트",
    aliases: ["HW", "Hardware", "기계", "장비", "디바이스"],
    includeKeywords: ["하드웨어", "기계", "회로", "칩", "보드"],
    excludeKeywords: ["소프트웨어", "프로그래밍", "코드"],
    examplePhrases: ["PCB 설계", "임베디드 시스템", "하드웨어 프로토타입"],
    scoreWeights: { keyword: 1.0, bm25: 1.0, vector: 1.0, xencoder: 1.0 }
  },
  { 
    level: 1, code: "C", name: "서비스", parentCode: null, active: true, order: 3,
    description: "비즈니스 서비스 및 운영 관련 프로젝트",
    aliases: ["Service", "비즈니스", "운영", "관리"],
    includeKeywords: ["서비스", "비즈니스", "운영", "관리", "프로세스"],
    excludeKeywords: [],
    examplePhrases: ["고객 서비스 개선", "비즈니스 프로세스 최적화"],
    scoreWeights: { keyword: 1.0, bm25: 1.0, vector: 1.0, xencoder: 1.0 }
  },
  
  // 중분류 - 소프트웨어
  { 
    level: 2, code: "A01", name: "웹개발", parentCode: "A", active: true, order: 1,
    description: "웹 애플리케이션 및 웹사이트 개발",
    aliases: ["Web", "웹", "인터넷", "브라우저"],
    includeKeywords: ["웹", "HTML", "CSS", "JavaScript", "React", "Vue", "Angular"],
    excludeKeywords: ["모바일", "데스크톱", "네이티브"],
    examplePhrases: ["React 웹 애플리케이션", "Node.js 백엔드 API", "반응형 웹사이트"],
    scoreWeights: { keyword: 1.2, bm25: 1.0, vector: 1.0, xencoder: 1.0 }
  },
  { 
    level: 2, code: "A02", name: "모바일앱", parentCode: "A", active: true, order: 2,
    description: "모바일 애플리케이션 개발",
    aliases: ["Mobile", "모바일", "앱", "스마트폰"],
    includeKeywords: ["모바일", "앱", "iOS", "Android", "Swift", "Kotlin", "Flutter"],
    excludeKeywords: ["웹", "데스크톱", "브라우저"],
    examplePhrases: ["iOS 네이티브 앱", "Android 애플리케이션", "크로스플랫폼 모바일 앱"],
    scoreWeights: { keyword: 1.2, bm25: 1.0, vector: 1.0, xencoder: 1.0 }
  },
  { 
    level: 2, code: "A03", name: "데스크톱앱", parentCode: "A", active: true, order: 3,
    description: "데스크톱 애플리케이션 개발",
    aliases: ["Desktop", "데스크톱", "PC", "윈도우", "맥"],
    includeKeywords: ["데스크톱", "윈도우", "macOS", "Linux", "Electron", "Qt"],
    excludeKeywords: ["웹", "모바일", "브라우저"],
    examplePhrases: ["Electron 데스크톱 앱", "Qt 네이티브 애플리케이션"],
    scoreWeights: { keyword: 1.0, bm25: 1.0, vector: 1.0, xencoder: 1.0 }
  },
  
  // 소분류 - 웹개발
  { 
    level: 3, code: "A0101", name: "프론트엔드", parentCode: "A01", active: true, order: 1,
    description: "사용자 인터페이스 및 클라이언트 사이드 개발",
    aliases: ["Frontend", "FE", "클라이언트", "UI", "UX"],
    includeKeywords: ["React", "Vue", "Angular", "HTML", "CSS", "JavaScript", "TypeScript"],
    excludeKeywords: ["서버", "백엔드", "데이터베이스"],
    examplePhrases: ["React 컴포넌트 개발", "반응형 UI 구현", "사용자 경험 개선"],
    scoreWeights: { keyword: 1.3, bm25: 1.0, vector: 1.0, xencoder: 1.0 }
  },
  { 
    level: 3, code: "A0102", name: "백엔드", parentCode: "A01", active: true, order: 2,
    description: "서버 사이드 로직 및 API 개발",
    aliases: ["Backend", "BE", "서버", "API", "서버사이드"],
    includeKeywords: ["Node.js", "Spring", "Django", "API", "데이터베이스", "서버"],
    excludeKeywords: ["프론트엔드", "UI", "클라이언트"],
    examplePhrases: ["REST API 개발", "데이터베이스 설계", "서버 아키텍처"],
    scoreWeights: { keyword: 1.3, bm25: 1.0, vector: 1.0, xencoder: 1.0 }
  },
  { 
    level: 3, code: "A0103", name: "풀스택", parentCode: "A01", active: true, order: 3,
    description: "프론트엔드와 백엔드를 모두 포함하는 전체 스택 개발",
    aliases: ["Fullstack", "Full-stack", "전체스택", "올인원"],
    includeKeywords: ["풀스택", "전체", "프론트엔드", "백엔드", "MEAN", "MERN"],
    excludeKeywords: [],
    examplePhrases: ["MERN 스택 웹 애플리케이션", "전체 스택 개발 프로젝트"],
    scoreWeights: { keyword: 1.1, bm25: 1.0, vector: 1.0, xencoder: 1.0 }
  },
  
  // 소분류 - 모바일앱
  { 
    level: 3, code: "A0201", name: "iOS", parentCode: "A02", active: true, order: 1,
    description: "Apple iOS 플랫폼 애플리케이션 개발",
    aliases: ["아이폰", "iPhone", "iPad", "Apple"],
    includeKeywords: ["iOS", "Swift", "Objective-C", "Xcode", "iPhone", "iPad"],
    excludeKeywords: ["Android", "Google", "Java", "Kotlin"],
    examplePhrases: ["Swift로 개발한 iOS 앱", "iPhone 네이티브 애플리케이션"],
    scoreWeights: { keyword: 1.4, bm25: 1.0, vector: 1.0, xencoder: 1.0 }
  },
  { 
    level: 3, code: "A0202", name: "Android", parentCode: "A02", active: true, order: 2,
    description: "Google Android 플랫폼 애플리케이션 개발",
    aliases: ["안드로이드", "구글", "Google"],
    includeKeywords: ["Android", "Kotlin", "Java", "Android Studio", "Google"],
    excludeKeywords: ["iOS", "Swift", "iPhone", "Apple"],
    examplePhrases: ["Kotlin으로 개발한 Android 앱", "안드로이드 네이티브 애플리케이션"],
    scoreWeights: { keyword: 1.4, bm25: 1.0, vector: 1.0, xencoder: 1.0 }
  },
  { 
    level: 3, code: "A0203", name: "크로스플랫폼", parentCode: "A02", active: true, order: 3,
    description: "여러 플랫폼에서 동작하는 모바일 애플리케이션 개발",
    aliases: ["Cross-platform", "멀티플랫폼", "하이브리드"],
    includeKeywords: ["Flutter", "React Native", "Xamarin", "Ionic", "크로스플랫폼"],
    excludeKeywords: ["네이티브", "iOS만", "Android만"],
    examplePhrases: ["Flutter 크로스플랫폼 앱", "React Native 하이브리드 앱"],
    scoreWeights: { keyword: 1.2, bm25: 1.0, vector: 1.0, xencoder: 1.0 }
  }
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
