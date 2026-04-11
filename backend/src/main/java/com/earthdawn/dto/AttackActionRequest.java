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
    private boolean aggressiveAttack;
    private boolean defensiveStance;
}
