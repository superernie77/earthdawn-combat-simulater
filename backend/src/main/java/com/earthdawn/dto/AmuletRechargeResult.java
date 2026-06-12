package com.earthdawn.dto;

import lombok.*;

/**
 * Ergebnis eines Amulett-Aufladeversuchs über eine geopferte Erholungsprobe.
 * Wurf ≥ 3 → Amulett wird aufgeladen, Heilung verfällt. Wurf < 3 → Aufladen scheitert,
 * die Probe heilt stattdessen regulär. In beiden Fällen wird ein Erholungs-Slot verbraucht.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmuletRechargeResult {
    private String amuletName;
    private int toughnessStep;
    private int woundPenalty;
    private int rollStep;
    private RollResult roll;
    /** true = Wurf ≥ 3, Amulett aufgeladen (Heilung geopfert). */
    private boolean recharged;
    /** Geheilter Schaden, falls der Wurf < 3 war und stattdessen regulär geheilt wurde (sonst 0). */
    private int healed;
    private int remainingDamage;
    private int recoveryTestsRemaining;
    private int recoveryTestsMax;
}
