package com.hyodream.backend.user.domain;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor // JPA 필수
@AllArgsConstructor
public class Address {
    private String city; // 시/도 (예: 서울특별시)
    private String street; // 도로명 주소
    private String zipcode; // 우편번호
}