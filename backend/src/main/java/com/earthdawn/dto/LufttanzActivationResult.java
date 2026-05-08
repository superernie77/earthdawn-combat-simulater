package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LufttanzActivationResult {
    private String actorName;
    private int rank;
    /** Bonus auf INITIATIVE_STEP (= Rang). */
    private int initiativeBonus;
    /** Schaden, den der Anwender für die Aktivierung erleidet. */
    private int damageTaken;
    private String description;
}
