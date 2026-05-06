package com.earthdawn.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TigersprungResult {
    private String actorName;
    private int rank;
    private int initiativeBonus;
    private int newInitiative;
    private int damageTaken;
    private String description;
}
