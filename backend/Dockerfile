# 1. 빌드 단계 (Builder)
# build.gradle에 Java 21로 설정되어 있으므로 openjdk:21 사용
FROM openjdk:21-jdk-slim AS builder
WORKDIR /app

# Gradle 캐싱을 위해 설정 파일들만 먼저 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# 실행 권한 부여 (윈도우에서 작성 시 발생할 수 있는 줄바꿈 문제 해결 포함)
RUN chmod +x ./gradlew

# 소스 코드 복사
COPY src src

# 빌드 수행 (테스트는 스킵하여 속도 향상)
RUN ./gradlew bootJar -x test

# 2. 실행 단계 (Runtime)
# 실행 환경도 Java 21 필수
FROM openjdk:21-jdk-slim
WORKDIR /app

# 빌드 단계에서 생성된 JAR 파일 복사
# build.gradle의 version이 '0.0.1-SNAPSHOT'이므로 패턴 매칭됨
COPY --from=builder /app/build/libs/*-SNAPSHOT.jar app.jar

# Docker Compose의 포트와 일치
EXPOSE 8080

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]