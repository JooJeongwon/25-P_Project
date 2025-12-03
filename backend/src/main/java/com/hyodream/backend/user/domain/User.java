package com.hyodream.backend.user.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "users") // DB 예약어 'user'와 겹치지 않게 'users'로 지정
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 1) 로그인/회원가입 정보
    @Column(nullable = false, unique = true)
    private String username; // 아이디 (로그인용)

    @Column(nullable = false)
    private String password; // 암호화된 비밀번호 (비밀번호 확인은 DB에 저장 안 함!)

    @Column(nullable = false)
    private String name; // 실명

    // 2) 개인 신상 정보
    private String phone; // 전화번호

    private LocalDate birthDate; // 생년월일 (나이 계산 및 추천 알고리즘에 필수)

    @Embedded // 아까 만든 Address 클래스를 여기에 쏙 넣음
    private Address address;

    // 3) 마이페이지 - 지병/필터링 정보 (UserDisease와 1:N 관계)
    // "cascade = CascadeType.ALL" -> 유저 탈퇴하면 지병 정보도 같이 삭제됨
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserDisease> diseases = new ArrayList<>();

    // 2. 알레르기 (추가)
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserAllergy> allergies = new ArrayList<>();

    // 3. 기대효과 (추가)
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserHealthGoal> healthGoals = new ArrayList<>();

    // -- 편의 메서드 --
    // 지병 추가할 때 씀
    public void addDisease(UserDisease userDisease) {
        this.diseases.add(userDisease);
        userDisease.setUser(this);
    }

    public void addAllergy(UserAllergy userAllergy) {
        this.allergies.add(userAllergy);
        userAllergy.setUser(this);
    }

    public void addHealthGoal(UserHealthGoal userHealthGoal) {
        this.healthGoals.add(userHealthGoal);
        userHealthGoal.setUser(this);
    }
}