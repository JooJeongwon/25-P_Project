package com.hyodream.backend.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hyodream.backend.user.domain.HealthGoal;

import java.util.Optional;

public interface HealthGoalRepository extends JpaRepository<HealthGoal, Long> {
    Optional<HealthGoal> findByName(String name);
}