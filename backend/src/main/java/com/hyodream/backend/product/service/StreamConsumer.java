package com.hyodream.backend.product.service;

import com.hyodream.backend.product.domain.EventType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StreamConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private final StringRedisTemplate redisTemplate;

    // 스트림에서 메시지가 오면 실행되는 함수
    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        Map<String, String> event = message.getValue();
        String userId = event.get("userId");
        String category = event.get("category"); // 예: "관절염"
        String typeStr = event.get("type"); // "CLICK", "CART" ...

        // 카테고리가 없거나 비어있으면 -> 점수 집계 안 하고 종료 (방어 로직)
        if (category == null || category.trim().isEmpty() || "null".equals(category)) {
            System.out.println("Event Ignored: No Category (UserId: " + userId + ")");
            return;
        }

        // 점수 계산 로직
        double score = 1.0; // 기본값
        try {
            // Enum에서 점수 꺼내오기
            EventType type = EventType.valueOf(typeStr);
            score = type.getScore();
        } catch (Exception e) {
            System.err.println("알 수 없는 이벤트 타입: " + typeStr);
        }

        System.out.println("Event Consumed: " + userId + " / " + category + " / +" + score + "점");

        // Redis ZSet에 점수 누적
        String key = "interest:user:" + userId;
        redisTemplate.opsForZSet().incrementScore(key, category, score);

        // TTL 설정: 36시간 (어르신 맞춤형)
        redisTemplate.expire(key, Duration.ofHours(36));
    }
}