package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FearResult {
    private String actorName;
    private String targetName;
    /** Würfelstufe (WIL-Step + Rang + Bonus − Wunden) */
    private int rollStep;
    private RollResult roll;
    private RollResult karmaRoll;
    /** Mystische Verteidigung des Ziels */
    private int spellDefense;
    private boolean success;
    /** Erfolge gesamt (1 + Übererfolge) */
    private int successes;
    /** Tatsächlicher Malus je Probe (= Erfolge × −2) */
    private int penalty;
    /** Dauer in Runden (= Talentrang) */
    private int duration;
    /** Mindestwurf der Willenskraft-Widerstandsprobe (= Verängstigen-Stufe: WIL-Step + Rang) */
    private int resistTargetNumber;
    private String description;
}
