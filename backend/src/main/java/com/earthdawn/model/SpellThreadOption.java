package com.earthdawn.model;

import com.earthdawn.model.enums.SpellThreadOptionType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

/** Eine wählbare Zusatzfaden-Option eines Zaubers (z.B. "Wirkung Verstärken (Wirkungsstufe +2)"). */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpellThreadOption {

    /** Anzeigetext, exakt wie im Regelwerk. */
    @Column(name = "label", length = 200)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "option_type", length = 30)
    private SpellThreadOptionType type;

    /** Nur für EFFECT_STEP relevant: Erhöhung der Wirkungsstufe je gewähltem Faden. */
    @Column(name = "option_value")
    @Builder.Default
    private int value = 0;
}
