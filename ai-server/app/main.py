from fastapi import FastAPI, HTTPException
from dotenv import load_dotenv
from typing import List
import json

from openai import OpenAI

from app.models import (
    RecommendRequest,
    RecommendResponse,
)

# --------------------------------
# 환경변수 로드 (.env)
# --------------------------------
load_dotenv()

# --------------------------------
# OpenAI 클라이언트
# --------------------------------
client = OpenAI()

# --------------------------------
# FastAPI 앱
# --------------------------------
app = FastAPI(
    title="AI Recommendation Server",
    description="백엔드가 전달한 후보 상품 리스트 중 5개를 추천하는 AI 서버",
    version="1.0.0",
)

# --------------------------------
# GPT 추천 로직
# --------------------------------
def recommend_with_gpt(req: RecommendRequest) -> List[int]:
    # 후보 상품 요약 (토큰 절약)
    candidates_text = ""
    for p in req.candidates:
        candidates_text += (
            f"- ID {p.id}: {p.name} "
            f"(효능: {', '.join(p.benefits) if p.benefits else '없음'}, "
            f"알러지: {', '.join(p.allergens) if p.allergens else '없음'})\n"
        )

    prompt = f"""
너는 헬스케어 쇼핑몰의 추천 AI다.

아래 [후보 상품 목록] 중에서
사용자의 건강 상태에 가장 적합한 상품 5개를 선택하라.

[사용자 정보]
- 질병: {', '.join(req.diseases) if req.diseases else '없음'}
- 알레르기: {', '.join(req.allergies) if req.allergies else '없음'}
- 개선 목표: {', '.join(req.goals) if req.goals else '없음'}

[후보 상품 목록]
{candidates_text}

[추천 규칙]
1. 사용자의 알레르기 성분이 포함된 상품은 절대 선택하지 말 것
2. 질병 및 개선 목표와 관련된 효능이 있는 상품을 우선 선택
3. 반드시 후보 상품 목록에 존재하는 ID만 사용할 것
4. 정확히 5개를 선택할 것
5. ID는 중복 없이 반환할 것

[출력 형식(JSON)]
{{"product_ids": [ID1, ID2, ID3, ID4, ID5]}}
"""

    try:
        response = client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[
                {
                    "role": "system",
                    "content": "너는 JSON만 출력하는 추천 엔진이다.",
                },
                {
                    "role": "user",
                    "content": prompt,
                },
            ],
            temperature=0.2,
        )
    except Exception as e:
        raise HTTPException(
            status_code=502,
            detail=f"OpenAI API 호출 실패: {str(e)}",
        )

    try:
        content = response.choices[0].message.content
        data = json.loads(content)

        product_ids = data.get("product_ids")

        if not product_ids:
            raise ValueError("product_ids 필드가 없음")

        if len(product_ids) != 5:
            raise ValueError("GPT가 정확히 5개의 ID를 반환하지 않음")

        if len(set(product_ids)) != 5:
            raise ValueError("중복된 상품 ID가 반환됨")

        return product_ids

    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"GPT 응답 파싱 실패: {str(e)}",
        )

# --------------------------------
# API 엔드포인트
# --------------------------------
@app.post("/recommend", response_model=RecommendResponse)
def recommend(req: RecommendRequest):

    # 1️⃣ 후보 상품 필드 자체가 없는 경우
    if req.candidates is None:
        raise HTTPException(
            status_code=400,
            detail="후보 상품(candidates)이 전달되지 않았습니다."
        )

    # 2️⃣ 후보 상품은 있지만 비어있는 경우
    if len(req.candidates) == 0:
        raise HTTPException(
            status_code=400,
            detail="후보 상품 리스트가 비어 있습니다."
        )

    # 3️⃣ 후보 상품 개수가 5개 미만인 경우
    if len(req.candidates) < 5:
        raise HTTPException(
            status_code=400,
            detail=f"후보 상품은 최소 5개 이상 필요합니다. (전달받은 개수: {len(req.candidates)})"
        )

    product_ids = recommend_with_gpt(req)
    return RecommendResponse(product_ids=product_ids)