package com.hyodream.backend.user.domain; // 패키지명은 본인 프로젝트에 맞게

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class CoupangTest {

    // ==========================================
    // 여기에 키를 다시 한 번 정확히(재발급 후 저장 필수!) 넣어주세요.
    private static final String ACCESS_KEY = "519b7089-5708-40e9-809d-c5b4866a1679";
    private static final String SECRET_KEY = "2039880ece7259d2b8f53b259627768ebe3a536c";
    private static final String PRODUCT_ID = "9024163013";
    // ==========================================

    public static void main(String[] args) {
        String method = "GET";
        // URL 경로 (도메인 제외)
        String path = "/v2/providers/seller_api/apis/api/v1/marketplace/seller-products/" + PRODUCT_ID;

        try {
            // 1. 시간 생성 (UTC)
            String datetime = ZonedDateTime.now(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("yyMMdd'T'HHmmss'Z'"));

            // 2. 서명할 메시지 조합
            String message = method + path + datetime;

            // 3. HMAC-SHA256 서명 생성
            String signature = generateHmac(SECRET_KEY, message);

            // 4. 헤더 생성
            String authorization = String.format(
                    "CEA algorithm=HmacSHA256, access-key=%s, signed-date=%s, signature=%s",
                    ACCESS_KEY, datetime, signature);

            System.out.println(">>> 생성된 서명 정보");
            System.out.println("시간: " + datetime);
            System.out.println("메시지: " + message);
            System.out.println("Authorization: " + authorization);

            // 5. 요청 보내기 (Java 11+ HttpClient 사용)
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api-gateway.coupang.com" + path))
                    .header("Authorization", authorization)
                    .header("Content-Type", "application/json")
                    .header("X-Requested-By", "MY_VENDOR_ID") // 본인 아이디 임의 입력
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("\n>>> 응답 결과");
            System.out.println("상태 코드: " + response.statusCode());
            System.out.println("응답 본문: " + response.body());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // HMAC 서명 함수
    private static String generateHmac(String secret, String message)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac hmacSha256 = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        hmacSha256.init(secretKeySpec);
        byte[] signatureBytes = hmacSha256.doFinal(message.getBytes(StandardCharsets.UTF_8));

        // Hex String으로 변환
        StringBuilder result = new StringBuilder();
        for (byte b : signatureBytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}