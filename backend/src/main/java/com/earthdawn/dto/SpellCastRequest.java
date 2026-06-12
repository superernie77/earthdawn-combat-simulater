package com.earthdawn.dto;

import lombok.Data;

@Data
public class SpellCastRequest {
    private Long sessionId;
    private Long casterCombatantId;
    private Long targetCombatantId;
    private Long spellId;
    private boolean spendKarma;
    /** Verzweiflungsschlag-Amulette (Zauber), die +6 auf den Zauberwurf geben. Equipment-IDs. */
    private java.util.List<Long> amuletCastIds;
    /** Verzweiflungsschlag-Amulette (Zauber), die +6 auf den Schadenswurf geben. Equipment-IDs. */
    private java.util.List<Long> amuletDamageIds;
}
