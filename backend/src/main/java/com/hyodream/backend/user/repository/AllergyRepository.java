package com.hyodream.backend.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hyodream.backend.user.domain.Allergy;

import java.util.Optional;

public interface AllergyRepository extends JpaRepository<Allergy, Long> {
    Optional<Allergy> findByName(String name);
}