package com.earthdawn.model.enums;

/** Hindernis-Arten auf der Kampfkarte. */
public enum ObstacleType {
    /** Blockiert Bewegung. */
    WALL,
    /** Blockiert Bewegung nur, wenn geschlossen ({@code doorOpen == false}). */
    DOOR,
    /** Blockiert Bewegung. */
    TREE,
    /** Blockiert Bewegung. */
    ROCK,
    /** Möbelstück (Tisch, Truhe, …) — blockiert Bewegung. */
    FURNITURE
}
