package com.hyodream.backend.order.repository;

import com.hyodream.backend.order.domain.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {
    List<Cart> findByUserId(Long userId); // 내 장바구니 목록 조회

    Optional<Cart> findByUserIdAndProductId(Long userId, Long productId); // 이미 담은 상품인지 확인
}