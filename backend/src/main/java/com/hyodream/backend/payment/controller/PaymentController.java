package com.hyodream.backend.payment.controller;

import com.hyodream.backend.payment.dto.PaymentResponseDto;
import com.hyodream.backend.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // 특정 주문의 결제 정보 조회 (영수증)
    // GET http://localhost:8080/api/payments/order/{orderId}
    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponseDto> getPaymentInfo(@PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.getPaymentInfo(orderId));
    }
}