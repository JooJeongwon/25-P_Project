package com.hyodream.backend.product.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReviewRating {
    GOOD("좋아요"),
    AVERAGE("보통이에요"),
    BAD("별로예요");

    private final String description;
}