package com.earthdawn.dto;

import lombok.Data;

@Data
public class ThreadweaveRequest {
    private Long sessionId;
    private Long casterCombatantId;
    private Long spellId;
    private boolean spendKarma;
}
