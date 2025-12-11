package com.hyodream.backend.product.controller;

import com.hyodream.backend.global.util.JwtUtil;
import com.hyodream.backend.product.domain.EventType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "Event API", description = "사용자 행동(클릭, 장바구니 등) 이벤트 수집")
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;
    private final com.hyodream.backend.product.repository.ProductRepository productRepository;

    @Operation(summary = "상품 클릭/조회 이벤트 수집", description = "사용자가 상품을 클릭하면 해당 상품의 가장 구체적인 카테고리를 자동 추출하여 실시간 관심사에 반영합니다.")
    @PostMapping("/view")
    public void logProductView(
            @Parameter(description = "상품 ID") @RequestParam Long productId,
            @Parameter(description = "이벤트 타입 (CLICK, CART, PURCHASE)") @RequestParam(defaultValue = "CLICK") EventType type,
            @Parameter(description = "비로그인 유저 세션 ID") @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @Parameter(description = "로그인 유저 토큰") @RequestHeader(value = "Authorization", required = false) String token
    ) {
        // 1. 상품 정보 조회 및 카테고리 추출
        String targetCategory = "기타"; // 기본값
        var productOpt = productRepository.findById(productId);
        
        if (productOpt.isPresent()) {
            var p = productOpt.get();
            // 가장 구체적인 카테고리 우선 추출 (4 -> 3 -> 2 -> 1)
            if (p.getCategory4() != null && !p.getCategory4().isEmpty()) targetCategory = p.getCategory4();
            else if (p.getCategory3() != null && !p.getCategory3().isEmpty()) targetCategory = p.getCategory3();
            else if (p.getCategory2() != null && !p.getCategory2().isEmpty()) targetCategory = p.getCategory2();
            else if (p.getCategory1() != null && !p.getCategory1().isEmpty()) targetCategory = p.getCategory1();
            
            // 만약 효능(HealthBenefit)이 있다면, 효능을 관심사로 잡는 게 더 강력할 수 있음 (선택 사항)
            // 여기서는 카테고리 우선으로 하되, 필요시 로직 변경 가능
        }

        String userId = sessionId; // 기본값은 세션ID

        // 토큰이 있으면 진짜 username을 꺼냄
        if (token != null && token.startsWith("Bearer ")) {
            String jwt = token.substring(7);
            if (jwtUtil.validateToken(jwt)) {
                userId = jwtUtil.getUsername(jwt); // "test_user"가 나옴
            }
        }

        if (userId == null)
            userId = "unknown";

        // Redis Stream에 이벤트 발행 (Producer)
        Map<String, String> fields = new HashMap<>();
        fields.put("userId", userId);
        fields.put("productId", productId.toString());
        fields.put("category", targetCategory); // 추출한 카테고리 사용
        fields.put("type", type.name()); // 이벤트 타입 저장 (CLICK, CART 등)
        fields.put("timestamp", String.valueOf(System.currentTimeMillis()));

        redisTemplate.opsForStream().add("product-view-stream", fields);

        System.out.println("Event [" + type + "] Published for: " + userId + ", Category: " + targetCategory);
    }
}