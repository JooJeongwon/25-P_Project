package com.hyodream.backend.order.repository;

import com.hyodream.backend.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    // 내 주문 목록 조회 (날짜 내림차순: 최신순)
    List<Order> findAllByUserIdOrderByOrderDateDesc(Long userId);
}