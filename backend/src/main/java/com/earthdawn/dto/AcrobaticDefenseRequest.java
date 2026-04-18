package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcrobaticDefenseRequest {
    private Long sessionId;
    private Long actorCombatantId;
    private int bonusSteps;
    private boolean spendKarma;
}
