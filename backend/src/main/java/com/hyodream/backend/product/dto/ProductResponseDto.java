package com.hyodream.backend.product.dto;

import com.hyodream.backend.product.domain.Product;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import java.util.List;

@Getter
public class ProductResponseDto {

    @Schema(description = "상품 ID", example = "1")
    private Long id;

    @Schema(description = "상품명", example = "종근당 락토핏 골드")
    private String name;

    @Schema(description = "가격", example = "15900")
    private int price;

    @Schema(description = "상품 이미지 URL", example = "https://shopping-phinf.pstatic.net/...")
    private String imageUrl;

    @Schema(description = "구매 링크 URL", example = "https://search.shopping.naver.com/...")
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

    @Schema(description = "효능 태그 (자동 분석)", example = "[\"장 건강\", \"면역력 강화\"]")
    private List<String> healthBenefits;

    @Schema(description = "포함된 알레르기 성분 (로그인 유저의 경우 필터링됨)", example = "[\"우유\", \"대두\"]")
    private List<String> allergens;

    @Schema(description = "추천 사유 (추천 API에서만 값 존재)", example = "최근 보신 '피자'와 비슷한 상품이에요")
    private String reason;

    public void setReason(String reason) {
        this.reason = reason;
    }

    // 엔티티 -> DTO 변환 생성자
    public ProductResponseDto(Product product) {
        this.id = product.getId();
        this.name = product.getName();
        this.price = product.getPrice();
        this.imageUrl = product.getImageUrl();
        this.itemUrl = product.getItemUrl();
        this.brand = product.getBrand();
        this.maker = product.getMaker();
        this.category1 = product.getCategory1();
        this.category2 = product.getCategory2();
        this.category3 = product.getCategory3();
        this.category4 = product.getCategory4();
        this.healthBenefits = product.getHealthBenefits();
        this.allergens = product.getAllergens();
    }
}