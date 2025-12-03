package com.hyodream.backend.product.controller;

import com.hyodream.backend.product.dto.ProductRequestDto;
import com.hyodream.backend.product.dto.ProductResponseDto;
import com.hyodream.backend.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // 1. 상품 등록 (관리자용 - 일단 누구나 쓸 수 있게 열어둠 or 토큰 필요)
    @PostMapping
    public ResponseEntity<String> createProduct(@RequestBody ProductRequestDto dto) {
        productService.createProduct(dto);
        return ResponseEntity.ok("상품 등록 완료!");
    }

    // 2. 전체 목록 조회
    @GetMapping
    public ResponseEntity<List<ProductResponseDto>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    // 3. 상세 조회
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDto> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProduct(id));
    }

    // 4. AI 맞춤 추천 상품 조회 (New!)
    // GET http://localhost:8080/api/products/recommend
    // 헤더: Authorization: Bearer {토큰}
    @GetMapping("/recommend")
    public ResponseEntity<List<ProductResponseDto>> getRecommendedProducts(
            Authentication authentication // 로그인한 사용자 정보
    ) {
        // 서비스에서 AI한테 물어보고 결과 받아오기
        List<ProductResponseDto> recommendations = productService.getRecommendedProducts(authentication.getName());
        return ResponseEntity.ok(recommendations);
    }
}