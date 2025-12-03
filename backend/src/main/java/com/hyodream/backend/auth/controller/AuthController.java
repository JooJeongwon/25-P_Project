package com.hyodream.backend.auth.controller;

import com.hyodream.backend.auth.dto.LoginRequestDto;
import com.hyodream.backend.auth.dto.SignupRequestDto;
import com.hyodream.backend.auth.service.AuthService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap; // import 추가
import java.util.Map; // import 추가

@RestController
@RequestMapping("/api/auth") // 공통 주소
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 회원가입 API
    // 주소: POST http://localhost:8080/api/auth/signup
    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody SignupRequestDto dto) {
        authService.signup(dto);
        return ResponseEntity.ok("회원가입 성공!");
    }

    // 👇👇 [추가] 로그인 API
    // POST http://localhost:8080/api/auth/login
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequestDto dto) {
        // 1. 서비스에게 로그인 시키고 Access Token 받아옴
        String accessToken = authService.login(dto.getUsername(), dto.getPassword());

        // 2. JSON 형태로 응답 (프론트가 쓰기 편하게)
        // { "accessToken": "eyJh..." }
        Map<String, String> response = new HashMap<>();
        response.put("accessToken", accessToken);

        return ResponseEntity.ok(response);
    }
}