package com.earthdawn.dto;

import lombok.*;

/**
 * Ergebnis der Löwenherz-Aktivierung. Der Bonus wirkt anschließend auf jede
 * Willenskraftprobe zum Abschütteln von Talenten, Zaubern und Fähigkeiten.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoewenherzResult {
    private String actorName;
    private int rank;
    private int resistBonus;
    private int damageTaken;
    private String description;
}
