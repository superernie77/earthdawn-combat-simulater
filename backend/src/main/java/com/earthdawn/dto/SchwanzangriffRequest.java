package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchwanzangriffRequest {
    private Long sessionId;
    private Long actorCombatantId;
    private Long defenderCombatantId;
    /** Optionale am Schwanz befestigte Waffe (muss tailWeapon=true sein); null = waffenloser Schwanzschlag. */
    private Long weaponId;
    private int bonusSteps;
    private boolean spendKarma;
}
