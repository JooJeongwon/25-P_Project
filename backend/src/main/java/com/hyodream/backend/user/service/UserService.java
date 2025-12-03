package com.hyodream.backend.user.service;

import com.hyodream.backend.auth.dto.SignupRequestDto; // 👈 에러 해결: import 추가
import com.hyodream.backend.user.domain.Allergy;
import com.hyodream.backend.user.domain.Disease;
import com.hyodream.backend.user.domain.HealthGoal;
import com.hyodream.backend.user.domain.User;
import com.hyodream.backend.user.domain.UserAllergy;
import com.hyodream.backend.user.domain.UserDisease;
import com.hyodream.backend.user.domain.UserHealthGoal;
import com.hyodream.backend.user.dto.HealthInfoRequestDto;
import com.hyodream.backend.user.repository.AllergyRepository;
import com.hyodream.backend.user.repository.DiseaseRepository;
import com.hyodream.backend.user.repository.HealthGoalRepository;
import com.hyodream.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DiseaseRepository diseaseRepository;
    private final AllergyRepository allergyRepository;
    private final HealthGoalRepository healthGoalRepository;

    // 건강 정보 저장/수정
    @Transactional
    public void updateHealthInfo(String username, HealthInfoRequestDto dto) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        // 1. 지병 처리
        user.getDiseases().clear();
        if (dto.getDiseaseNames() != null) {
            for (String name : dto.getDiseaseNames()) {
                Disease d = diseaseRepository.findByName(name)
                        .orElseThrow(() -> new RuntimeException("지병을 찾을 수 없음: " + name));
                user.addDisease(UserDisease.createUserDisease(d));
            }
        }

        // 2. 알레르기 처리
        user.getAllergies().clear();
        if (dto.getAllergyNames() != null) {
            for (String name : dto.getAllergyNames()) {
                Allergy a = allergyRepository.findByName(name)
                        .orElseThrow(() -> new RuntimeException("알레르기를 찾을 수 없음: " + name));
                user.addAllergy(UserAllergy.createUserAllergy(a));
            }
        }

        // 3. 기대효과 처리
        user.getHealthGoals().clear();
        if (dto.getHealthGoalNames() != null) {
            for (String name : dto.getHealthGoalNames()) {
                HealthGoal h = healthGoalRepository.findByName(name)
                        .orElseThrow(() -> new RuntimeException("기대효과를 찾을 수 없음: " + name));
                user.addHealthGoal(UserHealthGoal.createUserHealthGoal(h));
            }
        }
    }

    // 내 정보 조회 (컨트롤러에서 필요해서 추가)
    @Transactional(readOnly = true)
    public User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자 없음"));
    }

    // 회원 탈퇴
    @Transactional
    public void deleteUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자 없음"));
        userRepository.delete(user);
    }

    // 프로필 수정
    @Transactional
    public void updateProfile(String username, SignupRequestDto dto) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자 없음"));

        if (dto.getName() != null)
            user.setName(dto.getName());
        if (dto.getPhone() != null)
            user.setPhone(dto.getPhone());
    }
}