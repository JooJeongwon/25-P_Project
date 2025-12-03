package com.hyodream.backend.order.dto;

import com.hyodream.backend.order.domain.OrderItem;
import lombok.Getter;

@Getter
public class OrderItemResponseDto {
    private Long productId;
    private String productName; // 상품 이름 (DB 조회해서 채움)
    private int count;
    private int orderPrice; // 구매 당시 가격

    public OrderItemResponseDto(OrderItem orderItem, String productName) {
        this.productId = orderItem.getProductId();
        this.productName = productName;
        this.count = orderItem.getCount();
        this.orderPrice = orderItem.getOrderPrice();
    }
}