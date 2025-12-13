"""
네이버 스마트스토어 크롤러 (undetected-chromedriver 버전)
FastAPI API 서버에서 호출 가능한 형태로 모듈화됨
"""

import undetected_chromedriver as uc
from selenium.webdriver.common.by import By
import requests
import json
import csv
import re
import time
import random
from typing import Optional, List
from datetime import datetime


def random_delay(min_sec=1.0, max_sec=3.0):
    delay = random.uniform(min_sec, max_sec)
    time.sleep(delay)


def extract_preloaded_state(html: str) -> Optional[dict]:
    pattern = r'window\.__PRELOADED_STATE__\s*=\s*({.*?});?\s*</script>'
    match = re.search(pattern, html, re.DOTALL)
    if match:
        try:
            return json.loads(match.group(1))
        except:
            pass

    pattern2 = r'__PRELOADED_STATE__\s*=\s*(\{.+?\})\s*;?\s*window\.'
    match2 = re.search(pattern2, html, re.DOTALL)
    if match2:
        try:
            return json.loads(match2.group(1))
        except:
            pass

    return None


def parse_product_info(data: dict) -> dict:
    result = {}

    smart_store = data.get("smartStoreV2", {})
    channel = smart_store.get("channel", {})
    result["payReferenceKey"] = channel.get("payReferenceKey", "")

    simple_product = data.get("simpleProductForDetailPage", {}).get("A", {})
    result["productNo"] = simple_product.get("productNo", "")
    result["name"] = simple_product.get("name", "")
    result["price"] = simple_product.get("salePrice", 0)
    result["original_price"] = simple_product.get("originalPrice", 0)
    result["discount_rate"] = simple_product.get("discountRate", 0)

    product_channel = simple_product.get("channel", {})
    result["seller"] = product_channel.get("channelName", "")
    result["channelNo"] = product_channel.get("channelNo", "")

    review_amount = simple_product.get("reviewAmount", {})
    result["review_count"] = review_amount.get("totalReviewCount", 0)
    result["rating"] = review_amount.get("averageReviewScore", 0)

    images = simple_product.get("representativeImageUrls", [])
    if not images:
        images = simple_product.get("productImages", [])
    result["images"] = images

    return result


def human_like_scroll(driver):
    for _ in range(random.randint(2, 4)):
        scroll_amount = random.randint(200, 500)
        driver.execute_script(f"window.scrollBy(0, {scroll_amount});")
        time.sleep(random.uniform(0.3, 0.8))


def fetch_reviews(pay_reference_key: str, product_no: int, page: int = 1, page_size: int = 20):
    url = "https://m.shopping.naver.com/popup/api/v1/contents/reviews/query-pages"

    headers = {
        "accept": "application/json, text/plain, */*",
        "origin": "https://m.shopping.naver.com",
        "user-agent": "Mozilla/5.0",
        "content-type": "application/json",
    }

    payload = {
        "checkoutMerchantNo": int(pay_reference_key),
        "originProductNo": int(product_no),
        "page": page,
        "pageSize": page_size,
        "reviewSearchSortType": "REVIEW_RANKING",
    }

    try:
        response = requests.post(url, headers=headers, json=payload, timeout=15)
        if response.status_code == 200:
            return response.json()
        else:
            return None
    except:
        return None


def fetch_all_reviews(pay_reference_key: str, product_no: int, max_pages=5) -> List[dict]:
    all_reviews = []

    for page in range(1, max_pages + 1):
        result = fetch_reviews(pay_reference_key, product_no, page=page)
        if not result:
            break

        reviews = result.get("contents", [])
        if not reviews:
            break

        all_reviews.extend(reviews)

        if result.get("last"):
            break

        random_delay(0.5, 1.0)

    return all_reviews


def get_product_info(url: str) -> Optional[dict]:
    if url.startswith("https://m."):
        url = url.replace("https://m.smartstore.naver.com", "https://smartstore.naver.com")

    options = uc.ChromeOptions()
    options.add_argument("--lang=ko-KR")
    options.add_argument("--disable-popup-blocking")
    options.add_argument("--window-size=1920,1080")

    driver = None
    try:
        driver = uc.Chrome(options=options, version_main=131)
        driver.set_page_load_timeout(30)

        driver.get(url)
        random_delay(1.5, 3.5)

        if "captcha" in driver.page_source.lower():
            time.sleep(30)

        human_like_scroll(driver)
        html = driver.page_source

        state = extract_preloaded_state(html)
        if state:
            product_info = parse_product_info(state)
            product_info["raw_state"] = state
            return product_info

        return None

    finally:
        if driver:
            driver.quit()


def crawl_product(url: str, max_pages: int = 5):
    """상품 정보 + 리뷰 전체 수집 API용"""
    product = get_product_info(url)
    if not product:
        return {"error": "상품 정보를 가져올 수 없음"}

    pay_key = product.get("payReferenceKey")
    prod_no = product.get("productNo")

    if not pay_key or not prod_no:
        return {"product": product, "reviews": [], "error": "API 호출 키 부족"}

    reviews = fetch_all_reviews(pay_key, prod_no, max_pages=max_pages)

    return {
        "product": product,
        "review_count": len(reviews),
        "reviews": reviews,
    }