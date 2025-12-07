package com.hyodream.backend.auth.controller;

import com.hyodream.backend.auth.dto.LoginRequestDto;
import com.hyodream.backend.auth.dto.SignupRequestDto;
import com.hyodream.backend.auth.service.AuthService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth") // 공통 주소
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 회원가입 API
    // POST http://localhost:8080/api/auth/signup
    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody SignupRequestDto dto) {
        authService.signup(dto);
        return ResponseEntity.ok("회원가입 성공!");
    }

    // 로그인 API
    // POST http://localhost:8080/api/auth/login
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequestDto dto) {
        // 서비스에게 로그인 시키고 Access Token 받아옴
        String accessToken = authService.login(dto.getUsername(), dto.getPassword());

        // JSON 형태로 응답
        // { "accessToken": "eyJh..." }
        Map<String, String> response = new HashMap<>();
        response.put("accessToken", accessToken);

        return ResponseEntity.ok(response);
    }

    // 로그아웃 API
    // POST http://localhost:8080/api/auth/logout
    // Header: Authorization: Bearer {토큰}
    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestHeader("Authorization") String authHeader) {
        // "Bearer " 제거하고 순수 토큰만 추출
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);
            authService.logout(accessToken);
            return ResponseEntity.ok("로그아웃 되었습니다.");
        } else {
            throw new RuntimeException("토큰이 없습니다.");
        }
    }
}