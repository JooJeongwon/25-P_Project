package com.hyodream.backend.product.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class ProductRequestDto {
    private String name; // 상품명
    private int price; // 가격
    private String description; // 설명
    private String imageUrl; // 이미지 URL
    private String volume; // 용량
    private String sizeInfo; // 크기
    private List<String> healthBenefits; // 효능 태그 (예: ["관절", "당뇨"])
}