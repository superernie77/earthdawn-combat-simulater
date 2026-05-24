package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrinkPotionResult {
    /** true = Heiltrank (Extra-Probe gerollt), false = Erholungstrank (Bonus für nächste Probe gesetzt). */
    private boolean extraRecovery;
    private String potionName;
    /** Für Erholungstrank: der jetzt ausstehende Bonus-Stufen (kumulativ). */
    private int pendingBonus;
    /** Für Heiltrank: das Ergebnis der sofortigen Extra-Erholungsprobe (null bei Erholungstrank). */
    private RecoveryTestResult recovery;
}
