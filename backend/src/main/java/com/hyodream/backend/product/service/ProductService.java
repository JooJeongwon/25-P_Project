package com.hyodream.backend.product.service;

import com.hyodream.backend.global.client.AiClient;
import com.hyodream.backend.global.client.crawler.CrawlerClient;
import com.hyodream.backend.global.client.crawler.dto.CrawlerResponseDto;
import com.hyodream.backend.global.client.review.AiReviewClient;
import com.hyodream.backend.global.client.review.dto.ReviewAnalysisRequestDto;
import com.hyodream.backend.global.client.review.dto.ReviewAnalysisResponseDto;
import com.hyodream.backend.product.domain.AnalysisStatus;
import com.hyodream.backend.product.domain.Product;
import com.hyodream.backend.product.domain.ProductDetail;
import com.hyodream.backend.product.domain.SearchLog;
import com.hyodream.backend.product.dto.AiProductDetailDto;
import com.hyodream.backend.product.dto.AiRecommendationRequestDto;
import com.hyodream.backend.product.dto.ProductRequestDto;
import com.hyodream.backend.product.dto.ProductResponseDto;
import com.hyodream.backend.product.dto.ReviewRequestDto;
import com.hyodream.backend.product.naver.service.NaverShoppingService;
import com.hyodream.backend.product.repository.ProductRepository;
import com.hyodream.backend.product.repository.SearchLogRepository;
import com.hyodream.backend.user.domain.User;
import com.hyodream.backend.user.dto.HealthInfoRequestDto;
import com.hyodream.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final SearchLogRepository searchLogRepository;
    private final NaverShoppingService naverShoppingService;
    private final AiClient aiClient; // Recommendation
    private final ProductSyncService productSyncService; // Async Sync Service

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final EntityManager entityManager;

    // 상품 등록 (관리자용)
    @Transactional
    public void createProduct(ProductRequestDto dto) {
        Product product = new Product();
        product.setName(dto.getName());
        product.setPrice(dto.getPrice());
        product.setDescription(dto.getDescription());
        product.setImageUrl(dto.getImageUrl());
        product.setItemUrl(dto.getItemUrl());
        product.setBrand(dto.getBrand());
        product.setMaker(dto.getMaker());
        product.setCategory1(dto.getCategory1());
        product.setCategory2(dto.getCategory2());
        product.setCategory3(dto.getCategory3());
        product.setCategory4(dto.getCategory4());
        product.setVolume(dto.getVolume());
        product.setSizeInfo(dto.getSizeInfo());

        if (dto.getHealthBenefits() != null) {
            for (String benefit : dto.getHealthBenefits()) {
                product.addBenefit(benefit);
            }
        }
        if (dto.getAllergens() != null) {
            for (String allergen : dto.getAllergens()) {
                product.addAllergen(allergen);
            }
        }
        productRepository.save(product);
    }

    // 전체 상품 목록 조회
    @Transactional(readOnly = true)
    public Page<ProductResponseDto> getAllProducts(int page, int size, String sort, String identifier) {
        Sort sortCondition = Sort.by("recentSales").descending().and(Sort.by("id").descending());
        if ("latest".equals(sort)) {
            sortCondition = Sort.by("id").descending();
        }

        boolean isLogin = false;
        List<String> userAllergies = new ArrayList<>();
        if (identifier != null && !identifier.equals("unknown") && !identifier.startsWith("session:")) {
            userRepository.findByUsername(identifier).ifPresent(user -> {
                user.getAllergies().forEach(ua -> userAllergies.add(ua.getAllergy().getName()));
            });
            if (!userAllergies.isEmpty()) isLogin = true;
        }
        if (userAllergies.isEmpty()) userAllergies.add("NONE");

        Pageable pageable = PageRequest.of(page, size, sortCondition);
        Page<Product> productPage = productRepository.findAllWithPersonalization(isLogin, userAllergies, pageable);

        List<Product> originalList = productPage.getContent();
        List<Product> resultList = new ArrayList<>(originalList);

        if (page == 0 && identifier != null && !identifier.equals("unknown")) {
            String redisKey = "interest:user:" + identifier;
            Set<String> topInterests = redisTemplate.opsForZSet().reverseRange(redisKey, 0, 0);

            if (topInterests != null && !topInterests.isEmpty()) {
                String interestCategory = topInterests.iterator().next();
                List<Product> interestProducts = productRepository
                        .findByKeywordInBenefitsOrCategories(interestCategory);

                if (interestProducts.size() > 3) {
                    interestProducts = interestProducts.subList(0, 3);
                }
                for (int i = interestProducts.size() - 1; i >= 0; i--) {
                    resultList.add(0, interestProducts.get(i));
                }
            }
        }

        List<Long> addedIds = new ArrayList<>();
        List<ProductResponseDto> finalDtos = new ArrayList<>();
        for (Product p : resultList) {
            if (!addedIds.contains(p.getId())) {
                finalDtos.add(new ProductResponseDto(p));
                addedIds.add(p.getId());
            }
            if (finalDtos.size() >= size) break;
        }
        return new PageImpl<>(finalDtos, pageable, productPage.getTotalElements());
    }

    // [Modified] 상품 상세 조회 (비동기 크롤링 적용)
    @Transactional(readOnly = true)
    public ProductResponseDto getProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("상품이 없습니다."));

        // [Optimistic Lock Fix] 메인 스레드에서는 절대 '저장(save)'을 수행하지 않음.
        // ProductDetail이 없더라도 여기서 생성하지 않고 비동기 서비스에 위임함.
        // 단순히 조회만 하므로 낙관적 락 충돌이 발생하지 않음.

        ProductDetail detailEntity = product.getDetail();
        
        // 1. 크롤링 갱신 체크 (마지막 갱신으로부터 3일 지났거나, 상세 정보가 아예 없는 경우)
        boolean needUpdate = false;
        if (detailEntity == null) {
            needUpdate = true;
        } else {
            if (detailEntity.getLastCrawledAt() == null) {
                needUpdate = true;
            } else if (detailEntity.getLastCrawledAt().isBefore(LocalDateTime.now().minusDays(3))) {
                needUpdate = true;
            }
            // 이미 진행 중이면 중복 요청 방지
            if (detailEntity.getStatus() == AnalysisStatus.PROGRESS) {
                needUpdate = false;
            }
        }

        if (needUpdate && product.getItemUrl() != null && !product.getItemUrl().isEmpty()) {
            // 2. [Async] 비동기로 데이터 갱신 요청
            try {
                // DB 저장을 제거하고 비동기 서비스에 위임 (낙관적 락 방지)
                productSyncService.updateProductDetailsAsync(product.getId());
                log.info("🚀 Triggered async product sync for ID: {}", id);
            } catch (Exception e) {
                log.error("Failed to trigger async sync: {}", e.getMessage());
            }
        }

        // 3. 현재 DB에 있는 데이터 즉시 반환 (단, 갱신 요청 시 DTO에는 PROGRESS로 표기)
        ProductResponseDto responseDto = new ProductResponseDto(product);
        if (needUpdate) {
            // detail이 없거나 갱신이 필요하면 PROGRESS 상태로 응답
            responseDto.setAnalysisStatus(AnalysisStatus.PROGRESS);
        }
        return responseDto;
    }


    // AI + 실시간 + 유저 기대효과 하이브리드 추천
    @Transactional(readOnly = true)
    public com.hyodream.backend.product.dto.RecommendationResponseDto getRecommendedProducts(String identifier, boolean isLogin) {
        // ... (기존 추천 로직 유지) ...
        return getRecommendedProductsInternal(identifier, isLogin);
    }

    // 추천 로직 내부 메서드로 분리 (가독성 위해)
    private com.hyodream.backend.product.dto.RecommendationResponseDto getRecommendedProductsInternal(String identifier, boolean isLogin) {
        com.hyodream.backend.product.dto.RecommendationResponseDto response = new com.hyodream.backend.product.dto.RecommendationResponseDto();
        Set<Long> addedIds = new HashSet<>();

        // Real-time
        try {
            String redisKey = "interest:user:" + identifier;
            Set<String> topInterests = redisTemplate.opsForZSet().reverseRange(redisKey, 0, 0);
            
            if (topInterests != null && !topInterests.isEmpty()) {
                String hotCategory = topInterests.iterator().next();
                log.info("🔥 Real-time Interest Detected for user '{}': {}", identifier, hotCategory);
                
                List<Product> candidates = productRepository.findByKeywordInBenefitsOrCategories(hotCategory);
                log.info("   -> Found {} candidate products for interest '{}'", candidates.size(), hotCategory);
                
                List<ProductResponseDto> sectionProducts = new ArrayList<>();
                int count = 0;
                for (Product p : candidates) {
                    if (count >= 4) break;
                    if (addedIds.contains(p.getId())) continue;
                    ProductResponseDto dto = new ProductResponseDto(p);
                    dto.setReason("최근 관심사 '" + hotCategory + "' 관련");
                    sectionProducts.add(dto);
                    addedIds.add(p.getId());
                    count++;
                }
                if (!sectionProducts.isEmpty()) {
                    response.setRealTime(new com.hyodream.backend.product.dto.RecommendationSection(
                            "최근 보신 '" + hotCategory + "' 관련 상품", sectionProducts));
                    log.info("   -> Added Real-time section with {} products", sectionProducts.size());
                } else {
                    log.warn("   -> Real-time candidates were found but filtered out (duplicates or empty).");
                }
            } else {
                log.info("ℹ️ No Real-time Interest found in Redis for user '{}' (Key: {})", identifier, redisKey);
            }
        } catch (Exception e) {
            log.error("⚠️ Real-time recommendation error: {}", e.getMessage());
        }

        response.setHealthGoals(new ArrayList<>());
        response.setDiseases(new ArrayList<>());

        if (isLogin) {
            try {
                User user = userRepository.findByUsername(identifier)
                        .orElseThrow(() -> new RuntimeException("사용자 없음"));

                // Health Goals
                if (user.getHealthGoals() != null) {
                    for (var userGoal : user.getHealthGoals()) {
                        String goalName = userGoal.getHealthGoal().getName();
                        List<Product> candidates = productRepository.findByHealthBenefitsContaining(goalName);
                        List<ProductResponseDto> sectionProducts = new ArrayList<>();
                        int count = 0;
                        for (Product p : candidates) {
                            if (count >= 2) break;
                            if (addedIds.contains(p.getId())) continue;
                            ProductResponseDto dto = new ProductResponseDto(p);
                            dto.setReason("목표: " + goalName);
                            sectionProducts.add(dto);
                            addedIds.add(p.getId());
                            count++;
                        }
                        if (!sectionProducts.isEmpty()) {
                            response.getHealthGoals().add(new com.hyodream.backend.product.dto.RecommendationSection(
                                    "고객님의 '" + goalName + "' 관리를 위한 추천", sectionProducts));
                        }
                    }
                }

                // Diseases
                if (user.getDiseases() != null) {
                    for (var userDisease : user.getDiseases()) {
                        String diseaseName = userDisease.getDisease().getName();
                        List<Product> candidates = productRepository.findTopSellingProductsByDisease(diseaseName);
                        List<ProductResponseDto> sectionProducts = new ArrayList<>();
                        int count = 0;
                        for (Product p : candidates) {
                            if (count >= 2) break;
                            if (addedIds.contains(p.getId())) continue;
                            ProductResponseDto dto = new ProductResponseDto(p);
                            dto.setReason("같은 '" + diseaseName + "' 환우들의 선택");
                            sectionProducts.add(dto);
                            addedIds.add(p.getId());
                            count++;
                        }
                        if (!sectionProducts.isEmpty()) {
                            response.getDiseases().add(new com.hyodream.backend.product.dto.RecommendationSection(
                                    "'" + diseaseName + "' 환우들이 많이 선택한 상품", sectionProducts));
                        }
                    }
                }

                // AI
                try {
                    // 1. 후보군 생성 (인기 30 + 신규 20)
                    List<Product> popular = productRepository.findTop30ByOrderByRecentSalesDesc();
                    List<Product> newProducts = productRepository.findTop20ByOrderByCreatedAtDesc();

                    Set<Product> candidatePool = new HashSet<>(popular);
                    candidatePool.addAll(newProducts);

                    List<AiRecommendationRequestDto.CandidateProductDto> candidateDtos = candidatePool.stream()
                            .map(p -> new AiRecommendationRequestDto.CandidateProductDto(
                                    p.getId(),
                                    p.getName(),
                                    p.getHealthBenefits(),
                                    p.getAllergens(),
                                    p.getCategory1()
                            ))
                            .toList();

                    AiRecommendationRequestDto requestDto = AiRecommendationRequestDto.builder()
                            .diseaseNames(user.getDiseases().stream().map(d -> d.getDisease().getName()).toList())
                            .allergyNames(user.getAllergies().stream().map(a -> a.getAllergy().getName()).toList())
                            .healthGoalNames(user.getHealthGoals().stream().map(h -> h.getHealthGoal().getName()).toList())
                            .candidates(candidateDtos)
                            .build();

                    var aiResponse = aiClient.getRecommendations(requestDto);
                    List<Long> aiProductIds = aiResponse.productIds();

                    if (aiProductIds != null && !aiProductIds.isEmpty()) {
                        List<Product> aiCandidates = productRepository.findAllById(aiProductIds);
                        Map<Long, Product> productMap = aiCandidates.stream()
                                .collect(Collectors.toMap(Product::getId, p -> p));
                        List<ProductResponseDto> sectionProducts = new ArrayList<>();
                        int count = 0;
                        for (Long id : aiProductIds) {
                            if (count >= 3) break;
                            if (addedIds.contains(id)) continue;
                            Product p = productMap.get(id);
                            if (p != null) {
                                ProductResponseDto dto = new ProductResponseDto(p);
                                dto.setReason("AI 종합 분석");
                                sectionProducts.add(dto);
                                addedIds.add(p.getId());
                                count++;
                            }
                        }
                        if (!sectionProducts.isEmpty()) {
                            response.setAi(new com.hyodream.backend.product.dto.RecommendationSection(
                                    "AI가 분석한 맞춤 상품", sectionProducts));
                        }
                    }
                } catch (Exception e) {
                    log.error("AI Recommendation Error: {}", e.getMessage());
                }
            } catch (Exception e) {}
        }
        return response;
    }

    // 상품 검색
    @Transactional
    public Page<ProductResponseDto> searchProducts(String keyword, int page, int size, String sort) {
        if (keyword == null || keyword.trim().isEmpty()) return Page.empty();

        try {
            SearchLog log = searchLogRepository.findById(keyword).orElse(null);
            boolean needApiCall = false;

            if (log == null) {
                log = new SearchLog(keyword, LocalDateTime.now(), LocalDateTime.now());
                needApiCall = true;
            } else {
                log.recordSearch();
                if (log.getLastApiCallAt() == null || log.getLastApiCallAt().isBefore(LocalDateTime.now().minusHours(24))) {
                    needApiCall = true;
                }
            }

            if (needApiCall) {
                naverShoppingService.importNaverProducts(keyword);
                log.recordApiCall();
            }
            searchLogRepository.save(log);
        } catch (Exception e) {
            System.err.println("⚠️ Naver Import Failed: " + e.getMessage());
        }

        boolean isLogin = false;
        List<String> userAllergies = new ArrayList<>();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String username = auth.getName();
            userRepository.findByUsername(username).ifPresent(user -> {
                user.getAllergies().forEach(ua -> userAllergies.add(ua.getAllergy().getName()));
            });
            if (!userAllergies.isEmpty()) isLogin = true;
        }
        if (userAllergies.isEmpty()) userAllergies.add("NONE");

        Sort sortCondition = Sort.by("id").descending();
        if ("popular".equals(sort)) {
            sortCondition = Sort.by("recentSales").descending().and(Sort.by("id").descending());
        }

        Pageable pageable = PageRequest.of(page, size, sortCondition);
        return productRepository.findByNameContainingWithPersonalization(keyword, isLogin, userAllergies, pageable)
                .map(ProductResponseDto::new);
    }

    // 연관 상품 추천
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getRelatedProducts(Long productId) {
        if (!productRepository.existsById(productId)) return new ArrayList<>();
        List<Product> relatedProducts = productRepository.findFrequentlyBoughtTogether(productId);
        if (relatedProducts.isEmpty()) {
            relatedProducts = productRepository.findSimilarProductsByBenefits(productId);
        }
        return relatedProducts.stream().map(ProductResponseDto::new).collect(Collectors.toList());
    }

    @Transactional
    public void increaseTotalSales(Long productId, int count) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("상품 없음"));
        product.setTotalSales(product.getTotalSales() + count);
    }

    @Transactional
    public void decreaseTotalSales(Long productId, int count) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("상품 없음"));
        if (product.getTotalSales() >= count) {
            product.setTotalSales(product.getTotalSales() - count);
        } else {
            product.setTotalSales(0);
        }
    }
}