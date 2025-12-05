package com.hyodream.backend.product.controller;

import com.hyodream.backend.product.dto.ProductRequestDto;
import com.hyodream.backend.product.dto.ProductResponseDto;
import com.hyodream.backend.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Page;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // 상품 등록 (관리자용 - 일단 누구나 쓸 수 있게 열어둠 or 토큰 필요)
    @PostMapping
    public ResponseEntity<String> createProduct(@RequestBody ProductRequestDto dto) {
        productService.createProduct(dto);
        return ResponseEntity.ok("상품 등록 완료!");
    }

    // 전체 목록 조회
    // 사용법: GET /api/products?page=0&size=10
    // 헤더: X-Session-Id (비로그인 유저 식별용)
    @GetMapping
    public ResponseEntity<Page<ProductResponseDto>> getAllProducts(
            @RequestParam(defaultValue = "0") int page, // 안 보내면 0페이지(처음)
            @RequestParam(defaultValue = "10") int size, // 안 보내면 10개씩
            @RequestParam(defaultValue = "latest") String sort, // 정렬 기준 추가 (기본값: 인기순)
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            Authentication auth // 로그인 여부 확인용
    ) {
        // 식별자 결정: 로그인했으면 ID, 아니면 세션ID
        String identifier = (auth != null && auth.isAuthenticated()) ? auth.getName() : sessionId;
        if (identifier == null)
            identifier = "unknown";

        return ResponseEntity.ok(productService.getAllProducts(page, size, sort, identifier));
    }

    // 상세 조회
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDto> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProduct(id));
    }

    // 추천 상품 조회
    // GET http://localhost:8080/api/products/recommend
    // 헤더: Authorization bearer (로그인 시), X-Session-Id (비로그인 시)
    // AI 추천 (로그인 필수)
    @GetMapping("/recommend")
    public ResponseEntity<List<ProductResponseDto>> getRecommendedProducts(
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            Authentication auth) {
        String identifier;
        boolean isLogin = false;

        // 식별자 결정 (로그인 우선 -> 없으면 세션ID)
        if (auth != null && auth.isAuthenticated()) {
            identifier = auth.getName();
            isLogin = true;
        } else if (sessionId != null) {
            identifier = sessionId;
            isLogin = false;
        } else {
            // 둘 다 없으면 추천해줄 근거가 없음 -> 빈 리스트 반환
            return ResponseEntity.ok(List.of());
        }

        return ResponseEntity.ok(productService.getRecommendedProducts(identifier, isLogin));
    }

    // 사용법: GET /api/products/search?keyword=관절&page=0&size=10
    @GetMapping("/search")
    public ResponseEntity<Page<ProductResponseDto>> searchProducts(
            @RequestParam("keyword") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(productService.searchProducts(keyword, page, size));
    }

    // 연관 상품 추천 API
    // GET http://localhost:8080/api/products/{id}/related
    @GetMapping("/{id}/related")
    public ResponseEntity<List<ProductResponseDto>> getRelatedProducts(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getRelatedProducts(id));
    }
}