package com.earthdawn.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ThreadweaveResult {
    private String casterName;
    private String spellName;
    private int rollStep;
    private RollResult roll;
    private RollResult karmaRoll;
    private int targetNumber;
    private boolean success;
    private int threadsWoven;
    private int threadsRequired;
    private boolean readyToCast;

    /** War dieser Faden ein Zusatzfaden (statt eines Pflichtfadens)? */
    private boolean extraThread;

    /** Bei Erfolg eines Zusatzfadens: Text der gewählten Option. */
    private String extraThreadLabel;

    /** Anzahl bisher gewobener Zusatzfäden. */
    private int extraThreadCount;

    /** Maximal erlaubte Zusatzfäden (= Fadenweben-Rang). */
    private int extraThreadMax;

    private String description;
}
