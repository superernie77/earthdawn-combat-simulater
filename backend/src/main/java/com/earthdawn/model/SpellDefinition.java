package com.earthdawn.model;

import com.earthdawn.model.enums.*;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "spell_definitions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpellDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Disziplin-Name (z.B. "Elementarist", "Illusionist", "Magier", "Geisterbeschwörer") */
    private String discipline;

    /** Mindest-Kreis um den Zauber zu lernen */
    private int circle;

    /** Anzahl zu webender Fäden (0 = sofort zauberbar) */
    @Builder.Default
    private int threads = 0;

    /** Mindestwurf für Fadenweben */
    @Builder.Default
    private int weavingDifficulty = 0;

    /** Fester Mindestwurf für Spruchzauberei (0 = Zauberverteidigung des Ziels) */
    @Builder.Default
    private int castingDifficulty = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpellEffectType effectType;

    /** Schadens-/Heilungsstufe */
    @Builder.Default
    private int effectStep = 0;

    /** true = Mystische Rüstung, false = Physische Rüstung (Ausnahme) */
    @Column(columnDefinition = "boolean default true")
    @Builder.Default
    private boolean useMysticArmor = true;

    // --- Buff/Debuff-Felder ---

    @Enumerated(EnumType.STRING)
    private StatType modifyStat;

    @Enumerated(EnumType.STRING)
    private ModifierOperation modifyOperation;

    @Builder.Default
    private double modifyValue = 0;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TriggerContext modifyTrigger = TriggerContext.ALWAYS;

    /** Effekt-Dauer in Runden (-1 = permanent) */
    @Builder.Default
    private int duration = 0;

    @Column(length = 1000)
    private String description;

    /** Kurzbeschreibung des Effekts für Kampflog */
    @Column(length = 200)
    private String effectDescription;

    /**
     * Was passiert bei Übererfolgen?
     * "DAMAGE"   → +2 Schadensstufe pro Übererfolg (Standard für Schadenszauber)
     * "DURATION" → Dauer verlängert sich (2 Runden/Min pro Übererfolg — wird im Log angezeigt)
     * "TARGET"   → zusätzliches Ziel pro Übererfolg (nicht mechanisch umgesetzt)
     * "NONE"     → kein Bonus
     */
    @Builder.Default
    @Column(length = 20)
    private String extraSuccessEffect = "NONE";
}
