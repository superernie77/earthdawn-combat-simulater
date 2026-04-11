package com.earthdawn.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DieRollDetail {
    private int sides;
    private List<Integer> rolls;
    private int total;
    private boolean exploded;
}
