package com.hyodream.backend.global.client.crawler.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Data
public class CrawlerResponseDto {
    private ProductInfo product;
    
    @JsonProperty("review_count")
    private int reviewCount;
    
    // Naver returns a list of dictionaries for reviews. 
    // We can map important fields or use Map if dynamic.
    // Based on crawler.py logic, it returns the raw "contents" list from Naver API.
    private List<Map<String, Object>> reviews;
    
    private String error;

    @Getter
    @Setter
    public static class ProductInfo {
        private String payReferenceKey;
        private String productNo;
        private String name;
        private int price;
        
        @JsonProperty("original_price")
        private int originalPrice;
        
        @JsonProperty("discount_rate")
        private int discountRate;
        
        private String seller;
        private String channelNo;
        
        @JsonProperty("review_count")
        private int reviewCount;
        
        private double rating;
        private List<String> images;
    }
}
