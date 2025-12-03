package com.hyodream.backend.product.controller;

import com.hyodream.backend.product.dto.ReviewRequestDto;
import com.hyodream.backend.product.dto.ReviewResponseDto;
import com.hyodream.backend.product.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    // 1. 리뷰 작성 (로그인 필수)
    // POST http://localhost:8080/api/reviews
    @PostMapping
    public ResponseEntity<String> createReview(
            @RequestBody ReviewRequestDto dto,
            Authentication authentication // 토큰에서 사용자 정보 꺼내기
    ) {
        reviewService.createReview(authentication.getName(), dto);
        return ResponseEntity.ok("리뷰가 등록되었습니다.");
    }

    // 2. 특정 상품의 리뷰 목록 조회 (누구나 가능)
    // GET http://localhost:8080/api/reviews/products/{productId}
    @GetMapping("/products/{productId}")
    public ResponseEntity<List<ReviewResponseDto>> getProductReviews(@PathVariable Long productId) {
        return ResponseEntity.ok(reviewService.getReviewsByProductId(productId));
        // (서비스 메서드 이름을 getReviewsByProductId로 바꾸는 걸 추천합니다!)
    }

    // 3. 내가 쓴 리뷰 조회 (수정됨)
    // GET http://localhost:8080/api/reviews/my
    @GetMapping("/my")
    public ResponseEntity<List<ReviewResponseDto>> getMyReviews(Authentication authentication) {
        // 서비스 호출
        List<ReviewResponseDto> myReviews = reviewService.getMyReviews(authentication.getName());
        return ResponseEntity.ok(myReviews);
    }

    // 3. 리뷰 수정
    // PUT http://localhost:8080/api/reviews/{reviewId}
    @PutMapping("/{reviewId}")
    public ResponseEntity<String> updateReview(
            @PathVariable Long reviewId,
            @RequestBody ReviewRequestDto dto,
            Authentication auth) {
        reviewService.updateReview(reviewId, auth.getName(), dto);
        return ResponseEntity.ok("리뷰가 수정되었습니다.");
    }

    // 4. 리뷰 삭제
    // DELETE http://localhost:8080/api/reviews/{reviewId}
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<String> deleteReview(
            @PathVariable Long reviewId,
            Authentication auth) {
        reviewService.deleteReview(reviewId, auth.getName());
        return ResponseEntity.ok("리뷰가 삭제되었습니다.");
    }
}