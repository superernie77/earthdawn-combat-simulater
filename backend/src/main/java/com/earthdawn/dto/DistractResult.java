package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistractResult {
    private String actorName;
    private String targetName;
    private int rollStep;
    private RollResult roll;
    private RollResult karmaRoll;
    /** Soziale Verteidigung des Ziels */
    private int socialDefense;
    private boolean success;
    /** Basis-Erfolg + Übererfolge */
    private int successes;
    /** −successes auf KV des Anwenders (Toter Winkel rückwärts) */
    private int actorPenalty;
    /** −successes auf KV des Ziels (Toter Winkel für Verbündete) */
    private int targetPenalty;
    private int damageTaken;
    private String description;
}
