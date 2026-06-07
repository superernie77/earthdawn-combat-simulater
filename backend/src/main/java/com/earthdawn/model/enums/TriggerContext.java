package com.earthdawn.model.enums;

public enum TriggerContext {
    ALWAYS,
    ON_MELEE_ATTACK,
    ON_RANGED_ATTACK,
    ON_SPELL_CAST,
    ON_MELEE_DEFENSE,
    ON_RANGED_DEFENSE,
    ON_SPELL_DEFENSE,
    ON_DAMAGE_DEALT,
    ON_DAMAGE_RECEIVED,
    ON_INITIATIVE,
    ON_RECOVERY_TEST,
    ON_SOCIAL_ACTION,
    /** Effekt liegt auf dem Verteidiger und schwächt Angriffe gegen ihn (z.B. Phantomkrieger −3). */
    ON_INCOMING_ATTACK
}
