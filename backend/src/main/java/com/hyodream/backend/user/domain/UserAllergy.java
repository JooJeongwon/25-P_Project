package com.hyodream.backend.user.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class UserAllergy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allergy_id")
    private Allergy allergy;

    public static UserAllergy createUserAllergy(Allergy allergy) {
        UserAllergy userAllergy = new UserAllergy();
        userAllergy.setAllergy(allergy);
        return userAllergy;
    }
}