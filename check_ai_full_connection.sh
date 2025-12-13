#!/bin/bash

# ==============================================================================
# AI & Crawler Service Connection Check Script
# ==============================================================================
# 이 스크립트는 로컬에서 실행 중인 AI 추천 서버, AI 리뷰 분석 서버, 크롤러 서버,
# 그리고 백엔드와의 연결 상태를 점검합니다.
#
# 전제 조건:
# 1. Docker Compose로 모든 서비스가 실행 중이어야 합니다. (mysql, redis, ai-server 등)
# 2. 백엔드 서버가 실행 중이어야 합니다. (./gradlew bootRun)
# ==============================================================================

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}       HyoDream AI & Crawler Service Connectivity Check      ${NC}"
echo -e "${BLUE}============================================================${NC}"

# 1. AI Recommendation Server (Port 8000)
# ------------------------------------------------------------------------------
echo -e "\n${YELLOW}[1] Checking AI Recommendation Server (Port 8000)...${NC}"
AI_REC_URL="http://localhost:8000/ai/recommend"
AI_REC_PAYLOAD='{
  "diseases": ["고혈압"],
  "allergies": ["땅콩"],
  "goals": ["혈행 개선"],
  "candidates": [
    {"id": 101, "name": "오메가3 프리미엄", "benefits": ["혈행 개선", "눈 건강"], "allergens": [], "category": "식품"},
    {"id": 102, "name": "종합비타민", "benefits": ["면역력", "피로 회복"], "allergens": [], "category": "식품"},
    {"id": 103, "name": "루테인 지아잔틴", "benefits": ["눈 건강"], "allergens": ["대두"], "category": "식품"},
    {"id": 104, "name": "홍삼 스틱", "benefits": ["면역력", "기억력 개선"], "allergens": [], "category": "식품"},
    {"id": 105, "name": "유산균 골드", "benefits": ["장 건강"], "allergens": ["우유"], "category": "식품"}
  ]
}'

AI_REC_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$AI_REC_URL" \
    -H "Content-Type: application/json" \
    -d "$AI_REC_PAYLOAD")

if [ "$AI_REC_RESPONSE" -eq 200 ]; then
    echo -e "${GREEN}✅ AI Recommendation Server is ONLINE (Status: 200 OK)${NC}"
    # 실제 응답 확인
    RESPONSE_BODY=$(curl -s -X POST "$AI_REC_URL" -H "Content-Type: application/json" -d "$AI_REC_PAYLOAD")
    echo "   Response: $RESPONSE_BODY"
else
    echo -e "${RED}❌ AI Recommendation Server FAILED (Status: $AI_REC_RESPONSE)${NC}"
    echo "   Ensure 'docker compose up ai-server' is running."
fi


# 2. AI Review Analysis Server (Port 8001)
# ------------------------------------------------------------------------------
echo -e "\n${YELLOW}[2] Checking AI Review Analysis Server (Port 8001)...${NC}"
AI_REVIEW_URL="http://localhost:8001/analyze"
AI_REVIEW_PAYLOAD='{
  "reviews": [
    "배송도 빠르고 효과도 좋은 것 같아요. 부모님이 좋아하십니다.",
    "생각보다 별로예요. 맛이 너무 없어서 먹기 힘드네요."
  ]
}'

AI_REVIEW_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$AI_REVIEW_URL" \
    -H "Content-Type: application/json" \
    -d "$AI_REVIEW_PAYLOAD")

if [ "$AI_REVIEW_RESPONSE" -eq 200 ]; then
    echo -e "${GREEN}✅ AI Review Server is ONLINE (Status: 200 OK)${NC}"
    RESPONSE_BODY=$(curl -s -X POST "$AI_REVIEW_URL" -H "Content-Type: application/json" -d "$AI_REVIEW_PAYLOAD")
    echo "   Response: $RESPONSE_BODY"
else
    echo -e "${RED}❌ AI Review Server FAILED (Status: $AI_REVIEW_RESPONSE)${NC}"
    echo "   Ensure 'docker compose up ai-review' is running."
fi


# 3. Crawler Server (Port 8002)
# ------------------------------------------------------------------------------
echo -e "\n${YELLOW}[3] Checking Crawler Server (Port 8002)...${NC}"
CRAWLER_URL="http://localhost:8002/crawl"
# 네이버 스마트스토어 예시 URL (안정적인 테스트를 위해 존재하는 상품 URL 사용 권장)
# 여기서는 테스트용으로 유효할 가능성이 높은 URL을 사용하거나, 에러 처리(400/500)가 아닌지 확인합니다.
# 실제 크롤링은 시간이 걸리므로 타임아웃(Max 10s)을 설정합니다.
TARGET_URL="https://smartstore.naver.com/some-shop/products/1234567890" 
# NOTE: 위 URL은 가짜이므로 크롤러 내부 로직에서 에러가 날 수 있으나, 서버 연결 자체는 성공(500 or 200)해야 함.
# 정확한 테스트를 위해 단순 health check용 호출을 시도하거나, 실제 URL을 넣어야 합니다.
# 여기서는 서버가 응답을 주는지(Connection Refused가 아닌지)에 집중합니다.

CRAWLER_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 -X POST "$CRAWLER_URL" \
    -H "Content-Type: application/json" \
    -d "{\"url\": \"$TARGET_URL\", \"max_pages\": 1}")

# 크롤러는 URL이 유효하지 않으면 500이나 400을 뱉을 수 있음. 000(Connection Refused)만 아니면 연결은 된 것.
if [ "$CRAWLER_RESPONSE" -ne 000 ]; then
    echo -e "${GREEN}✅ Crawler Server is REACHABLE (Status: $CRAWLER_RESPONSE)${NC}"
    if [ "$CRAWLER_RESPONSE" -eq 200 ]; then
        echo "   Crawling Success (Fake URL but handled?)"
    else
        echo "   Server responded (likely error due to fake URL, which is expected)."
    fi
else
    echo -e "${RED}❌ Crawler Server FAILED (Connection Refused)${NC}"
    echo "   Ensure 'docker compose up crawler' is running."
fi


# 4. Backend Connection (Port 8080)
# ------------------------------------------------------------------------------
echo -e "\n${YELLOW}[4] Checking Backend Server (Port 8080)...${NC}"
BACKEND_URL="http://localhost:8080/api/products/search?keyword=test&size=1"

BACKEND_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" -X GET "$BACKEND_URL")

if [ "$BACKEND_RESPONSE" -eq 200 ] || [ "$BACKEND_RESPONSE" -eq 401 ]; then
    echo -e "${GREEN}✅ Backend Server is ONLINE (Status: $BACKEND_RESPONSE)${NC}"
else
    echo -e "${RED}❌ Backend Server FAILED (Status: $BACKEND_RESPONSE)${NC}"
    echo "   Ensure './gradlew bootRun' is executed."
fi

echo -e "\n${BLUE}============================================================${NC}"
echo -e "${BLUE}                   Check Complete                           ${NC}"
echo -e "${BLUE}============================================================${NC}"
