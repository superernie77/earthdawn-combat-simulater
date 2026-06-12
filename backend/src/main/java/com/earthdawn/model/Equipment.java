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

    /** Rüstung/Schild: Initiativemalus */
    @Builder.Default
    private int initiativePenalty = 0;

    /** Schild: Bonus auf Körperliche Verteidigung (KV) */
    @Builder.Default
    private int physicalDefenseBonus = 0;

    /** Schild: Bonus auf Mystische Verteidigung (MV) */
    @Builder.Default
    private int mysticDefenseBonus = 0;

    /** Trank: Anzahl im Besitz */
    @Builder.Default
    private int quantity = 1;

    /** Trank: Heilungsstufe-Bonus (wird zur Zähigkeitsstufe addiert; z.B. 7 für Heiltrank) */
    @Builder.Default
    private int healStep = 0;

    /** Krallenhand-Marker: vom gleichnamigen Talent automatisch verwaltete Waffe (kann nicht manuell entfernt werden, erlaubt Karma auf Schadenswurf). */
    @Column(columnDefinition = "boolean default false")
    @Builder.Default
    private boolean clawWeapon = false;

    /** Heiltrank-Marker: gibt eine Extra-Erholungsprobe (ignoriert Tageslimit). false = Erholungstrank (verbraucht einen normalen Slot). */
    @Column(columnDefinition = "boolean default false")
    @Builder.Default
    private boolean extraRecovery = false;

    /**
     * Nur für ARMOR und SHIELD: Gibt an, ob dieses Stück gerade angelegt ist.
     * Nur das aktive Stück trägt zur Rüstung/Verteidigung/Initiativemalus bei.
     * Für andere Typen (WEAPON, POTION) immer true.
     */
    @Column(columnDefinition = "boolean default true")
    @Builder.Default
    private boolean active = true;

    // --- Verzweiflungsschlag-Amulett (Typ AMULET) ---

    /**
     * Amulett: ist es gerade geladen (einsatzbereit)? Nach einer Anwendung false,
     * bis es über eine geopferte Erholungsprobe (≥3) wieder aufgeladen wird.
     */
    @Column(columnDefinition = "boolean default true")
    @Builder.Default
    private boolean charged = true;

    /**
     * Amulett: true = wirkt auf Zauber (Zauber-/Schadenswurf), false = wirkt auf
     * physische Angriffe (Angriffs-/Schadenswurf).
     */
    @Column(columnDefinition = "boolean default false")
    @Builder.Default
    private boolean amuletForSpell = false;

    /** Amulett: Stufen-Bonus bei Anwendung (Standard 6). Wird auf Angriffs- oder Schadenswurf addiert. */
    @Builder.Default
    private int amuletStepBonus = 0;

    /**
     * Blutmagie-Schaden dieses Gegenstands (Amulett: Standard 3). Reduziert dauerhaft
     * Bewusstlosigkeits- und Todesschwelle, solange der Gegenstand getragen wird.
     */
    @Builder.Default
    private int bloodMagicDamage = 0;
}
