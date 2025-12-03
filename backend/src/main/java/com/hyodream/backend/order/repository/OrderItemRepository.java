package com.hyodream.backend.order.repository;

import com.hyodream.backend.order.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    // ⭐ "이 유저(userId)가 주문한 내역(Order) 중에 이 상품(productId)이 포함되어 있니?"
    // OrderItem -> Order -> userId 순으로 타고 들어가서 확인함
    @Query("SELECT CASE WHEN COUNT(oi) > 0 THEN true ELSE false END " +
            "FROM OrderItem oi " +
            "JOIN oi.order o " +
            "WHERE o.userId = :userId AND oi.productId = :productId")
    boolean existsByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);
}