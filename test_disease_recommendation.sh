#!/bin/bash

# ==========================================
# HyoDream Disease Recommendation Test
# Scenario: User A (Hypertension) buys 'Omega3' -> User B (Hypertension) sees 'Omega3' recommendation
# ==========================================

BASE_URL="http://localhost:8080/api"

echo "🔹 [Step 1] Creating User A (The Buyer)..."
USER_A="userA_$(date +%s)"
curl -s -X POST "$BASE_URL/auth/signup" \
     -H "Content-Type: application/json" \
     -d "{
           \"username\": \"$USER_A\",
           \"password\": \"pass1234\",
           \"name\": \"Buyer Kim\",
           \"phone\": \"010-1111-2222\",
           \"birthDate\": \"1960-01-01\",
           \"gender\": \"MALE\",
           \"role\": \"USER\"
         }"
# Login User A
LOGIN_A=$(curl -s -X POST "$BASE_URL/auth/login" \
     -H "Content-Type: application/json" \
     -d "{ \"username\": \"$USER_A\", \"password\": \"pass1234\" }")
TOKEN_A=$(echo $LOGIN_A | grep -o '"accessToken":"[^" ]*' | sed 's/"accessToken":"//')
echo -e "\n✅ User A Logged in."

echo -e "\n🔹 [Step 2] User A registers '고혈압'..."
curl -s -X POST "$BASE_URL/user/health" \
     -H "Authorization: Bearer $TOKEN_A" \
     -H "Content-Type: application/json" \
     -d "{ \"diseases\": [\"고혈압\"] }"
echo -e "\n✅ User A has 'Hypertension'."

echo -e "\n🔹 [Step 3] User A searches for '오메가3' to find a product ID..."
SEARCH_RES=$(curl -s -G "$BASE_URL/products/search" \
    --data-urlencode "keyword=오메가3" \
    --data-urlencode "page=0" \
    --data-urlencode "size=1" \
    -H "Authorization: Bearer $TOKEN_A")

# Extract Product ID (Assuming response structure: content[0].id)
# This simple grep might fail if JSON is complex, but usually works for simple structures.
PRODUCT_ID=$(echo $SEARCH_RES | grep -o '"id":[0-9]*' | head -1 | sed 's/"id"://')

if [ -z "$PRODUCT_ID" ]; then
    echo "❌ Failed to find product ID for '오메가3'. Import might have failed."
    echo "Response: $SEARCH_RES"
    exit 1
fi
echo -e "\n✅ Found Product ID: $PRODUCT_ID"

echo -e "\n🔹 [Step 4] User A buys Product $PRODUCT_ID..."
curl -s -X POST "$BASE_URL/orders" \
     -H "Authorization: Bearer $TOKEN_A" \
     -H "Content-Type: application/json" \
     -d "[{ \"productId\": $PRODUCT_ID, \"count\": 2 }]"
echo -e "\n✅ Order Placed."

# ==========================================

echo -e "\n🔹 [Step 5] Creating User B (The Receiver)..."
USER_B="userB_$(date +%s)"
curl -s -X POST "$BASE_URL/auth/signup" \
     -H "Content-Type: application/json" \
     -d "{
           \"username\": \"$USER_B\",
           \"password\": \"pass1234\",
           \"name\": \"Receiver Lee\",
           \"phone\": \"010-3333-4444\",
           \"birthDate\": \"1965-01-01\",
           \"gender\": \"FEMALE\",
           \"role\": \"USER\"
         }"
# Login User B
LOGIN_B=$(curl -s -X POST "$BASE_URL/auth/login" \
     -H "Content-Type: application/json" \
     -d "{ \"username\": \"$USER_B\", \"password\": \"pass1234\" }")
TOKEN_B=$(echo $LOGIN_B | grep -o '"accessToken":"[^" ]*' | sed 's/"accessToken":"//')
echo -e "\n✅ User B Logged in."

echo -e "\n🔹 [Step 6] User B registers '고혈압' (Same as User A)..."
curl -s -X POST "$BASE_URL/user/health" \
     -H "Authorization: Bearer $TOKEN_B" \
     -H "Content-Type: application/json" \
     -d "{ \"diseases\": [\"고혈압\"] }"
echo -e "\n✅ User B has 'Hypertension'."

echo -e "\n🔹 [Step 7] Checking Recommendations for User B..."
REC_RES=$(curl -s -G "$BASE_URL/products/recommend" \
     -H "Authorization: Bearer $TOKEN_B")

# Verify if PRODUCT_ID is in the recommendation list
if [[ "$REC_RES" == *"$PRODUCT_ID"* ]]; then
    echo "✅ [PASS] Success! Product $PRODUCT_ID is recommended to User B."
else
    echo "❌ [FAIL] Product $PRODUCT_ID NOT found in recommendations."
    echo "Response: $REC_RES"
fi

echo -e "\n🎉 Disease Recommendation Test Completed."
