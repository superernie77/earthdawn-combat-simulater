package com.earthdawn.dto;

import lombok.*;

/**
 * Flüchtiger Dialog-Status eines Kombattanten — wird nicht persistiert, nur über WebSocket
 * an alle Zuschauer der Session gesendet, damit Mitspieler sehen was ein Spieler gerade plant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DialogState {
    /** Aktionstyp: "ATTACK", "RANGED_ATTACK", "SPELL", "TAUNT", "DISTRACT", "RIPOSTE" etc. — null wenn kein Dialog offen. */
    private String actionType;
    /** Name des gewählten Ziels (zur Anzeige). */
    private String targetName;
    /** Name der gewählten Waffe (zur Anzeige). */
    private String weaponName;
    /** Name des gewählten Zaubers (zur Anzeige). */
    private String spellName;
}
