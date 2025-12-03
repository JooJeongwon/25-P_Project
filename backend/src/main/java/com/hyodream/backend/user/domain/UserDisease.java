package com.hyodream.backend.user.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class UserDisease {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disease_id")
    private Disease disease;

    // 생성 메서드
    public static UserDisease createUserDisease(Disease disease) {
        UserDisease userDisease = new UserDisease();
        userDisease.setDisease(disease);
        return userDisease;
    }
}