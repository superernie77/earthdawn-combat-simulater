package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiposteResult {
    private String defenderName;
    private String attackerName;
    private int riposteStep;
    private RollResult riposteRoll;
    private RollResult karmaRoll;
    private int attackTotal;
    private boolean riposteAttempted;
    private boolean success;
    private int extraSuccesses;
    private int damageCost;

    /** Gegenangriff ausgelöst (Übererfolge > 0 und pariert) */
    private boolean counterAttack;
    private int counterAttackTotal;
    private boolean counterAttackHit;
    private int counterDamageStep;
    private RollResult counterDamageRoll;
    private int counterArmorValue;
    private int counterNetDamage;
    private boolean counterWoundDealt;
    private KnockdownResult counterKnockdown;

    /** Schaden durch Annehmen des Angriffs (wenn Riposte fehlschlägt oder nicht versucht) */
    private int incomingNetDamage;

    private String description;
}
