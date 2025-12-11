package com.hyodream.backend.product.service;

import com.hyodream.backend.global.client.AiClient;
import com.hyodream.backend.product.domain.Product;
import com.hyodream.backend.product.domain.SearchLog;
import com.hyodream.backend.product.dto.ProductRequestDto;
import com.hyodream.backend.product.dto.ProductResponseDto;
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
    private final AiClient aiClient;

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    // 상품 등록 (관리자용 - 나중에 쿠팡 API로 대체될 부분)
    @Transactional
    public void createProduct(ProductRequestDto dto) {
        Product product = new Product();
        product.setName(dto.getName());
        product.setPrice(dto.getPrice());
        product.setDescription(dto.getDescription());
        product.setImageUrl(dto.getImageUrl());
        product.setItemUrl(dto.getItemUrl()); // 상품 URL
        product.setBrand(dto.getBrand());
        product.setMaker(dto.getMaker());
        product.setCategory1(dto.getCategory1());
        product.setCategory2(dto.getCategory2());
        product.setCategory3(dto.getCategory3());
        product.setCategory4(dto.getCategory4());
        product.setVolume(dto.getVolume());
        product.setSizeInfo(dto.getSizeInfo());

        // 효능 태그 저장
        if (dto.getHealthBenefits() != null) {
            for (String benefit : dto.getHealthBenefits()) {
                product.addBenefit(benefit);
            }
        }

        // 알레르기 정보 저장 로직
        if (dto.getAllergens() != null) {
            for (String allergen : dto.getAllergens()) {
                product.addAllergen(allergen); // 엔티티에 만들어둔 메서드 호출
            }
        }

        productRepository.save(product);
    }

    // 전체 상품 목록 조회 (알레르기 필터링 + 인기순 + 실시간 주입)
    @Transactional(readOnly = true)
    public Page<ProductResponseDto> getAllProducts(int page, int size, String sort, String identifier) {

        // A. 정렬 기준 결정 (기본값: 인기순)
        Sort sortCondition = Sort.by("recentSales").descending().and(Sort.by("id").descending());

        if ("latest".equals(sort)) {
            sortCondition = Sort.by("id").descending(); // 최신순 요청 시 변경
        }

        // B. 유저 알레르기 정보 가져오기
        boolean isLogin = false;
        List<String> userAllergies = new ArrayList<>();

        if (identifier != null && !identifier.equals("unknown") && !identifier.startsWith("session:")) {
            // 로그인 유저(username)인 경우 DB 조회
            userRepository.findByUsername(identifier).ifPresent(user -> {
                user.getAllergies().forEach(ua -> userAllergies.add(ua.getAllergy().getName()));
            });
            if (!userAllergies.isEmpty())
                isLogin = true;
        }
        // 쿼리 에러 방지용 더미 데이터
        if (userAllergies.isEmpty())
            userAllergies.add("NONE");

        // C. 기본 목록 조회 (알레르기 필터링 + 정렬 적용)
        Pageable pageable = PageRequest.of(page, size, sortCondition);
        Page<Product> productPage = productRepository.findAllWithPersonalization(isLogin, userAllergies, pageable);

        List<Product> originalList = productPage.getContent();
        List<Product> resultList = new ArrayList<>(originalList);

        // D. [첫 페이지]일 때만 실시간 관심사 5개 '강제 주입'
        if (page == 0 && identifier != null && !identifier.equals("unknown")) {
            String redisKey = "interest:user:" + identifier;
            Set<String> topInterests = redisTemplate.opsForZSet().reverseRange(redisKey, 0, 0);

            if (topInterests != null && !topInterests.isEmpty()) {
                String interestCategory = topInterests.iterator().next();

                // 관심 상품 검색 (효능 OR 카테고리)
                List<Product> interestProducts = productRepository
                        .findByKeywordInBenefitsOrCategories(interestCategory);

                // 상위 3개만 주입 (개수 제한)
                if (interestProducts.size() > 3) {
                    interestProducts = interestProducts.subList(0, 3);
                }

                // 맨 앞에 삽입
                for (int i = interestProducts.size() - 1; i >= 0; i--) {
                    resultList.add(0, interestProducts.get(i));
                }
            }
        }

        // E. 중복 제거 및 DTO 변환
        List<Long> addedIds = new ArrayList<>();
        List<ProductResponseDto> finalDtos = new ArrayList<>();

        for (Product p : resultList) {
            if (!addedIds.contains(p.getId())) {
                finalDtos.add(new ProductResponseDto(p));
                addedIds.add(p.getId());
            }
            if (finalDtos.size() >= size)
                break;
        }

        return new PageImpl<>(finalDtos, pageable, productPage.getTotalElements());
    }

    // 상품 상세 조회 (ID로 찾기)
    @Transactional(readOnly = true)
    public ProductResponseDto getProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("상품이 없습니다."));
        return new ProductResponseDto(product);
    }

    // AI + 실시간 + 유저 기대효과 하이브리드 추천 (로그인 유저 전용)
    @Transactional(readOnly = true)
    public com.hyodream.backend.product.dto.RecommendationResponseDto getRecommendedProducts(String identifier, boolean isLogin) {
        com.hyodream.backend.product.dto.RecommendationResponseDto response = new com.hyodream.backend.product.dto.RecommendationResponseDto();
        Set<Long> addedIds = new HashSet<>(); // 전체 섹션 통합 중복 방지용

        // [A] 실시간 행동 기반 추천 (Quota: 4개)
        try {
            String redisKey = "interest:user:" + identifier;
            Set<String> topInterests = redisTemplate.opsForZSet().reverseRange(redisKey, 0, 0);

            if (topInterests != null && !topInterests.isEmpty()) {
                String hotCategory = topInterests.iterator().next();
                List<Product> candidates = productRepository.findByKeywordInBenefitsOrCategories(hotCategory);
                List<ProductResponseDto> sectionProducts = new ArrayList<>();

                int count = 0;
                for (Product p : candidates) {
                    if (count >= 4) break;
                    if (addedIds.contains(p.getId())) continue;

                    ProductResponseDto dto = new ProductResponseDto(p);
                    // 개별 reason도 남겨두지만, 섹션 타이틀이 주된 설명임
                    dto.setReason("최근 관심사 '" + hotCategory + "' 관련");
                    sectionProducts.add(dto);
                    addedIds.add(p.getId());
                    count++;
                }
                
                if (!sectionProducts.isEmpty()) {
                    response.setRealTime(new com.hyodream.backend.product.dto.RecommendationSection(
                            "최근 보신 '" + hotCategory + "' 관련 상품", sectionProducts));
                }
            }
        } catch (Exception e) {
            log.warn("Redis Recommendation Failed: {}", e.getMessage());
        }

        // 초기화 (null 방지)
        response.setHealthGoals(new ArrayList<>());
        response.setDiseases(new ArrayList<>());

        if (isLogin) {
            try {
                User user = userRepository.findByUsername(identifier)
                        .orElseThrow(() -> new RuntimeException("사용자 없음"));

                // [B] 유저 기대효과(HealthGoal) 기반 추천 (Quota: 목표당 2개)
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

                // [C] 지병(Disease) 기반 추천 (Quota: 지병당 2개)
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

                // [D] AI 기반 추천 (Quota: 3개 고정)
                try {
                    HealthInfoRequestDto requestDto = new HealthInfoRequestDto();
                    requestDto.setDiseaseNames(user.getDiseases().stream().map(d -> d.getDisease().getName()).toList());
                    requestDto.setAllergyNames(user.getAllergies().stream().map(a -> a.getAllergy().getName()).toList());
                    requestDto.setHealthGoalNames(user.getHealthGoals().stream().map(h -> h.getHealthGoal().getName()).toList());

                    var aiResponse = aiClient.getRecommendations(requestDto);
                    List<Long> aiProductIds = aiResponse.productIds();

                    if (aiProductIds != null && !aiProductIds.isEmpty()) {
                        List<Product> aiCandidates = productRepository.findAllById(aiProductIds);
                        
                        // ID 순서 유지를 위한 매핑
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
                    log.warn("AI Recommendation Failed: {}", e.getMessage());
                }

            } catch (Exception e) {
                log.warn("DB Recommendation Failed (User Load Error): {}", e.getMessage());
            }
        }

        return response;
    }

    // 상품 검색 기능 (통합 검색: Cache-Aside 패턴 적용)
    // 읽기 전용 제거 -> Import 때문에 쓰기 트랜잭션 필요
    @Transactional
    public Page<ProductResponseDto> searchProducts(String keyword, int page, int size, String sort) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Page.empty(); // 빈 리스트 대신 빈 페이지 반환
        }

        // 1. [Cache-Aside] 네이버 API를 통한 데이터 최신화 확인
        try {
            SearchLog log = searchLogRepository.findById(keyword).orElse(null);
            boolean needUpdate = false;

            if (log == null) {
                needUpdate = true; // 최초 검색
            } else if (log.getLastUpdatedAt().isBefore(LocalDateTime.now().minusHours(24))) {
                needUpdate = true; // TTL 만료 (24시간 경과)
            }

            if (needUpdate) {
                // 네이버 API 호출하여 DB 적재 (필터링 로직 포함)
                // 주의: importNaverProducts 내부에서 현재 유저 컨텍스트를 타므로, 
                // 로그인 유저의 알러지 상품은 저장되지 않음.
                naverShoppingService.importNaverProducts(keyword);

                // 로그 갱신
                if (log == null) {
                    log = new SearchLog(keyword, LocalDateTime.now());
                } else {
                    log.updateTimestamp();
                }
                searchLogRepository.save(log);
                System.out.println("✅ [Cache-Aside] Updated DB from Naver for keyword: " + keyword);
            }
        } catch (Exception e) {
            // 외부 API 장애 시에도 기존 DB 데이터로 검색 진행 (Fallback)
            System.err.println("⚠️ Naver Import Failed: " + e.getMessage());
        }

        // 2. DB 조회 (로그인 여부 및 알러지 필터링 적용)
        boolean isLogin = false;
        List<String> userAllergies = new ArrayList<>();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String username = auth.getName();
            userRepository.findByUsername(username).ifPresent(user -> {
                user.getAllergies().forEach(ua -> userAllergies.add(ua.getAllergy().getName()));
            });
            if (!userAllergies.isEmpty()) {
                isLogin = true;
            }
        }
        
        // 쿼리 에러 방지용 더미 데이터
        if (userAllergies.isEmpty()) {
            userAllergies.add("NONE");
        }

        // 정렬 기준 적용
        Sort sortCondition = Sort.by("id").descending(); // 기본: 최신순
        if ("popular".equals(sort)) {
            sortCondition = Sort.by("recentSales").descending().and(Sort.by("id").descending());
        }

        Pageable pageable = PageRequest.of(page, size, sortCondition);

        // 안전한 검색 쿼리 실행
        return productRepository.findByNameContainingWithPersonalization(keyword, isLogin, userAllergies, pageable)
                .map(ProductResponseDto::new);
    }

    // 연관 상품 추천 (함께 많이 산 상품 + 고도화된 Fallback)
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getRelatedProducts(Long productId) {
        // 상품 존재 확인
        if (!productRepository.existsById(productId)) {
            return new ArrayList<>();
        }

        // [우선순위] 함께 많이 산 상품 (협업 필터링)
        List<Product> relatedProducts = productRepository.findFrequentlyBoughtTogether(productId);

        // [Fallback] 데이터 부족 시 -> "태그 유사도" 높은 순 추천 (콘텐츠 기반 필터링)
        if (relatedProducts.isEmpty()) {
            relatedProducts = productRepository.findSimilarProductsByBenefits(productId);
        }
        // 결과 반환 (DTO 변환)
        return relatedProducts.stream()
                .map(ProductResponseDto::new)
                .collect(Collectors.toList());
    }

    // 판매량 증가 (주문 시 호출됨)
    @Transactional
    public void increaseTotalSales(Long productId, int count) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("상품 없음"));

        // 누적 판매량 증가
        product.setTotalSales(product.getTotalSales() + count);
    }

    // 판매량 감소 (주문 취소 시 호출)
    @Transactional
    public void decreaseTotalSales(Long productId, int count) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("상품 없음"));

        // 0보다 작아지면 안 되므로 방어 로직 추가
        if (product.getTotalSales() >= count) {
            product.setTotalSales(product.getTotalSales() - count);
        } else {
            product.setTotalSales(0);
        }
    }
}