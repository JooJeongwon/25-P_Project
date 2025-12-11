package com.hyodream.backend.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "개인화 맞춤 추천 전체 응답")
public class RecommendationResponseDto {

    @Schema(description = "실시간 추천 섹션 (최근 본 상품/카테고리 기반)")
    private RecommendationSection realTime;

    @Schema(description = "건강 목표 기반 추천 섹션 목록")
    private List<RecommendationSection> healthGoals;

    @Schema(description = "지병(Disease) 기반 추천 섹션 목록")
    private List<RecommendationSection> diseases;

    @Schema(description = "AI 종합 추천 섹션")
    private RecommendationSection ai;
}