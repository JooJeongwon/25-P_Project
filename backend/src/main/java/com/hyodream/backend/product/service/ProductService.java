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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final AiClient aiClient;

    private final UserRepository userRepository;

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

        productRepository.save(product);
    }

    // 전체 상품 목록 조회 (사용자용)
    @Transactional(readOnly = true)
    public Page<ProductResponseDto> getAllProducts(int page, int size) {
        // 페이지 만들기: page번 페이지, size개씩, id 내림차순(최신순) 정렬
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());

        // findAll(pageable)을 호출하면 알아서 Page 객체를 줍니다.
        return productRepository.findAll(pageable)
                .map(ProductResponseDto::new); // 엔티티 -> DTO 변환도 map으로 한 방에!
    }

    // 상품 상세 조회 (ID로 찾기)
    @Transactional(readOnly = true)
    public ProductResponseDto getProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("상품이 없습니다."));
        return new ProductResponseDto(product);
    }

    // AI 추천 로직 추가
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getRecommendedProducts(String username) {
        // 사용자 정보 가져오기
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자 없음"));

        // AI에게 보낼 데이터 만들기 (User 엔티티 -> DTO 변환)
        HealthInfoRequestDto requestDto = new HealthInfoRequestDto();
        // 지병 목록 변환
        requestDto.setDiseaseNames(user.getDiseases().stream()
                .map(ud -> ud.getDisease().getName()).toList());
        // 알레르기 목록 변환
        requestDto.setAllergyNames(user.getAllergies().stream()
                .map(ua -> ua.getAllergy().getName()).toList());

        // 기대효과 목록 변환
        requestDto.setHealthGoalNames(user.getHealthGoals().stream()
                .map(uh -> uh.getHealthGoal().getName()).toList());

        // AI 서버 호출 (ID 리스트 받음)
        List<Long> productIds = aiClient.getRecommendations(requestDto);

        // 받아온 ID로 우리 DB에서 상품 조회
        List<Product> products = productRepository.findAllById(productIds);

        return products.stream()
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
}