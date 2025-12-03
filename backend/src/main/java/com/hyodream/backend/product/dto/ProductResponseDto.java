package com.hyodream.backend.product.dto;

import com.hyodream.backend.product.domain.Product;
import lombok.Getter;
import java.util.List;

@Getter
public class ProductResponseDto {
    private Long id;
    private String name;
    private int price;
    private String imageUrl;
    private List<String> healthBenefits;

    // 엔티티 -> DTO 변환 생성자
    public ProductResponseDto(Product product) {
        this.id = product.getId();
        this.name = product.getName();
        this.price = product.getPrice();
        this.imageUrl = product.getImageUrl();
        this.healthBenefits = product.getHealthBenefits();
    }
}