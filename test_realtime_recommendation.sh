#!/bin/bash

# ==========================================
# HyoDream Real-time Recommendation Test Script
# Scenario: User searches/clicks 'Lutein' -> Sees 'Eye Health' recommendations
# ==========================================

BASE_URL="http://localhost:8080/api"
USERNAME="realtime_user_$(date +%s)"
PASSWORD="password123!"

echo "🔹 [Step 1] Creating a new user ($USERNAME)..."
curl -s -X POST "$BASE_URL/auth/signup" \
     -H "Content-Type: application/json" \
     -d "{
           \"username\": \"$USERNAME\",
           \"password\": \"$PASSWORD\",
           \"name\": \"Realtime User\",
           \"phone\": \"010-7777-7777\",
           \"birthDate\": \"1980-01-01\",
           \"gender\": \"MALE\",
           \"role\": \"USER\"
         }"

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
echo "✅ Logged in."

# ---------------------------------------------------------
# Action: Search and Click 'Lutein' (루테인)
# ---------------------------------------------------------
KEYWORD="루테인"
echo -e "\n🔹 [Step 3] Searching for '$KEYWORD'..."
SEARCH_RES=$(curl -s -G "$BASE_URL/products/search" \
    --data-urlencode "keyword=$KEYWORD" \
    --data-urlencode "page=0" \
    --data-urlencode "size=5" \
    -H "Authorization: Bearer $TOKEN")

# Extract first product ID
PRODUCT_ID=$(echo $SEARCH_RES | grep -o '"id":[0-9]*' | head -1 | sed 's/"id"://')

if [ -z "$PRODUCT_ID" ]; then
    echo "❌ Failed to find any product for '$KEYWORD'."
    echo "Response: $SEARCH_RES"
    exit 1
fi
echo "✅ Found Product ID: $PRODUCT_ID (Related to '$KEYWORD')"

echo -e "\n🔹 [Step 4] Clicking (Viewing Detail) Product $PRODUCT_ID..."
# 상세 조회를 하면 Redis에 관심사가 기록되어야 함 (EventController Logic)
curl -s -G "$BASE_URL/products/$PRODUCT_ID" \
     -H "Authorization: Bearer $TOKEN" > /dev/null
echo "✅ Product detail viewed."

# [추가] 이벤트 API 호출 (EventController가 별도로 있으므로 명시적 호출 필요)
echo "🔹 [Step 4-1] Sending Event Log (CLICK)..."
curl -s -X POST "$BASE_URL/events/view" \
     -H "Authorization: Bearer $TOKEN" \
     --data "productId=$PRODUCT_ID" \
     --data "type=CLICK"
echo -e "\n✅ Event log sent."

# Redis Stream 처리 등을 위해 잠시 대기 (비동기 처리 가능성)
echo "⏳ Waiting 2 seconds for real-time analysis..."
sleep 2

# ---------------------------------------------------------
# Verification: Check Recommendations
# ---------------------------------------------------------
echo -e "\n🔹 [Step 5] Checking Real-time Recommendations..."
REC_RES=$(curl -s -G "$BASE_URL/products/recommend" \
     -H "Authorization: Bearer $TOKEN")

# Check if 'realTime' section exists and contains data
REALTIME_SECTION=$(echo $REC_RES | grep -o '"realTime":{[^}]*}')

if [[ "$REALTIME_SECTION" != "" && "$REALTIME_SECTION" != '"realTime":null' ]]; then
    echo "✅ [PASS] Real-time recommendation received!"
    echo "   Data: $REALTIME_SECTION"
else
    echo "⚠️ [WARN] Real-time recommendation missing or null."
    echo "   Response: $REC_RES"
    
    # 디버깅: 메인 페이지(page=0) 주입 확인
    echo -e "\n   Trying Main Page Injection Check..."
    MAIN_RES=$(curl -s -G "$BASE_URL/products" \
        --data-urlencode "page=0" \
        -H "Authorization: Bearer $TOKEN")
    
    # 메인 페이지 상단에 '루테인'이나 '눈 건강' 관련 상품이 있는지 확인 (단순 텍스트 매칭)
    if [[ "$MAIN_RES" == *"눈 건강"* || "$MAIN_RES" == *"루테인"* ]]; then
        echo "   ✅ [PASS] Found related keywords in Main Page (Page 0)."
    else
        echo "   ❌ [FAIL] No related products found in Main Page either."
    fi
fi

echo -e "\n🎉 Real-time Test Completed."
