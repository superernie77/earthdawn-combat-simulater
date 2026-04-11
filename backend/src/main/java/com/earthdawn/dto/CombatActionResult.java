package com.earthdawn.dto;

import com.earthdawn.model.enums.ActionType;
import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CombatActionResult {
    private String actorName;
    private String targetName;
    private ActionType actionType;

    private boolean aggressiveAttack;
    private int attackStep;
    private RollResult attackRoll;
    private RollResult karmaRoll;
    private int defenseValue;
    private boolean hit;

    private int extraSuccesses;
    private int damageStep;
    private RollResult damageRoll;
    private int armorValue;
    private int netDamage;
    private boolean woundDealt;
    private int newWounds;
    private int totalWounds;
    private int woundThreshold;
    private boolean targetDefeated;

    private List<String> attackBonusNotes;

    /** Treffer, aber Schaden noch nicht angewandt — Ziel kann Ausweichen versuchen. */
    private boolean hitPendingDodge;
    private Long dodgeDefenderId;
    private int pendingDodgeDamage;

    /** Niedergeschlagen-Probe Ergebnis (wenn Wunde zugefügt). */
    private KnockdownResult knockdownResult;

    private String description;
}
