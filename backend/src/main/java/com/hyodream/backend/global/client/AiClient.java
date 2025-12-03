package com.hyodream.backend.global.client;

import com.hyodream.backend.user.dto.HealthInfoRequestDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

// name: 그냥 별명
// url: docker-compose에 적힌 AI 서버 컨테이너 이름 ("http://hyodream-ai:8000")
@FeignClient(name = "ai-client", url = "http://hyodream-ai:8000")
public interface AiClient {

    // 파이썬 서버의 API 주소가 "/recommend" 라고 가정
    @PostMapping("/recommend")
    List<Long> getRecommendations(@RequestBody HealthInfoRequestDto healthInfo);
}