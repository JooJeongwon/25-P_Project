#!/bin/bash

# ==========================================
# HyoDream Integrated Search (Cache-Aside) Test Script
# ==========================================

BASE_URL="http://localhost:8080/api"
USERNAME="search_tester_$(date +%s)"
PASSWORD="password123!"

echo "🔹 [Step 1] Creating a new user ($USERNAME)..."
curl -s -X POST "$BASE_URL/auth/signup" \
     -H "Content-Type: application/json" \
     -d "{
           \"username\": \"$USERNAME\",
           \"password\": \"$PASSWORD\",
           \"name\": \"Search Tester\",
           \"phone\": \"010-1111-2222\",
           \"birthDate\": \"1990-01-01\",
           \"gender\": \"MALE\",
           \"role\": \"USER\"
         }"
echo -e "\n✅ User created."

echo -e "\n🔹 [Step 2] Logging in..."
LOGIN_RES=$(curl -s -X POST "$BASE_URL/auth/login" \
     -H "Content-Type: application/json" \
     -d "{
           \"username\": \"$USERNAME\",
           \"password\": \"$PASSWORD\"
         }")

# [수정됨] grep 정규식에서 충돌나던 작은따옴표 제거 및 파싱 로직 개선
TOKEN=$(echo $LOGIN_RES | grep -o '"accessToken":"[^"]*"' | sed 's/"accessToken":"//' | sed 's/"//')

if [ -z "$TOKEN" ]; then
    echo "❌ Login failed. Response: $LOGIN_RES"
    exit 1
fi
echo "✅ Logged in. Token obtained."

echo -e "\n🔹 [Step 3] Registering 'Milk' allergy..."
curl -s -X POST "$BASE_URL/user/health" \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d "{
           \"allergyNames\": [\"우유\"]
         }"
echo -e "\n✅ Allergy 'Milk' registered."

echo -e "\n🔹 [Step 4] Searching '초코파이' (1st Try - Cache Miss)..."
# 처음이므로 네이버 API를 호출하여 가져와야 함
RES_1=$(curl -s -G "$BASE_URL/products/search" \
    --data-urlencode "keyword=초코파이" \
    --data-urlencode "page=0" \
    --data-urlencode "size=10" \
    -H "Authorization: Bearer $TOKEN")

COUNT_1=$(echo $RES_1 | grep -o "id" | wc -l)
if [ "$COUNT_1" -gt 0 ]; then
    echo "✅ [PASS] 1st Search success ($COUNT_1 items found). Data imported."
else
    # [수정됨] 괄호 관련 문법 오류 방지
    echo "⚠️ [WARN] 1st Search returned 0 items. (Naver API issue or Parse Error)"
    echo "Response: $RES_1"
fi

echo -e "\n🔹 [Step 5] Searching '초코파이' (2nd Try - Cache Hit)..."
# 두 번째이므로 DB에서 바로 가져와야 함
RES_2=$(curl -s -G "$BASE_URL/products/search" \
    --data-urlencode "keyword=초코파이" \
    --data-urlencode "page=0" \
    --data-urlencode "size=10" \
    -H "Authorization: Bearer $TOKEN")

COUNT_2=$(echo $RES_2 | grep -o "id" | wc -l)
if [ "$COUNT_2" -gt 0 ]; then
    echo "✅ [PASS] 2nd Search success ($COUNT_2 items found). Data retrieved from DB."
else
    echo "❌ [FAIL] 2nd Search failed. DB retrieval issue."
fi

echo -e "\n🔹 [Step 6] Searching dangerous product '매일우유'..."
# 우유 알러지가 있으므로, 네이버에서 가져오더라도 결과는 필터링되어야 함
# [수정됨] BASE_BASE_URL 오타 수정 -> BASE_URL
RES_DANGEROUS=$(curl -s -G "$BASE_URL/products/search" \
    --data-urlencode "keyword=매일우유" \
    --data-urlencode "page=0" \
    --data-urlencode "size=10" \
    -H "Authorization: Bearer $TOKEN")

COUNT_D=$(echo $RES_DANGEROUS | grep -o "id" | wc -l)
if [ "$COUNT_D" -eq 0 ]; then
    echo "✅ [PASS] Dangerous product correctly filtered out (0 items)."
else
    echo "❌ [FAIL] Filter failed! Found $COUNT_D items."
    # echo "Response: $RES_DANGEROUS"
fi

echo -e "\n🎉 Integrated Search Test Completed."