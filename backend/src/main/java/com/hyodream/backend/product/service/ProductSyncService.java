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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Async
    @Transactional
    public void updateProductDetailsAsync(Long productId) {
        log.info("🔄 [Async] Starting background synchronization for product ID: {}", productId);

        try {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("상품이 없습니다."));

            if (product.getItemUrl() == null || product.getItemUrl().isEmpty()) {
                log.warn("⚠️ Item URL is missing for product ID: {}", productId);
                if (product.getDetail() != null) {
                    product.getDetail().setStatus(AnalysisStatus.FAILED);
                }
                return;
            }

            // 1. Crawler 서비스에 요청
            CrawlerResponseDto crawledData = crawlerClient.crawlProduct(
                    new CrawlerClient.CrawlRequest(product.getItemUrl(), 5));

            if (crawledData != null && crawledData.getProduct() != null) {
                CrawlerResponseDto.ProductInfo info = crawledData.getProduct();

                // 2. 상품 상세 정보 업데이트 (트랜잭션 내에서 수행되므로 즉시 반영)
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
                
                // 3. 리뷰 데이터 저장 & 텍스트 수집
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
                            // 숫자로 변환 시도
                            int score = 0;
                            try {
                                score = Integer.parseInt(String.valueOf(r.getOrDefault("reviewScore", 0)));
                            } catch (NumberFormatException e) {
                                score = 5; // 기본값
                            }
                            reviewDto.setScore(score);
                            reviewDto.setProductOption((String) r.get("productOptionContent"));

                            reviewService.saveCrawledReview(reviewDto);
                            reviewContents.add(content);
                        } catch (Exception e) {
                            log.warn("⚠️ Failed to parse review: {}", e.getMessage());
                        }
                    }
                }

                // 4. AI 감성 분석 요청
                if (!reviewContents.isEmpty()) {
                    try {
                        log.info("🧠 Requesting sentiment analysis for {} reviews...", reviewContents.size());
                        ReviewAnalysisResponseDto sentiment = aiReviewClient.analyzeReviews(
                                new ReviewAnalysisRequestDto(reviewContents));

                        product.getDetail().updateSentimentAnalysis(
                                sentiment.getPositivePercent(),
                                sentiment.getNegativePercent(),
                                sentiment.getTotalReviews()
                        );
                        log.info("✅ Sentiment analysis updated: Pos={}%, Neg={}%",
                                sentiment.getPositivePercent(), sentiment.getNegativePercent());
                    } catch (Exception e) {
                        log.error("⚠️ Sentiment analysis failed: {}", e.getMessage());
                    }
                }
                
                // [New] 모든 작업 완료 후 상태 변경
                product.getDetail().setStatus(AnalysisStatus.COMPLETED);
                productRepository.save(product);

                log.info("✅ [Async] Product synchronization completed for ID: {}", productId);
            } else {
                // 크롤링 실패 시
                product.getDetail().setStatus(AnalysisStatus.FAILED);
                productRepository.save(product);
            }
        } catch (Exception e) {
            log.error("⚠️ [Async] Failed to sync product details (ID: {}): {}", productId, e.getMessage());
            // 예외 발생 시 상태를 FAILED로 변경
            try {
                productRepository.findById(productId).ifPresent(p -> {
                    if (p.getDetail() != null) {
                        p.getDetail().setStatus(AnalysisStatus.FAILED);
                        productRepository.save(p);
                    }
                });
            } catch (Exception ex) {
                log.error("Failed to update status to FAILED: {}", ex.getMessage());
            }
        }
    }
}
