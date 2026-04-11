package com.earthdawn.model;

import com.earthdawn.model.enums.SourceType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "active_effects")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveEffect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "combatant_state_id")
    @JsonIgnore
    private CombatantState combatantState;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    private SourceType sourceType;

    private Long sourceId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "active_effect_modifiers",
            joinColumns = @JoinColumn(name = "effect_id"))
    @Builder.Default
    private List<ModifierEntry> modifiers = new ArrayList<>();

    /**
     * Verbleibende Runden. -1 = permanent (bis dispelled).
     */
    @Builder.Default
    private int remainingRounds = -1;

    /** true = negativer Effekt/Zustand (rot), false = Buff (grün) */
    @Builder.Default
    private boolean negative = false;
}
