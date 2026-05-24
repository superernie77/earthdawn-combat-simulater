package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArztResult {
    private String healerName;
    private String woundedName;
    private int wounds;
    private int targetNumber;
    private int perStep;
    private int skillRank;
    private int rollStep;
    private RollResult roll;
    private boolean success;
    private int bonusGranted;
    private int newPendingBonus;
}
