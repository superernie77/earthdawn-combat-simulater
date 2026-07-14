package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NeutralizeMagicResult {
    private String actorName;
    /** Träger des Effekts. */
    private String targetName;
    private String effectName;
    /** Gewählte Stufe des Effekts. */
    private int effectLevel;
    /** Mindestwurf = Effektstufe + 10. */
    private int targetNumber;
    /** Würfelstufe (WIL-Step + Rang + Bonus − Wunden). */
    private int rollStep;
    private RollResult roll;
    private RollResult karmaRoll;
    private boolean success;
    /** true = Effekt wurde entfernt. */
    private boolean effectRemoved;
    private String description;
}
