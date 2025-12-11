package com.hyodream.backend.product.controller;

import com.hyodream.backend.product.dto.ProductRequestDto;
import com.hyodream.backend.product.dto.ProductResponseDto;
import com.hyodream.backend.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Page;

import java.util.List;

@Tag(name = "Product API", description = "상품 검색, 조회 및 추천 관련 API")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "상품 수동 등록 (관리자용)", description = "관리자가 상품을 직접 등록합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "상품 등록 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 입력 값")
    })
    @PostMapping
    public ResponseEntity<String> createProduct(@RequestBody ProductRequestDto dto) {
        productService.createProduct(dto);
        return ResponseEntity.ok("상품 등록 완료!");
    }

    @Operation(summary = "전체 상품 목록 조회", description = "DB에 저장된 모든 상품을 페이징하여 조회합니다. 인기순/최신순 정렬이 가능합니다.\n" +
            "첫 페이지(page=0) 조회 시, 사용자(또는 세션)의 실시간 관심사를 반영한 추천 상품 3개가 최상단에 자동 주입됩니다.")
    @GetMapping
    public ResponseEntity<PagedModel<ProductResponseDto>> getAllProducts(
            @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "정렬 기준 ('latest': 최신순, 'popular': 인기순)") @RequestParam(defaultValue = "latest") String sort,
            @Parameter(description = "비로그인 유저 세션 ID (개인화 추천을 위한 식별자)") @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            Authentication auth) {
        // 식별자 결정: 로그인했으면 ID, 아니면 세션ID
        String identifier = (auth != null && auth.isAuthenticated()) ? auth.getName() : sessionId;
        if (identifier == null)
            identifier = "unknown";

        Page<ProductResponseDto> result = productService.getAllProducts(page, size, sort, identifier);
        return ResponseEntity.ok(new PagedModel<>(result));
    }

    @Operation(summary = "상품 상세 조회", description = "상품 ID로 상세 정보를 조회합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDto> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProduct(id));
    }

    @Operation(summary = "개인화 맞춤 상품 추천 (섹션별 그룹화)", description = """
            사용자의 상태에 따라 4가지 섹션으로 그룹화된 추천 결과를 반환합니다.
            각 섹션은 `title`(추천 사유)과 `products`(상품 목록)으로 구성됩니다.

            **[응답 구조]**
            - **realTime:** [실시간] 최근 관심사(카테고리/효능) 기반 (최대 4개)
            - **healthGoals:** [건강목표] 사용자가 설정한 목표별 리스트 (목표당 2개)
            - **diseases:** [지병] 같은 지병을 가진 환우들의 선택 (지병당 2개)
            - **ai:** [AI] 종합 분석 결과 (3개 고정)
            """)
    @GetMapping("/recommend")
    public ResponseEntity<com.hyodream.backend.product.dto.RecommendationResponseDto> getRecommendedProducts(
            @Parameter(description = "비로그인 유저 세션 ID") @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
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
            // 둘 다 없으면 빈 객체 반환
            return ResponseEntity.ok(new com.hyodream.backend.product.dto.RecommendationResponseDto());
        }

        return ResponseEntity.ok(productService.getRecommendedProducts(identifier, isLogin));
    }

    @Operation(summary = "상품 키워드 검색", description = """
            키워드로 상품을 검색하고, 네이버 쇼핑 API 결과를 실시간으로 캐싱합니다.

            **[검색 및 데이터 동기화 로직]**
            1. **API 연동:** 네이버 쇼핑 API를 통해 **상위 20개** 상품을 실시간 조회합니다.
            2. **알러지 필터링 (로그인 회원):** 회원의 알러지 정보와 일치하는 유해 상품은 **자동 필터링**되어 결과에서 제외되며, DB에도 저장되지 않습니다. (비회원은 필터링 없음)
            3. **데이터 최신화 (Upsert):**
               - **신규 상품:** DB에 새로 등록되며 판매량은 0으로 초기화됩니다.
               - **기존 상품:** 이미 존재하는 상품(`naverProductId`)은 가격, 이미지, 판매 상태(`ON_SALE`), 상세 정보를 최신으로 업데이트합니다. (누적 판매량은 유지)
            4. **자동 태깅:** 상품명과 카테고리를 분석하여 '알러지 성분(19종)'과 '기대 효능(7종)'을 자동으로 추출해 태깅합니다.

            **[데이터 관리 정책 (Background)]**
            - **자동 정리:** 매일 새벽 4시, **30일 이상** 검색/업데이트되지 않은 상품을 정리합니다.
              - 판매 이력이 **있는** 상품: '판매 중지(STOP_SELLING)' 상태로 변경 (주문 내역 보존)
              - 판매 이력이 **없는** 상품: DB에서 **영구 삭제** (용량 확보)
            """)
    @GetMapping("/search")
    public ResponseEntity<PagedModel<ProductResponseDto>> searchProducts(
            @Parameter(description = "검색어 (예: 관절, 루테인)") @RequestParam("keyword") String keyword,
            @Parameter(description = "페이지 번호") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "정렬 기준 ('latest': 최신순, 'popular': 인기순)") @RequestParam(defaultValue = "latest") String sort) {
        Page<ProductResponseDto> result = productService.searchProducts(keyword, page, size, sort);
        return ResponseEntity.ok(new PagedModel<>(result));
    }

    @Operation(summary = "연관 상품 추천", description = "해당 상품을 본 사용자들이 함께 많이 구매한 상품(Collaborative Filtering)을 추천합니다.")
    @GetMapping("/{id}/related")
    public ResponseEntity<List<ProductResponseDto>> getRelatedProducts(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getRelatedProducts(id));
    }
}