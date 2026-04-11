package com.earthdawn.repository;

import com.earthdawn.model.SkillDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SkillDefinitionRepository extends JpaRepository<SkillDefinition, Long> {
    Optional<SkillDefinition> findByName(String name);
    boolean existsByName(String name);
}
