package com.earthdawn.dto;

import lombok.Data;

@Data
public class SpellCastRequest {
    private Long sessionId;
    private Long casterCombatantId;
    private Long targetCombatantId;
    private Long spellId;
    private boolean spendKarma;
}
