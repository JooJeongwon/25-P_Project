package com.hyodream.backend.global.client.review.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ReviewAnalysisResponseDto {
    @JsonProperty("total_reviews")
    private int totalReviews;

    @JsonProperty("positive_percent")
    private double positivePercent;

    @JsonProperty("negative_percent")
    private double negativePercent;

    @JsonProperty("positive_count")
    private int positiveCount;

    @JsonProperty("negative_count")
    private int negativeCount;
}
