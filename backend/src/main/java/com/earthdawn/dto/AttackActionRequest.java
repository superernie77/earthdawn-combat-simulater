package com.earthdawn.dto;

import com.earthdawn.model.enums.ActionType;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttackActionRequest {
    private Long sessionId;
    private Long attackerCombatantId;
    private Long defenderCombatantId;
    private ActionType actionType;
    private Long talentId;
    private Long weaponId;
    private int bonusSteps;
    private boolean spendKarma;
    /** Karma zusätzlich auf den Schadenswurf einsetzen (nur erlaubt bei Krallenhand-Waffen). */
    private boolean spendKarmaForDamage;
    /** Blattschuss ankündigen: erlaubt nach Fehlschlag weitere Karmawürfel (max. Rang) — nur bei RANGED_ATTACK. */
    private boolean useBlattschuss;
    private boolean aggressiveAttack;
    private boolean defensiveStance;
    /** Verzweiflungsschlag-Amulette (physisch), die +6 auf den Angriffswurf geben. Equipment-IDs. */
    private java.util.List<Long> amuletAttackIds;
    /** Verzweiflungsschlag-Amulette (physisch), die +6 auf den Schadenswurf geben. Equipment-IDs. */
    private java.util.List<Long> amuletDamageIds;
}
