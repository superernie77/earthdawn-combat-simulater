package com.earthdawn.model;

import com.earthdawn.model.enums.ModifierOperation;
import com.earthdawn.model.enums.StatType;
import com.earthdawn.model.enums.TriggerContext;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModifierEntry {

    @Enumerated(EnumType.STRING)
    @Column(name = "target_stat")
    private StatType targetStat;

    @Enumerated(EnumType.STRING)
    @Column(name = "mod_operation")
    private ModifierOperation operation;

    @Column(name = "mod_value")
    private double value;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_context")
    @Builder.Default
    private TriggerContext triggerContext = TriggerContext.ALWAYS;

    @Column(name = "mod_description")
    private String description;
}
