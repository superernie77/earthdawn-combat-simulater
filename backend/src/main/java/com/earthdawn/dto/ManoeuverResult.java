package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManoeuverResult {
    private String actorName;
    private String targetName;
    private int rollStep;
    private RollResult roll;
    private RollResult karmaRoll;
    private int defenseValue;
    private boolean success;
    private int successes;
    private int defenseBonus;
    private int attackBonus;
    private int damageTaken;
    private String description;
}
