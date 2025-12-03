package com.hyodream.backend.order.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderRequestDto {
    private Long productId;
    private int count;
}