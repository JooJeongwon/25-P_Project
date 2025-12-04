package com.hyodream.backend.payment.repository; // 패키지 확인!

import com.hyodream.backend.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    // 주문 번호로 결제 내역 찾기
    Optional<Payment> findByOrderId(Long orderId);
}