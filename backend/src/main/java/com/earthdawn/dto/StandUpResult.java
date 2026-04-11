package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StandUpResult {
    private String actorName;
    /** true = Aufstehen (Hauptaktion), false = Aufspringen (Probe) */
    private boolean simpleStandUp;
    // Aufspringen only:
    private int rollStep;
    private RollResult roll;
    private RollResult karmaRoll;
    private int targetNumber;
    private boolean success;
    private int damageTaken;
    // common:
    private boolean stillKnockedDown;
    private String description;
}
