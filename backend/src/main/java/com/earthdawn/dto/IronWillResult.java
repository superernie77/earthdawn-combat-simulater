package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IronWillResult {
    private String actorName;
    private int rollStep;
    private RollResult roll;
    private RollResult karmaRoll;
    /** Angriffswurf des Zauberers / Talents gegen das der Widerstand gewürfelt wird */
    private int attackTotal;
    private boolean success;
    /** Effekt wurde abgewehrt */
    private boolean effectNegated;
    private int damageTaken;
    private String description;
}
