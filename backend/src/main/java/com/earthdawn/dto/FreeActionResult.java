package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FreeActionResult {
    private String actorName;
    private String targetName;
    private String talentName;
    private int rollStep;
    private RollResult roll;
    private RollResult karmaRoll;
    private int defenseValue;
    private boolean success;
    private int extraSuccesses;
    private boolean effectApplied;
    private int damageTaken;
    private String description;
}
