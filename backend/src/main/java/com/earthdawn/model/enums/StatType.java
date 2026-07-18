package com.earthdawn.model.enums;

public enum StatType {
    PHYSICAL_DEFENSE,
    SPELL_DEFENSE,
    SOCIAL_DEFENSE,
    PHYSICAL_ARMOR,
    MYSTIC_ARMOR,
    INITIATIVE_STEP,
    ATTACK_STEP,
    DAMAGE_STEP,
    WOUND_THRESHOLD,
    UNCONSCIOUSNESS_RATING,
    DEATH_RATING,
    KARMA_STEP,
    RECOVERY_STEP,
    CARRYING_CAPACITY,
    /** Bonusstufen auf die Ausweichen-Reaktionsprobe (Basis 0; z.B. Nebelschild). */
    DODGE_STEP,
    /** Bewegungsrate auf der Kampfkarte in Feldern (Basis = characters.movement_hexes). */
    MOVEMENT_HEXES
}
