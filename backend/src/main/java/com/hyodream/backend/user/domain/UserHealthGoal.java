package com.hyodream.backend.user.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class UserHealthGoal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "health_goal_id")
    private HealthGoal healthGoal;

    public static UserHealthGoal createUserHealthGoal(HealthGoal healthGoal) {
        UserHealthGoal userHealthGoal = new UserHealthGoal();
        userHealthGoal.setHealthGoal(healthGoal);
        return userHealthGoal;
    }
}