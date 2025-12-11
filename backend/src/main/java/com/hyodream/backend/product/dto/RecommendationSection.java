package com.hyodream.backend.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "추천 섹션 (제목 + 상품 목록)")
public class RecommendationSection {

    @Schema(description = "섹션 제목 (예: '최근 보신 피자와 비슷한 상품')", example = "최근 보신 피자와 비슷한 상품")
    private String title;

    @Schema(description = "해당 섹션의 추천 상품 목록")
    private List<ProductResponseDto> products;
}