package com.hyodream.backend.product.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(columnDefinition = "TEXT", nullable = true) // 👈 내용 작성은 선택 (null 허용)
    private String content;

    @Enumerated(EnumType.STRING) // 👈 숫자가 아니라 문자열("GOOD")로 저장
    private ReviewRating rating;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}