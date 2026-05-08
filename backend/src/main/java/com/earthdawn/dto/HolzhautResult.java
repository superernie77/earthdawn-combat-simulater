package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HolzhautResult {
    /** Talentrang. */
    private int rank;
    /** ZÄH-Step, der der Probe zugrunde liegt. */
    private int toughnessStep;
    /** Würfelstufe der Probe (toughnessStep + rank). */
    private int rollStep;
    /** Detailliertes Wurfergebnis. */
    private RollResult roll;
    /** Neuer aktiver Bonus (= total der Probe). */
    private int bonus;
    /** Vorheriger Bonus (für UI-Anzeige bei Überschreiben), 0 wenn vorher inaktiv. */
    private int previousBonus;
    /** Bei /end: Anzahl der durch Holzhaut geheilten Schadenspunkte (zwischen 0 und Bonus). */
    private int healed;
}
