package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZweitwaffeRequest {
    private Long sessionId;
    private Long actorCombatantId;
    private Long defenderCombatantId;
    private Long weaponId;
    private int bonusSteps;
    private boolean spendKarma;
}
