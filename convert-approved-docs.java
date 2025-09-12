// 기존 승인된 문서를 DocumentEntity로 변환하는 스크립트
// MongoDB에서 직접 실행

// 1. 승인된 IngestRequest 조회
db.ingest_requests.find({status: "COMPLETED"}).forEach(function(request) {
    // 2. DocumentEntity 생성
    var document = {
        ownerId: request.ownerId,
        serial: {
            subCode: request.proposedCategory.subCode,
            number: Math.floor(Math.random() * 10000),
            full: request.proposedCategory.subCode + "-" + String(Math.floor(Math.random() * 10000)).padStart(4, '0')
        },
        category: {
            majorCode: request.proposedCategory.majorCode,
            majorName: request.proposedCategory.majorName,
            midCode: request.proposedCategory.midCode,
            midName: request.proposedCategory.midName,
            subCode: request.proposedCategory.subCode,
            subName: request.proposedCategory.subName
        },
        purpose: request.proposedPurpose,
        content: request.extractedText,
        source: {
            type: request.source.type,
            repoUrl: request.source.repoUrl,
            files: request.source.files
        },
        tags: request.tags || [],
        requestedAt: request.requestedAt,
        approvedAt: request.approvedAt,
        version: 1,
        createdAt: new Date(),
        updatedAt: new Date()
    };
    
    // 3. documents 컬렉션에 저장
    db.documents.insertOne(document);
    print("문서 변환 완료: " + request.id + " -> " + document.serial.full);
});
