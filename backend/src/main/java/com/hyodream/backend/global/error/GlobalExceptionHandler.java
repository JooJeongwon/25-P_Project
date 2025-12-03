package com.hyodream.backend.global.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice // ⭐ 모든 컨트롤러에서 발생하는 에러를 여기서 잡겠다!
public class GlobalExceptionHandler {

    // 1. 비즈니스 로직 에러 (RuntimeException) 처리
    // 예: "상품이 없습니다", "구매자가 아닙니다", "중복 리뷰입니다" 등
    // 우리가 throw new RuntimeException("메시지") 한 것들이 다 여기로 옴
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException e) {
        // 500 에러 대신 -> 400 Bad Request로 바꿔서 리턴
        ErrorResponse response = new ErrorResponse(400, e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // 2. 그 외 알 수 없는 모든 에러 (Exception) 처리
    // 예: NullPointerException, DB 연결 실패 등 예상치 못한 에러
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        e.printStackTrace(); // 서버 로그에는 에러 내용 출력 (디버깅용)

        // 클라이언트에게는 "서버 에러"라고만 알려줌 (보안상 상세 내용 숨김)
        ErrorResponse response = new ErrorResponse(500, "서버 내부 오류가 발생했습니다.");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}