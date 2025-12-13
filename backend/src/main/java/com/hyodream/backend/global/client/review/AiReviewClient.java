package com.hyodream.backend.global.client.review;

import com.hyodream.backend.global.client.review.dto.ReviewAnalysisRequestDto;
import com.hyodream.backend.global.client.review.dto.ReviewAnalysisResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "ai-review-client", url = "${ai.review.url}")
public interface AiReviewClient {

    @PostMapping("/analyze")
    ReviewAnalysisResponseDto analyzeReviews(@RequestBody ReviewAnalysisRequestDto request);
}
