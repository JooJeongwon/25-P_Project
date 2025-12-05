package com.hyodream.backend.product.service;

import com.hyodream.backend.product.domain.Product;
import com.hyodream.backend.product.dto.ProductRequestDto;
import com.hyodream.backend.product.dto.ProductResponseDto;
import com.hyodream.backend.product.repository.ProductRepository;
import com.hyodream.backend.global.client.AiClient;

import com.hyodream.backend.user.domain.User;
import com.hyodream.backend.user.repository.UserRepository;
import com.hyodream.backend.user.dto.HealthInfoRequestDto;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
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

                // 관심 상품 5개 가져오기
                List<Product> interestProducts = productRepository
                        .findTop5ByHealthBenefitsContainingOrderByIdDesc(interestCategory);

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
            // 알레르기 필터링은 위 쿼리에서 이미 했지만, 주입된 상품(interestProducts)도 알레르기 체크가 필요할 수 있음
            // 하지만 주입된 상품은 "사용자가 직접 클릭해서 점수를 올린" 상품이므로, 알레르기가 있어도 보여주는 게 맞음 (의도적 접근)
            // 따라서 여기선 중복만 제거
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

    // AI + 실시간 하이브리드 추천 (로그인 유저 전용)
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getRecommendedProducts(String identifier, boolean isLogin) {
        List<Product> finalProducts = new ArrayList<>();

        // [A] 실시간 행동 기반 추천 (공통)
        // 로그인 여부와 상관없이 식별자(identifier)로 Redis 조회
        String redisKey = "interest:user:" + identifier;
        Set<String> topInterests = redisTemplate.opsForZSet().reverseRange(redisKey, 0, 0);

        if (topInterests != null && !topInterests.isEmpty()) {
            String hotCategory = topInterests.iterator().next();
            // 해당 태그 상품 3개 가져오기 (메서드 재활용)
            List<Product> realTimePicks = productRepository.findByHealthBenefitsContaining(hotCategory);

            // 최대 3개만
            if (realTimePicks.size() > 3)
                realTimePicks = realTimePicks.subList(0, 3);
            finalProducts.addAll(realTimePicks);
        }

        // [B] AI 기반 추천 (로그인 유저만!)
        if (isLogin) {
            try {
                User user = userRepository.findByUsername(identifier)
                        .orElseThrow(() -> new RuntimeException("사용자 없음"));

                HealthInfoRequestDto requestDto = new HealthInfoRequestDto();
                requestDto.setDiseaseNames(user.getDiseases().stream().map(d -> d.getDisease().getName()).toList());
                requestDto.setAllergyNames(user.getAllergies().stream().map(a -> a.getAllergy().getName()).toList());
                requestDto.setHealthGoalNames(
                        user.getHealthGoals().stream().map(h -> h.getHealthGoal().getName()).toList());

                List<Long> aiProductIds = aiClient.getRecommendations(requestDto);
                List<Product> aiProducts = productRepository.findAllById(aiProductIds);

                finalProducts.addAll(aiProducts);

            } catch (Exception e) {
                System.err.println("⚠️ AI 서버 연동 실패 (Fallback 가동): " + e.getMessage());
            }
        }

        // [C] 중복 제거 및 반환
        return finalProducts.stream()
                .distinct()
                .map(ProductResponseDto::new)
                .collect(Collectors.toList());
    }

    // 상품 검색 기능 (이름으로)
    @Transactional(readOnly = true)
    public Page<ProductResponseDto> searchProducts(String keyword, int page, int size) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Page.empty(); // 빈 리스트 대신 빈 페이지 반환
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());

        return productRepository.findByNameContaining(keyword, pageable)
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

        // (선택) 재고 관리(Stock) 기능도 나중에 여기에 넣으면 됨
        // product.decreaseStock(count);
    }

    // 8. 판매량 감소 (주문 취소 시 호출)
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