#!/bin/bash

# ==========================================
# HyoDream DB Optimization Test (Upsert)
# ==========================================

BASE_URL="http://localhost:8080/api"
KEYWORD="생수" # 실제 검색 결과를 확인하기 위해 키워드 고정

echo "🔹 [Step 1] Initial Search (Import)..."
# 생수 검색 (5개)
curl -s -G "$BASE_URL/products/search" \
    --data-urlencode "keyword=$KEYWORD" \
    --data-urlencode "page=0" \
    --data-urlencode "size=5" > /dev/null

echo "✅ Import triggered."

# DB에서 개수 확인
LIST_RES=$(curl -s -G "$BASE_URL/products/search" \
    --data-urlencode "keyword=$KEYWORD" \
    --data-urlencode "page=0" \
    --data-urlencode "size=100")

# [수정됨] 따옴표 수정: '"id":' (작은따옴표로 감쌈)
COUNT_1=$(echo "$LIST_RES" | grep -o '"id":' | wc -l)
echo "👉 Initial Count: $COUNT_1"

echo -e "\n🔹 [Step 2] Second Search (Should be Update, NOT Insert)..."
curl -s -G "$BASE_URL/products/search" \
    --data-urlencode "keyword=$KEYWORD" \
    --data-urlencode "page=0" \
    --data-urlencode "size=5" > /dev/null

LIST_RES_2=$(curl -s -G "$BASE_URL/products/search" \
    --data-urlencode "keyword=$KEYWORD" \
    --data-urlencode "page=0" \
    --data-urlencode "size=100")

# [수정됨] 따옴표 수정
COUNT_2=$(echo "$LIST_RES_2" | grep -o '"id":' | wc -l)
echo "👉 Second Count: $COUNT_2"

if [ "$COUNT_1" -eq "$COUNT_2" ]; then
    echo "✅ [PASS] Upsert works! Product count remains same."
else
    echo "⚠️ [WARN] Count changed ($COUNT_1 -> $COUNT_2). Upsert logic might need checking."
fi

echo -e "\n🎉 DB Optimization Test Completed."