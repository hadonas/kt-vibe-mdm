#!/bin/bash

# MDM 시스템 연결 테스트 스크립트

echo "======================================"
echo "MDM 시스템 연결 테스트"
echo "======================================"

# 컨테이너 상태 확인
echo "1. 컨테이너 상태 확인"
echo "--------------------------------------"
docker-compose ps

echo ""
echo "2. 백엔드 헬스체크"
echo "--------------------------------------"
if curl -f http://localhost:8080/api/actuator/health 2>/dev/null; then
    echo "✅ 백엔드 정상 작동 중"
else
    echo "❌ 백엔드 연결 실패"
    echo "백엔드 로그 확인:"
    docker-compose logs --tail=10 backend
fi

echo ""
echo "3. 프론트엔드 헬스체크"
echo "--------------------------------------"
if curl -f http://localhost:3000/health 2>/dev/null; then
    echo "✅ 프론트엔드 정상 작동 중"
else
    echo "❌ 프론트엔드 연결 실패"
    echo "프론트엔드 로그 확인:"
    docker-compose logs --tail=10 frontend
fi

echo ""
echo "4. MongoDB 연결 확인"
echo "--------------------------------------"
if docker-compose exec -T mongodb mongosh --eval "db.adminCommand('ping')" >/dev/null 2>&1; then
    echo "✅ MongoDB 정상 작동 중"
else
    echo "❌ MongoDB 연결 실패"
fi

echo ""
echo "5. 네트워크 정보"
echo "--------------------------------------"
echo "컨테이너 간 네트워크 정보:"
docker network inspect mdm_mdm-network --format '{{range .Containers}}{{.Name}}: {{.IPv4Address}}{{"\n"}}{{end}}' 2>/dev/null || echo "네트워크 정보를 가져올 수 없습니다."

echo ""
echo "6. 포트 확인"
echo "--------------------------------------"
echo "열린 포트 목록:"
netstat -tlnp 2>/dev/null | grep -E ':(3000|8080|27017)' || echo "netstat 명령어를 사용할 수 없습니다."

echo ""
echo "7. API 테스트"
echo "--------------------------------------"
echo "백엔드 API 테스트 (회원가입 엔드포인트):"
if curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/auth/signup | grep -q "405\|404\|200"; then
    echo "✅ API 엔드포인트 접근 가능"
else
    echo "❌ API 엔드포인트 접근 불가"
fi

echo ""
echo "======================================"
echo "테스트 완료"
echo "======================================"
echo ""
echo "접속 URL:"
echo "- 프론트엔드: http://localhost:3000"
echo "- 백엔드 API: http://localhost:8080/api"
echo "- 백엔드 헬스체크: http://localhost:8080/api/actuator/health"
echo ""
echo "문제 발생 시 확인사항:"
echo "1. 모든 컨테이너가 실행 중인지 확인: docker-compose ps"
echo "2. 로그 확인: docker-compose logs [service-name]"
echo "3. 환경 변수 확인: docker-compose config"
echo "4. 포트 충돌 확인: netstat -tlnp | grep -E ':(3000|8080|27017)'"
