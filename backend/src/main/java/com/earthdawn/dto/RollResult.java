package com.earthdawn.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RollResult {
    private int step;
    private String diceExpression;
    private List<DieRollDetail> dice;
    private int total;
    private boolean exploded;
}
