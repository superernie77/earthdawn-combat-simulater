package com.earthdawn.model;

import com.earthdawn.model.enums.DeclaredActionType;
import com.earthdawn.model.enums.DeclaredStance;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "combatant_states")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CombatantState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "combat_session_id")
    @JsonIgnore
    private CombatSession combatSession;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "character_id")
    private GameCharacter character;

    /** Anzeigename — überschreibt character.name wenn gesetzt (z.B. bei Duplikaten: "Goblin 2") */
    private String displayName;

    // --- Kampfzustand (unabhängig vom persistierten Charakter) ---
    private int initiative;
    private int initiativeOrder;
    private int currentDamage;
    private int wounds;
    private int currentKarma;

    @Builder.Default
    private boolean defeated = false;

    @Builder.Default
    private boolean isNpc = false;

    /** Wurde diese Runde bereits eine Aktion ausgeführt? */
    @Column(columnDefinition = "boolean default false")
    @Builder.Default
    private boolean hasActedThisRound = false;

    /** Ausstehender Angriffsbonus (z.B. aus Aggressivem Angriff) — wird beim nächsten Angriff verbraucht. */
    @Column(columnDefinition = "integer default 0")
    @Builder.Default
    private int pendingAttackBonus = 0;

    /** Ausstehender Verteidigungsbonus (z.B. aus Defensiver Haltung) — wird beim nächsten eingehenden Angriff verbraucht. */
    @Column(columnDefinition = "integer default 0")
    @Builder.Default
    private int pendingDefenseBonus = 0;

    /** Ausstehender Schaden aus einem Treffer, der noch nicht angewandt wurde (wartet auf Ausweichen-Auflösung). */
    @Column(columnDefinition = "integer default 0")
    @Builder.Default
    private int pendingDodgeDamage = 0;

    /** Angriffswurf-Total des ausstehenden Treffers (für Ausweichen-Probe). */
    @Column(columnDefinition = "integer default 0")
    @Builder.Default
    private int pendingDodgeAttackTotal = 0;

    /** Ist der Kombattant niedergeschlagen? */
    @Column(columnDefinition = "boolean default false")
    @Builder.Default
    private boolean knockedDown = false;

    /** Schadensstufe des ausstehenden Treffers. */
    @Column(columnDefinition = "integer default 0")
    @Builder.Default
    private int pendingDamageStep = 0;

    /** Rüstungswert des ausstehenden Treffers. */
    @Column(columnDefinition = "integer default 0")
    @Builder.Default
    private int pendingArmorValue = 0;

    /** Schadenswurf des ausstehenden Treffers (JSON). */
    @Column(columnDefinition = "text")
    private String pendingDamageRollJson;

    // --- Zauber-Vorbereitung (Fadenweben) ---

    /** ID des aktuell vorbereiteten Zaubers (null = kein Zauber in Vorbereitung) */
    private Long preparingSpellId;

    /** Bereits gewobene Fäden */
    @Column(columnDefinition = "integer default 0")
    @Builder.Default
    private int threadsWoven = 0;

    /** Benötigte Fäden (kopiert vom Zauber bei Beginn) */
    @Column(columnDefinition = "integer default 0")
    @Builder.Default
    private int threadsRequired = 0;

    // --- Deklarationsphase (Ansage zu Rundenbeginn) ---

    /** Hat dieser Kombattant für die aktuelle Runde bereits deklariert? */
    @Column(columnDefinition = "boolean default false")
    @Builder.Default
    private boolean hasDeclared = false;

    /** Gewählte Haltung für diese Runde. */
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20) default 'NONE'")
    @Builder.Default
    private DeclaredStance declaredStance = DeclaredStance.NONE;

    /** Gewählter Handlungstyp für diese Runde. */
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20) default 'WEAPON'")
    @Builder.Default
    private DeclaredActionType declaredActionType = DeclaredActionType.WEAPON;

    @OneToMany(mappedBy = "combatantState", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<ActiveEffect> activeEffects = new ArrayList<>();
}
