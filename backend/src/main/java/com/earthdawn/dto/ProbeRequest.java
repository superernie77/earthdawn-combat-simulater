package com.earthdawn.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProbeRequest {
    private Long characterId;
    private Long talentId;
    private Long skillId;
    private int bonusSteps;
    private int targetNumber;
    private boolean spendKarma;
    private Long combatSessionId;
    private Long combatantStateId;
}
