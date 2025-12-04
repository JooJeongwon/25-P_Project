package com.hyodream.backend.payment.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId; // 주문 ID (객체 참조 X)

    private int amount; // 결제 금액

    private String paymentMethod; // CARD, CASH, KAKAO_PAY 등

    private LocalDateTime paymentDate;

    // 생성 메서드
    public static Payment createPayment(Long orderId, int amount, String method) {
        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setAmount(amount);
        payment.setPaymentMethod(method);
        payment.setPaymentDate(LocalDateTime.now());
        return payment;
    }
}