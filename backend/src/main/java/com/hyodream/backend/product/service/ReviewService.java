package com.hyodream.backend.product.service;

import com.hyodream.backend.order.repository.OrderItemRepository;
import com.hyodream.backend.product.domain.Product;
import com.hyodream.backend.product.domain.Review;
import com.hyodream.backend.product.dto.ReviewRequestDto;
import com.hyodream.backend.product.dto.ReviewResponseDto;
import com.hyodream.backend.product.repository.ProductRepository;
import com.hyodream.backend.product.repository.ReviewRepository;
import com.hyodream.backend.user.domain.User;
import com.hyodream.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    // 1. 리뷰 작성
    @Transactional
    public void createReview(String username, ReviewRequestDto dto) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자 없음"));

        // 상품 존재 확인 (MSA에서는 여기서 ProductClient 호출로 변경됨)
        if (!productRepository.existsById(dto.getProductId())) {
            throw new RuntimeException("존재하지 않는 상품입니다.");
        }

        // 구매 여부 확인
        if (!orderItemRepository.existsByUserIdAndProductId(user.getId(), dto.getProductId())) {
            throw new RuntimeException("상품을 구매한 사용자만 리뷰를 작성할 수 있습니다.");
        }

        // 중복 방지
        if (reviewRepository.existsByUserIdAndProductId(user.getId(), dto.getProductId())) {
            throw new RuntimeException("이미 리뷰를 작성하셨습니다.");
        }

        Review review = new Review();
        review.setUserId(user.getId());
        review.setProductId(dto.getProductId()); // ID만 저장
        review.setContent(dto.getContent());
        review.setRating(dto.getRating());

        reviewRepository.save(review);
    }

    // 2. 상품별 리뷰 조회 (MSA 대비 리팩토링)
    @Transactional(readOnly = true)
    public List<ReviewResponseDto> getReviewsByProductId(Long productId) {
        List<Review> reviews = reviewRepository.findByProductId(productId);

        // 상품 이름 가져오기
        // (MSA 환경에선 여기서 Product Service를 호출해서 이름을 받아옴)
        String productName = productRepository.findById(productId)
                .map(Product::getName)
                .orElse("알 수 없는 상품");

        List<ReviewResponseDto> dtos = new ArrayList<>();
        for (Review review : reviews) {
            dtos.add(new ReviewResponseDto(review, productName));
        }
        return dtos;
    }

    // 3. 리뷰 수정 (New!)
    @Transactional
    public void updateReview(Long reviewId, String username, ReviewRequestDto dto) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("리뷰가 없습니다."));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자 없음"));

        // 본인 확인 (이게 핵심 보안 로직)
        if (!review.getUserId().equals(user.getId())) {
            throw new RuntimeException("작성자만 수정할 수 있습니다.");
        }

        // 내용 수정
        review.setContent(dto.getContent());
        review.setRating(dto.getRating());
        // (JPA 변경 감지로 자동 저장됨)
    }

    // 4. 리뷰 삭제 (New!)
    @Transactional
    public void deleteReview(Long reviewId, String username) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("리뷰가 없습니다."));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자 없음"));

        // 본인 확인
        if (!review.getUserId().equals(user.getId())) {
            throw new RuntimeException("작성자만 삭제할 수 있습니다.");
        }

        reviewRepository.delete(review);
    }

    // 5. 내가 쓴 리뷰 조회 (New!)
    @Transactional(readOnly = true)
    public List<ReviewResponseDto> getMyReviews(String username) {
        // 1. 사용자 찾기 (username -> userId)
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자 없음"));

        // 2. 리뷰 목록 조회
        List<Review> reviews = reviewRepository.findByUserId(user.getId());

        // 3. 상품 이름 채워 넣기 (MSA 방식: ID로 상품 조회)
        List<ReviewResponseDto> dtos = new ArrayList<>();

        for (Review review : reviews) {
            String productName = productRepository.findById(review.getProductId())
                    .map(Product::getName)
                    .orElse("삭제된 상품"); // 상품이 없을 경우 예외처리

            dtos.add(new ReviewResponseDto(review, productName));
        }

        return dtos;
    }
}