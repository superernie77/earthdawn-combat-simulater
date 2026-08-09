package com.earthdawn.dto;

import lombok.Data;

/** Anfrage für das Talent Herzliches Lachen (stärkt die Moral der Verbündeten). */
@Data
public class HearteningLaughRequest {
    private Long sessionId;
    private Long actorCombatantId;
    private int bonusSteps;
    private boolean spendKarma;
}
