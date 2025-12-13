package com.hyodream.backend.global.client.review.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewAnalysisRequestDto {
    private List<String> reviews;
}
