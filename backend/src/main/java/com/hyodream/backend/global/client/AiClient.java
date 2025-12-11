package com.hyodream.backend.global.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hyodream.backend.user.dto.HealthInfoRequestDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

// name: Feign Client 이름
// url: application.yaml에서 설정된 ai.server.url 사용 (기본값: http://hyodream-ai:8000)
@FeignClient(name = "ai-client", url = "${ai.server.url:http://hyodream-ai:8000}")
public interface AiClient {

    @PostMapping("/recommend-products")
    AiRecommendResponse getRecommendations(@RequestBody HealthInfoRequestDto healthInfo);

    // 응답 DTO (Inner Record)
    record AiRecommendResponse(
        @JsonProperty("product_ids") List<Long> productIds
    ) {}
}