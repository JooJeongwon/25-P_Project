package com.hyodream.backend.auth.service;

import com.hyodream.backend.auth.dto.SignupRequestDto;
import com.hyodream.backend.global.util.JwtUtil;
import com.hyodream.backend.user.domain.Address;
import com.hyodream.backend.user.domain.User;
import com.hyodream.backend.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public void signup(SignupRequestDto dto) {
        // 1. 아이디 중복 체크
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new RuntimeException("이미 존재하는 아이디입니다.");
        }

        // 2. 주소 객체 생성
        Address address = new Address(dto.getCity(), dto.getStreet(), dto.getZipcode());

        // 3. 유저 엔티티 생성
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setName(dto.getName());
        user.setPhone(dto.getPhone());
        user.setBirthDate(dto.getBirthDate());
        user.setAddress(address);

        // 4. 비밀번호 암호화 (핵심!)
        String encodedPassword = passwordEncoder.encode(dto.getPassword());
        user.setPassword(encodedPassword);

        // 5. DB 저장
        userRepository.save(user);
    }

    // ⭐ 로그인 메서드 추가
    @Transactional
    public String login(String username, String password) {
        // 1. 아이디 검사
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("가입되지 않은 아이디입니다."));

        // 2. 비밀번호 검사 (입력비번 vs DB암호화비번)
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }

        // 3. 토큰 발급
        String accessToken = jwtUtil.createAccessToken(user.getUsername());
        String refreshToken = jwtUtil.createRefreshToken(user.getUsername());

        // 4. Redis에 Refresh Token 저장 (Key: username, Value: refreshToken)
        // 유효기간 7일 (TimeUnit.DAYS)
        redisTemplate.opsForValue().set(
                user.getUsername(),
                refreshToken,
                7,
                TimeUnit.DAYS);

        return accessToken; // 일단 Access Token만 반환 (나중엔 DTO로 반환 추천)
    }
}