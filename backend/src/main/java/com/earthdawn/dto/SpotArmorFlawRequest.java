package com.earthdawn.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpotArmorFlawRequest {
    private Long sessionId;
    private Long actorCombatantId;
    private Long targetCombatantId;
    private int bonusSteps;
    private boolean spendKarma;
}
