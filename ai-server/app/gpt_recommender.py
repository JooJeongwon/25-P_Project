import json
from openai import OpenAI
from fastapi import HTTPException
from app.models import RecommendRequest

client = OpenAI()  # OPENAI_API_KEY는 환경변수로


def recommend_with_gpt(req: RecommendRequest) -> list[int]:
    # 후보 상품 요약 (토큰 절약)
    candidates_text = ""
    for p in req.candidates:
        candidates_text += (
            f"- ID {p.id}: {p.name} "
            f"(효능: {', '.join(p.benefits) if p.benefits else '없음'}, "
            f"알레르기: {', '.join(p.allergens) if p.allergens else '없음'})\n"
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

    response = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[
            {"role": "system", "content": "너는 JSON만 출력하는 추천 엔진이다."},
            {"role": "user", "content": prompt},
        ],
        temperature=0.2
    )

    try:
        content = response.choices[0].message.content
        data = json.loads(content)
        return data["product_ids"]
    except Exception:
        raise HTTPException(status_code=500, detail="GPT 응답 파싱 실패")