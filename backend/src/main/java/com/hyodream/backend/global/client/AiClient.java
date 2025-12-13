package com.hyodream.backend.global.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hyodream.backend.product.dto.AiRecommendationRequestDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "ai-client", url = "${ai.server.url}")
public interface AiClient {

    // 추천 시스템
    @PostMapping("/recommend")
    AiRecommendResponse getRecommendations(@RequestBody AiRecommendationRequestDto request);

    record AiRecommendResponse(@JsonProperty("product_ids") List<Long> productIds) {
    }
}
