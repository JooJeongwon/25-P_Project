package com.hyodream.backend.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hyodream.backend.user.domain.Disease;

import java.util.Optional;

public interface DiseaseRepository extends JpaRepository<Disease, Long> {
    Optional<Disease> findByName(String name);
}