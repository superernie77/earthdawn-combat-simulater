package com.earthdawn.dto;

import lombok.*;

/**
 * Ergebnis der Sprint-Aktivierung: kein Würfelwurf, die Bewegungsrate steigt
 * für die aktuelle Runde um den Rang.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SprintResult {
    private String actorName;
    private int rank;
    private int movementBonus;
    private int newMovement;
    private int damageTaken;
    private String description;
}
