package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiposteRequest {
    private Long sessionId;
    private Long defenderCombatantId;
    private int bonusSteps;
    private boolean spendKarma;
    /** true = Riposte ausführen; false = Angriff annehmen (kein Parieren) */
    private boolean riposteAttempted;
}
