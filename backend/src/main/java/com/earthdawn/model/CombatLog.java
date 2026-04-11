package com.earthdawn.model;

import com.earthdawn.model.enums.ActionType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "combat_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CombatLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "combat_session_id")
    @JsonIgnore
    private CombatSession combatSession;

    private int round;

    @Column(name = "logged_at")
    private LocalDateTime loggedAt;

    @Enumerated(EnumType.STRING)
    private ActionType actionType;

    private String actorName;
    private String targetName;

    @Column(length = 2000)
    private String description;

    @Column(length = 4000)
    private String rollDetailsJson;

    private boolean success;
}
