package com.hyodream.backend.payment.dto;

import com.hyodream.backend.payment.domain.Payment;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
public class PaymentResponseDto {
    private Long paymentId;
    private Long orderId;
    private int amount;
    private String paymentMethod;
    private LocalDateTime paymentDate;

    public PaymentResponseDto(Payment payment) {
        this.paymentId = payment.getId();
        this.orderId = payment.getOrderId();
        this.amount = payment.getAmount();
        this.paymentMethod = payment.getPaymentMethod();
        this.paymentDate = payment.getPaymentDate();
    }
}