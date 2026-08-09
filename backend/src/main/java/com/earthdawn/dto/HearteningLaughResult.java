package com.earthdawn.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Ergebnis von Herzliches Lachen: Buff auf die Soziale VK + Furcht-Widerstand der Verbündeten. */
@Data
@Builder
public class HearteningLaughResult {
    private String actorName;
    private int rollStep;
    private RollResult roll;
    private RollResult karmaRoll;
    private int targetNumber;
    private boolean success;
    private int successes;
    /** Bonus pro Erfolg × Erfolge = +bonus auf Soziale VK und Furcht-Widerstand. */
    private int bonus;
    private int duration;
    /** Namen der begünstigten Verbündeten. */
    private List<String> affectedAllies;
    private String description;
}
