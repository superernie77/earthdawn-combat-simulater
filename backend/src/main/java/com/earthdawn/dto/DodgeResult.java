package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DodgeResult {
    private String defenderName;
    private int rollStep;
    private RollResult roll;
    private RollResult karmaRoll;
    private int attackTotal;
    private boolean success;
    private int damageCost;
    private int damageStep;
    private RollResult damageRoll;
    private int armorValue;
    private int netDamageApplied;
    private int newWounds;
    private int totalWounds;
    private int woundThreshold;
    private boolean targetDefeated;
    private KnockdownResult knockdownResult;
    private String description;
}
