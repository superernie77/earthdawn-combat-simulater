package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcrobaticDefenseResult {
    private String actorName;
    private int rollStep;
    private RollResult roll;
    private RollResult karmaRoll;
    /** Höchste KV der Gegner — der Mindestwurf */
    private int targetNumber;
    private boolean success;
    /** Basis-Erfolg + Übererfolge */
    private int successes;
    /** +2 × successes auf KV */
    private int bonusApplied;
    private int damageTaken;
    private String description;
}
