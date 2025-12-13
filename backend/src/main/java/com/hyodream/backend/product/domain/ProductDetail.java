package com.hyodream.backend.product.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "product_details")
public class ProductDetail {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId 
    @JoinColumn(name = "product_id")
    private Product product;

    // --- 상세 정보 ---
    private int originalPrice;
    private int discountRate;
    private String seller;
    
    // 기본 통계
    private long reviewCount = 0;
    private double averageRating = 0.0;

    // --- [New] AI 감성 분석 결과 ---
    private double positiveRatio = 0.0; // 긍정 비율 (%)
    private double negativeRatio = 0.0; // 부정 비율 (%)
    private int analyzedReviewCount = 0; // 분석에 사용된 리뷰 수 (갱신 여부 판단용)
    
    // 마지막 크롤링 시간
    private LocalDateTime lastCrawledAt;

    @Enumerated(EnumType.STRING)
    private AnalysisStatus status = AnalysisStatus.NONE;

    public ProductDetail(Product product) {
        this.product = product;
        this.productId = product.getId();
        this.status = AnalysisStatus.NONE;
    }

    public void updateCrawledData(int originalPrice, int discountRate, String seller, long reviewCount, double averageRating) {
        this.originalPrice = originalPrice;
        this.discountRate = discountRate;
        this.seller = seller;
        this.reviewCount = reviewCount;
        this.averageRating = averageRating;
        this.lastCrawledAt = LocalDateTime.now();
        // 크롤링만 성공해도 일단 완료로 처리 (감성 분석은 후속 작업)
        // 감성 분석까지 포함한다면 ProductSyncService에서 제어하는 것이 더 좋음
    }

    // 감성 분석 결과 업데이트 메서드
    public void updateSentimentAnalysis(double positiveRatio, double negativeRatio, int analyzedReviewCount) {
        this.positiveRatio = positiveRatio;
        this.negativeRatio = negativeRatio;
        this.analyzedReviewCount = analyzedReviewCount;
    }

    public void setStatus(AnalysisStatus status) {
        this.status = status;
    }
}
