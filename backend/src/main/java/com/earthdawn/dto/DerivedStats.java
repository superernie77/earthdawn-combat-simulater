package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DerivedStats {
    private int physicalDefense;
    private int spellDefense;
    private int socialDefense;
    private int woundThreshold;
    private int unconsciousnessRating;
    private int deathRating;
    private int initiativeStep;
    private int physicalArmor;
    private int mysticArmor;
    private int karmaStep;
    private int recoveryStep;
    private int carryingCapacity;
}
