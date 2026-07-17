package com.earthdawn.service;

import com.earthdawn.model.CombatSession;
import com.earthdawn.model.CombatantState;
import com.earthdawn.model.GameCharacter;
import com.earthdawn.model.MapObstacle;
import com.earthdawn.model.enums.CombatStatus;
import com.earthdawn.model.enums.ObstacleType;
import com.earthdawn.repository.CombatSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

/**
 * Kampfkarte: Hexdistanz, Erreichbarkeit (BFS), Bewegungsregeln (Initiative-Reihenfolge,
 * Budget, GM-Override), Hindernisse und Türen.
 */
@ExtendWith(MockitoExtension.class)
class CombatMapServiceTest {

    @Mock CombatSessionRepository sessionRepo;
    @Mock CombatService combatService;

    @InjectMocks CombatMapService mapService;

    private CombatSession session;
    private CombatantState hero;   // Initiative-Order 0 → ist dran
    private CombatantState orc;    // Initiative-Order 1

    @BeforeEach
    void setUp() {
        session = CombatSession.builder()
                .id(1L).name("Test").status(CombatStatus.ACTIVE)
                .mapEnabled(true).mapWidth(10).mapHeight(8)
                .combatants(new ArrayList<>()).log(new ArrayList<>())
                .obstacles(new ArrayList<>())
                .build();
        hero = combatant(10L, "Held", 0, 2, 2, 5);
        orc = combatant(20L, "Ork", 1, 7, 2, 3);
        session.getCombatants().add(hero);
        session.getCombatants().add(orc);

        lenient().when(combatService.findById(1L)).thenReturn(session);
        lenient().when(combatService.findCombatant(eq(session), eq(10L))).thenReturn(hero);
        lenient().when(combatService.findCombatant(eq(session), eq(20L))).thenReturn(orc);
    }

    // --- Hexmathematik ---

    @Test
    void hexDistance_isSymmetricAndMatchesKnownValues() {
        assertThat(HexUtil.distance(2, 2, 2, 2)).isZero();
        assertThat(HexUtil.distance(2, 2, 3, 2)).isEqualTo(1);
        assertThat(HexUtil.distance(0, 0, 5, 0)).isEqualTo(5);
        // Distanz ist symmetrisch
        assertThat(HexUtil.distance(1, 3, 6, 5)).isEqualTo(HexUtil.distance(6, 5, 1, 3));
        // Jeder der 6 Nachbarn hat Distanz 1
        for (int[] n : HexUtil.neighbors(4, 3)) {
            assertThat(HexUtil.distance(4, 3, n[0], n[1])).isEqualTo(1);
        }
        for (int[] n : HexUtil.neighbors(4, 4)) { // ungerade Zeile
            assertThat(HexUtil.distance(4, 4, n[0], n[1])).isEqualTo(1);
        }
    }

    // --- Bewegung ---

    @Test
    void move_activeCombatant_withinBudget_succeedsAndConsumesBudget() {
        mapService.moveCombatant(1L, 10L, 5, 2, false);

        assertThat(hero.getMapQ()).isEqualTo(5);
        assertThat(hero.getMapR()).isEqualTo(2);
        assertThat(hero.getMovedHexesThisRound()).isEqualTo(3);
    }

