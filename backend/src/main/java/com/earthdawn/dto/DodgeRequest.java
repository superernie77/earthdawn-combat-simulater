package com.earthdawn.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DodgeRequest {
    private Long sessionId;
    private Long defenderCombatantId;
    private boolean dodgeAttempted;
    private int bonusSteps;
    private boolean spendKarma;
}
