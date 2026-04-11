package com.earthdawn.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FreeActionRequest {
    private Long sessionId;
    private Long actorCombatantId;
    private Long targetCombatantId;  // null wenn SELF-Effekt
    private Long talentId;
    private int bonusSteps;
    private boolean spendKarma;
}
