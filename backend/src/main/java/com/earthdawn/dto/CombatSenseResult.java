package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CombatSenseResult {
    private String actorName;
    private String targetName;
    private int rollStep;
    private RollResult roll;
    private RollResult karmaRoll;
    /** Mystische Verteidigung des Ziels */
    private int mysticDefense;
    private boolean success;
    /** Basis-Erfolg + Übererfolge */
    private int successes;
    /** +2 × successes auf eigene KV (bis Ende der Runde) */
    private int defenseBonus;
    /** +2 × successes auf eigenen nächsten Angriff gegen dieses Ziel */
    private int attackBonus;
    private int damageTaken;
    private String description;
}
