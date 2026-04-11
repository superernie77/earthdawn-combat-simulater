package com.earthdawn.repository;

import com.earthdawn.model.CombatSession;
import com.earthdawn.model.enums.CombatStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CombatSessionRepository extends JpaRepository<CombatSession, Long> {
    List<CombatSession> findByOrderByCreatedAtDesc();
    List<CombatSession> findByStatusOrderByCreatedAtDesc(CombatStatus status);
}
