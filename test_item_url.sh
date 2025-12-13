#!/bin/bash

# 색상 설정
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

KEYWORD="피자"

# [1] URL 인코딩 (이 부분은 파이썬이 작동했으므로 그대로 유지)
ENCODED_KEYWORD=$(python3 -c "import urllib.parse; print(urllib.parse.quote('$KEYWORD'))")

API_URL="http://localhost:8080/api/products/search?keyword=${ENCODED_KEYWORD}&size=1"

echo -e "${GREEN}[1] 상품 검색 API 호출 (키워드: ${KEYWORD} -> ${ENCODED_KEYWORD})${NC}"
RESPONSE=$(curl -s -X GET "${API_URL}")

# 응답 확인
if [ -z "$RESPONSE" ]; then
    echo -e "${RED}API 응답이 비어있습니다.${NC}"
    exit 1
fi

# [수정됨] itemUrl 추출 (Python 대신 grep과 cut 사용)
# 설명: "itemUrl":"..." 패턴을 찾아서 따옴표(")를 기준으로 4번째 칸의 데이터를 가져옴
ITEM_URL=$(echo "$RESPONSE" | grep -o '"itemUrl":"[^"]*"' | head -1 | cut -d'"' -f4)

echo -e "추출된 itemUrl: ${ITEM_URL}"

if [ -z "$ITEM_URL" ]; then
    echo -e "${RED}[실패] itemUrl 필드가 비어있거나 찾을 수 없습니다.${NC}"
    echo "Response Snippet: ${RESPONSE:0:100}..." # 응답 앞부분만 출력해서 확인
    exit 1
else
    echo -e "${GREEN}[성공] itemUrl이 정상적으로 반환되었습니다.${NC}"
    echo -e "Link: ${ITEM_URL}"
fi