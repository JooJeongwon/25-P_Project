package com.hyodream.backend.product.domain;

import com.hyodream.backend.global.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "products")
public class Product extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 네이버 쇼핑 고유 ID (중복 저장 방지 및 업데이트 키)
    @Column(unique = true)
    private String naverProductId;

    @Column(nullable = false)
    private String name; // 상품명

    @Column(nullable = false)
    private int price; // 가격

    @Enumerated(EnumType.STRING)
    private ProductStatus status = ProductStatus.ON_SALE; // 판매 상태

    @Column(columnDefinition = "TEXT")
    private String description; // 상세 설명

    private String imageUrl; // 이미지 주소

    @Column(columnDefinition = "TEXT")
    private String itemUrl; // 상품 원본 링크 (네이버 쇼핑 상세 페이지)

    // 상세 분류 정보
    private String brand;
    private String maker;
    private String category1;
    private String category2;
    private String category3;
    private String category4;

    // 어르신 맞춤 정보
    private String volume; // 용량 (예: 120정)
    private String sizeInfo; // 알약 크기

    // 전체 누적 판매량
    private int totalSales = 0;

    // 최근 한 달 판매량 (인기순 정렬용)
    private int recentSales = 0;

    // 이 상품의 효능 (AI 추천용 태그)
    // "관절", "당뇨" 같은 단순 문자열 리스트이므로 ElementCollection 사용
    // 별도 테이블(product_benefits)로 저장되지만, Product랑 한 몸 취급 (LifeCycle 같음)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_benefits", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "benefit")
    private List<String> healthBenefits = new ArrayList<>();

    // 알레르기 성분 (예: "땅콩", "우유")
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_allergens", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "allergen")
    private List<String> allergens = new ArrayList<>();

    // 생성 편의 메서드
    public void addBenefit(String benefit) {
        this.healthBenefits.add(benefit);
    }

    public void addAllergen(String allergen) {
        this.allergens.add(allergen);
    }
}