package com.earthdawn.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FieldUpdateRequest {
    /** Feldname: silver, gold, copper, karma, damage, wounds, legendPoints, notes, ... */
    private String field;
    /** Delta: positiv = addieren, negativ = subtrahieren */
    private Integer delta;
    /** Absoluter Wert (überschreibt delta wenn gesetzt) */
    private Integer absoluteValue;
}
