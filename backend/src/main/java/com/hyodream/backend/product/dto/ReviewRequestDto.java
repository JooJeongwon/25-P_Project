package com.hyodream.backend.product.dto;

import com.hyodream.backend.product.domain.ReviewRating;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewRequestDto {
    @Schema(description = "리뷰할 상품 ID", example = "100")
    private Long productId;

    @Schema(description = "리뷰 내용", example = "배송도 빠르고 부모님이 좋아하십니다.")
    private String content;

    @Schema(description = "만족도 평가 (GOOD, BAD)", example = "GOOD")
    private ReviewRating rating;
}