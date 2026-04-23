package com.earthdawn.dto;

import com.earthdawn.model.enums.SpellEffectType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SpellCastResult {
    private String casterName;
    private String targetName;
    private String spellName;
    private SpellEffectType effectType;

    private int castStep;
    private RollResult castRoll;
    private RollResult karmaRoll;
    private int defenseValue;
    private boolean success;
    private int extraSuccesses;

    // Schadenszauber
    private int damageStep;
    private int damageStepBonus;
    private RollResult damageRoll;
    private int armorValue;
    private int netDamage;
    private boolean woundDealt;
    private int newWounds;
    private int totalWounds;
    private int woundThreshold;
    private boolean targetDefeated;
    private KnockdownResult knockdownResult;

    // Buff/Debuff/Heal
    private String effectApplied;
    private int effectDuration;
    private int healedAmount;

    private String description;
}
