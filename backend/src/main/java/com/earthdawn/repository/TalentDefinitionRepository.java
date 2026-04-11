package com.earthdawn.repository;

import com.earthdawn.model.TalentDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TalentDefinitionRepository extends JpaRepository<TalentDefinition, Long> {
    Optional<TalentDefinition> findByName(String name);
    boolean existsByName(String name);
    List<TalentDefinition> findByNameIn(List<String> names);
}
