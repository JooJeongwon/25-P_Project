package com.hyodream.backend.order.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order; // 어느 주문서에 속하는지

    private Long productId; // 상품 ID (MSA 고려하여 객체 대신 ID)
    private int orderPrice; // 주문 당시 가격 (가격은 변할 수 있으므로 기록해둠)
    private int count; // 주문 수량

    // -- 생성 메서드 --
    public static OrderItem createOrderItem(Long productId, int orderPrice, int count) {
        OrderItem orderItem = new OrderItem();
        orderItem.setProductId(productId);
        orderItem.setOrderPrice(orderPrice);
        orderItem.setCount(count);
        return orderItem;
    }
}