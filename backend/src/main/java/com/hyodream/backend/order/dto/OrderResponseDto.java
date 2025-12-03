package com.hyodream.backend.order.dto;

import com.hyodream.backend.order.domain.Order;
import com.hyodream.backend.order.domain.OrderStatus;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.List;

@Getter
public class OrderResponseDto {
    private Long orderId;
    private LocalDateTime orderDate;
    private OrderStatus status;
    private List<OrderItemResponseDto> orderItems; // 주문한 상품들 목록

    public OrderResponseDto(Order order, List<OrderItemResponseDto> orderItems) {
        this.orderId = order.getId();
        this.orderDate = order.getOrderDate();
        this.status = order.getStatus();
        this.orderItems = orderItems;
    }
}