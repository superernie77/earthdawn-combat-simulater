package com.earthdawn.model.enums;

/** Art einer Zusatzfaden-Option eines Zaubers. */
public enum SpellThreadOptionType {
    /** Wird automatisch verrechnet: erhöht die Wirkungsstufe (effectStep) um `value`. */
    EFFECT_STEP,
    /**
     * Wird automatisch verrechnet: verstärkt den Buff-/Debuff-Modifikator des Zaubers um `value`
     * (in Wirkrichtung — positive Modifikatoren steigen, negative sinken weiter). Bei
     * Phantomkrieger skaliert zusätzlich der Angreifer-Malus mit.
     */
    BUFF_VALUE,
    /** Wird automatisch verrechnet: verlängert die Wirkungsdauer um `value` Runden je Faden. */
    DURATION_ROUNDS,
    /**
     * Wird nur angezeigt (Log/Modal) — die Auswirkung interpretiert der Spielleiter.
     * Für alles, was die Engine nicht kennt: Reichweite (kein Distanzsystem), zusätzliche Ziele
     * (Zauberpfad ist einzelzielig), Wirkungsdauer (Regel in Minuten, App rechnet in Runden),
     * sowie Boni auf Nicht-Kampf-Proben (Heimlichkeit/Wahrnehmung sind keine StatTypes).
     */
    DISPLAY
}
