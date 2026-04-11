package com.earthdawn.repository;

import com.earthdawn.model.DisciplineDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DisciplineRepository extends JpaRepository<DisciplineDefinition, Long> {
    Optional<DisciplineDefinition> findByName(String name);
    boolean existsByName(String name);
}
