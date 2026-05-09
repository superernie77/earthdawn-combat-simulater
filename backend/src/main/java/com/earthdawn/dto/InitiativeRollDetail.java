package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiativeRollDetail {
    private Long combatantId;
    private String combatantName;
    private boolean npc;
    /** Effektive Initiative-Stufe (inkl. Boni wie Tigersprung/Lufttanz und Rüstungsmalus). */
    private int step;
    /** Detaillierter Würfelwurf — Würfel-Liste, Total, Explosionen. */
    private RollResult roll;
    /** Endgültiges Total (= roll.total). */
    private int total;
    /** Reihenfolge in der Initiative (0 = handelt zuerst). */
    private int order;
    /** Aktive Effekte mit Auswirkung auf die Initiative-Probe (z.B. "Tigersprung +3", "Lufttanz +5"). */
    private java.util.List<String> bonusNotes;
}
