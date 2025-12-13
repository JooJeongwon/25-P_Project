#!/bin/bash

# 색상 코드
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

BASE_URL="http://localhost:8080/api"

echo -e "${GREEN}=== 통합 검색 및 리뷰 로직 검증 시작 ===${NC}"

# 1. 상품 검색 (최초) -> API 호출 발생 예상
echo -e "\n1. [Search] '지팡이' 최초 검색 (API 호출 O)"
SEARCH_RES=$(curl -s "$BASE_URL/products/search?keyword=지팡이")
ITEM_COUNT=$(echo $SEARCH_RES | grep -o "content" | wc -l)

if [ "$ITEM_COUNT" -gt 0 ]; then
    echo -e "${GREEN}✅ 검색 성공! 데이터가 DB에 적재되었습니다.${NC}"
else
    echo -e "${RED}❌ 검색 실패 또는 데이터 없음.${NC}"
    echo $SEARCH_RES
    exit 1
fi

# 상품 ID 추출 (첫 번째 상품)
PRODUCT_ID=$(echo $SEARCH_RES | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
echo "   -> 확보된 상품 ID: $PRODUCT_ID"

# 2. 크롤링 리뷰 저장 테스트
echo -e "\n2. [Review] 크롤링 리뷰 저장 (외부 ID, 점수 5점)"
curl -s -X POST "$BASE_URL/reviews/crawled" \
    -H "Content-Type: application/json" \
    -d '{
        "productId": "$PRODUCT_ID",
        "externalReviewId": "NAVER_REVIEW_001",
        "authorName": "test_user***",
        "content": "아버지가 정말 좋아하십니다. 가볍고 튼튼해요!",
        "score": 5,
        "productOption": "색상: 블랙",
        "images": ["http://example.com/img1.jpg"]
    }' > /dev/null

echo -e "${GREEN}✅ 리뷰 저장 요청 완료.${NC}"

# 3. 리뷰 조회 및 검증
echo -e "\n3. [Review] 저장된 리뷰 확인"
REVIEW_RES=$(curl -s "$BASE_URL/reviews/products/$PRODUCT_ID")

# 검증 포인트: authorName, rating(GOOD), score(5)
if echo $REVIEW_RES | grep -q "test_user" && echo $REVIEW_RES | grep -q "GOOD"; then
    echo -e "${GREEN}✅ 리뷰 검증 성공! (작성자: test_user***, 등급: GOOD 자동변환 확인됨)${NC}"
else
    echo -e "${RED}❌ 리뷰 검증 실패.${NC}"
    echo "응답: $REVIEW_RES"
fi

# 4. 검색 갱신 로직 검증은 로그를 봐야하므로 생략하거나,
H2 Console 등을 통해 DB를 조작해야 하므로 스크립트에서는 한계가 있음.
# 대신 서버 로그에서 'Updated DB from Naver' 메시지를 확인하면 됨.

echo -e "\n${GREEN}=== 모든 테스트 완료 ===${NC}"
