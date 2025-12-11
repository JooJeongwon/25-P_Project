package com.hyodream.backend.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class ProductRequestDto {

    @Schema(description = "상품명", example = "정관장 홍삼정 에브리타임")
    private String name;

    @Schema(description = "가격", example = "98000")
    private int price;

    @Schema(description = "상품 상세 설명", example = "면역력 증진에 도움을 줄 수 있는 홍삼 제품입니다.")
    private String description;

    @Schema(description = "상품 이미지 URL", example = "https://example.com/images/red_ginseng.jpg")
    private String imageUrl;

    @Schema(description = "상품 원본 URL", example = "https://search.shopping.naver.com/...")
    private String itemUrl;

    @Schema(description = "브랜드", example = "정관장")
    private String brand;

    @Schema(description = "제조사", example = "한국인삼공사")
    private String maker;

    @Schema(description = "카테고리 1 (대분류)", example = "식품")
    private String category1;

    @Schema(description = "카테고리 2 (중분류)", example = "건강식품")
    private String category2;

    @Schema(description = "카테고리 3 (소분류)", example = "홍삼")
    private String category3;

    @Schema(description = "카테고리 4 (세분류)", example = "홍삼농축액")
    private String category4;

    @Schema(description = "용량/수량", example = "10ml x 30포")
    private String volume;

    @Schema(description = "크기/규격 정보", example = "가로 20cm, 세로 10cm")
    private String sizeInfo;

    @Schema(description = "효능 태그 목록", example = "[\"면역력 강화\", \"피로 회복\"]")
    private List<String> healthBenefits;

    @Schema(description = "알레르기 유발 성분 목록", example = "[\"없음\"]")
    private List<String> allergens;
}