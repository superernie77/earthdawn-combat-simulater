package com.earthdawn.repository;

import com.earthdawn.model.GameCharacter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CharacterRepository extends JpaRepository<GameCharacter, Long> {
    List<GameCharacter> findByNameContainingIgnoreCase(String name);
    List<GameCharacter> findByOrderByNameAsc();
}
