#!/bin/bash

# ==========================================
# HyoDream Recommendation Logic Test Script
# (Health Goal Matching)
# ==========================================

BASE_URL="http://localhost:8080/api"
USERNAME="healthuser_$(date +%s)"
PASSWORD="password123!"

echo "🔹 [Step 1] Creating a new user ($USERNAME)..."
curl -s -X POST "$BASE_URL/auth/signup" \
     -H "Content-Type: application/json" \
     -d "{
           \"username\": \"$USERNAME\",
           \"password\": \"$PASSWORD\",
           \"name\": \"Health User\",
           \"phone\": \"010-9999-8888\",
           \"birthDate\": \"1985-05-05\",
           \"gender\": \"FEMALE\",
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

TOKEN=$(echo $LOGIN_RES | grep -o '"accessToken":"[^" ]*' | sed 's/"accessToken":"//')

if [ -z "$TOKEN" ]; then
    echo "❌ Login failed."
    exit 1
fi
echo "✅ Logged in. Token obtained."

echo -e "\n🔹 [Step 3] Registering Health Goal: '기억력 개선'..."
# 기대효과(HealthGoal) 등록 (스도쿠 테스트를 위해 변경)
curl -s -X POST "$BASE_URL/user/health" \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d "{
           \"goals\": [\"기억력 개선\"]
         }"
echo -e "\n✅ Health Goal '기억력 개선' registered."

echo -e "\n🔹 [Step 4] Triggering Import: Searching for '스도쿠' (Non-food item)..."
# 검색을 수행하여 '스도쿠' 상품을 가져오고, '기억력 개선' 태그가 자동 생성되게 함
SEARCH_RES=$(curl -s -G "$BASE_URL/products/search" \
    --data-urlencode "keyword=스도쿠" \
    --data-urlencode "page=0" \
    --data-urlencode "size=5" \
    -H "Authorization: Bearer $TOKEN")

SEARCH_COUNT=$(echo $SEARCH_RES | grep -o '"content":[' | wc -l)
echo -e "\n✅ Search executed. (Assuming 'Sudoku' products are now imported with 'Memory' tag)"

echo -e "\n🔹 [Step 5] Checking Recommendations..."
RECOMMEND_RES=$(curl -s -G "$BASE_URL/products/recommend" \
     -H "Authorization: Bearer $TOKEN")

# 결과 확인
REC_COUNT=$(echo $RECOMMEND_RES | grep -o '"id":' | wc -l)

if [ "$REC_COUNT" -gt 0 ]; then
    echo "✅ [PASS] Recommended products returned ($REC_COUNT items)."
    echo "       (Ideally, verify if '스도쿠' or related items are present)"
else
    echo "⚠️ [WARN] No recommendations returned."
    echo "Response: $RECOMMEND_RES"
fi

echo -e "\n🎉 Recommendation Test Completed."
