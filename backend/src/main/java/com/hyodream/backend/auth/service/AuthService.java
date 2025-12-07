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
        // 아이디 중복 체크
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new RuntimeException("이미 존재하는 아이디입니다.");
        }

        // 주소 객체 생성
        Address address = new Address(dto.getCity(), dto.getStreet(), dto.getZipcode());

        // 유저 엔티티 생성
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setName(dto.getName());
        user.setPhone(dto.getPhone());
        user.setBirthDate(dto.getBirthDate());
        user.setAddress(address);

        // 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(dto.getPassword());
        user.setPassword(encodedPassword);

        // DB 저장
        userRepository.save(user);
    }

    // 로그인 메서드 추가
    @Transactional
    public String login(String username, String password) {
        // 아이디 검사
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("가입되지 않은 아이디입니다."));

        // 비밀번호 검사
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }

        // 토큰 발급
        String accessToken = jwtUtil.createAccessToken(user.getUsername());
        String refreshToken = jwtUtil.createRefreshToken(user.getUsername());

        // Redis에 Refresh Token 저장 (Key: username, Value: refreshToken)
        // 유효기간 7일 (TimeUnit.DAYS)
        redisTemplate.opsForValue().set(
                user.getUsername(),
                refreshToken,
                7,
                TimeUnit.DAYS);

        return accessToken; // 일단 Access Token만 반환 (나중엔 DTO로 반환 추천)
    }

    // 로그아웃 (Access Token을 블랙리스트에 추가)
    public void logout(String accessToken) {
        // 토큰 유효시간 계산 (남은 시간만큼만 블랙리스트에 저장)
        // 편의상 30분으로 고정하거나, JwtUtil에서 남은 시간 계산 메서드 추가 가능
        // 여기선 간단하게 30분(Access Token 수명)으로 설정
        long expiration = 30 * 60 * 1000L;

        // Redis에 저장 (Key: 토큰, Value: "logout")
        redisTemplate.opsForValue().set(accessToken, "logout", expiration, TimeUnit.MILLISECONDS);

        // Refresh Token도 삭제 (재로그인 방지)
        String username = jwtUtil.getUsername(accessToken);

        redisTemplate.delete("RT:" + username); // Refresh Token 키 규칙에 맞게 삭제
    }
}