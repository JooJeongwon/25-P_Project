#!/bin/bash

# 색상 설정
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}[1] Docker Compose 빌드 및 실행 중...${NC}"
# 백엔드 Dockerfile이 생겼으므로 새로 빌드해야 함
docker-compose up -d --build

echo -e "${GREEN}[2] 서버 시작 대기 중 (30초)...${NC}"
sleep 30

# 로그인 및 세션 ID 발급 (간소화를 위해 하드코딩된 테스트 계정 사용 가정)
# 실제로는 회원가입 -> 로그인 절차를 거쳐야 함.
# 여기서는 AI 서버가 '비로그인' 상태에서도 동작하는지, 혹은 '로그인' 시 동작하는지 확인.
# 현재 로직상 '로그인' 유저에게만 AI 추천이 동작하므로(ProductService 참고),
# 테스트의 완전성을 위해서는 통합 테스트 환경 구성이 필요하지만,
# 우선 컨테이너들이 정상적으로 떴는지 확인하는 'health check' 위주로 진행.

echo -e "${GREEN}[3] 컨테이너 상태 확인${NC}"
docker-compose ps

echo -e "${GREEN}[4] AI 서버 직접 호출 테스트 (curl)${NC}"
# AI 서버 포트가 8000으로 매핑되어 있으므로 로컬에서 호출 가능
curl -X POST http://localhost:8000/recommend-products \
     -H "Content-Type: application/json" \
     -d '{ "diseases": ["고혈압"], "allergies": [], "goals": ["혈압 관리"] }' | python3 -m json.tool

echo -e "${GREEN}[5] 백엔드 Health Check${NC}"
curl -s http://localhost:8080/actuator/health || echo "Actuator not enabled or server not ready"

echo -e "${GREEN}테스트 완료. 로그를 확인하고 문제가 있다면 docker-compose logs -f [서비스명] 으로 디버깅하세요.${NC}"
