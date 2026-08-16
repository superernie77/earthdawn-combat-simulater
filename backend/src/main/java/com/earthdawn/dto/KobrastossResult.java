package com.earthdawn.dto;

import lombok.*;

/**
 * Ergebnis der Kobrastoß-Ansage in der Ansagephase. Der eigentliche Vergleich gegen die
 * Initiative des Ziels — und damit der Angriffsbonus — wird erst beim Initiativewurf ermittelt.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KobrastossResult {
    private String actorName;
    private String targetName;
    private int rank;
    private int initiativeBonus;
    private int damageTaken;
    private String description;
}
