package com.earthdawn.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProbeResult {
    private String probeName;
    private int step;
    private String diceExpression;
    private List<DieRollDetail> dice;
    private int total;
    private int targetNumber;
    private boolean success;
    private int extraSuccesses;
    private String successDegree;
    private boolean karmaUsed;
    private RollResult karmaRoll;
    /** Wundenmalus, der bereits in 'step' eingerechnet wurde — zur Anzeige im Result-Panel. */
    private int woundPenalty;
    /** Ausrüstungs-Probenbonus (z.B. +2 durch Leichte Stiefel), bereits in 'step' eingerechnet. */
    private int equipmentBonus;
}
