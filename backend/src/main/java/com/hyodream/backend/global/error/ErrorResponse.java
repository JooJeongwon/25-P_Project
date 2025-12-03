package com.hyodream.backend.global.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ErrorResponse {
    private int status; // 에러 코드 (예: 400, 404)
    private String message; // 친절한 설명 (예: "구매자만 리뷰를 쓸 수 있습니다.")
}