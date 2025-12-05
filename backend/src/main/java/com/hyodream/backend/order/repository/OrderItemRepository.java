package com.hyodream.backend.order.repository;

import com.hyodream.backend.order.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    // 이 유저(userId)가 주문한 내역(Order) 중에 이 상품(productId)이 포함되어 있는지
    // OrderItem -> Order -> userId 순으로 타고 들어가서 확인함
    @Query("SELECT CASE WHEN COUNT(oi) > 0 THEN true ELSE false END " +
            "FROM OrderItem oi " +
            "JOIN oi.order o " +
            "WHERE o.userId = :userId AND oi.productId = :productId")
    boolean existsByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);

    // 📊 통계 쿼리: 특정 날짜(startDate) 이후에 판매된 상품별 수량 합계 조회
    // 결과: [ [상품ID, 판매수량], [상품ID, 판매수량], ... ]
    @Query("SELECT oi.productId, SUM(oi.count) " +
            "FROM OrderItem oi JOIN oi.order o " +
            "WHERE o.orderDate >= :startDate " +
            "AND o.status = 'ORDER' " + // 취소된 건 제외
            "GROUP BY oi.productId")
    List<Object[]> countSalesByProductSince(@Param("startDate") LocalDateTime startDate);
}