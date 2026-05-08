package com.earthdawn.dto;

import lombok.*;

/**
 * Lufttanz-Bonusangriff: zusätzlicher Nahkampfangriff nach erfolgreichem Treffer mit
 * Initiative-Vorsprung ≥ 10. Ziel und Waffe stehen bereits im pendingLufttanz*-State des
 * Anwenders fest und werden nicht aus dem Request übernommen.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LufttanzAttackRequest {
    private Long sessionId;
    private Long attackerCombatantId;
    private int bonusSteps;
    private boolean spendKarma;
    private boolean spendKarmaForDamage;
}
