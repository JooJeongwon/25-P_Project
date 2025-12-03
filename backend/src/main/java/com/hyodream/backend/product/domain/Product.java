package com.hyodream.backend.product.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // 상품명

    @Column(nullable = false)
    private int price; // 가격

    @Column(columnDefinition = "TEXT")
    private String description; // 상세 설명

    private String imageUrl; // 이미지 주소

    // 어르신 맞춤 정보
    private String volume; // 용량 (예: 120정)
    private String sizeInfo; // 알약 크기

    // ⭐ 이 상품의 효능 (AI 추천용 태그)
    // "관절", "당뇨" 같은 단순 문자열 리스트이므로 ElementCollection 사용
    // 별도 테이블(product_benefits)로 저장되지만, Product랑 한 몸 취급 (LifeCycle 같음)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_benefits", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "benefit")
    private List<String> healthBenefits = new ArrayList<>();

    // 생성 편의 메서드
    public void addBenefit(String benefit) {
        this.healthBenefits.add(benefit);
    }
}