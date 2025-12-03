package com.hyodream.backend.user.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class HealthInfoRequestDto {
    private List<String> diseaseNames; // 지병
    private List<String> allergyNames; // 알레르기
    private List<String> healthGoalNames; // 기대효과
}