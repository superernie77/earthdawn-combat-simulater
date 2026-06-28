package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryTestResult {
    private int toughnessStep;
    private int woundPenalty;
    private int rollStep;
    private int bonusSteps;
    private RollResult roll;
    /** Karmawürfel (W6/Stufe 4) auf die Erholungsprobe, falls eingesetzt — sonst null. */
    private RollResult karmaRoll;
    private int healed;
    private int remainingDamage;
    private int recoveryTestsRemaining;
    private int recoveryTestsMax;
    private boolean usedExtraSlot;
    private String potionName;
}
