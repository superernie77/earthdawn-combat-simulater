package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnockdownResult {
    private String targetName;
    private int rollStep;
    private RollResult roll;
    private int targetNumber;
    private boolean knockedDown;
    private String description;
}
