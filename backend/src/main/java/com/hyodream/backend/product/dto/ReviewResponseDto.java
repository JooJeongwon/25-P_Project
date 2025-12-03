package com.hyodream.backend.product.dto;

import com.hyodream.backend.product.domain.Review;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
public class ReviewResponseDto {
    private Long id;
    private Long productId;
    private String productName;
    private Long userId;
    private String content;
    private String rating; // "좋아요" 같은 한글 설명을 내려줌
    private String ratingCode; // "GOOD" 같은 코드도 같이 줌 (프론트 처리용)
    private LocalDateTime createdAt;

    public ReviewResponseDto(Review review, String productName) {
        this.id = review.getId();
        this.productId = review.getProductId(); // ID 바로 꺼냄
        this.productName = productName; // 외부에서 주입
        this.userId = review.getUserId();
        this.content = review.getContent();
        this.rating = review.getRating().getDescription();
        this.ratingCode = review.getRating().name();
        this.createdAt = review.getCreatedAt();
    }
}