package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NachtretenRequest {
    private Long sessionId;
    private Long actorCombatantId;
    private Long defenderCombatantId;
    private int bonusSteps;
    private boolean spendKarma;
}
