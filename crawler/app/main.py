import logging  # 1. 로깅 모듈 임포트 (필수)
import traceback

from fastapi import FastAPI
from pydantic import BaseModel
from app.crawler import crawl_product

# 2. 로거 설정 (이 부분이 빠져서 에러가 난 겁니다)
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI()

class CrawlRequest(BaseModel):
    url: str
    max_pages: int = 5

@app.post("/crawl")
def crawl(req: CrawlRequest):
    # 3. 로그 출력
    logger.info(f"👉 [REQUEST] URL: {req.url}") 
    
    try:
        result = crawl_product(req.url, req.max_pages)
        logger.info(f"👈 [RESULT] Data: {result}") # 결과 확인용
        return result
    except Exception as e:
        print(f"❌ 크롤링 에러 발생: {str(e)}")
        traceback.print_exc()
        logger.error(f"❌ [ERROR] During crawling: {e}")
        raise e