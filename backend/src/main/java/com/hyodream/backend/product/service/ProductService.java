package com.hyodream.backend.product.service;

import com.hyodream.backend.global.client.AiClient;
import com.hyodream.backend.global.client.crawler.CrawlerClient;
import com.hyodream.backend.global.client.crawler.dto.CrawlerResponseDto;
import com.hyodream.backend.global.client.review.AiReviewClient;
import com.hyodream.backend.global.client.review.dto.ReviewAnalysisRequestDto;
import com.hyodream.backend.global.client.review.dto.ReviewAnalysisResponseDto;
import com.hyodream.backend.product.domain.AnalysisStatus;
import com.hyodream.backend.product.domain.Product;
import com.hyodream.backend.product.domain.ReviewAnalysis;
import com.hyodream.backend.product.domain.SearchLog;
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
                        .findByKeywordInBenefitsOrCategoriesWithAllergyCheck(interestCategory, isLogin, userAllergies);

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

    // [Modified] 상품 상세 조회 (비동기 AI 분석 적용)
    @Transactional(readOnly = true)
    public ProductResponseDto getProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("상품이 없습니다."));

        // AI 분석 상태 확인
        // 분석 정보가 없으면 최초 1회 분석 요청 (리뷰가 없어도 COMPLETED 상태 생성을 위해 실행됨)
        boolean needAnalysis = (product.getAnalysis() == null);

        if (needAnalysis) {
            try {
                productSyncService.analyzeProductReviews(product.getId());
                log.info("🚀 Triggered async review analysis for ID: {}", id);
            } catch (Exception e) {
                log.error("Failed to trigger async analysis: {}", e.getMessage());
            }
        }

        ProductResponseDto responseDto = new ProductResponseDto(product);
        if (needAnalysis) {
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
        
        // 0. User Allergy Info Extraction
        List<String> userAllergies = new ArrayList<>();
        if (isLogin) {
            try {
                userRepository.findByUsername(identifier).ifPresent(user -> {
                    user.getAllergies().forEach(ua -> userAllergies.add(ua.getAllergy().getName()));
                });
            } catch (Exception e) {
                log.error("Failed to fetch user allergies: {}", e.getMessage());
            }
        }
        if (userAllergies.isEmpty()) userAllergies.add("NONE");
        boolean hasAllergies = !userAllergies.contains("NONE");
        
        log.info("🔍 Recommendation Debug - User: {}, Allergies: {}, hasAllergies: {}", identifier, userAllergies, hasAllergies);

        // Real-time
        try {
            String redisKey = "interest:user:" + identifier;
            Set<String> topInterests = redisTemplate.opsForZSet().reverseRange(redisKey, 0, 0);
            
            if (topInterests != null && !topInterests.isEmpty()) {
                String hotCategory = topInterests.iterator().next();
                log.info("🔥 Real-time Interest Detected for user '{}': {}", identifier, hotCategory);
                
                // [Modified] Use Allergy Check
                List<Product> candidates = productRepository.findByKeywordInBenefitsOrCategoriesWithAllergyCheck(
                        hotCategory, isLogin && hasAllergies, userAllergies);
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
                        log.info("🎯 Processing Health Goal: {}", goalName);
                        // [Modified] Use Allergy Check
                        List<Product> candidates = productRepository.findByHealthBenefitsContainingWithAllergyCheck(
                                goalName, isLogin && hasAllergies, userAllergies);
                        log.info("   -> Found {} candidates for goal '{}' (Allergy Filtered)", candidates.size(), goalName);
                        
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
                        // [Modified] Use Allergy Check
                        List<Product> candidates = productRepository.findTopSellingProductsByDiseaseWithAllergyCheck(
                                diseaseName, isLogin && hasAllergies, userAllergies);
                        
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
                    // 1. 후보군 생성 (인기 80 + 신규 20, 알레르기 필터링 없이 전달 -> AI가 판단)
                    List<Product> popular = productRepository.findTop80ByOrderByRecentSalesDesc();
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
        // [Real-time] 인기순 정렬의 즉각적인 반응을 위해 recentSales도 함께 증가
        product.setRecentSales(product.getRecentSales() + count);
    }

    @Transactional
    public void decreaseTotalSales(Long productId, int count) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("상품 없음"));
        
        // 전체 판매량 감소
        if (product.getTotalSales() >= count) {
            product.setTotalSales(product.getTotalSales() - count);
        } else {
            product.setTotalSales(0);
        }

        // [Real-time] 최근 판매량도 감소 (주문 취소 시 순위 하락 반영)
        if (product.getRecentSales() >= count) {
            product.setRecentSales(product.getRecentSales() - count);
        } else {
            product.setRecentSales(0);
        }
    }
}