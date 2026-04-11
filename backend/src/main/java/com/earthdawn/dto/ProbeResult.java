package com.earthdawn.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProbeResult {
    private String probeName;
    private int step;
    private String diceExpression;
    private List<DieRollDetail> dice;
    private int total;
    private int targetNumber;
    private boolean success;
    private int extraSuccesses;
    private String successDegree;
    private boolean karmaUsed;
    private RollResult karmaRoll;
}
