package com.earthdawn.dto;

import lombok.*;

/**
 * Synchronisierter Modal-Status pro Kampf-Session: wird bei jeder result-produzierenden Aktion
 * inkrementiert und über den Session-Broadcast verteilt, damit alle Zuschauer dasselbe Modal
 * sehen. Ein expliziter Dismiss-Aufruf bumpt die Version mit type=null/payload=null,
 * woraufhin alle Clients ihr Modal schließen.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveModalState {
    /** Monoton wachsend pro Session. Frontend triggert beim Wechsel die Modal-Aktion. */
    private int version;
    /** Modal-Typ (z.B. "ATTACK_RESULT", "INITIATIVE", "TIGERSPRUNG", ...). null = geschlossen. */
    private String type;
    /** Result-DTO als rohes Objekt (Jackson serialisiert je nach Typ). null wenn type=null. */
    private Object payload;
}
