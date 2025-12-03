package com.hyodream.backend.order.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "carts")
public class Cart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId; // 누구의 장바구니인가?

    @Column(nullable = false)
    private Long productId; // 어떤 상품인가? (ID만 저장)

    private int count; // 몇 개 담았나?

    // 생성 메서드
    public static Cart createCart(Long userId, Long productId, int count) {
        Cart cart = new Cart();
        cart.setUserId(userId);
        cart.setProductId(productId);
        cart.setCount(count);
        return cart;
    }
}