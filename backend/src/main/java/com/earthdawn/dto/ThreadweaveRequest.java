package com.earthdawn.dto;

import lombok.Data;

@Data
public class ThreadweaveRequest {
    private Long sessionId;
    private Long casterCombatantId;
    private Long spellId;
    private boolean spendKarma;

    /**
     * Index der gewählten Zusatzfaden-Option in {@code SpellDefinition.threadOptions}.
     * Nur erforderlich (und nur erlaubt), wenn dieser Faden ein Zusatzfaden ist —
     * also alle Pflichtfäden bereits gewoben sind.
     */
    private Integer extraThreadOptionIndex;
}
