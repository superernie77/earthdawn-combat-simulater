package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NeutralizeMagicRequest {
    private Long sessionId;
    private Long actorCombatantId;
    /** Kombattant, der den zu neutralisierenden Effekt trägt. */
    private Long targetCombatantId;
    /** ID des zu neutralisierenden ActiveEffect. */
    private Long effectId;
    /** Vom Anwender/GM gewählte Stufe des Effekts (Effekte tragen keine eigene Stufe). */
    private int effectLevel;
    private int bonusSteps;
    private boolean spendKarma;
}
