package com.earthdawn.model;

import com.earthdawn.model.enums.AttributeType;
import com.earthdawn.model.enums.FreeActionTarget;
import com.earthdawn.model.enums.StatType;
import com.earthdawn.model.enums.TriggerContext;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "talent_definitions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TalentDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    /** Welches Attribut dieser Talent verwendet */
    @Enumerated(EnumType.STRING)
    private AttributeType attribute;

    @Column(length = 1000)
    private String description;

    /** Ob dieses Talent einen Würfelwurf erlaubt */
    @Builder.Default
    private boolean testable = true;

    /** Ob dieses Talent ein Angriffstalent ist (Nahkampf, Fernkampf, Zauber) */
    @Column(columnDefinition = "boolean default false")
    @Builder.Default
    private boolean attackTalent = false;

    /**
     * Passive Modifikatoren die dieses Talent gewährt.
     * value = Bonus pro Rang (bei rankScaled=true) oder fixer Wert.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "talent_passive_modifiers",
            joinColumns = @JoinColumn(name = "talent_id"))
    @Builder.Default
    private List<ModifierEntry> passiveModifiers = new ArrayList<>();

    /**
     * Wenn true: modifier.value wird mit CharacterTalent.rank multipliziert
     * wenn passive Modifikatoren angewendet werden.
     */
    @Builder.Default
    private boolean rankScaled = false;

    // --- Freie Kampfaktion ---

    @Column(columnDefinition = "boolean default false")
    @Builder.Default
    private boolean freeAction = false;

    /** Gegen welche Verteidigung des Ziels gewürfelt wird */
    @Enumerated(EnumType.STRING)
    private StatType freeActionTestStat;

    /** Wer den Effekt erhält: Anwender (SELF) oder Ziel (TARGET) */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private FreeActionTarget freeActionEffectTarget = FreeActionTarget.SELF;

    /** Welcher Stat beeinflusst wird */
    @Enumerated(EnumType.STRING)
    private StatType freeActionModifyStat;

    /** In welchem Kontext der Modifier aktiv ist */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TriggerContext freeActionTriggerContext = TriggerContext.ALWAYS;

    /** Wert pro Übererfolg (positiv oder negativ) */
    @Column(columnDefinition = "double precision default 0")
    @Builder.Default
    private double freeActionValuePerSuccess = 0;

    /** Dauer des Effekts in Runden */
    @Column(columnDefinition = "integer default 1")
    @Builder.Default
    private int freeActionDuration = 1;

    /** Schaden den der Anwender bei Ausführung nimmt */
    @Column(columnDefinition = "integer default 0")
    @Builder.Default
    private int freeActionDamageCost = 0;
}
