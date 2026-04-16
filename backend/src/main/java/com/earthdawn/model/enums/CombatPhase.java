package com.earthdawn.model.enums;

/**
 * Phasen innerhalb einer Kampfrunde.
 * DECLARATION: Alle Teilnehmer wählen Haltung (aggressiv/defensiv/keine) + Handlungstyp (Waffe/Zauber).
 *              Initiative wird erst gewürfelt, wenn alle deklariert haben.
 * ACTION:      Initiative ist gewürfelt, Kombattanten handeln in Reihenfolge.
 */
public enum CombatPhase {
    DECLARATION,
    ACTION
}
