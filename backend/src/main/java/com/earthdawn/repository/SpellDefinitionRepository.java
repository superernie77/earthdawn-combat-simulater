package com.earthdawn.repository;

import com.earthdawn.model.SpellDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpellDefinitionRepository extends JpaRepository<SpellDefinition, Long> {
    List<SpellDefinition> findByDisciplineOrderByCircleAscNameAsc(String discipline);
    List<SpellDefinition> findAllByOrderByDisciplineAscCircleAscNameAsc();
    Optional<SpellDefinition> findByNameAndDiscipline(String name, String discipline);
}
