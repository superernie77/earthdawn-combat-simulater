package com.earthdawn.model;

import com.earthdawn.model.enums.ObstacleType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

/** Ein Hindernis auf der Kampfkarte (axiale Hexkoordinaten q, r). */
@Entity
@Table(name = "map_obstacles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MapObstacle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private CombatSession combatSession;

    @Enumerated(EnumType.STRING)
    @Column(name = "obstacle_type", nullable = false, length = 20)
    private ObstacleType type;

    @Column(nullable = false)
    private int q;

    @Column(nullable = false)
    private int r;

    /** Nur für DOOR relevant: offene Türen sind passierbar. */
    @Column(name = "door_open", nullable = false)
    @Builder.Default
    private boolean doorOpen = false;

    /** Blockiert dieses Hindernis derzeit die Bewegung? */
    public boolean blocksMovement() {
        return type != ObstacleType.DOOR || !doorOpen;
    }
}
