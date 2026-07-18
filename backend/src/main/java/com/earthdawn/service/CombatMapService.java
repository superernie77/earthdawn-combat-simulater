package com.earthdawn.service;

import com.earthdawn.model.CombatSession;
import com.earthdawn.model.CombatantState;
import com.earthdawn.model.MapObstacle;
import com.earthdawn.model.enums.ActionType;
import com.earthdawn.model.enums.CombatStatus;
import com.earthdawn.model.enums.ObstacleType;
import com.earthdawn.model.enums.StatType;
import com.earthdawn.model.enums.TriggerContext;
import com.earthdawn.repository.CombatSessionRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;

/**
 * Kampfkarte: Platzierung, Bewegung und Hindernisse.
 *
 * Bewusst als eigener Service neben CombatService — die Karte ist eine optionale Zusatzschicht
 * und fasst keinerlei Kampf-Logik an. Jede Änderung wird über den bestehenden
 * Session-Broadcast an alle Clients (Tracker + Kartenfenster) verteilt.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CombatMapService {

    private final CombatSessionRepository sessionRepo;
    private final CombatService combatService;
    private final ModifierAggregator modifiers;

    /** Effektive Bewegungsrate in Feldern — Basis vom Charakterbogen, moduliert durch Effekte (z.B. Schmerzen ×0,5). */
    int effectiveMovement(CombatantState c) {
        return Math.max(0, modifiers.getEffectiveValue(c, StatType.MOVEMENT_HEXES, TriggerContext.ALWAYS));
    }

    // --- Karte aktivieren / konfigurieren ---

    public CombatSession configureMap(Long sessionId, boolean enabled, Integer width, Integer height) {
        CombatSession session = combatService.findById(sessionId);
        session.setMapEnabled(enabled);
        if (width != null) session.setMapWidth(clamp(width, 8, 60));
        if (height != null) session.setMapHeight(clamp(height, 6, 40));
        sessionRepo.save(session);
        combatService.broadcast(session);
        return session;
    }

    // --- Kombattanten platzieren (Spielleiter, typischerweise im SETUP) ---

    /** Setzt die Position; q = -1 entfernt den Kombattanten von der Karte. */
    public CombatSession placeCombatant(Long sessionId, Long combatantId, int q, int r) {
        CombatSession session = requireMap(sessionId);
        CombatantState c = combatService.findCombatant(session, combatantId);

        if (q < 0) {
            c.setMapQ(null);
            c.setMapR(null);
        } else {
            requireFreeCell(session, q, r, combatantId);
            c.setMapQ(q);
            c.setMapR(r);
        }
        sessionRepo.save(session);
        combatService.broadcast(session);
        return session;
    }

    // --- Bewegung ---

    /**
     * Bewegt einen Kombattanten auf ein Zielfeld. Regeln (außer bei {@code gmOverride}):
     * Kampf aktiv, Kombattant ist laut Initiative dran, das Ziel ist über freie Felder
     * erreichbar und das Bewegungsbudget der Runde reicht. Kosten = kürzester freier Weg.
     */
    public CombatSession moveCombatant(Long sessionId, Long combatantId, int q, int r, boolean gmOverride) {
        CombatSession session = requireMap(sessionId);
        CombatantState c = combatService.findCombatant(session, combatantId);

        if (c.getMapQ() == null || c.getMapR() == null) {
            throw new IllegalStateException("Kombattant ist noch nicht auf der Karte platziert.");
        }
        requireFreeCell(session, q, r, combatantId);

        if (gmOverride) {
            c.setMapQ(q);
            c.setMapR(r);
        } else {
            if (session.getStatus() != CombatStatus.ACTIVE) {
                throw new IllegalStateException("Bewegung ist nur im aktiven Kampf möglich.");
            }
            if (c.isDefeated()) throw new IllegalStateException("Besiegte Kombattanten können sich nicht bewegen.");
            CombatantState active = activeTurnCombatant(session);
            if (active == null || !Objects.equals(active.getId(), c.getId())) {
                throw new IllegalStateException("Bewegung nur in Initiative-Reihenfolge — "
                        + (active != null ? cn(active) + " ist dran." : "diese Runde ist abgeschlossen."));
            }
            int budget = Math.max(0, effectiveMovement(c) - c.getMovedHexesThisRound());
            Integer cost = pathCost(session, c.getMapQ(), c.getMapR(), q, r, budget);
            if (cost == null) {
                throw new IllegalStateException("Zielfeld nicht erreichbar (Budget: noch "
                        + budget + " Felder, Hindernisse blockieren).");
            }
            c.setMovedHexesThisRound(c.getMovedHexesThisRound() + cost);
            c.setMapQ(q);
            c.setMapR(r);
            combatService.addLog(session, cn(c), null, ActionType.MAP_MOVE,
                    cn(c) + " bewegt sich " + cost + " Feld" + (cost == 1 ? "" : "er")
                            + " (" + c.getMovedHexesThisRound() + "/" + effectiveMovement(c) + ").",
                    true);
        }
        sessionRepo.save(session);
        combatService.broadcast(session);
        return session;
    }

    // --- Hindernisse ---

    public CombatSession addObstacle(Long sessionId, ObstacleType type, int q, int r) {
        CombatSession session = requireMap(sessionId);
        if (!HexUtil.inBounds(q, r, session.getMapWidth(), session.getMapHeight())) {
            throw new IllegalStateException("Feld liegt außerhalb der Karte.");
        }
        if (obstacleAt(session, q, r) != null) {
            throw new IllegalStateException("Auf diesem Feld steht bereits ein Hindernis.");
        }
        if (combatantAt(session, q, r, null) != null) {
            throw new IllegalStateException("Auf diesem Feld steht ein Kombattant.");
        }
        session.getObstacles().add(MapObstacle.builder()
                .combatSession(session).type(type).q(q).r(r).build());
        sessionRepo.save(session);
        combatService.broadcast(session);
        return session;
    }

    public CombatSession removeObstacle(Long sessionId, Long obstacleId) {
        CombatSession session = requireMap(sessionId);
        boolean removed = session.getObstacles().removeIf(o -> o.getId().equals(obstacleId));
        if (!removed) throw new EntityNotFoundException("Hindernis nicht gefunden: " + obstacleId);
        sessionRepo.save(session);
        combatService.broadcast(session);
        return session;
    }

    public CombatSession toggleDoor(Long sessionId, Long obstacleId) {
        CombatSession session = requireMap(sessionId);
        MapObstacle door = session.getObstacles().stream()
                .filter(o -> o.getId().equals(obstacleId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Hindernis nicht gefunden: " + obstacleId));
        if (door.getType() != ObstacleType.DOOR) {
            throw new IllegalStateException("Nur Türen können geöffnet/geschlossen werden.");
        }
        door.setDoorOpen(!door.isDoorOpen());
        sessionRepo.save(session);
        combatService.broadcast(session);
        return session;
    }

    // --- Regeln / Helfer ---

    /** Wer ist laut Initiative dran? Erster nicht besiegter Kombattant, der noch nicht gehandelt hat. */
    CombatantState activeTurnCombatant(CombatSession session) {
        return session.getCombatants().stream()
                .sorted((a, b) -> Integer.compare(a.getInitiativeOrder(), b.getInitiativeOrder()))
                .filter(c -> !c.isDefeated() && !c.isHasActedThisRound())
                .findFirst()
                .orElse(null);
    }

    /**
     * Kosten des kürzesten freien Wegs von (fromQ, fromR) nach (toQ, toR) per BFS,
     * begrenzt auf {@code maxCost}. Blockiert: Hindernisse (offene Türen nicht) und
     * andere Kombattanten. {@code null} = nicht erreichbar innerhalb des Budgets.
     */
    Integer pathCost(CombatSession session, int fromQ, int fromR, int toQ, int toR, int maxCost) {
        if (fromQ == toQ && fromR == toR) return 0;
        Map<Long, Integer> visited = new HashMap<>();
        Queue<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{fromQ, fromR, 0});
        visited.put(key(fromQ, fromR), 0);
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            if (cur[2] >= maxCost) continue;
            for (int[] n : HexUtil.neighbors(cur[0], cur[1])) {
                int nq = n[0], nr = n[1];
                if (!HexUtil.inBounds(nq, nr, session.getMapWidth(), session.getMapHeight())) continue;
                if (visited.containsKey(key(nq, nr))) continue;
                if (isBlocked(session, nq, nr)) continue;
                int cost = cur[2] + 1;
                if (nq == toQ && nr == toR) return cost;
                visited.put(key(nq, nr), cost);
                queue.add(new int[]{nq, nr, cost});
            }
        }
        return null;
    }

    /** Feld blockiert durch Hindernis oder Kombattant. */
    boolean isBlocked(CombatSession session, int q, int r) {
        MapObstacle o = obstacleAt(session, q, r);
        if (o != null && o.blocksMovement()) return true;
        return combatantAt(session, q, r, null) != null;
    }

    private MapObstacle obstacleAt(CombatSession session, int q, int r) {
        return session.getObstacles().stream()
                .filter(o -> o.getQ() == q && o.getR() == r)
                .findFirst().orElse(null);
    }

    private CombatantState combatantAt(CombatSession session, int q, int r, Long exceptId) {
        return session.getCombatants().stream()
                .filter(c -> c.getMapQ() != null && c.getMapQ() == q && c.getMapR() != null && c.getMapR() == r)
                .filter(c -> exceptId == null || !c.getId().equals(exceptId))
                .findFirst().orElse(null);
    }

    private void requireFreeCell(CombatSession session, int q, int r, Long movingCombatantId) {
        if (!HexUtil.inBounds(q, r, session.getMapWidth(), session.getMapHeight())) {
            throw new IllegalStateException("Feld liegt außerhalb der Karte.");
        }
        MapObstacle o = obstacleAt(session, q, r);
        if (o != null && o.blocksMovement()) {
            throw new IllegalStateException("Feld ist durch ein Hindernis blockiert.");
        }
        CombatantState other = combatantAt(session, q, r, movingCombatantId);
        if (other != null) {
            throw new IllegalStateException("Feld ist bereits von " + cn(other) + " besetzt.");
        }
    }

    private CombatSession requireMap(Long sessionId) {
        CombatSession session = combatService.findById(sessionId);
        if (!session.isMapEnabled()) {
            throw new IllegalStateException("Für diese Session ist keine Kampfkarte aktiviert.");
        }
        return session;
    }

    private static long key(int q, int r) {
        return ((long) q << 32) | (r & 0xffffffffL);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String cn(CombatantState c) {
        return c.getDisplayName() != null ? c.getDisplayName() : c.getCharacter().getName();
    }
}
