package com.hyodream.backend.product.service;

import com.hyodream.backend.order.repository.OrderItemRepository;
import com.hyodream.backend.product.domain.Product;
import com.hyodream.backend.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductScheduler {

    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;

    // 매일 자정(0시 0분 0초)에 실행
    // cron = "초 분 시 일 월 요일"
    // 테스트용: @Scheduled(cron = "0/10 * * * * *") - 10초에 한번
    @Scheduled(cron = "0 0 0 * * *") // 매일 자정
    @Transactional
    public void updateRecentSales() {
        System.out.println("🔄 [스케줄러] 최근 한 달 판매량 업데이트 시작...");

        // 모든 상품의 recentSales를 일단 0으로 초기화 (안 팔린 건 0이어야 하니까)
        List<Product> allProducts = productRepository.findAll();
        for (Product p : allProducts) {
            p.setRecentSales(0);
        }

        // 최근 30일간 판매 데이터 집계
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusDays(30);
        List<Object[]> salesData = orderItemRepository.countSalesByProductSince(oneMonthAgo);

        // 상품 정보 업데이트
        for (Object[] row : salesData) {
            Long productId = (Long) row[0];
            Long countLong = (Long) row[1]; // DB 결과는 Long으로 나옴
            int count = countLong.intValue();

            productRepository.findById(productId).ifPresent(product -> {
                product.setRecentSales(count);
            });
        }

        System.out.println("✅ [스케줄러] 업데이트 완료!");
    }
}