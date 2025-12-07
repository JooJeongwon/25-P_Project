package com.hyodream.backend.global.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.hyodream.backend.global.util.JwtUtil;

import java.io.IOException;
import java.util.ArrayList;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 헤더에서 토큰 꺼내기 ("Authorization: Bearer eyJ...")
        String authHeader = request.getHeader("Authorization");

        // 토큰이 없거나, "Bearer "로 시작하지 않으면 그냥 다음 필터로 넘김 (-> 결국 인증 실패로 403 뜸)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // "Bearer " 글자 떼고 순수 토큰만 추출
        String token = authHeader.substring(7);

        // 토큰이 유효한지 검사 (JwtUtil 이용)
        if (jwtUtil.validateToken(token)) {

            // 유효한 토큰이지만, 로그아웃 했는지 Redis 확인
            String isLogout = redisTemplate.opsForValue().get(token);

            if (isLogout == null) { // "로그아웃 기록이 없으면" (정상 토큰이면)

                // 유효하면 사용자 이름 꺼내기
                String username = jwtUtil.getUsername(token);

                // 사용자 인증 확인 (SecurityContext에 등록)
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        username, null, new ArrayList<>()); // 권한은 일단 비워둠

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 스프링 시큐리티에게 로그인 사용자 알려줌
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        // 다음 단계로 진행 (Controller 등)
        filterChain.doFilter(request, response);
    }
}