#!/bin/bash

# Base URL
API_URL="http://localhost:8080/api"

echo "🔍 1. Searching for products (keyword: 지팡이)..."

# [수정된 부분] 한글 파라미터를 안전하게 보내기 위해 -G 와 --data-urlencode 사용
# -G: GET 요청으로 처리 (데이터를 URL 뒤에 쿼리스트링으로 붙임)
# --data-urlencode: 한글 등을 자동으로 %EC%... 형태로 변환해줌
SEARCH_RESPONSE=$(curl -s -G "$API_URL/products/search" \
  --data-urlencode "keyword=지팡이" \
  -d "page=0" \
  -d "size=1")

echo "Response: $SEARCH_RESPONSE"

# Extract Product ID (using jq if available, otherwise grep/sed)
PRODUCT_ID=$(echo $SEARCH_RESPONSE | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)

if [ -z "$PRODUCT_ID" ]; then
  echo "❌ No product found."
  exit 1
fi

echo "✅ Found Product ID: $PRODUCT_ID"

echo "⏳ 2. Requesting Product Detail (Triggers Crawling + Sentiment Analysis)..."
START_TIME=$(date +%s)
DETAIL_RESPONSE=$(curl -s "$API_URL/products/$PRODUCT_ID")
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo "⏱️ Request took $DURATION seconds."

echo "🔍 3. Checking Result..."
# Check if "rating" or "reviewCount" is updated
REVIEW_COUNT=$(echo $DETAIL_RESPONSE | grep -o '"reviewCount":[0-9]*' | head -1 | cut -d':' -f2)
POSITIVE_PERCENT=$(echo $DETAIL_RESPONSE | grep -o '"positivePercent":[0-9.]*' | head -1 | cut -d':' -f2)

# 화면 출력용으로 너무 길면 자르기
echo "Detail Response Preview: ${DETAIL_RESPONSE:0:200}..." 
echo "------------------------------------------------"
echo "Review Count: $REVIEW_COUNT"
echo "Positive Sentiment: $POSITIVE_PERCENT"

if [ "$REVIEW_COUNT" -gt "0" ] 2>/dev/null; then
  echo "✅ Crawling Success! (Review Count > 0)"
else
  echo "⚠️ Crawling might have failed or no reviews found."
fi

if [ ! -z "$POSITIVE_PERCENT" ]; then
  echo "✅ Sentiment Analysis Success! (Positive Percent found)"
else
  echo "⚠️ Sentiment Analysis might have failed."
fi