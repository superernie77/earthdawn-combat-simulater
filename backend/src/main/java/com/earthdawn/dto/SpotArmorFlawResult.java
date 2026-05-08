package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpotArmorFlawResult {
    private String actorName;
    private String targetName;

    /** Würfelstufe (WAH-Step + Rang + Bonus) */
    private int rollStep;
    private RollResult roll;
    private RollResult karmaRoll;

    /** TN = max(MV des Ziels, physische Rüstung des Ziels) */
    private int targetNumber;
    /** Mystische Verteidigung des Ziels (zur Anzeige) */
    private int spellDefense;
    /** Physische Rüstung des Ziels (zur Anzeige) */
    private int physicalArmor;

    private boolean success;
    /** Erfolge gesamt (1 + Übererfolge bei Erfolg, 0 bei Fehlschlag) */
    private int successes;
    /** Schadensbonus pro physischem Angriff gegen das Ziel (= 2 × successes) */
    private int damageBonus;
    /** Dauer in Runden (= Talentrang) */
    private int duration;

    /** 1 Schaden Überanstrengung an den Anwender — immer 1, wenn die Aktion ausgeführt wurde. */
    private int strainCost;

    private String description;
}
