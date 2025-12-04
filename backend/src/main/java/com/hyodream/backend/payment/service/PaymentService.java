package com.hyodream.backend.payment.service;

import com.hyodream.backend.payment.domain.Payment;
import com.hyodream.backend.payment.dto.PaymentResponseDto;
import com.hyodream.backend.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    // 결제 처리 (OrderService에서 호출)
    @Transactional
    public void processPayment(Long orderId, int amount, String method) {
        // PG사 연동한다면 로직이 들어갈 자리
        Payment payment = Payment.createPayment(orderId, amount, method);
        paymentRepository.save(payment);
    }

    // 결제 내역 조회 (Controller에서 호출)
    @Transactional(readOnly = true)
    public PaymentResponseDto getPaymentInfo(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("결제 정보가 없습니다."));
        return new PaymentResponseDto(payment);
    }
}