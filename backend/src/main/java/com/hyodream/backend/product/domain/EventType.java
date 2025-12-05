package com.hyodream.backend.product.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EventType {
    CLICK(1.0), // 단순 클릭
    LONG_VIEW(2.0), // 오래 보기 (10초 이상)
    CART(5.0), // 장바구니 담기
    ORDER(10.0); // 주문 완료 (가장 강력한 관심)

    private final double score;
}