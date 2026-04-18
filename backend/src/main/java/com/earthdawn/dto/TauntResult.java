package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TauntResult {
    private String actorName;
    private String targetName;
    /** Würfelstufe (CHA-Step + Rang + Bonus) */
    private int rollStep;
    private RollResult roll;
    private RollResult karmaRoll;
    /** Soziale Verteidigung des Ziels */
    private int socialDefense;
    private boolean success;
    private int extraSuccesses;
    /** Tatsächlicher Malus je Probe (= extraSuccesses × −1) */
    private int penalty;
    /** Dauer in Runden (= Talentrang) */
    private int duration;

    /** Starrsinn-Gegenprobe des Ziels (null wenn Ziel kein Starrsinn-Talent hat) */
    private RollResult resistRoll;
    private int resistStep;
    /** Wenn resistRoll != null: hat die Gegenprobe die Wirkung negiert? */
    private boolean resisted;

    private String description;
}
