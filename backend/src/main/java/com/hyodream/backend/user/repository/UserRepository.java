package com.hyodream.backend.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hyodream.backend.user.domain.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // 아이디 중복 검사용(회원가입)
    boolean existsByUsername(String username);

    // 아이디로 정보찾기 (로그인)
    Optional<User> findByUsername(String username);
}