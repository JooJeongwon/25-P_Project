package com.hyodream.backend.product.dto;

import com.hyodream.backend.product.domain.ReviewRating;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewRequestDto {
    private Long productId;
    private String content; // 선택사항이라 @NotBlank 안 붙임
    private ReviewRating rating; // "GOOD", "AVERAGE", "BAD" 중 하나
}