    @Test
    void move_secondMoveInSameRound_usesRemainingBudget() {
        mapService.moveCombatant(1L, 10L, 5, 2, false); // 3 verbraucht, 2 übrig
        mapService.moveCombatant(1L, 10L, 6, 2, false); // 1 weiter

        assertThat(hero.getMovedHexesThisRound()).isEqualTo(4);

        assertThatThrownBy(() -> mapService.moveCombatant(1L, 10L, 9, 2, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nicht erreichbar");
    }

    @Test
    void move_beyondMovementRate_isRejected() {
        assertThatThrownBy(() -> mapService.moveCombatant(1L, 10L, 9, 2, false)) // Distanz 7 > 5
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nicht erreichbar");
        assertThat(hero.getMapQ()).isEqualTo(2);
    }

    @Test
    void move_notYourTurn_isRejected() {
        assertThatThrownBy(() -> mapService.moveCombatant(1L, 20L, 6, 2, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Initiative-Reihenfolge");
    }

    @Test
    void move_afterActiveActed_nextCombatantMayMove() {
        hero.setHasActedThisRound(true);
        mapService.moveCombatant(1L, 20L, 6, 2, false);
        assertThat(orc.getMapQ()).isEqualTo(6);
    }

    @Test
    void move_gmOverride_ignoresTurnBudgetAndPhase() {
        session.setStatus(CombatStatus.SETUP);
        mapService.moveCombatant(1L, 20L, 0, 7, true); // nicht dran, weit weg, SETUP

        assertThat(orc.getMapQ()).isZero();
        assertThat(orc.getMapR()).isEqualTo(7);
        assertThat(orc.getMovedHexesThisRound()).isZero(); // Override kostet kein Budget
    }

    @Test
    void move_ontoOccupiedCell_isRejected() {
        hero.setMapQ(6);
        hero.setMapR(2);
        assertThatThrownBy(() -> mapService.moveCombatant(1L, 10L, 7, 2, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("besetzt");
    }

    @Test
    void move_unplacedCombatant_isRejected() {
        hero.setMapQ(null);
        hero.setMapR(null);
        assertThatThrownBy(() -> mapService.moveCombatant(1L, 10L, 3, 2, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nicht auf der Karte");
    }

    @Test
    void move_withoutMapEnabled_isRejected() {
        session.setMapEnabled(false);
        assertThatThrownBy(() -> mapService.moveCombatant(1L, 10L, 3, 2, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("keine Kampfkarte");
    }

    // --- Hindernisse blockieren Wege ---

    @Test
    void move_aroundWall_costsTheDetour() {
        // Wand-Riegel bei q=3 über die Zeilen 0-4: direkter Weg (3 Felder) blockiert
        for (int r = 0; r <= 4; r++) {
            session.getObstacles().add(obstacle(100L + r, ObstacleType.WALL, 3, r));
        }
        // Ziel (4,2) wäre direkt 2 Felder — mit Mauer braucht der Umweg mehr Budget als 5
        assertThatThrownBy(() -> mapService.moveCombatant(1L, 10L, 4, 2, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nicht erreichbar");
    }

    @Test
    void move_throughOpenDoor_isAllowed_closedBlocks() {
        for (int r = 0; r <= 7; r++) {
            if (r == 2) {
                session.getObstacles().add(MapObstacle.builder()
                        .id(200L).type(ObstacleType.DOOR).q(3).r(2).doorOpen(false).build());
            } else {
                session.getObstacles().add(obstacle(300L + r, ObstacleType.WALL, 3, r));
            }
        }
        // Tür zu → kein Durchkommen
        assertThatThrownBy(() -> mapService.moveCombatant(1L, 10L, 4, 2, false))
                .isInstanceOf(IllegalStateException.class);

        // Tür öffnen → Weg frei (2,2 → 3,2 → 4,2 = 2 Felder)... aber auf der Tür darf man stehen?
        // Offene Tür ist passierbar UND betretbar.
        mapService.toggleDoor(1L, 200L);
        mapService.moveCombatant(1L, 10L, 4, 2, false);
        assertThat(hero.getMapQ()).isEqualTo(4);
        assertThat(hero.getMovedHexesThisRound()).isEqualTo(2);
    }

    // --- Platzierung & Hindernis-Verwaltung ---

    @Test
    void placeCombatant_freeCell_setsPosition_minusOneRemoves() {
        mapService.placeCombatant(1L, 20L, 4, 4);
        assertThat(orc.getMapQ()).isEqualTo(4);

        mapService.placeCombatant(1L, 20L, -1, -1);
        assertThat(orc.getMapQ()).isNull();
        assertThat(orc.getMapR()).isNull();
    }

    @Test
    void placeCombatant_onObstacleOrOccupied_isRejected() {
        session.getObstacles().add(obstacle(400L, ObstacleType.TREE, 4, 4));
        assertThatThrownBy(() -> mapService.placeCombatant(1L, 20L, 4, 4))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Hindernis");
        assertThatThrownBy(() -> mapService.placeCombatant(1L, 20L, 2, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("besetzt");
        assertThatThrownBy(() -> mapService.placeCombatant(1L, 20L, 99, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("außerhalb");
    }

    @Test
    void addObstacle_validCell_appends_duplicateOrOccupiedRejected() {
        mapService.addObstacle(1L, ObstacleType.ROCK, 5, 5);
        assertThat(session.getObstacles()).hasSize(1);

        assertThatThrownBy(() -> mapService.addObstacle(1L, ObstacleType.WALL, 5, 5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bereits ein Hindernis");
        assertThatThrownBy(() -> mapService.addObstacle(1L, ObstacleType.WALL, 2, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Kombattant");
    }

    @Test
    void toggleDoor_onNonDoor_isRejected() {
        session.getObstacles().add(obstacle(500L, ObstacleType.TREE, 5, 5));
        assertThatThrownBy(() -> mapService.toggleDoor(1L, 500L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nur Türen");
    }

    @Test
    void configureMap_clampsDimensions() {
        mapService.configureMap(1L, true, 500, 2);
        assertThat(session.getMapWidth()).isEqualTo(60);
        assertThat(session.getMapHeight()).isEqualTo(6);
    }

    // --- Helpers ---

    private CombatantState combatant(long id, String name, int order, Integer q, Integer r, int movement) {
        GameCharacter ch = GameCharacter.builder()
                .id(id).name(name).movementHexes(movement)
                .equipment(new ArrayList<>()).talents(new ArrayList<>())
                .skills(new ArrayList<>()).spells(new ArrayList<>())
                .build();
        return CombatantState.builder()
                .id(id).character(ch).initiativeOrder(order)
                .mapQ(q).mapR(r)
                .activeEffects(new ArrayList<>())
                .build();
    }

    private MapObstacle obstacle(long id, ObstacleType type, int q, int r) {
        return MapObstacle.builder().id(id).type(type).q(q).r(r).build();
    }
}
