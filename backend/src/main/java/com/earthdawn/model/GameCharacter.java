package com.earthdawn.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "characters")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameCharacter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- Basisinfo ---
    @Column(nullable = false)
    private String name;
    private String playerName;
    private int circle;
    private long legendPoints;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "discipline_id")
    private DisciplineDefinition discipline;

    // --- Attribute (Wert = Step in ED4 FASA) ---
    private int dexterity;
    private int strength;
    private int toughness;
    private int perception;
    private int willpower;
    private int charisma;

    // --- Abgeleitete Werte (null = auto-berechnen) ---
    private Integer physicalDefense;
    private Integer spellDefense;
    private Integer socialDefense;
    private Integer woundThreshold;
    private Integer unconsciousnessRating;
    private Integer deathRating;
    private Integer physicalArmor;
    private Integer mysticArmor;

    // --- Waffe ---
    private String weaponName;
    /** Basis-Schadensstufe der Waffe (vor STR-Bonus) */
    private int weaponDamageStep;

    // --- Karma ---
    @Column(columnDefinition = "integer default 5")
    @Builder.Default private int karmaModifier = 5;
    private int karmaMax;
    private int karmaCurrent;

    // --- Währung ---
    private int gold;
    private int silver;
    private int copper;

    // --- Aktueller Zustand ---
    private int currentDamage;
    private int wounds;

    // --- Notizen ---
    @Column(length = 4000)
    private String notes;

    // --- Beziehungen ---
    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<CharacterTalent> talents = new ArrayList<>();

    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<CharacterSkill> skills = new ArrayList<>();

    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<Equipment> equipment = new ArrayList<>();

    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<CharacterSpell> spells = new ArrayList<>();
}
