# 🛒 효드림 (HyoDream) Backend

## 🛠️ 실행 방법
1. Docker Desktop을 키기
2. 터미널에서 `docker compose up -d`를 입력해 DB와 Redis를 띄우기
3. `backend` 폴더에서 `./gradlew bootRun`으로 서버를 실행

## ⚠️ 주의사항 (필독)
* **Redis 연결 오류 시:** `application.yml`의 `spring.data.redis.host` 값을 본인 Docker Redis 컨테이너 IP로 변경해야 함
* **API 문서:** 서버 실행 후 `http://localhost:8080/swagger-ui/index.html` 접속