from typing import List, Optional
from pydantic import BaseModel


# 후보 상품 하나
class CandidateProduct(BaseModel):
    id: int
    name: str
    benefits: List[str]
    allergens: List[str]
    category: Optional[str] = None


# 백엔드 → AI 요청 모델
class RecommendRequest(BaseModel):
    diseases: List[str]
    allergies: List[str]
    goals: List[str]
    candidates: List[CandidateProduct]


# AI → 백엔드 응답 모델
class RecommendResponse(BaseModel):
    product_ids: List[int]