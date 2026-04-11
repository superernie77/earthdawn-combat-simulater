package com.earthdawn.model;

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

    @OneToMany(mappedBy = "combatantState", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<ActiveEffect> activeEffects = new ArrayList<>();
}
