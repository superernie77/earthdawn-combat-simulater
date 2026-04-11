package com.earthdawn.model;

import com.earthdawn.model.enums.EquipmentType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "character_equipment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id", nullable = false)
    @JsonIgnore
    private GameCharacter character;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EquipmentType type;

    @Column(length = 500)
    private String description;

    /** Waffe: Bonus auf Schadenstufe */
    @Builder.Default
    private int damageBonus = 0;

    /** Rüstung: Physische Rüstung (gegen Nahkampf/Fernkampf) */
    @Builder.Default
    private int physicalArmor = 0;

    /** Rüstung: Mystische Rüstung (gegen Zauber) */
    @Builder.Default
    private int mysticalArmor = 0;
}
