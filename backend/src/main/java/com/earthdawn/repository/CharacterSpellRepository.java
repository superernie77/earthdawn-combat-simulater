package com.earthdawn.repository;

import com.earthdawn.model.CharacterSpell;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CharacterSpellRepository extends JpaRepository<CharacterSpell, Long> {
}
