package com.hyodream.backend.user.controller;

import com.hyodream.backend.auth.dto.SignupRequestDto; // 수정용 DTO
import com.hyodream.backend.user.domain.User; // 👈 에러 해결: import 추가
import com.hyodream.backend.user.dto.HealthInfoRequestDto;
import com.hyodream.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication; // 👈 에러 해결: import 추가
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 건강 정보 저장
    @PostMapping("/health")
    public ResponseEntity<String> updateHealthInfo(
            @RequestBody HealthInfoRequestDto dto,
            Authentication auth) {
        userService.updateHealthInfo(auth.getName(), dto);
        return ResponseEntity.ok("건강 정보가 저장되었습니다.");
    }

    // 내 정보 조회
    @GetMapping("/profile")
    public ResponseEntity<User> getMyProfile(Authentication auth) {
        return ResponseEntity.ok(userService.getUser(auth.getName()));
    }

    // 회원 수정
    @PutMapping("/profile")
    public ResponseEntity<String> updateProfile(@RequestBody SignupRequestDto dto, Authentication auth) {
        userService.updateProfile(auth.getName(), dto);
        return ResponseEntity.ok("회원 정보가 수정되었습니다.");
    }

    // 회원 탈퇴
    @DeleteMapping
    public ResponseEntity<String> deleteUser(Authentication auth) {
        userService.deleteUser(auth.getName());
        return ResponseEntity.ok("회원 탈퇴 처리되었습니다.");
    }
}