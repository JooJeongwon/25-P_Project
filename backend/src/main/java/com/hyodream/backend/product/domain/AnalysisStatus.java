package com.hyodream.backend.product.domain;

public enum AnalysisStatus {
    NONE,       // 분석 전 / 데이터 없음
    PROGRESS,   // 분석/크롤링 진행 중
    COMPLETED,  // 분석 완료
    FAILED      // 실패
}
