package com.hyodream.backend.product.repository;

import com.hyodream.backend.product.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    // 1. 특정 상품의 리뷰 목록 조회
    List<Review> findByProductId(Long productId);

    // 2. 내가 쓴 리뷰 목록 조회 (마이페이지용)
    List<Review> findByUserId(Long userId);

    // 3. 중복 작성 방지용 (이미 썼니?)
    boolean existsByUserIdAndProductId(Long userId, Long productId);
}