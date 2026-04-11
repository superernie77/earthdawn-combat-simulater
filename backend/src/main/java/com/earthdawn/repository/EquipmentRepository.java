package com.earthdawn.repository;

import com.earthdawn.model.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {
    List<Equipment> findByCharacterId(Long characterId);
}
