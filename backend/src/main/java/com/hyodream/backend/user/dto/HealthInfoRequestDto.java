package com.hyodream.backend.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class HealthInfoRequestDto {

    @Schema(description = "지병(질환) 목록", example = "[\"당뇨\", \"고혈압\"]")
    @JsonProperty("diseases")
    private List<String> diseaseNames; // 지병

    @Schema(description = "알레르기 목록", example = "[\"우유\", \"땅콩\"]")
    @JsonProperty("allergies")
    private List<String> allergyNames; // 알레르기

    @Schema(description = "건강 목표(기대효과) 목록", example = "[\"면역력 강화\", \"관절 건강\"]")
    @JsonProperty("goals")
    private List<String> healthGoalNames; // 기대효과
}