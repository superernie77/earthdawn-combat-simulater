package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CombatSenseRequest {
    private Long sessionId;
    private Long actorCombatantId;
    private Long targetCombatantId;
    private int bonusSteps;
    private boolean spendKarma;
}
