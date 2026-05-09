package com.earthdawn.model;

import com.earthdawn.dto.InitiativeRollDetail;
import com.earthdawn.model.enums.CombatPhase;
import com.earthdawn.model.enums.CombatStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "combat_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CombatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Builder.Default
    private int round = 0;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CombatStatus status = CombatStatus.SETUP;

    /** Aktuelle Phase der Runde: DECLARATION (Ansage) oder ACTION (nach Initiative). */
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20) default 'ACTION'")
    @Builder.Default
    private CombatPhase phase = CombatPhase.ACTION;

    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "combatSession", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("initiativeOrder ASC")
    @Builder.Default
    private List<CombatantState> combatants = new ArrayList<>();

    @OneToMany(mappedBy = "combatSession", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id DESC")
    @Builder.Default
    private List<CombatLog> log = new ArrayList<>();

    /**
     * Letzte Initiative-Probe (pro Kombattant: Würfel + Total + Reihenfolge). Wird bei jeder
     * Initiative-Probe gesetzt und über WebSocket mit der Session verteilt — damit das Frontend
     * ein Detail-Modal zeigen kann. Nicht persistiert.
     */
    @Transient
    private List<InitiativeRollDetail> lastInitiativeRolls;

    /** Rundennummer, in der die letzte Initiative-Probe gewürfelt wurde — für UI-Trigger. Nicht persistiert. */
    @Transient
    @Builder.Default
    private int lastInitiativeRollRound = 0;
}
