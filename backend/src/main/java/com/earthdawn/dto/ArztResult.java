package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArztResult {
    /** Behandlungsmodus: VERLETZUNG (verlorene LP) oder WUNDE (Wundversorgung). */
    private String mode;
    private String healerName;
    private String woundedName;
    private int wounds;
    private int targetNumber;
    private int perStep;
    private int skillRank;
    private int rollStep;
    private RollResult roll;
    private boolean success;
    /** VERLETZUNG: gewährter Bonus (= Rang) auf die nächste Erholungsprobe; sonst 0. */
    private int bonusGranted;
    private int newPendingBonus;
    /** Anzahl aktuell versorgter Wunden (deren −1-Malus bei Erholungsproben unterdrückt ist). */
    private int woundsTreated;
    /** Verbleibende Verbandszeug-Anwendungen des Heilers nach dieser Behandlung. */
    private int verbandszeugRemaining;
}
