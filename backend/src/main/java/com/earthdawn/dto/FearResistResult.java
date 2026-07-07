package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FearResistResult {
    private String targetName;
    /** Würfelstufe der Widerstandsprobe (WIL-Step − Wunden) */
    private int resistStep;
    private RollResult roll;
    /** Mindestwurf (= Verängstigen-Stufe des Adepten) */
    private int targetNumber;
    /** true = Furcht abgeschüttelt, Effekt entfernt */
    private boolean success;
    private String description;
}
