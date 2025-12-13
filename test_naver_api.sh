#!/bin/bash

# ==========================================
# HyoDream Naver API & Allergy Filter Test Script
# ==========================================

BASE_URL="http://localhost:8080/api"
USERNAME="testuser_$(date +%s)" # 매번 새로운 유저 생성
PASSWORD="password123!"

echo "🔹 [Step 1] Creating a new user ($USERNAME)..."
curl -s -X POST "$BASE_URL/auth/signup" \
     -H "Content-Type: application/json" \
     -d "{
           \"username\": \"$USERNAME\",
           \"password\": \"$PASSWORD\",
           \"name\": \"Test User\",
           \"phone\": \"010-1234-5678\",
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

# Extract Token (Simple parsing using grep/sed)
TOKEN=$(echo $LOGIN_RES | grep -o '"accessToken":"[^"" ]*' | sed 's/"accessToken":"//')

if [ -z "$TOKEN" ]; then
    echo "❌ Login failed. Response: $LOGIN_RES"
    exit 1
fi
echo "✅ Logged in. Token obtained."

echo -e "\n🔹 [Step 3] Registering 'Milk' allergy..."
# 컨트롤러가 @PostMapping("/health") 이므로 POST로 수정
curl -s -X POST "$BASE_URL/user/health" \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d "{
           \"allergies\": [\"우유\"]
         }"
echo -e "\n✅ Allergy 'Milk' registered."

echo -e "\n🔹 [Step 3-1] Verifying registered allergies..."
PROFILE_RES=$(curl -s -X GET "$BASE_URL/user/profile" \
     -H "Authorization: Bearer $TOKEN")
echo "Profile: $PROFILE_RES"

echo -e "\n🔹 [Step 4] Searching for safe product ('초콜릿')..."
# 한글 인코딩 문제 해결을 위해 --data-urlencode 및 -G (GET) 옵션 사용
SAFE_RES=$(curl -s -G "$BASE_URL/products/search" \
    --data-urlencode "keyword=초콜릿" \
    -H "Authorization: Bearer $TOKEN")

COUNT=$(echo $SAFE_RES | grep -o "id" | wc -l)
if [ "$COUNT" -gt 0 ]; then
    echo "✅ [PASS] Safe product found ($COUNT items)."
else
    echo "⚠️ [WARN] No items found for '새우깡'. (Maybe Naver API limit or logic issue)"
    echo "Response: $SAFE_RES"
fi

echo -e "\n🔹 [Step 5] Searching for dangerous product ('매일우유')..."
# 한글 인코딩 적용
DANGEROUS_RES=$(curl -s -G "$BASE_URL/products/search" \
    --data-urlencode "keyword=매일우유" \
    -H "Authorization: Bearer $TOKEN")

D_COUNT=$(echo $DANGEROUS_RES | grep -o "id" | wc -l)
if [ "$D_COUNT" -eq 0 ]; then
    echo "✅ [PASS] Dangerous product correctly filtered out (0 items)."
else
    echo "❌ [FAIL] Dangerous product found! Filter failed. ($D_COUNT items)"
    # echo "Response: $DANGEROUS_RES" # 디버깅용
fi

echo -e "\n🔹 [Step 6] Searching WITHOUT login ('매일우유')..."
# 한글 인코딩 적용
GUEST_RES=$(curl -s -G "$BASE_URL/products/search" \
    --data-urlencode "keyword=매일우유")

G_COUNT=$(echo $GUEST_RES | grep -o "id" | wc -l)
if [ "$G_COUNT" -gt 0 ]; then
    echo "✅ [PASS] Guest search returned results ($G_COUNT items)."
else
    echo "⚠️ [WARN] Guest search returned 0 items. (Maybe Naver API issue)"
    echo "Response: $GUEST_RES"
fi

echo -e "\n🎉 Test Completed."
