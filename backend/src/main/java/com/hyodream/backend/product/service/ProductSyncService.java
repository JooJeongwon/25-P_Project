package com.hyodream.backend.product.service;

import com.hyodream.backend.global.client.crawler.CrawlerClient;
import com.hyodream.backend.global.client.crawler.dto.CrawlerResponseDto;
import com.hyodream.backend.global.client.review.AiReviewClient;
import com.hyodream.backend.global.client.review.dto.ReviewAnalysisRequestDto;
import com.hyodream.backend.global.client.review.dto.ReviewAnalysisResponseDto;
import com.hyodream.backend.product.domain.AnalysisStatus;
import com.hyodream.backend.product.domain.Product;
import com.hyodream.backend.product.domain.ProductDetail;
import com.hyodream.backend.product.dto.ReviewRequestDto;
import com.hyodream.backend.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSyncService {

    private final ProductRepository productRepository;
    private final CrawlerClient crawlerClient;
    private final AiReviewClient aiReviewClient;
    private final ReviewService reviewService;
    private final PlatformTransactionManager transactionManager;

    @Async
    public void updateProductDetailsAsync(Long productId) {
        log.info("🔄 [Async] Starting background synchronization for product ID: {}", productId);

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        String itemUrl = null;

        // 1. [Native Query] DB 레벨에서 원자적으로 상태 선점 시도
        try {
            // 이 쿼리는 락 충돌 없이 DB가 알아서 직렬화함
            // 결과가 1 이상이면 내가 UPDATE/INSERT 성공 (선점)
            // 결과가 0이면 이미 PROGRESS 상태이고 좀비가 아님 (선점 실패)
            int updatedRows = txTemplate.execute(status -> productRepository.startSyncNative(productId));
            
            if (updatedRows > 0) {
                // 선점 성공 -> URL 조회 (이건 단순 조회라 충돌 없음)
                itemUrl = productRepository.findById(productId)
                        .map(Product::getItemUrl)
                        .orElse(null);
                log.info("🏁 [Async] Acquired sync lock for ID: {}", productId);
            } else {
                log.info("✋ [Async] Another thread is already handling ID: {}. Skipping.", productId);
                return;
            }
        } catch (Exception e) {
            log.error("⚠️ [Async] DB Error during sync setup: {}", e.getMessage());
            return;
        }

        if (itemUrl == null || itemUrl.isEmpty()) {
            log.warn("⚠️ Item URL is missing or product not found for ID: {}", productId);
            // 상태를 다시 FAILED 등으로 돌려놓는 게 좋겠지만, 일단 URL 없으면 진행 불가
            return;
        }

        log.info("🌐 Sending request to Crawler for URL: {}", itemUrl);

        try {
            // 2. [No Transaction] 외부 API 요청 (크롤링)
            CrawlerResponseDto crawledData = crawlerClient.crawlProduct(
                    new CrawlerClient.CrawlRequest(itemUrl, 5));

            if (crawledData != null && crawledData.getProduct() != null) {
                // 3. [Transaction] 결과 저장 (여전히 충돌 가능성 있으나 비관적 락으로 방어)
                txTemplate.execute(status -> {
                    finishSyncLogic(productId, crawledData);
                    return null;
                });
            } else {
                log.warn("⚠️ Crawler returned empty data or null.");
                txTemplate.execute(status -> {
                    failSyncLogic(productId);
                    return null;
                });
            }
        } catch (Exception e) {
            log.error("⚠️ [Async] Error during crawling/saving: {}", e.getMessage());
            try {
                txTemplate.execute(status -> {
                    failSyncLogic(productId);
                    return null;
                });
            } catch (Exception ex) {
                log.error("Failed to mark as FAILED", ex);
            }
        }
    }

    // startSyncLogic 제거됨 (Native Query로 대체)

    private void finishSyncLogic(Long productId, CrawlerResponseDto crawledData) {
        // [Critical] 종료 시에도 비관적 락 사용 (데이터 정합성 보장)
        Product product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new RuntimeException("Product not found during finishSync"));

        CrawlerResponseDto.ProductInfo info = crawledData.getProduct();

        if (product.getDetail() == null) {
            product.setDetail(new ProductDetail(product));
        }

        product.getDetail().updateCrawledData(
                info.getOriginalPrice(),
                info.getDiscountRate(),
                info.getSeller(),
                (long) info.getReviewCount(),
                info.getRating()
        );

        // 리뷰 저장 (별도 서비스 호출 - 여기서 예외 발생해도 상품 정보는 저장되도록 try-catch)
        try {
            saveReviews(product, crawledData);
        } catch (Exception e) {
            log.error("⚠️ Review saving failed: {}", e.getMessage());
        }

        product.getDetail().setStatus(AnalysisStatus.COMPLETED);
        productRepository.saveAndFlush(product);
        log.info("✅ [Async] Sync completed for ID: {}", productId);
    }

    private void saveReviews(Product product, CrawlerResponseDto crawledData) {
         List<String> reviewContents = new ArrayList<>();
        if (crawledData.getReviews() != null) {
            for (Map<String, Object> r : crawledData.getReviews()) {
                try {
                    String content = (String) r.getOrDefault("reviewContent", "");
                    if (content == null || content.isBlank()) continue;

                    ReviewRequestDto reviewDto = new ReviewRequestDto();
                    reviewDto.setProductId(product.getId());
                    reviewDto.setExternalReviewId(String.valueOf(r.get("id")));
                    reviewDto.setAuthorName("익명");
                    reviewDto.setContent(content);
                    int score = 0;
                    try {
                        score = Integer.parseInt(String.valueOf(r.getOrDefault("reviewScore", 0)));
                    } catch (NumberFormatException e) { score = 5; }
                    reviewDto.setScore(score);
                    reviewDto.setProductOption((String) r.get("productOptionContent"));

                    reviewService.saveCrawledReview(reviewDto);
                    reviewContents.add(content);
                } catch (Exception e) {
                    log.warn("⚠️ Failed to parse review: {}", e.getMessage());
                }
            }
        }

        // AI 감성 분석
        if (!reviewContents.isEmpty()) {
            try {
                ReviewAnalysisResponseDto sentiment = aiReviewClient.analyzeReviews(
                        new ReviewAnalysisRequestDto(reviewContents));
                product.getDetail().updateSentimentAnalysis(
                        sentiment.getPositivePercent(),
                        sentiment.getNegativePercent(),
                        sentiment.getTotalReviews()
                );
            } catch (Exception e) {
                log.error("⚠️ Sentiment analysis failed: {}", e.getMessage());
            }
        }
    }

    private void failSyncLogic(Long productId) {
        // 실패 처리도 락을 걸고 안전하게 수행
        try {
            Product product = productRepository.findByIdWithLock(productId).orElse(null);
            if (product != null && product.getDetail() != null) {
                if (product.getDetail().getStatus() == AnalysisStatus.COMPLETED) return;
                product.getDetail().setStatus(AnalysisStatus.FAILED);
                productRepository.saveAndFlush(product);
            }
        } catch (Exception e) {
            log.error("Failed to mark as FAILED: {}", e.getMessage());
        }
    }
}