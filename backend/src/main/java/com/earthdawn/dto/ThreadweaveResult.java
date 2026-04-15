package com.earthdawn.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ThreadweaveResult {
    private String casterName;
    private String spellName;
    private int rollStep;
    private RollResult roll;
    private RollResult karmaRoll;
    private int targetNumber;
    private boolean success;
    private int threadsWoven;
    private int threadsRequired;
    private boolean readyToCast;
    private String description;
}
