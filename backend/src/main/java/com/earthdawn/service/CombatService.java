package com.earthdawn.service;

import com.earthdawn.dto.*;
import com.earthdawn.model.*;
import com.earthdawn.model.enums.*;
import com.earthdawn.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class CombatService {

    private final CombatSessionRepository sessionRepo;
    private final CharacterRepository characterRepo;
    private final StepRollService diceService;
    private final ModifierAggregator modifiers;
    private final SimpMessagingTemplate websocket;
    private final ObjectMapper objectMapper;

    // --- Live Modal State (in-memory, pro Session) ---

    /** Synchronisierter Modal-Status pro Session — überlebt Server-Lifetime, nicht persistiert. */
    private final java.util.Map<Long, LiveModalState> liveModals = new java.util.concurrent.ConcurrentHashMap<>();

    /** Pro Session: aktive Dialog-Zustände aller Kombattanten (combatantId → DialogState). */
    private final java.util.Map<Long, java.util.Map<Long, com.earthdawn.dto.DialogState>> dialogStates
            = new java.util.concurrent.ConcurrentHashMap<>();

    /** Setzt einen neuen Modal-Status für die Session und bumpt die Version. */
    private void openLiveModal(Long sessionId, String type, Object payload) {
        liveModals.compute(sessionId, (k, prev) -> {
            int next = (prev == null ? 0 : prev.getVersion()) + 1;
            return LiveModalState.builder().version(next).type(type).payload(payload).build();
        });
    }

    /** Schließt das aktive Modal: bumpt Version, setzt type/payload auf null. */
    private void closeLiveModal(Long sessionId) {
        liveModals.compute(sessionId, (k, prev) -> {
            int next = (prev == null ? 0 : prev.getVersion()) + 1;
            return LiveModalState.builder().version(next).type(null).payload(null).build();
        });
    }

    /** Hängt den aktuellen Modal-Status aus dem Cache an die Session (transientes Feld). */
    private void attachLiveModal(CombatSession session) {
        if (session != null && session.getId() != null) {
            session.setLiveModal(liveModals.get(session.getId()));
        }
    }

    /** Public Endpoint-Trigger: aktuelles Modal schließen + broadcasten. */
    public CombatSession dismissModal(Long sessionId) {
        CombatSession session = findById(sessionId);
        closeLiveModal(sessionId);
        attachLiveModal(session);
        broadcast(session);
        return session;
    }

    // --- Session Management ---

    public List<CombatSession> findAll() {
        return sessionRepo.findByOrderByCreatedAtDesc();
    }

    public CombatSession findById(Long id) {
        CombatSession session = sessionRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Kampfsession nicht gefunden: " + id));
        enrichTransientFields(session);
        return session;
    }

    /** Berechnet flüchtige Anzeigefelder (z.B. aktueller Initiative-Step) für alle Kombattanten. */
    private void enrichTransientFields(CombatSession session) {
        for (CombatantState c : session.getCombatants()) {
            try {
                c.setBaseInitiativeStep(
                    modifiers.getEffectiveValue(c, StatType.INITIATIVE_STEP, TriggerContext.ALWAYS)
                );
                c.setCurrentInitiativeStep(
                    modifiers.getEffectiveValue(c, StatType.INITIATIVE_STEP, TriggerContext.ON_INITIATIVE)
                );
            } catch (Exception e) {
                c.setBaseInitiativeStep(0);
                c.setCurrentInitiativeStep(0);
            }
        }
        attachLiveModal(session);
        attachDialogStates(session);
    }

    /** Hängt die aktuellen Dialog-Zustände aus dem Cache an die Session (transientes Feld). */
    private void attachDialogStates(CombatSession session) {
        if (session != null && session.getId() != null) {
            var map = dialogStates.get(session.getId());
            session.setActiveDialogs(map != null ? new java.util.HashMap<>(map) : new java.util.HashMap<>());
        }
    }

    /** Setzt oder löscht den Dialog-Status eines Kombattanten und broadcasted die Session. */
    public void updateDialogState(Long sessionId, Long combatantId, com.earthdawn.dto.DialogState state) {
        if (state == null || state.getActionType() == null) {
            var map = dialogStates.get(sessionId);
            if (map != null) map.remove(combatantId);
        } else {
            dialogStates.computeIfAbsent(sessionId, k -> new java.util.concurrent.ConcurrentHashMap<>())
                        .put(combatantId, state);
        }
        try {
            CombatSession session = sessionRepo.findById(sessionId).orElse(null);
            if (session != null) broadcast(session);
        } catch (Exception e) {
            log.warn("Dialog-State broadcast fehlgeschlagen: {}", e.getMessage());
        }
    }

    public CombatSession createSession(String name) {
        CombatSession session = CombatSession.builder()
                .name(name)
                .status(CombatStatus.SETUP)
                .createdAt(LocalDateTime.now())
                .build();
        return sessionRepo.save(session);
    }

    public CombatSession addCombatant(Long sessionId, Long characterId, boolean isNpc) {
        CombatSession session = findById(sessionId);
        GameCharacter character = characterRepo.findById(characterId)
                .orElseThrow(() -> new EntityNotFoundException("Charakter nicht gefunden: " + characterId));

        String baseName = character.getName();
        long sameNameCount = session.getCombatants().stream()
                .filter(c -> baseName.equals(c.getCharacter().getName()))
                .count();
        String displayName = sameNameCount == 0 ? null : baseName + " " + (sameNameCount + 1);

        CombatantState combatant = CombatantState.builder()
                .combatSession(session)
                .character(character)
                .displayName(displayName)
                .currentDamage(character.getCurrentDamage())
                .wounds(character.getWounds())
                .currentKarma(character.getKarmaCurrent())
                .isNpc(isNpc)
                .build();

        session.getCombatants().add(combatant);

        // Während eines laufenden Kampfes nachträglich beitreten:
        if (session.getStatus() == CombatStatus.ACTIVE) {
            String name = displayName != null ? displayName : baseName;
            if (session.getPhase() == CombatPhase.ACTION) {
                // Initiative ist bereits gewürfelt → für den Neuzugang würfeln und einsortieren.
                int initStep = modifiers.getEffectiveValue(combatant, StatType.INITIATIVE_STEP, TriggerContext.ON_INITIATIVE);
                combatant.setInitiative(diceService.roll(initStep).getTotal());
                combatant.setHasDeclared(true); // Ansagephase ist vorbei
                session.getCombatants().sort(Comparator
                        .comparingInt(CombatantState::getInitiative).reversed()
                        .thenComparingInt(c -> c.isNpc() ? 1 : 0));
                for (int i = 0; i < session.getCombatants().size(); i++) {
                    session.getCombatants().get(i).setInitiativeOrder(i);
                }
                addLog(session, name, null, ActionType.INITIATIVE,
                        name + " betritt den Kampf (Initiative " + combatant.getInitiative() + ").", true);
            } else {
                // Ansagephase: Neuzugang muss noch ansagen; Initiative wird beim Übergang gewürfelt.
                combatant.setHasDeclared(false);
                addLog(session, name, null, ActionType.COMBAT_OPTION,
                        name + " betritt den Kampf und muss noch ansagen.", true);
            }
        }

        CombatSession saved = sessionRepo.save(session);
        broadcast(saved);
        return saved;
    }

    public CombatSession removeCombatant(Long sessionId, Long combatantId) {
        CombatSession session = findById(sessionId);
        session.getCombatants().removeIf(c -> c.getId().equals(combatantId));
        CombatSession saved = sessionRepo.save(session);
        broadcast(saved);
        return saved;
    }

    // --- Initiative ---

    /**
     * Start des Kampfes: Session wird aktiv, Runde 1 beginnt in der Deklarationsphase.
     * Initiative wird erst gewürfelt, wenn alle Kombattanten deklariert haben.
     */
    public CombatSession rollInitiative(Long sessionId) {
        CombatSession session = findById(sessionId);
        session.setStatus(CombatStatus.ACTIVE);
        session.setRound(1);
        session.setPhase(CombatPhase.DECLARATION);
        resetDeclarations(session);
        addLog(session, null, null, ActionType.ROUND_CHANGE,
                "Runde 1 beginnt — Ansagephase. Alle Kombattanten wählen Haltung und Handlung.", true);
        CombatSession saved = sessionRepo.save(session);
        broadcast(saved);
        return saved;
    }

    private String rerollInitiative(CombatSession session) {
        // Würfeln pro Kombattant + temporär Rolls speichern für UI-Modal
        java.util.Map<Long, RollResult> rollsById = new java.util.HashMap<>();
        java.util.Map<Long, Integer> stepsById = new java.util.HashMap<>();
        java.util.Map<Long, RollResult> karmaRollsById = new java.util.HashMap<>();
        for (CombatantState combatant : session.getCombatants()) {
            if (combatant.isDefeated()) continue;
            int initStep = modifiers.getEffectiveValue(combatant, StatType.INITIATIVE_STEP, TriggerContext.ON_INITIATIVE);
            RollResult roll = diceService.roll(initStep);
            int total = roll.getTotal();
            // Karma auf Initiative (Disziplin-Fähigkeit ab Kreis 3): 1 Karma → +W6 (Stufe 4)
            if (combatant.isKarmaInitiativeThisRound()
                    && canUseKarmaOnInitiative(combatant.getCharacter())
                    && combatant.getCurrentKarma() > 0) {
                RollResult karmaRoll = diceService.roll(4);
                total += karmaRoll.getTotal();
                combatant.setCurrentKarma(combatant.getCurrentKarma() - 1);
                karmaRollsById.put(combatant.getId(), karmaRoll);
            }
            combatant.setInitiative(total);
            rollsById.put(combatant.getId(), roll);
            stepsById.put(combatant.getId(), initStep);
        }
        session.getCombatants().sort(Comparator
                .comparingInt(CombatantState::getInitiative).reversed()
                .thenComparingInt(c -> c.isNpc() ? 1 : 0)); // heroes before NPCs on tie
        for (int i = 0; i < session.getCombatants().size(); i++) {
            session.getCombatants().get(i).setInitiativeOrder(i);
        }
        // Detail-Liste für UI in finaler Reihenfolge bauen
        java.util.List<InitiativeRollDetail> details = new java.util.ArrayList<>();
        for (CombatantState c : session.getCombatants()) {
            if (c.isDefeated()) continue;
            RollResult roll = rollsById.get(c.getId());
            if (roll == null) continue;

            // Aktive Effekte sammeln, die ON_INITIATIVE/ALWAYS auf INITIATIVE_STEP wirken
            java.util.List<String> bonusNotes = new java.util.ArrayList<>();
            for (ActiveEffect effect : c.getActiveEffects()) {
                for (ModifierEntry mod : effect.getModifiers()) {
                    if (mod.getTargetStat() != StatType.INITIATIVE_STEP) continue;
                    TriggerContext tc = mod.getTriggerContext();
                    if (tc != TriggerContext.ALWAYS && tc != TriggerContext.ON_INITIATIVE) continue;
                    int v = (int) mod.getValue();
                    bonusNotes.add(effect.getName() + " " + (v >= 0 ? "+" : "") + v);
                }
            }
            // Wunden + Rüstungs-/Schild-Initiative-Malus zur Anzeige
            if (c.getWounds() > 0) {
                bonusNotes.add("Wunden −" + c.getWounds());
            }
            int armorPenalty = c.getCharacter().getEquipment().stream()
                    .filter(e -> e.getType() == EquipmentType.ARMOR || e.getType() == EquipmentType.SHIELD)
                    .mapToInt(Equipment::getInitiativePenalty)
                    .sum();
            if (armorPenalty > 0) {
                bonusNotes.add("Rüstungsmalus −" + armorPenalty);
            }
            RollResult karmaRoll = karmaRollsById.get(c.getId());
            if (karmaRoll != null) {
                bonusNotes.add("Karma +" + karmaRoll.getTotal());
            }

            details.add(InitiativeRollDetail.builder()
                    .combatantId(c.getId())
                    .combatantName(c.getCharacter().getName())
                    .npc(c.isNpc())
                    .step(stepsById.getOrDefault(c.getId(), 0))
                    .roll(roll)
                    .total(c.getInitiative())
                    .order(c.getInitiativeOrder())
                    .bonusNotes(bonusNotes)
                    .build());
        }
        session.setLastInitiativeRolls(details);
        session.setLastInitiativeRollRound(session.getRound());
        // Zusammenfassung für den Log
        StringBuilder sb = new StringBuilder("Reihenfolge: ");
        session.getCombatants().stream()
                .filter(c -> !c.isDefeated())
                .forEach(c -> sb.append(c.getCharacter().getName()).append(" (").append(c.getInitiative()).append("), "));
        if (sb.toString().endsWith(", ")) sb.setLength(sb.length() - 2);
        return sb.toString();
    }

    /** Disziplinen, die ab dem 3. Kreis Karma auf ihre Initiative-Probe einsetzen dürfen. */
    private static final java.util.Set<String> KARMA_INITIATIVE_DISCIPLINES =
            java.util.Set.of("Dieb", "Kundschafter", "Luftsegler", "Schütze");

    /** True, wenn die Disziplin Karma auf Initiative erlaubt und der Charakter mindestens im 3. Kreis ist. */
    private boolean canUseKarmaOnInitiative(GameCharacter c) {
        return c != null && c.getDiscipline() != null
                && KARMA_INITIATIVE_DISCIPLINES.contains(c.getDiscipline().getName())
                && c.getCircle() >= 3;
    }

    /**
     * Wählt/entwählt in der Ansagephase, ob ein Kombattant beim Initiativewurf 1 Karma für +W6 (Stufe 4)
     * einsetzt. Karma wird erst beim tatsächlichen Wurf abgezogen.
     */
    public CombatSession setKarmaInitiative(Long sessionId, Long combatantId, boolean spend) {
        CombatSession session = findById(sessionId);
        CombatantState c = findCombatant(session, combatantId);
        if (session.getPhase() != CombatPhase.DECLARATION) {
            throw new IllegalStateException("Karma auf Initiative kann nur in der Ansagephase gewählt werden.");
        }
        if (spend) {
            if (!canUseKarmaOnInitiative(c.getCharacter())) {
                throw new IllegalStateException("Diese Disziplin kann (noch) kein Karma auf Initiative einsetzen.");
            }
            if (c.getCurrentKarma() <= 0) {
                throw new IllegalStateException(c.getCharacter().getName() + " hat kein Karma mehr.");
            }
        }
        c.setKarmaInitiativeThisRound(spend);
        sessionRepo.save(session);
        broadcast(session);
        return session;
    }

    // --- Attack ---

    public CombatActionResult performAttack(AttackActionRequest req) {
        CombatSession session = findById(req.getSessionId());
        CombatantState attacker = findCombatant(session, req.getAttackerCombatantId());
        CombatantState defender = findCombatant(session, req.getDefenderCombatantId());

        if (attacker.isDefeated()) {
            throw new IllegalStateException(attacker.getCharacter().getName() + " ist bewusstlos/besiegt und kann nicht handeln!");
        }
        if (session.getPhase() != CombatPhase.ACTION) {
            throw new IllegalStateException("Angriff nur in der Aktionsphase möglich (nicht während der Ansage).");
        }
        if (attacker.isHasActedThisRound()) {
            throw new IllegalStateException(attacker.getCharacter().getName() + " hat diese Runde bereits gehandelt!");
        }

        TriggerContext atkCtx = req.getActionType() == ActionType.RANGED_ATTACK
                ? TriggerContext.ON_RANGED_ATTACK : TriggerContext.ON_MELEE_ATTACK;

        // 1. Angriffsstufe — aktive Effekt-Boni sammeln für Ergebnisanzeige
        java.util.List<String> attackBonusNotes = new java.util.ArrayList<>();
        for (ActiveEffect effect : attacker.getActiveEffects()) {
            for (ModifierEntry mod : effect.getModifiers()) {
                if (mod.getTargetStat() != StatType.ATTACK_STEP) continue;
                TriggerContext tc = mod.getTriggerContext();
                if (tc != TriggerContext.ALWAYS && tc != atkCtx) continue;
                int v = (int) mod.getValue();
                attackBonusNotes.add(effect.getName() + " " + (v >= 0 ? "+" : "") + v);
            }
        }
        if (attacker.getWounds() > 0) {
            attackBonusNotes.add("Wunden −" + attacker.getWounds());
        }
        int attackStep = modifiers.getEffectiveValue(attacker, StatType.ATTACK_STEP, atkCtx);

        // Effekte auf dem VERTEIDIGER, die Angriffe gegen ihn schwächen (z.B. Phantomkrieger −3)
        for (ActiveEffect eff : defender.getActiveEffects()) {
            for (ModifierEntry mod : eff.getModifiers()) {
                if (mod.getTargetStat() != StatType.ATTACK_STEP) continue;
                if (mod.getTriggerContext() != TriggerContext.ON_INCOMING_ATTACK) continue;
                int v = (int) mod.getValue();
                attackStep += v;
                attackBonusNotes.add(eff.getName() + " " + (v >= 0 ? "+" : "") + v);
            }
        }

        // Magische Markierung nach Verbrauch entfernen (gilt nur für 1 Fernkampfangriff)
        if (req.getActionType() == ActionType.RANGED_ATTACK) {
            attacker.getActiveEffects().removeIf(e -> TalentNames.MAGISCHE_MARKIERUNG.equals(e.getName()));
        }

        if (req.getTalentId() != null) {
            attackStep += attacker.getCharacter().getTalents().stream()
                    .filter(t -> t.getTalentDefinition().getId().equals(req.getTalentId()))
                    .findFirst().map(CharacterTalent::getRank).orElse(0);
        }
        // Waffen-Fertigkeit (alternativ zum Talent): Rang wie beim Talent, aber kein Karma
        if (req.getSkillId() != null) {
            attackStep += attacker.getCharacter().getSkills().stream()
                    .filter(s -> s.getSkillDefinition().getId().equals(req.getSkillId()))
                    .findFirst().map(CharacterSkill::getRank).orElse(0);
        }
        attackStep += req.getBonusSteps();

        // Verzweiflungsschlag-Amulette auf den Angriffswurf (+6 je Amulett, entlädt sie)
        boolean amuletsUsed = false;
        int amuletAttackBonus = applyAmulets(attacker, req.getAmuletAttackIds(), false, "Angriff", attackBonusNotes);
        if (amuletAttackBonus != 0) { attackStep += amuletAttackBonus; amuletsUsed = true; }

        // Ausstehende Angriffsboni (z.B. aus Manövrieren) verbrauchen
        if (attacker.getPendingAttackBonus() != 0) {
            int pending = attacker.getPendingAttackBonus();
            attackStep += pending;
            attackBonusNotes.add("Manövrieren " + (pending >= 0 ? "+" : "") + pending);
            attacker.setPendingAttackBonus(0);
        }

        // Aggressive/defensive Haltung werden in der Ansagephase deklariert — die zugehörigen
        // Boni/Mali sind bereits als ActiveEffect am Angreifer aktiv und fließen über
        // den ModifierAggregator automatisch in attackStep / Verteidigungswerte ein.
        boolean wasAggressive = attacker.getDeclaredStance() == DeclaredStance.AGGRESSIVE;

        // 1b. Blattschuss-Validierung: nur RANGED_ATTACK, Talent vorhanden, nicht bereits verwendet
        int blattschussRank = 0;
        boolean blattschussActive = false;
        if (req.isUseBlattschuss()) {
            if (req.getActionType() != ActionType.RANGED_ATTACK) {
                throw new IllegalStateException("Blattschuss ist nur bei Projektil-/Wurfwaffen-Angriffen einsetzbar.");
            }
            if (attacker.isBlattschussUsedThisRound()) {
                throw new IllegalStateException("Blattschuss wurde diese Runde bereits eingesetzt.");
            }
            CharacterTalent bs = attacker.getCharacter().getTalents().stream()
                    .filter(t -> TalentNames.BLATTSCHUSS.equals(t.getTalentDefinition().getName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Talent 'Blattschuss' nicht gefunden."));
            blattschussRank = bs.getRank();
            blattschussActive = true;
            // 2 Schaden Überanstrengung
            attacker.setCurrentDamage(attacker.getCurrentDamage() + 2);
            attacker.setBlattschussUsedThisRound(true);
        }

        // 2. Karma — immer W6 (Stufe 4). Bei Waffen-Fertigkeiten ist kein Karma erlaubt.
        RollResult karmaRoll = null;
        if (req.isSpendKarma() && req.getSkillId() == null && attacker.getCurrentKarma() > 0) {
            karmaRoll = diceService.roll(4);
            attacker.setCurrentKarma(attacker.getCurrentKarma() - 1);
        }

        // 3. Angriffswurf (mindestens Stufe 1)
        attackStep = Math.max(1, attackStep);
        RollResult attackRoll = diceService.roll(attackStep);
        int attackTotal = attackRoll.getTotal() + (karmaRoll != null ? karmaRoll.getTotal() : 0);

        // 4. Verteidigung
        TriggerContext defCtx = req.getActionType() == ActionType.RANGED_ATTACK
                ? TriggerContext.ON_RANGED_DEFENSE : TriggerContext.ON_MELEE_DEFENSE;

        // Verteidigungs-Effekte des Verteidigers sammeln (zur Anzeige)
        java.util.List<String> defenseNotes = new java.util.ArrayList<>();
        for (ActiveEffect effect : defender.getActiveEffects()) {
            for (ModifierEntry mod : effect.getModifiers()) {
                if (mod.getTargetStat() != StatType.PHYSICAL_DEFENSE) continue;
                TriggerContext tc = mod.getTriggerContext();
                if (tc != TriggerContext.ALWAYS && tc != defCtx) continue;
                int v = (int) mod.getValue();
                defenseNotes.add(effect.getName() + " " + (v >= 0 ? "+" : "") + v);
            }
        }

        int pd = modifiers.getEffectiveValue(defender, StatType.PHYSICAL_DEFENSE, defCtx);

        // Ausstehenden Verteidigungsbonus (z.B. aus Manövrieren) verbrauchen — auch in Notes aufnehmen
        if (defender.getPendingDefenseBonus() != 0) {
            int pending = defender.getPendingDefenseBonus();
            pd += pending;
            defenseNotes.add("Manövrieren " + (pending >= 0 ? "+" : "") + pending);
            defender.setPendingDefenseBonus(0);
        }

        boolean hit = attackTotal >= pd;

        CombatActionResult.CombatActionResultBuilder result = CombatActionResult.builder()
                .actorName(attacker.getCharacter().getName())
                .targetName(defender.getCharacter().getName())
                .actionType(req.getActionType())
                .aggressiveAttack(wasAggressive)
                .attackStep(attackStep)
                .attackRoll(attackRoll)
                .karmaRoll(karmaRoll)
                .defenseValue(pd)
                .hit(hit)
                .attackBonusNotes(attackBonusNotes)
                .defenseNotes(defenseNotes)
                .blattschussActive(blattschussActive)
                .blattschussRank(blattschussRank);

        // Ein-/Zweihändige Waffe ↔ Schild-Automatik (gilt unabhängig von Treffer/Fehlschlag)
        com.earthdawn.model.Equipment mainWeapon = req.getWeaponId() == null ? null
                : attacker.getCharacter().getEquipment().stream()
                    .filter(e -> e.getId().equals(req.getWeaponId()))
                    .findFirst().orElse(null);
        boolean shieldChanged = applyTwoHandedShieldRule(attacker, mainWeapon, result);

        // Blattschuss + Fehlschlag: pending-State setzen, damit weitere Karma eingesetzt werden können
        if (!hit && blattschussActive && blattschussRank > 0) {
            attacker.setPendingBlattschussDefenderId(defender.getId());
            attacker.setPendingBlattschussTotal(attackTotal);
            attacker.setPendingBlattschussKarmaUsed(0);
            attacker.setPendingBlattschussRank(blattschussRank);
            attacker.setPendingBlattschussWeaponId(req.getWeaponId() != null ? req.getWeaponId() : -1L);
            attacker.setPendingBlattschussDefense(pd);
            result.blattschussCanAddKarma(true).blattschussKarmaUsed(0);
        }

        // Lufttanz-Trigger: Nahkampf, Lufttanz aktiv, Initiative-Vorsprung ≥ 10, noch nicht
        // ausgelöst/verbraucht. Hängt NICHT vom Treffer ab — der Zusatzangriff wird allein durch
        // den Initiative-Vorsprung gegen das Ziel des Nahkampfangriffs gewährt (auch bei Fehlschlag).
        if (req.getActionType() == ActionType.MELEE_ATTACK
                && attacker.isLufttanzActivatedThisRound()
                && !attacker.isLufttanzBonusUsedThisRound()
                && (attacker.getPendingLufttanzTargetId() == null || attacker.getPendingLufttanzTargetId() < 0)) {
            int initDiff = attacker.getInitiative() - defender.getInitiative();
            if (initDiff >= 10) {
                attacker.setPendingLufttanzTargetId(defender.getId());
                attacker.setPendingLufttanzWeaponId(req.getWeaponId() != null ? req.getWeaponId() : -1L);
                result.lufttanzBonusReady(true).lufttanzInitiativeDiff(initDiff);
            }
        }

        if (hit) {
            // 5. Übererfolge: je 5 über VK → +2 Schadensstufen
            int extraSuccesses = (attackTotal - pd) / 5;

            // 6. Schadensstufe = Stärke-Stufe + Waffe + Übererfolge
            int damageStep = modifiers.getEffectiveValue(attacker, StatType.DAMAGE_STEP, TriggerContext.ON_DAMAGE_DEALT);
            // Rohe STR-Stufe + Wundenabzug separat (für Klammer-Breakdown im UI)
            int damageStrengthStepRaw = diceService.attributeToStep(attacker.getCharacter().getStrength());
            int damageWoundPenalty = attacker.getWounds();

            // Schadensboni sammeln und (für ziel-spezifische Effekte) auch auf damageStep addieren.
            // Nicht-zielgebundene DAMAGE_STEP-Modifikatoren sind bereits via ModifierAggregator
            // in damageStep eingerechnet — wir listen sie nur zur Anzeige.
            java.util.List<String> damageBonusNotes = new java.util.ArrayList<>();
            for (ActiveEffect eff : attacker.getActiveEffects()) {
                int effectBonus = 0;
                for (ModifierEntry mod : eff.getModifiers()) {
                    if (mod.getTargetStat() != StatType.DAMAGE_STEP) continue;
                    TriggerContext tc = mod.getTriggerContext();
                    if (tc != TriggerContext.ALWAYS && tc != TriggerContext.ON_DAMAGE_DEALT) continue;
                    effectBonus += (int) mod.getValue();
                }
                if (effectBonus == 0) continue;

                boolean isTargetSpecific = eff.getTargetCombatantId() != null;
                if (isTargetSpecific) {
                    // Ziel-spezifisch: nur anwenden, wenn das Ziel passt UND nur bei physischen Angriffen
                    if (req.getActionType() != ActionType.MELEE_ATTACK && req.getActionType() != ActionType.RANGED_ATTACK) continue;
                    if (!eff.getTargetCombatantId().equals(defender.getId())) continue;
                    damageStep += effectBonus;
                    String suffix = eff.getRemainingRounds() > 0
                            ? " (noch " + eff.getRemainingRounds() + " Runden)"
                            : eff.getRemainingRounds() < 0 ? " (permanent)" : "";
                    damageBonusNotes.add(eff.getName() + " " + (effectBonus >= 0 ? "+" : "") + effectBonus + suffix);
                } else {
                    // Nicht-zielgebunden: bereits in damageStep — nur Notiz hinzufügen
                    damageBonusNotes.add(eff.getName() + " " + (effectBonus >= 0 ? "+" : "") + effectBonus);
                }
            }
            // Wundenabzug NICHT als Chip hinzufügen — wird im Klammer-Breakdown unter dem Schaden-Step gezeigt.
            result.damageBonusNotes(damageBonusNotes);

            boolean weaponIsClaw = false;
            int weaponBonus = 0;
            String weaponName = null;
            if (req.getWeaponId() != null) {
                com.earthdawn.model.Equipment weapon = attacker.getCharacter().getEquipment().stream()
                        .filter(e -> e.getId().equals(req.getWeaponId()))
                        .findFirst().orElse(null);
                if (weapon != null) {
                    damageStep += weapon.getDamageBonus();
                    weaponBonus = weapon.getDamageBonus();
                    weaponName = weapon.getName();
                    weaponIsClaw = weapon.isClawWeapon();
                }
            }
            result.damageStrengthStep(damageStrengthStepRaw)
                  .damageWoundPenalty(damageWoundPenalty)
                  .damageWeaponBonus(weaponBonus)
                  .damageWeaponName(weaponName);
            damageStep += extraSuccesses * 2;

            // Verzweiflungsschlag-Amulette auf den Schadenswurf (+6 je Amulett). Entladen erst, wenn der
            // Schaden tatsächlich angewendet wird — nicht bei Fehlschlag / erfolgreichem Ausweichen/Riposte.
            java.util.List<com.earthdawn.model.Equipment> damageAmulets =
                    collectAmulets(attacker, req.getAmuletDamageIds(), false, "Schaden", damageBonusNotes);
            int amuletDamageBonus = damageAmulets.stream().mapToInt(com.earthdawn.model.Equipment::getAmuletStepBonus).sum();
            if (amuletDamageBonus != 0) damageStep += amuletDamageBonus;
            result.damageBonusNotes(damageBonusNotes);

            RollResult damageRoll = diceService.roll(damageStep);

            // 6a. Karma auf Schaden — nur bei Krallenhand-Waffe erlaubt
            RollResult damageKarmaRoll = null;
            if (req.isSpendKarmaForDamage()) {
                if (!weaponIsClaw) {
                    throw new IllegalStateException(
                            "Karma auf den Schadenswurf ist nur bei Krallenhand-Waffen erlaubt.");
                }
                if (attacker.getCurrentKarma() <= 0) {
                    throw new IllegalStateException(attacker.getCharacter().getName() + " hat kein Karma mehr.");
                }
                damageKarmaRoll = diceService.roll(4);
                attacker.setCurrentKarma(attacker.getCurrentKarma() - 1);
            }
            int damageTotal = damageRoll.getTotal() + (damageKarmaRoll != null ? damageKarmaRoll.getTotal() : 0);

            // 6b. Rüstung
            int armor = modifiers.getEffectiveValue(defender, StatType.PHYSICAL_ARMOR, TriggerContext.ON_DAMAGE_RECEIVED);
            int netDamage = Math.max(0, damageTotal - armor);

            // 7. Reaktionsmöglichkeiten des Verteidigers (Riposte und/oder Ausweichen)
            boolean defenderHasRiposte = req.getActionType() == ActionType.MELEE_ATTACK
                    && defender.getCharacter().getTalents().stream()
                    .anyMatch(t -> TalentNames.RIPOSTE.equals(t.getTalentDefinition().getName()))
                    && defender.getPendingRiposteAttackTotal() < 0;
            boolean defenderHasDodge = defender.getCharacter().getTalents().stream()
                    .anyMatch(t -> TalentNames.AUSWEICHEN.equals(t.getTalentDefinition().getName()));

            if (defenderHasRiposte || defenderHasDodge) {
                // Beide möglichen Pending-Zustände setzen — Verteidiger wählt eine Reaktion
                if (defenderHasRiposte) {
                    defender.setPendingRiposteAttackTotal(attackTotal);
                    defender.setPendingRiposteAttackerId(attacker.getId());
                    defender.setPendingRiposteDamage(netDamage);
                    result.hitPendingRiposte(true).riposteDefenderId(defender.getId());
                }
                if (defenderHasDodge) {
                    defender.setPendingDodgeDamage(netDamage);
                    defender.setPendingDodgeAttackTotal(attackTotal);
                    defender.setPendingDamageStep(damageStep);
                    defender.setPendingArmorValue(armor);
                    try { defender.setPendingDamageRollJson(objectMapper.writeValueAsString(damageRoll)); } catch (JsonProcessingException e) { log.error("Fehler beim Serialisieren des Schadenswurfs", e); }
                    result.hitPendingDodge(true).dodgeDefenderId(defender.getId()).pendingDodgeDamage(netDamage);
                }
                // Schaden-Amulette für den ausstehenden Treffer reservieren (erst bei Schadensanwendung entladen)
                if (!damageAmulets.isEmpty()) {
                    defender.setPendingDamageAmuletIds(damageAmulets.stream()
                            .map(a -> String.valueOf(a.getId())).collect(java.util.stream.Collectors.joining(",")));
                    defender.setPendingDamageAmuletAttackerId(attacker.getId());
                }
                // Wenn nur Riposte (kein Ausweichen): Aktion ist durch, Treffer wird beim Riposte-Resolve angewendet
                // Wenn Ausweichen aktiv ist: regulärer Pfad — Schaden steht aus, Aktion ebenfalls verbraucht
                attacker.setHasActedThisRound(true);
                result.extraSuccesses(extraSuccesses).damageStep(damageStep).damageRoll(damageRoll)
                      .damageKarmaRoll(damageKarmaRoll)
                      .armorValue(armor).netDamage(netDamage);
                CombatActionResult actionResult = result.build();
                actionResult.setDescription(buildDescription(actionResult));
                String tag = defenderHasRiposte && defenderHasDodge
                        ? " (Riposte oder Ausweichen möglich!)"
                        : defenderHasRiposte ? " (Riposte möglich!)" : " (Ausweichen möglich!)";
                addLog(session, attacker.getCharacter().getName(), defender.getCharacter().getName(),
                        req.getActionType(), actionResult.getDescription() + tag, hit);
                if (amuletsUsed || shieldChanged) characterRepo.save(attacker.getCharacter());
                sessionRepo.save(session);
                broadcastWithModal(session, "ATTACK_RESULT", actionResult);
                return actionResult;
            }

            {
                int wt2 = modifiers.getEffectiveValue(defender, StatType.WOUND_THRESHOLD, TriggerContext.ALWAYS);
                int prevWounds = defender.getWounds();
                KnockdownResult kdr = applyDamageToDefender(session, defender, netDamage);
                int newWounds = defender.getWounds() - prevWounds;
                // Schaden wurde angewendet → Schaden-Amulette jetzt entladen
                if (!damageAmulets.isEmpty()) {
                    damageAmulets.forEach(a -> a.setCharged(false));
                    amuletsUsed = true;
                }
                result.woundDealt(newWounds > 0)
                      .newWounds(newWounds)
                      .totalWounds(defender.getWounds())
                      .woundThreshold(wt2)
                      .targetDefeated(defender.isDefeated())
                      .knockdownResult(kdr);
            }

            result.extraSuccesses(extraSuccesses)
                  .damageStep(damageStep)
                  .damageRoll(damageRoll)
                  .damageKarmaRoll(damageKarmaRoll)
                  .armorValue(armor)
                  .netDamage(netDamage);
        }

        // Angreifer hat seine Aktion für diese Runde verbraucht
        attacker.setHasActedThisRound(true);

        CombatActionResult actionResult = result.build();
        actionResult.setDescription(buildDescription(actionResult));

        addLog(session, attacker.getCharacter().getName(), defender.getCharacter().getName(),
                req.getActionType(), actionResult.getDescription(), hit);

        if (amuletsUsed || shieldChanged) characterRepo.save(attacker.getCharacter());
        sessionRepo.save(session);
        broadcastWithModal(session, "ATTACK_RESULT", actionResult);
        return actionResult;
    }

    /**
     * Wendet Verzweiflungsschlag-Amulette an: validiert sie, addiert je ihren Stufen-Bonus (+6),
     * entlädt sie (charged=false) und fügt eine Anzeige-Notiz hinzu. Gibt die Summe der Boni zurück.
     *
     * @param forSpell true = Zauber-Amulette erwartet, false = physische Amulette erwartet
     * @param label    Anzeige-Label ("Angriff"/"Schaden"/"Zauber")
     */
    public int applyAmulets(CombatantState combatant, java.util.List<Long> amuletIds,
                            boolean forSpell, String label, java.util.List<String> notes) {
        java.util.List<com.earthdawn.model.Equipment> amulets = collectAmulets(combatant, amuletIds, forSpell, label, notes);
        amulets.forEach(a -> a.setCharged(false)); // sofort entladen
        return amulets.stream().mapToInt(com.earthdawn.model.Equipment::getAmuletStepBonus).sum();
    }

    /**
     * Validiert die Amulette, fügt Anzeige-Notizen hinzu und gibt die passenden Amulett-Objekte
     * zurück — OHNE sie zu entladen. Der Aufrufer entscheidet, wann entladen wird (z.B. erst wenn
     * Schaden tatsächlich angewendet wird).
     */
    java.util.List<com.earthdawn.model.Equipment> collectAmulets(CombatantState combatant, java.util.List<Long> amuletIds,
                            boolean forSpell, String label, java.util.List<String> notes) {
        java.util.List<com.earthdawn.model.Equipment> result = new java.util.ArrayList<>();
        if (amuletIds == null || amuletIds.isEmpty()) return result;
        for (Long id : amuletIds) {
            com.earthdawn.model.Equipment amulet = combatant.getCharacter().getEquipment().stream()
                    .filter(e -> e.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Amulett nicht gefunden: " + id));
            if (amulet.getType() != com.earthdawn.model.enums.EquipmentType.AMULET) {
                throw new IllegalStateException(amulet.getName() + " ist kein Amulett.");
            }
            if (!amulet.isCharged()) {
                throw new IllegalStateException("Amulett '" + amulet.getName() + "' ist nicht geladen.");
            }
            if (amulet.isAmuletForSpell() != forSpell) {
                throw new IllegalStateException("Amulett '" + amulet.getName() + "' passt nicht zu dieser Aktion ("
                        + (forSpell ? "Zauber" : "physischer Angriff") + ").");
            }
            notes.add(amulet.getName() + " (" + label + ") +" + amulet.getAmuletStepBonus());
            result.add(amulet);
        }
        return result;
    }

    /**
     * Entlädt die für einen ausstehenden Treffer reservierten Schaden-Amulette des Angreifers,
     * sobald der Schaden tatsächlich angewendet wird. Liest die CSV-IDs vom Verteidiger.
     */
    private void dischargePendingDamageAmulets(CombatSession session, CombatantState defender) {
        String csv = defender.getPendingDamageAmuletIds();
        Long attackerId = defender.getPendingDamageAmuletAttackerId();
        if (csv == null || csv.isBlank() || attackerId == null) return;
        CombatantState attacker = session.getCombatants().stream()
                .filter(c -> attackerId.equals(c.getId())).findFirst().orElse(null);
        if (attacker != null) {
            for (String idStr : csv.split(",")) {
                try {
                    long id = Long.parseLong(idStr.trim());
                    attacker.getCharacter().getEquipment().stream()
                            .filter(e -> e.getId().equals(id))
                            .findFirst().ifPresent(a -> a.setCharged(false));
                } catch (NumberFormatException ignored) {}
            }
            characterRepo.save(attacker.getCharacter());
        }
        defender.setPendingDamageAmuletIds(null);
        defender.setPendingDamageAmuletAttackerId(null);
    }

    /**
     * Schild-Automatik je nach Waffe: Eine zweihändige Waffe legt ein aktives Schild
     * (außer Buckler) automatisch ab; eine einhändige Waffe legt ein zuvor automatisch
     * abgelegtes Schild wieder an. Setzt ggf. die Anzeige-Felder im Ergebnis.
     *
     * @return true, wenn ein Schild-Zustand geändert wurde (→ Charakter muss gespeichert werden)
     */
    boolean applyTwoHandedShieldRule(CombatantState attacker, com.earthdawn.model.Equipment weapon,
                                     CombatActionResult.CombatActionResultBuilder result) {
        if (weapon == null) return false; // waffenlos/Zauber → Schildzustand unverändert
        boolean changed = false;
        if (weapon.isTwoHanded()) {
            for (com.earthdawn.model.Equipment e : attacker.getCharacter().getEquipment()) {
                if (e.getType() == com.earthdawn.model.enums.EquipmentType.SHIELD
                        && e.isActive() && !e.isBuckler()) {
                    e.setActive(false);
                    e.setAutoStowed(true);
                    if (result != null) result.shieldStowedName(e.getName());
                    changed = true;
                }
            }
        } else {
            for (com.earthdawn.model.Equipment e : attacker.getCharacter().getEquipment()) {
                if (e.getType() == com.earthdawn.model.enums.EquipmentType.SHIELD && e.isAutoStowed()) {
                    e.setActive(true);
                    e.setAutoStowed(false);
                    if (result != null) result.shieldRestoredName(e.getName());
                    changed = true;
                }
            }
        }
        return changed;
    }

    // --- Runden-Management ---

    public CombatSession nextRound(Long sessionId) {
        CombatSession session = findById(sessionId);
        session.setRound(session.getRound() + 1);

        // Effekt-Dauer reduzieren, ausstehende Boni + Haltungs-Effekte zurücksetzen
        for (CombatantState combatant : session.getCombatants()) {
            combatant.setHasActedThisRound(false);
            combatant.setPendingAttackBonus(0);
            combatant.setPendingDefenseBonus(0);
            combatant.setTigersprungUsedThisRound(false);
            combatant.setZweitWaffeUsedThisRound(false);
            combatant.setNachtretenUsedThisRound(false);
            combatant.setLufttanzActivatedThisRound(false);
            combatant.setLufttanzBonusUsedThisRound(false);
            combatant.setPendingLufttanzTargetId(-1L);
            combatant.setPendingLufttanzWeaponId(-1L);
            combatant.setBlattschussUsedThisRound(false);
            combatant.setKarmaInitiativeThisRound(false);
            clearBlattschussPending(combatant);
            combatant.setPendingRiposteAttackTotal(-1);
            combatant.setPendingRiposteAttackerId(null);
            combatant.setPendingRiposteDamage(0);
            // Unaufgelöste Schaden-Amulett-Reservierung verfällt (Amulett bleibt geladen)
            combatant.setPendingDamageAmuletIds(null);
            combatant.setPendingDamageAmuletAttackerId(null);
            // Aggressive / Defensive Haltungs-Effekte am Rundenende entfernen (unabhängig von remainingRounds)
            combatant.getActiveEffects().removeIf(effect ->
                    TalentNames.EFFECT_AGGRESSIVER_ANGRIFF.equals(effect.getName())
                 || TalentNames.EFFECT_DEFENSIVE_HALTUNG.equals(effect.getName()));
            combatant.getActiveEffects().removeIf(effect -> {
                if (effect.getRemainingRounds() == -1) return false;
                effect.setRemainingRounds(effect.getRemainingRounds() - 1);
                return effect.getRemainingRounds() <= 0;
            });
        }

        // Neue Runde beginnt in der Ansagephase
        session.setPhase(CombatPhase.DECLARATION);
        resetDeclarations(session);

        addLog(session, null, null, ActionType.ROUND_CHANGE,
                "Runde " + session.getRound() + " beginnt — Ansagephase. Alle wählen Haltung und Handlung.", true);
        CombatSession saved = sessionRepo.save(session);
        broadcast(saved);
        return saved;
    }

    // --- Ansagephase ---

    /** Ein Kombattant deklariert Haltung und Handlungstyp. Kann beliebig oft geändert werden. */
    public CombatSession declareAction(Long sessionId, Long combatantId,
                                        DeclaredStance stance, DeclaredActionType actionType) {
        CombatSession session = findById(sessionId);
        if (session.getPhase() != CombatPhase.DECLARATION) {
            throw new IllegalStateException("Ansagen sind nur in der Ansagephase möglich.");
        }
        CombatantState combatant = findCombatant(session, combatantId);
        if (combatant.isDefeated()) {
            throw new IllegalStateException(combatant.getCharacter().getName() + " ist besiegt und kann nicht deklarieren.");
        }

        combatant.setDeclaredStance(stance != null ? stance : DeclaredStance.NONE);
        combatant.setDeclaredActionType(actionType != null ? actionType : DeclaredActionType.WEAPON);
        combatant.setHasDeclared(true);

        String stanceLabel = switch (combatant.getDeclaredStance()) {
            case AGGRESSIVE -> "Aggressiv";
            case DEFENSIVE -> "Defensiv";
            default -> "Neutral";
        };
        String actionLabel = combatant.getDeclaredActionType() == DeclaredActionType.SPELL ? "Zauber" : "Waffe";
        addLog(session, combatant.getCharacter().getName(), null, ActionType.COMBAT_OPTION,
                combatant.getCharacter().getName() + " sagt an: " + stanceLabel + " / " + actionLabel + ".", true);

        // Alle nicht-besiegten deklariert? → Haltungseffekte anwenden + Initiative rollen → ACTION
        boolean allDeclared = session.getCombatants().stream()
                .filter(c -> !c.isDefeated())
                .allMatch(CombatantState::isHasDeclared);
        boolean initiativeJustRolled = false;
        if (allDeclared) {
            applyDeclaredStances(session);
            String summary = rerollInitiative(session);
            session.setPhase(CombatPhase.ACTION);
            addLog(session, null, null, ActionType.INITIATIVE,
                    "Alle Ansagen erfolgt. Initiative gewürfelt! " + summary, true);
            initiativeJustRolled = true;
        }

        CombatSession saved = sessionRepo.save(session);
        if (initiativeJustRolled) {
            // Initiative-Modal mit Detail-Liste für alle Zuschauer öffnen
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("rolls", saved.getLastInitiativeRolls());
            payload.put("round", saved.getLastInitiativeRollRound());
            broadcastWithModal(saved, "INITIATIVE", payload);
        } else {
            broadcast(saved);
        }
        return saved;
    }

    /** Ansagephase erneut öffnen (z.B. um Auswahl zu ändern). Nur möglich, solange noch nicht alle deklariert haben. */
    public CombatSession undeclareAction(Long sessionId, Long combatantId) {
        CombatSession session = findById(sessionId);
        if (session.getPhase() != CombatPhase.DECLARATION) {
            throw new IllegalStateException("Ansage kann nur in der Ansagephase zurückgenommen werden.");
        }
        CombatantState combatant = findCombatant(session, combatantId);
        combatant.setHasDeclared(false);
        CombatSession saved = sessionRepo.save(session);
        broadcast(saved);
        return saved;
    }

    private void resetDeclarations(CombatSession session) {
        for (CombatantState c : session.getCombatants()) {
            c.setHasDeclared(false);
            c.setDeclaredStance(DeclaredStance.NONE);
            c.setDeclaredActionType(DeclaredActionType.WEAPON);
        }
    }

    /** Wendet die Auswirkungen der deklarierten Haltungen an (Aggressiv / Defensiv). */
    private void applyDeclaredStances(CombatSession session) {
        for (CombatantState c : session.getCombatants()) {
            if (c.isDefeated()) continue;
            String name = c.getCharacter().getName();
            switch (c.getDeclaredStance()) {
                case AGGRESSIVE -> {
                    // +3 Stufen Angriff (via ActiveEffect auf ATTACK_STEP), 1 Schaden sofort, -3 auf Verteidigung
                    c.setCurrentDamage(c.getCurrentDamage() + 1);
                    ActiveEffect eff = ActiveEffect.builder()
                            .combatantState(c)
                            .name(TalentNames.EFFECT_AGGRESSIVER_ANGRIFF)
                            .description("+3 Angriff, -3 Verteidigung, 1 Schaden")
                            .sourceType(SourceType.CONDITION)
                            .remainingRounds(1)
                            .negative(true)
                            .modifiers(List.of(
                                    ModifierEntry.builder().targetStat(StatType.ATTACK_STEP)
                                            .operation(ModifierOperation.ADD).value(3)
                                            .triggerContext(TriggerContext.ALWAYS).build(),
                                    ModifierEntry.builder().targetStat(StatType.PHYSICAL_DEFENSE)
                                            .operation(ModifierOperation.ADD).value(-3)
                                            .triggerContext(TriggerContext.ALWAYS).build(),
                                    ModifierEntry.builder().targetStat(StatType.SPELL_DEFENSE)
                                            .operation(ModifierOperation.ADD).value(-3)
                                            .triggerContext(TriggerContext.ALWAYS).build(),
                                    ModifierEntry.builder().targetStat(StatType.SOCIAL_DEFENSE)
                                            .operation(ModifierOperation.ADD).value(-3)
                                            .triggerContext(TriggerContext.ALWAYS).build()
                            ))
                            .build();
                    c.getActiveEffects().add(eff);
                    addLog(session, name, null, ActionType.COMBAT_OPTION,
                            name + " nimmt aggressive Haltung ein (+3 Angriff, -3 Verteidigung, 1 Schaden).", true);
                }
                case DEFENSIVE -> {
                    ActiveEffect eff = ActiveEffect.builder()
                            .combatantState(c)
                            .name(TalentNames.EFFECT_DEFENSIVE_HALTUNG)
                            .description("-3 Angriff, +3 Verteidigung")
                            .sourceType(SourceType.CONDITION)
                            .remainingRounds(1)
                            .negative(false)
                            .modifiers(List.of(
                                    ModifierEntry.builder().targetStat(StatType.ATTACK_STEP)
                                            .operation(ModifierOperation.ADD).value(-3)
                                            .triggerContext(TriggerContext.ALWAYS).build(),
                                    ModifierEntry.builder().targetStat(StatType.PHYSICAL_DEFENSE)
                                            .operation(ModifierOperation.ADD).value(3)
                                            .triggerContext(TriggerContext.ALWAYS).build(),
                                    ModifierEntry.builder().targetStat(StatType.SPELL_DEFENSE)
                                            .operation(ModifierOperation.ADD).value(3)
                                            .triggerContext(TriggerContext.ALWAYS).build(),
                                    ModifierEntry.builder().targetStat(StatType.SOCIAL_DEFENSE)
                                            .operation(ModifierOperation.ADD).value(3)
                                            .triggerContext(TriggerContext.ALWAYS).build()
                            ))
                            .build();
                    c.getActiveEffects().add(eff);
                    addLog(session, name, null, ActionType.COMBAT_OPTION,
                            name + " nimmt defensive Haltung ein (-3 Angriff, +3 Verteidigung).", true);
                }
                case NONE -> { /* keine Modifier */ }
            }
        }
    }

    public void deleteSession(Long sessionId) {
        sessionRepo.deleteById(sessionId);
    }

    public CombatSession endCombat(Long sessionId) {
        CombatSession session = findById(sessionId);
        session.setStatus(CombatStatus.FINISHED);

        // Schaden, Wunden und Karma aus dem Kampf auf die Charakterdatenblätter zurückschreiben
        for (CombatantState cs : session.getCombatants()) {
            GameCharacter c = cs.getCharacter();
            c.setCurrentDamage(cs.getCurrentDamage());
            c.setWounds(cs.getWounds());
            c.setKarmaCurrent(cs.getCurrentKarma());
            characterRepo.save(c);
        }

        addLog(session, null, null, ActionType.ROUND_CHANGE,
                "Kampf beendet! Schaden, Wunden und Karma aller Charaktere wurden auf die Datenblätter übertragen.", true);
        dialogStates.remove(sessionId);
        CombatSession saved = sessionRepo.save(session);
        broadcast(saved);
        return saved;
    }

    // --- Effekte ---

    public CombatSession addEffect(Long sessionId, Long combatantId, ActiveEffect effect) {
        CombatSession session = findById(sessionId);
        CombatantState combatant = findCombatant(session, combatantId);
        effect.setCombatantState(combatant);
        combatant.getActiveEffects().add(effect);
        addLog(session, null, combatant.getCharacter().getName(), ActionType.EFFECT_ADDED,
                "Effekt hinzugefügt: " + effect.getName(), true);
        CombatSession saved = sessionRepo.save(session);
        broadcast(saved);
        return saved;
    }

    public CombatSession removeEffect(Long sessionId, Long combatantId, Long effectId) {
        CombatSession session = findById(sessionId);
        CombatantState combatant = findCombatant(session, combatantId);
        combatant.getActiveEffects().stream()
                .filter(e -> e.getId().equals(effectId))
                .findFirst()
                .ifPresent(e -> {
                    combatant.getActiveEffects().remove(e);
                    addLog(session, null, combatant.getCharacter().getName(), ActionType.EFFECT_REMOVED,
                            "Effekt entfernt: " + e.getName(), true);
                });
        CombatSession saved = sessionRepo.save(session);
        broadcast(saved);
        return saved;
    }

    // --- Werte direkt anpassen ---

    public CombatSession updateCombatantValue(Long sessionId, Long combatantId, String field, int delta) {
        CombatSession session = findById(sessionId);
        CombatantState combatant = findCombatant(session, combatantId);
        String name = combatant.getCharacter().getName();

        switch (field) {
            case "damage" -> {
                combatant.setCurrentDamage(Math.max(0, combatant.getCurrentDamage() + delta));
                int ur = modifiers.getEffectiveValue(combatant, StatType.UNCONSCIOUSNESS_RATING, TriggerContext.ALWAYS);
                combatant.setDefeated(combatant.getCurrentDamage() >= ur);
            }
            case "wounds"     -> combatant.setWounds(Math.max(0, combatant.getWounds() + delta));
            case "karma"      -> combatant.setCurrentKarma(Math.max(0, combatant.getCurrentKarma() + delta));
            case "initiative" -> combatant.setInitiative(combatant.getInitiative() + delta);
            case "defeated"   -> combatant.setDefeated(delta > 0);
        }

        addLog(session, null, name, ActionType.VALUE_CHANGED,
                name + ": " + field + " um " + (delta >= 0 ? "+" : "") + delta + " geändert.", true);
        CombatSession saved = sessionRepo.save(session);
        broadcast(saved);
        return saved;
    }

    public List<CombatLog> getLog(Long sessionId) {
        CombatSession session = findById(sessionId);
        return session.getLog();
    }

    // --- Kampfoptionen ---

    public CombatSession declareCombatOption(Long sessionId, Long combatantId, String option) {
        CombatSession session = findById(sessionId);
        CombatantState combatant = findCombatant(session, combatantId);
        String name = combatant.getCharacter().getName();

        if (combatant.isDefeated()) {
            throw new IllegalStateException(name + " ist bewusstlos/besiegt und kann nicht handeln!");
        }

        switch (option) {
            case "USE_ACTION" -> {
                if (combatant.isHasActedThisRound()) {
                    throw new IllegalStateException(name + " hat diese Runde bereits gehandelt!");
                }
                combatant.setHasActedThisRound(true);
                addLog(session, name, null, ActionType.COMBAT_OPTION,
                        name + " nutzt eine Aktion (Zauber / Faden weben / Sonstiges).", true);
            }
            case "AGGRESSIVE_ATTACK", "DEFENSIVE_STANCE" ->
                    throw new IllegalStateException(
                            "Aggressive/Defensive Haltung werden in der Ansagephase (Rundenbeginn) gewählt, nicht während der Aktion.");
            default -> throw new IllegalArgumentException("Unbekannte Kampfoption: " + option);
        }

        CombatSession saved = sessionRepo.save(session);
        broadcast(saved);
        return saved;
    }

    // --- Ausweichen ---

    public DodgeResult resolveDodge(Long sessionId, DodgeRequest req) {
        CombatSession session = findById(sessionId);
        CombatantState defender = findCombatant(session, req.getDefenderCombatantId());
        String defName = defender.getCharacter().getName();

        int netDamage = defender.getPendingDodgeDamage();
        int attackTotal = defender.getPendingDodgeAttackTotal();
        int pendingDamageStep = defender.getPendingDamageStep();
        int pendingArmorValue = defender.getPendingArmorValue();
        RollResult pendingDamageRoll = null;
        try {
            if (defender.getPendingDamageRollJson() != null)
                pendingDamageRoll = objectMapper.readValue(defender.getPendingDamageRollJson(), RollResult.class);
        } catch (JsonProcessingException e) { log.error("Fehler beim Deserialisieren des ausstehenden Schadenswurfs", e); }

        // Ausstehende Werte zurücksetzen
        defender.setPendingDodgeDamage(0);
        defender.setPendingDodgeAttackTotal(0);
        defender.setPendingDamageStep(0);
        defender.setPendingArmorValue(0);
        defender.setPendingDamageRollJson(null);
        // Falls auch Riposte pending war: Riposte-Reaktion verwerfen, da Ausweichen gewählt wurde
        defender.setPendingRiposteAttackTotal(-1);
        defender.setPendingRiposteAttackerId(null);

        if (!req.isDodgeAttempted()) {
            // Kein Ausweichen — Schaden direkt anwenden
            int wtSkip = modifiers.getEffectiveValue(defender, StatType.WOUND_THRESHOLD, TriggerContext.ALWAYS);
            int prevWounds = defender.getWounds();
            KnockdownResult kdr = applyDamageToDefender(session, defender, netDamage);
            dischargePendingDamageAmulets(session, defender); // Schaden angewendet → Amulette entladen
            int newWounds = defender.getWounds() - prevWounds;
            addLog(session, defName, null, ActionType.DODGE, defName + " nimmt " + netDamage + " Schaden an (kein Ausweichen).", false);
            sessionRepo.save(session);
            DodgeResult declined = DodgeResult.builder()
                    .defenderName(defName)
                    .rollStep(0).roll(null).karmaRoll(null)
                    .attackTotal(attackTotal).success(false)
                    .damageCost(0).damageStep(pendingDamageStep).damageRoll(pendingDamageRoll).armorValue(pendingArmorValue).netDamageApplied(netDamage)
                    .newWounds(newWounds).totalWounds(defender.getWounds()).woundThreshold(wtSkip)
                    .targetDefeated(defender.isDefeated())
                    .knockdownResult(kdr)
                    .description(defName + " nimmt " + netDamage + " Schaden an.")
                    .build();
            broadcastWithModal(session, "DODGE", declined);
            return declined;
        }

        // Ausweichen-Probe
        CharacterTalent dodgeTalent = defender.getCharacter().getTalents().stream()
                .filter(t -> TalentNames.AUSWEICHEN.equals(t.getTalentDefinition().getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Ausweichen-Talent nicht gefunden"));

        // Kosten: 1 Schaden
        int damageCost = 1;
        defender.setCurrentDamage(defender.getCurrentDamage() + damageCost);

        // Karma — immer W6 (Stufe 4)
        RollResult karmaRoll = null;
        if (req.isSpendKarma() && defender.getCurrentKarma() > 0) {
            karmaRoll = diceService.roll(4);
            defender.setCurrentKarma(Math.max(0, defender.getCurrentKarma() - 1));
        }

        // Probe: DEX-Stufe + Rang + Bonus - Wunden
        int dexStep = Math.max(1, diceService.attributeToStep(defender.getCharacter().getDexterity()) - defender.getWounds());
        int rollStep = Math.max(1, dexStep + dodgeTalent.getRank() + req.getBonusSteps());
        RollResult roll = diceService.roll(rollStep);
        int total = roll.getTotal() + (karmaRoll != null ? karmaRoll.getTotal() : 0);

        boolean success = total >= attackTotal;

        int wtDodge = modifiers.getEffectiveValue(defender, StatType.WOUND_THRESHOLD, TriggerContext.ALWAYS);
        int netDamageApplied = 0;
        int newWounds = 0;
        KnockdownResult knockdownResult = null;
        if (!success) {
            netDamageApplied = netDamage;
            int prevWounds = defender.getWounds();
            knockdownResult = applyDamageToDefender(session, defender, netDamage);
            newWounds = defender.getWounds() - prevWounds;
            dischargePendingDamageAmulets(session, defender); // Treffer landet → Amulette entladen
        } else {
            // Erfolgreich ausgewichen → Schaden-Amulette bleiben geladen
            defender.setPendingDamageAmuletIds(null);
            defender.setPendingDamageAmuletAttackerId(null);
        }

        String desc = success
                ? defName + " weicht aus! (Wurf " + total + " vs Angriff " + attackTotal + ")"
                : defName + " kann nicht ausweichen. (Wurf " + total + " vs Angriff " + attackTotal + ") — " + netDamage + " Schaden.";
        addLog(session, defName, null, ActionType.DODGE, desc, success);

        sessionRepo.save(session);

        DodgeResult result = DodgeResult.builder()
                .defenderName(defName)
                .rollStep(rollStep).roll(roll).karmaRoll(karmaRoll)
                .attackTotal(attackTotal).success(success)
                .damageCost(damageCost).damageStep(pendingDamageStep).damageRoll(pendingDamageRoll).armorValue(pendingArmorValue).netDamageApplied(netDamageApplied)
                .newWounds(newWounds).totalWounds(defender.getWounds()).woundThreshold(wtDodge)
                .targetDefeated(defender.isDefeated())
                .knockdownResult(knockdownResult)
                .description(desc)
                .build();
        broadcastWithModal(session, "DODGE", result);
        return result;
    }

    /** Package-private: auch von SpellService genutzt */
    KnockdownResult applyDamageToDefender(CombatSession session, CombatantState defender, int netDamage) {
        defender.setCurrentDamage(defender.getCurrentDamage() + netDamage);
        int wt = modifiers.getEffectiveValue(defender, StatType.WOUND_THRESHOLD, TriggerContext.ALWAYS);
        int newWounds = netDamage / wt;
        defender.setWounds(defender.getWounds() + newWounds);
        int ur = modifiers.getEffectiveValue(defender, StatType.UNCONSCIOUSNESS_RATING, TriggerContext.ALWAYS);
        defender.setDefeated(defender.getCurrentDamage() >= ur);
        if (newWounds > 0 && !defender.isDefeated() && netDamage >= wt + 5) {
            return performKnockdownCheck(session, defender, netDamage, wt);
        }
        return null;
    }

    private KnockdownResult performKnockdownCheck(CombatSession session, CombatantState defender, int netDamage, int wt) {
        String name = defender.getCharacter().getName();
        int targetNumber = netDamage - wt;

        // Standhaftigkeit-Talent: STR-Step + Rang statt reiner STR-Step
        int strStep = Math.max(1, diceService.attributeToStep(defender.getCharacter().getStrength()) - defender.getWounds());
        int standhaftigkeitRank = defender.getCharacter().getTalents().stream()
                .filter(t -> TalentNames.STANDHAFTIGKEIT.equals(t.getTalentDefinition().getName()))
                .mapToInt(CharacterTalent::getRank)
                .findFirst().orElse(0);
        int rollStep = Math.max(1, strStep + standhaftigkeitRank);
        boolean usedTalent = standhaftigkeitRank > 0;

        RollResult roll = diceService.roll(rollStep);
        boolean knocked = roll.getTotal() < targetNumber;
        String talentLabel = usedTalent ? "Standhaftigkeit (STR+" + standhaftigkeitRank + ")" : "STR";
        String desc;
        if (knocked) {
            defender.setKnockedDown(true);
            applyNiedergeschlagenEffect(defender);
            // Akrobatische Verteidigung verliert sofort die Wirkung bei Niedergeschlagen
            defender.getActiveEffects().removeIf(e -> TalentNames.AKROBATISCHE_VERTEIDIGUNG.equals(e.getName()));
            desc = name + " ist niedergeschlagen! (" + talentLabel + " " + roll.getTotal() + " < " + targetNumber + ")";
        } else {
            desc = name + " bleibt stehen. (" + talentLabel + " " + roll.getTotal() + " vs " + targetNumber + ")";
        }
        addLog(session, name, null, ActionType.VALUE_CHANGED, desc, !knocked);
        return KnockdownResult.builder()
                .targetName(name)
                .rollStep(rollStep)
                .roll(roll)
                .targetNumber(targetNumber)
                .knockedDown(knocked)
                .description(desc)
                .build();
    }

    private void applyNiedergeschlagenEffect(CombatantState combatant) {
        ActiveEffect effect = ActiveEffect.builder()
                .combatantState(combatant)
                .name("Niedergeschlagen")
                .description("−3 auf alle Proben, −3 KV/MV/SV")
                .sourceType(SourceType.CONDITION)
                .remainingRounds(-1)
                .negative(true)
                .modifiers(List.of(
                        ModifierEntry.builder().targetStat(StatType.ATTACK_STEP)
                                .operation(ModifierOperation.ADD).value(-3)
                                .triggerContext(TriggerContext.ALWAYS).build(),
                        ModifierEntry.builder().targetStat(StatType.PHYSICAL_DEFENSE)
                                .operation(ModifierOperation.ADD).value(-3)
                                .triggerContext(TriggerContext.ALWAYS).build(),
                        ModifierEntry.builder().targetStat(StatType.SPELL_DEFENSE)
                                .operation(ModifierOperation.ADD).value(-3)
                                .triggerContext(TriggerContext.ALWAYS).build()
                ))
                .build();
        combatant.getActiveEffects().add(effect);
    }

    private void removeNiedergeschlagenEffect(CombatantState combatant) {
        combatant.getActiveEffects().removeIf(e -> "Niedergeschlagen".equals(e.getName()));
        combatant.setKnockedDown(false);
    }

    // --- Niedergeschlagen ---

    public StandUpResult standUp(Long sessionId, Long combatantId) {
        CombatSession session = findById(sessionId);
        CombatantState actor = findCombatant(session, combatantId);

        if (!actor.isKnockedDown()) throw new IllegalStateException("Kombattant ist nicht niedergeschlagen.");
        if (actor.isDefeated()) throw new IllegalStateException("Besiegte Kombattanten können nicht handeln.");
        if (actor.isHasActedThisRound()) throw new IllegalStateException("Diese Runde wurde bereits gehandelt.");

        removeNiedergeschlagenEffect(actor);
        actor.setHasActedThisRound(true);

        String desc = actor.getCharacter().getName() + " steht auf (Hauptaktion).";
        addLog(session, actor.getCharacter().getName(), null, ActionType.STAND_UP, desc, true);
        sessionRepo.save(session);
        broadcast(session);

        return StandUpResult.builder()
                .actorName(actor.getCharacter().getName())
                .simpleStandUp(true)
                .stillKnockedDown(false)
                .description(desc)
                .build();
    }

    public StandUpResult aufspringen(Long sessionId, Long combatantId, boolean spendKarma) {
        CombatSession session = findById(sessionId);
        CombatantState actor = findCombatant(session, combatantId);

        if (!actor.isKnockedDown()) throw new IllegalStateException("Kombattant ist nicht niedergeschlagen.");
        if (actor.isDefeated()) throw new IllegalStateException("Besiegte Kombattanten können nicht handeln.");

        int targetNumber = 6;
        int dexStep = Math.max(1, diceService.attributeToStep(actor.getCharacter().getDexterity()) - actor.getWounds());
        RollResult roll = diceService.roll(dexStep);

        RollResult karmaRoll = null;
        if (spendKarma && actor.getCurrentKarma() > 0) {
            karmaRoll = diceService.roll(4); // W6 = Stufe 4
            actor.setCurrentKarma(Math.max(0, actor.getCurrentKarma() - 1));
        }

        int total = roll.getTotal() + (karmaRoll != null ? karmaRoll.getTotal() : 0);
        boolean success = total >= targetNumber;

        // 2 Schaden in jedem Fall
        int damageTaken = 2;
        actor.setCurrentDamage(actor.getCurrentDamage() + damageTaken);
        int ur = modifiers.getEffectiveValue(actor, StatType.UNCONSCIOUSNESS_RATING, TriggerContext.ALWAYS);
        if (actor.getCurrentDamage() >= ur) actor.setDefeated(true);

        boolean stillKnockedDown = !success;
        if (success) {
            removeNiedergeschlagenEffect(actor);
        }

        String desc = success
                ? actor.getCharacter().getName() + " springt auf! (GE " + total + " vs " + targetNumber + ") − 2 Schaden."
                : actor.getCharacter().getName() + " scheitert beim Aufspringen. (GE " + total + " vs " + targetNumber + ") − 2 Schaden.";
        addLog(session, actor.getCharacter().getName(), null, ActionType.AUFSPRINGEN, desc, success);
        sessionRepo.save(session);
        broadcast(session);

        return StandUpResult.builder()
                .actorName(actor.getCharacter().getName())
                .simpleStandUp(false)
                .rollStep(dexStep)
                .roll(roll)
                .karmaRoll(karmaRoll)
                .targetNumber(targetNumber)
                .success(success)
                .damageTaken(damageTaken)
                .stillKnockedDown(stillKnockedDown)
                .description(desc)
                .build();
    }

    // --- Verspotten ---

    public TauntResult performTaunt(Long sessionId, TauntRequest req) {
        CombatSession session = findById(sessionId);
        CombatantState actor  = findCombatant(session, req.getActorCombatantId());
        CombatantState target = findCombatant(session, req.getTargetCombatantId());

        if (actor.isDefeated())  throw new IllegalStateException(actor.getCharacter().getName() + " ist besiegt und kann nicht handeln.");
        if (session.getPhase() != CombatPhase.ACTION) throw new IllegalStateException("Aktionen sind nur in der Aktionsphase möglich.");

        // Verspotten-Talent laden
        CharacterTalent ct = actor.getCharacter().getTalents().stream()
                .filter(t -> TalentNames.VERSPOTTEN.equals(t.getTalentDefinition().getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Talent 'Verspotten' nicht gefunden."));

        // 1 Überanstrengung sofort
        actor.setCurrentDamage(actor.getCurrentDamage() + 1);

        // Würfelstufe: CHA-Step + Rang + Bonus - Wunden
        int chaStep = Math.max(1, diceService.attributeToStep(actor.getCharacter().getCharisma()) - actor.getWounds());
        int rollStep = Math.max(1, chaStep + ct.getRank() + req.getBonusSteps());

        // Karma
        RollResult karmaRoll = null;
        if (req.isSpendKarma() && actor.getCurrentKarma() > 0) {
            karmaRoll = diceService.roll(4); // W6
            actor.setCurrentKarma(Math.max(0, actor.getCurrentKarma() - 1));
        }

        RollResult roll = diceService.roll(rollStep);
        int total = roll.getTotal() + (karmaRoll != null ? karmaRoll.getTotal() : 0);

        // Soziale Verteidigung des Ziels
        int socialDef = modifiers.getEffectiveValue(target, StatType.SOCIAL_DEFENSE, TriggerContext.ON_SOCIAL_ACTION);
        boolean success = total >= socialDef;
        int extraSuccesses = success ? (total - socialDef) / 5 : 0;

        String actorName  = actor.getCharacter().getName();
        String targetName = target.getCharacter().getName();

        TauntResult.TauntResultBuilder result = TauntResult.builder()
                .actorName(actorName)
                .targetName(targetName)
                .rollStep(rollStep)
                .roll(roll)
                .karmaRoll(karmaRoll)
                .socialDefense(socialDef)
                .success(success)
                .extraSuccesses(extraSuccesses);

        if (!success) {
            result.penalty(0).duration(0)
                  .description(actorName + " versucht " + targetName + " zu verspotten, scheitert aber. (" + total + " vs SV " + socialDef + ")");
            addLog(session, actorName, targetName, ActionType.TAUNT, result.build().getDescription(), false);
            sessionRepo.save(session);
            broadcast(session);
            return result.build();
        }

        // Erfolg = −1, je Übererfolg ein weiteres −1 (also −(1 + Übererfolge))
        int penalty = 1 + extraSuccesses;
        int duration = ct.getRank();

        // Starrsinn-Gegenprobe des Ziels
        RollResult resistRoll = null;
        int resistStep = 0;
        boolean resisted = false;

        java.util.Optional<CharacterTalent> resistTalent = target.getCharacter().getTalents().stream()
                .filter(t -> TalentNames.STARRSINN.equals(t.getTalentDefinition().getName()))
                .findFirst();

        if (resistTalent.isPresent()) {
            int wilStep = Math.max(1, diceService.attributeToStep(target.getCharacter().getWillpower()) - target.getWounds());
            resistStep = Math.max(1, wilStep + resistTalent.get().getRank());
            resistRoll = diceService.roll(resistStep);
            resisted = resistRoll.getTotal() >= total; // Gegenprobe vs Verspotten-Ergebnis
        }

        result.resistRoll(resistRoll).resistStep(resistStep).resisted(resisted);

        if (!resisted && penalty > 0) {
            // ActiveEffect auf das Ziel: -penalty auf ATTACK_STEP und SOCIAL_DEFENSE
            ActiveEffect tauntEffect = ActiveEffect.builder()
                    .combatantState(target)
                    .name("Verspottet")
                    .description("−" + penalty + " auf alle Aktionsproben und Soziale Verteidigung (" + actorName + ")")
                    .sourceType(SourceType.CONDITION)
                    .remainingRounds(duration)
                    .negative(true)
                    .modifiers(List.of(
                            ModifierEntry.builder().targetStat(StatType.ATTACK_STEP)
                                    .operation(ModifierOperation.ADD).value(-penalty)
                                    .triggerContext(TriggerContext.ALWAYS).build(),
                            ModifierEntry.builder().targetStat(StatType.SOCIAL_DEFENSE)
                                    .operation(ModifierOperation.ADD).value(-penalty)
                                    .triggerContext(TriggerContext.ALWAYS).build()
                    ))
                    .build();
            target.getActiveEffects().add(tauntEffect);
            result.penalty(penalty).duration(duration);
        } else {
            result.penalty(0).duration(0);
        }

        String desc;
        if (resisted) {
            desc = actorName + " verspottet " + targetName + " (" + total + " vs SV " + socialDef
                 + "), aber " + targetName + " widersteht mit Starrsinn (" + resistRoll.getTotal() + ")!";
        } else {
            desc = actorName + " verspottet " + targetName + " erfolgreich! " + extraSuccesses + " Übererfolg(e) → −"
                 + penalty + " auf Proben/SV für " + duration + " Runden.";
        }
        result.description(desc);

        addLog(session, actorName, targetName, ActionType.TAUNT, desc, success && !resisted);
        sessionRepo.save(session);

        TauntResult tauntResult = result.build();
        broadcastWithModal(session, "TAUNT", tauntResult);
        return tauntResult;
    }

    // --- Akrobatische Verteidigung ---

    public AcrobaticDefenseResult performAcrobaticDefense(Long sessionId, Long actorCombatantId,
                                                          int bonusSteps, boolean spendKarma) {
        CombatSession session = findById(sessionId);
        CombatantState actor  = findCombatant(session, actorCombatantId);

        if (actor.isDefeated()) throw new IllegalStateException(actor.getCharacter().getName() + " ist besiegt.");
        if (session.getPhase() != CombatPhase.ACTION) throw new IllegalStateException("Nur in der Aktionsphase möglich.");

        // Kombination mit Kampfsinn verboten
        boolean hasCombatSense = actor.getActiveEffects().stream()
                .anyMatch(e -> e.getName().startsWith(TalentNames.KAMPFSINN));
        if (hasCombatSense) throw new IllegalStateException(
                "Akrobatische Verteidigung und Kampfsinn können nicht in derselben Runde kombiniert werden.");

        CharacterTalent ct = actor.getCharacter().getTalents().stream()
                .filter(t -> TalentNames.AKROBATISCHE_VERTEIDIGUNG.equals(t.getTalentDefinition().getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Talent 'Akrobatische Verteidigung' nicht gefunden."));

        // 1 Überanstrengung
        actor.setCurrentDamage(actor.getCurrentDamage() + 1);

        // Würfelschritt: GES-Step + Rang + Bonus − Wunden
        int dexStep = Math.max(1, diceService.attributeToStep(actor.getCharacter().getDexterity()) - actor.getWounds());
        int rollStep = Math.max(1, dexStep + ct.getRank() + bonusSteps);

        RollResult karmaRoll = null;
        if (spendKarma && actor.getCurrentKarma() > 0) {
            karmaRoll = diceService.roll(4);
            actor.setCurrentKarma(Math.max(0, actor.getCurrentKarma() - 1));
        }

        RollResult roll = diceService.roll(rollStep);
        int total = roll.getTotal() + (karmaRoll != null ? karmaRoll.getTotal() : 0);

        // Ziel-TN: höchste KV aller nicht-besiegten Gegner
        int targetNumber = session.getCombatants().stream()
                .filter(c -> !c.getId().equals(actor.getId()) && !c.isDefeated())
                .mapToInt(c -> modifiers.getEffectiveValue(c, StatType.PHYSICAL_DEFENSE, TriggerContext.ALWAYS))
                .max().orElse(8);

        boolean success  = total >= targetNumber;
        int extraSucc    = success ? (total - targetNumber) / 5 : 0;
        int successes    = success ? 1 + extraSucc : 0;
        int bonusApplied = successes * 2;

        String actorName = actor.getCharacter().getName();

        if (success && bonusApplied > 0) {
            ActiveEffect effect = ActiveEffect.builder()
                    .combatantState(actor)
                    .name(TalentNames.AKROBATISCHE_VERTEIDIGUNG)
                    .description("+" + bonusApplied + " KV (Akrobatik, bis Rundenende)")
                    .sourceType(SourceType.TALENT)
                    .remainingRounds(1)
                    .negative(false)
                    .modifiers(List.of(
                            ModifierEntry.builder()
                                    .targetStat(StatType.PHYSICAL_DEFENSE)
                                    .operation(ModifierOperation.ADD)
                                    .value(bonusApplied)
                                    .triggerContext(TriggerContext.ALWAYS)
                                    .build()
                    ))
                    .build();
            actor.getActiveEffects().add(effect);
        }

        String desc = success
                ? actorName + " setzt Akrobatische Verteidigung ein! " + successes + " Erfolg(e) → +" + bonusApplied + " KV bis Rundenende."
                : actorName + " setzt Akrobatische Verteidigung ein, scheitert. (" + total + " vs KV " + targetNumber + ")";

        addLog(session, actorName, null, ActionType.ACROBATIC_DEFENSE, desc, success);
        sessionRepo.save(session);

        AcrobaticDefenseResult result = AcrobaticDefenseResult.builder()
                .actorName(actorName)
                .rollStep(rollStep)
                .roll(roll)
                .karmaRoll(karmaRoll)
                .targetNumber(targetNumber)
                .success(success)
                .successes(successes)
                .bonusApplied(bonusApplied)
                .damageTaken(1)
                .description(desc)
                .build();
        broadcastWithModal(session, "ACROBATIC_DEFENSE", result);
        return result;
    }

    // --- Kampfsinn ---

    public CombatSenseResult performCombatSense(Long sessionId, CombatSenseRequest req) {
        CombatSession session = findById(sessionId);
        CombatantState actor  = findCombatant(session, req.getActorCombatantId());
        CombatantState target = findCombatant(session, req.getTargetCombatantId());

        if (actor.isDefeated()) throw new IllegalStateException(actor.getCharacter().getName() + " ist besiegt.");
        if (session.getPhase() != CombatPhase.ACTION) throw new IllegalStateException("Nur in der Aktionsphase möglich.");

        // Nur gegen Gegner mit niedrigerer Initiative
        if (actor.getInitiativeOrder() >= target.getInitiativeOrder()) {
            throw new IllegalStateException("Kampfsinn kann nur gegen Gegner mit niedrigerer Initiative eingesetzt werden.");
        }

        // Kombination mit Akrobatischer Verteidigung verboten
        boolean hasAcrobatic = actor.getActiveEffects().stream()
                .anyMatch(e -> TalentNames.AKROBATISCHE_VERTEIDIGUNG.equals(e.getName()));
        if (hasAcrobatic) throw new IllegalStateException(
                "Kampfsinn und Akrobatische Verteidigung können nicht in derselben Runde kombiniert werden.");

        CharacterTalent ct = actor.getCharacter().getTalents().stream()
                .filter(t -> TalentNames.KAMPFSINN.equals(t.getTalentDefinition().getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Talent 'Kampfsinn' nicht gefunden."));

        // Max uses per round = talent rank
        long usesThisRound = actor.getActiveEffects().stream()
                .filter(e -> "Kampfsinn (KV)".equals(e.getName()))
                .count();
        if (usesThisRound >= ct.getRank()) {
            throw new IllegalStateException(
                "Kampfsinn kann in dieser Runde nicht öfter als Rang " + ct.getRank() + " mal eingesetzt werden.");
        }

        // Can't target the same opponent twice per round
        String targetNameCheck = target.getCharacter().getName();
        boolean alreadyTargeted = actor.getActiveEffects().stream()
                .anyMatch(e -> "Kampfsinn (KV)".equals(e.getName())
                        && e.getDescription() != null
                        && e.getDescription().contains("gegen " + targetNameCheck + " (Kampfsinn)"));
        if (alreadyTargeted) {
            throw new IllegalStateException(
                "Kampfsinn wurde in dieser Runde bereits gegen " + targetNameCheck + " eingesetzt.");
        }

        // 1 Überanstrengung
        actor.setCurrentDamage(actor.getCurrentDamage() + 1);

        // Würfelschritt: WAH-Step + Rang + Bonus − Wunden
        int perStep  = Math.max(1, diceService.attributeToStep(actor.getCharacter().getPerception()) - actor.getWounds());
        int rollStep = Math.max(1, perStep + ct.getRank() + req.getBonusSteps());

        RollResult karmaRoll = null;
        if (req.isSpendKarma() && actor.getCurrentKarma() > 0) {
            karmaRoll = diceService.roll(4);
            actor.setCurrentKarma(Math.max(0, actor.getCurrentKarma() - 1));
        }

        RollResult roll = diceService.roll(rollStep);
        int total = roll.getTotal() + (karmaRoll != null ? karmaRoll.getTotal() : 0);

        // Mystische Verteidigung des Ziels
        int mysticDef  = modifiers.getEffectiveValue(target, StatType.SPELL_DEFENSE, TriggerContext.ALWAYS);
        boolean success = total >= mysticDef;
        int extraSucc   = success ? (total - mysticDef) / 5 : 0;
        int successes   = success ? 1 + extraSucc : 0;
        int defenseBonus = successes * 2;
        int attackBonus  = successes * 2;

        String actorName  = actor.getCharacter().getName();
        String targetName = target.getCharacter().getName();
        // Kampfsinn ist eine freie Aktion — kein hasActedThisRound

        if (success && successes > 0) {
            // KV-Bonus auf den Anwender (vereinfacht: global, nicht nur vs. dieses Ziel)
            ActiveEffect defEffect = ActiveEffect.builder()
                    .combatantState(actor)
                    .name("Kampfsinn (KV)")
                    .description("+" + defenseBonus + " KV gegen " + targetName + " (Kampfsinn)")
                    .sourceType(SourceType.TALENT)
                    .remainingRounds(1)
                    .negative(false)
                    .modifiers(List.of(
                            ModifierEntry.builder()
                                    .targetStat(StatType.PHYSICAL_DEFENSE)
                                    .operation(ModifierOperation.ADD)
                                    .value(defenseBonus)
                                    .triggerContext(TriggerContext.ALWAYS)
                                    .build()
                    ))
                    .build();
            actor.getActiveEffects().add(defEffect);

            // Angriffsbonus auf den Anwender (für den nächsten Angriff, Nahkampf und Fernkampf)
            ActiveEffect atkEffect = ActiveEffect.builder()
                    .combatantState(actor)
                    .name("Kampfsinn (Angriff)")
                    .description("+" + attackBonus + " Angriff gegen " + targetName + " (Kampfsinn)")
                    .sourceType(SourceType.TALENT)
                    .remainingRounds(1)
                    .negative(false)
                    .modifiers(List.of(
                            ModifierEntry.builder()
                                    .targetStat(StatType.ATTACK_STEP)
                                    .operation(ModifierOperation.ADD)
                                    .value(attackBonus)
                                    .triggerContext(TriggerContext.ON_MELEE_ATTACK)
                                    .build(),
                            ModifierEntry.builder()
                                    .targetStat(StatType.ATTACK_STEP)
                                    .operation(ModifierOperation.ADD)
                                    .value(attackBonus)
                                    .triggerContext(TriggerContext.ON_RANGED_ATTACK)
                                    .build()
                    ))
                    .build();
            actor.getActiveEffects().add(atkEffect);
        }

        String desc = success
                ? actorName + " aktiviert Kampfsinn gegen " + targetName + "! " + successes + " Erfolg(e) → +"
                  + defenseBonus + " KV, +" + attackBonus + " auf Angriff bis Rundenende."
                : actorName + " setzt Kampfsinn gegen " + targetName + " ein, scheitert. (" + total + " vs MV " + mysticDef + ")";

        addLog(session, actorName, targetName, ActionType.COMBAT_SENSE, desc, success);
        sessionRepo.save(session);

        CombatSenseResult result = CombatSenseResult.builder()
                .actorName(actorName)
                .targetName(targetName)
                .rollStep(rollStep)
                .roll(roll)
                .karmaRoll(karmaRoll)
                .mysticDefense(mysticDef)
                .success(success)
                .successes(successes)
                .defenseBonus(defenseBonus)
                .attackBonus(attackBonus)
                .damageTaken(1)
                .description(desc)
                .build();
        broadcastWithModal(session, "COMBAT_SENSE", result);
        return result;
    }

    // --- Ablenken ---

    public DistractResult performDistract(Long sessionId, DistractRequest req) {
        CombatSession session = findById(sessionId);
        CombatantState actor  = findCombatant(session, req.getActorCombatantId());
        CombatantState target = findCombatant(session, req.getTargetCombatantId());

        if (actor.isDefeated()) throw new IllegalStateException(actor.getCharacter().getName() + " ist besiegt.");
        if (session.getPhase() != CombatPhase.ACTION) throw new IllegalStateException("Nur in der Aktionsphase möglich.");
        if (actor.isHasActedThisRound()) throw new IllegalStateException(actor.getCharacter().getName() + " hat diese Runde bereits gehandelt.");

        CharacterTalent ct = actor.getCharacter().getTalents().stream()
                .filter(t -> TalentNames.ABLENKEN.equals(t.getTalentDefinition().getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Talent 'Ablenken' nicht gefunden."));

        // 1 Überanstrengung
        actor.setCurrentDamage(actor.getCurrentDamage() + 1);

        // Würfelschritt: CHA-Step + Rang + Bonus − Wunden
        int chaStep  = Math.max(1, diceService.attributeToStep(actor.getCharacter().getCharisma()) - actor.getWounds());
        int rollStep = Math.max(1, chaStep + ct.getRank() + req.getBonusSteps());

        RollResult karmaRoll = null;
        if (req.isSpendKarma() && actor.getCurrentKarma() > 0) {
            karmaRoll = diceService.roll(4);
            actor.setCurrentKarma(Math.max(0, actor.getCurrentKarma() - 1));
        }

        RollResult roll = diceService.roll(rollStep);
        int total = roll.getTotal() + (karmaRoll != null ? karmaRoll.getTotal() : 0);

        // Soziale Verteidigung des Ziels
        int socialDef  = modifiers.getEffectiveValue(target, StatType.SOCIAL_DEFENSE, TriggerContext.ON_SOCIAL_ACTION);
        boolean success = total >= socialDef;
        int extraSucc   = success ? (total - socialDef) / 5 : 0;
        int successes   = success ? 1 + extraSucc : 0;

        String actorName  = actor.getCharacter().getName();
        String targetName = target.getCharacter().getName();
        actor.setHasActedThisRound(true);

        if (success && successes > 0) {
            // Anwender: −successes auf eigene KV (ist im Toten Winkel des Ziels)
            ActiveEffect actorEffect = ActiveEffect.builder()
                    .combatantState(actor)
                    .name("Ablenkt (KV−)")
                    .description("−" + successes + " KV gegen " + targetName + " (Ablenken)")
                    .sourceType(SourceType.CONDITION)
                    .remainingRounds(1)
                    .negative(true)
                    .modifiers(List.of(
                            ModifierEntry.builder()
                                    .targetStat(StatType.PHYSICAL_DEFENSE)
                                    .operation(ModifierOperation.ADD)
                                    .value(-successes)
                                    .triggerContext(TriggerContext.ALWAYS)
                                    .build()
                    ))
                    .build();
            actor.getActiveEffects().add(actorEffect);

            // Ziel: −successes auf KV (Toter Winkel für Verbündete)
            ActiveEffect targetEffect = ActiveEffect.builder()
                    .combatantState(target)
                    .name("Abgelenkt")
                    .description("−" + successes + " KV gegen Verbündete von " + actorName + " (Toter Winkel)")
                    .sourceType(SourceType.CONDITION)
                    .remainingRounds(1)
                    .negative(true)
                    .modifiers(List.of(
                            ModifierEntry.builder()
                                    .targetStat(StatType.PHYSICAL_DEFENSE)
                                    .operation(ModifierOperation.ADD)
                                    .value(-successes)
                                    .triggerContext(TriggerContext.ALWAYS)
                                    .build()
                    ))
                    .build();
            target.getActiveEffects().add(targetEffect);
        }

        String desc = success
                ? actorName + " lenkt " + targetName + " ab! " + successes + " Erfolg(e) → −" + successes
                  + " KV für " + actorName + " und " + targetName + " (Toter Winkel für Verbündete)."
                : actorName + " versucht " + targetName + " abzulenken, scheitert. (" + total + " vs SV " + socialDef + ")";

        addLog(session, actorName, targetName, ActionType.DISTRACT, desc, success);
        sessionRepo.save(session);

        DistractResult result = DistractResult.builder()
                .actorName(actorName)
                .targetName(targetName)
                .rollStep(rollStep)
                .roll(roll)
                .karmaRoll(karmaRoll)
                .socialDefense(socialDef)
                .success(success)
                .successes(successes)
                .actorPenalty(success ? successes : 0)
                .targetPenalty(success ? successes : 0)
                .damageTaken(1)
                .description(desc)
                .build();
        broadcastWithModal(session, "DISTRACT", result);
        return result;
    }

    // --- Schwachstelle erkennen ---

    /**
     * Schwachstelle erkennen: WAH-Step + Rang vs. max(MV, physische Rüstung) des Ziels.
     * Bei Erfolg: pro Erfolg +2 Schaden auf physische Angriffe (Nahkampf/Fernkampf) gegen
     * dieses spezifische Ziel für Rang Runden. Kostet 1 Überanstrengung. Verbraucht KEINE
     * Hauptaktion. Nicht für Zaubersprüche.
     *
     * Ein bereits aktiver Schwachstelle-Effekt gegen dasselbe Ziel wird ersetzt
     * (neue Probe überschreibt alte).
     */
    public SpotArmorFlawResult performSpotArmorFlaw(SpotArmorFlawRequest req) {
        CombatSession session = findById(req.getSessionId());
        CombatantState actor  = findCombatant(session, req.getActorCombatantId());
        CombatantState target = findCombatant(session, req.getTargetCombatantId());

        if (actor.isDefeated()) throw new IllegalStateException(actor.getCharacter().getName() + " ist besiegt.");
        if (session.getPhase() != CombatPhase.ACTION) throw new IllegalStateException("Nur in der Aktionsphase möglich.");

        CharacterTalent ct = actor.getCharacter().getTalents().stream()
                .filter(t -> TalentNames.SCHWACHSTELLE_ERKENNEN.equals(t.getTalentDefinition().getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Talent 'Schwachstelle erkennen' nicht gefunden."));

        // 1 Überanstrengung
        actor.setCurrentDamage(actor.getCurrentDamage() + 1);

        // Würfelschritt: WAH-Step + Rang + Bonus − Wunden
        int perStep  = Math.max(1, diceService.attributeToStep(actor.getCharacter().getPerception()) - actor.getWounds());
        int rollStep = Math.max(1, perStep + ct.getRank() + req.getBonusSteps());

        RollResult karmaRoll = null;
        if (req.isSpendKarma() && actor.getCurrentKarma() > 0) {
            karmaRoll = diceService.roll(4);
            actor.setCurrentKarma(Math.max(0, actor.getCurrentKarma() - 1));
        }

        RollResult roll = diceService.roll(rollStep);
        int total = roll.getTotal() + (karmaRoll != null ? karmaRoll.getTotal() : 0);

        // TN = max(MV, physische Rüstung)
        int spellDef = modifiers.getEffectiveValue(target, StatType.SPELL_DEFENSE, TriggerContext.ON_SPELL_DEFENSE);
        int physArmor = modifiers.getEffectiveValue(target, StatType.PHYSICAL_ARMOR, TriggerContext.ON_DAMAGE_RECEIVED);
        int tn = Math.max(spellDef, physArmor);

        boolean success = total >= tn;
        int extraSucc   = success ? (total - tn) / 5 : 0;
        int successes   = success ? 1 + extraSucc : 0;
        int damageBonus = successes * 2;
        int duration    = ct.getRank();

        String actorName  = actor.getCharacter().getName();
        String targetName = target.getCharacter().getName();

        if (success && successes > 0) {
            // Vorhandenen Effekt gegen dasselbe Ziel entfernen (neue Probe überschreibt)
            actor.getActiveEffects().removeIf(e ->
                    e.getSourceType() == SourceType.TALENT
                    && e.getName() != null
                    && e.getName().startsWith(TalentNames.SCHWACHSTELLE_ERKENNEN)
                    && req.getTargetCombatantId().equals(e.getTargetCombatantId()));

            ActiveEffect effect = ActiveEffect.builder()
                    .combatantState(actor)
                    .name(TalentNames.SCHWACHSTELLE_ERKENNEN + " vs " + targetName)
                    .description("+" + damageBonus + " Schaden gegen " + targetName + " (physische Angriffe) für " + duration + " Runde(n)")
                    .sourceType(SourceType.TALENT)
                    .remainingRounds(duration)
                    .negative(false)
                    .targetCombatantId(req.getTargetCombatantId())
                    .modifiers(List.of(
                            ModifierEntry.builder()
                                    .targetStat(StatType.DAMAGE_STEP)
                                    .operation(ModifierOperation.ADD)
                                    .value(damageBonus)
                                    .triggerContext(TriggerContext.ON_DAMAGE_DEALT)
                                    .build()
                    ))
                    .build();
            actor.getActiveEffects().add(effect);
        }

        String desc = success
                ? actorName + " erkennt Schwachstellen an " + targetName + "! " + successes
                  + " Erfolg(e) → +" + damageBonus + " Schaden auf physische Angriffe für " + duration + " Runde(n) (TN " + tn + ")."
                : actorName + " sucht Schwachstellen an " + targetName + ", findet keine. (" + total + " vs " + tn + ")";

        addLog(session, actorName, targetName, ActionType.SPOT_ARMOR_FLAW, desc, success);
        sessionRepo.save(session);

        SpotArmorFlawResult result = SpotArmorFlawResult.builder()
                .actorName(actorName)
                .targetName(targetName)
                .rollStep(rollStep)
                .roll(roll)
                .karmaRoll(karmaRoll)
                .targetNumber(tn)
                .spellDefense(spellDef)
                .physicalArmor(physArmor)
                .success(success)
                .successes(successes)
                .damageBonus(damageBonus)
                .duration(duration)
                .strainCost(1)
                .description(desc)
                .build();
        broadcastWithModal(session, "SPOT_ARMOR_FLAW", result);
        return result;
    }

    // --- Eiserner Wille ---

    /**
     * Freie Aktion: Widerstand gegen einen laufenden Zauber/Talent-Angriff.
     * Der Anwender würfelt WIL-Step + Rang vs. den Angriffswurf des Zauberers.
     * Bei Erfolg (≥ attackTotal): aktiver Effekt des Angriffs wird entfernt.
     *
     * @param sessionId       Session
     * @param actorCombatantId  Verteidiger (wer Eiserner Wille einsetzt)
     * @param attackTotal     Angriffswurf des Zauberers (aus dem UI übergeben)
     * @param spendKarma      Karma einsetzen?
     */
    public IronWillResult performIronWill(Long sessionId, Long actorCombatantId,
                                          int attackTotal, boolean spendKarma) {
        CombatSession session = findById(sessionId);
        CombatantState actor  = findCombatant(session, actorCombatantId);

        if (actor.isDefeated()) throw new IllegalStateException(actor.getCharacter().getName() + " ist besiegt.");
        if (session.getPhase() != CombatPhase.ACTION) throw new IllegalStateException("Nur in der Aktionsphase möglich.");

        CharacterTalent ct = actor.getCharacter().getTalents().stream()
                .filter(t -> TalentNames.EISERNER_WILLE.equals(t.getTalentDefinition().getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Talent 'Eiserner Wille' nicht gefunden."));

        // 1 Überanstrengung (freie Aktion — kein hasActedThisRound)
        actor.setCurrentDamage(actor.getCurrentDamage() + 1);

        // Würfelschritt: WIL-Step + Rang − Wunden
        int wilStep  = Math.max(1, diceService.attributeToStep(actor.getCharacter().getWillpower()) - actor.getWounds());
        int rollStep = Math.max(1, wilStep + ct.getRank());

        RollResult karmaRoll = null;
        if (spendKarma && actor.getCurrentKarma() > 0) {
            karmaRoll = diceService.roll(4);
            actor.setCurrentKarma(Math.max(0, actor.getCurrentKarma() - 1));
        }

        RollResult roll = diceService.roll(rollStep);
        int total = roll.getTotal() + (karmaRoll != null ? karmaRoll.getTotal() : 0);

        // Erfolg wenn Ergebnis ≥ Angriffswurf des Zauberers
        boolean success      = total >= attackTotal;
        boolean effectNegated = false;
        String actorName     = actor.getCharacter().getName();

        // Bei Erfolg: neuester negativer Effekt mit magischer Quelle entfernen
        if (success) {
            java.util.Optional<ActiveEffect> toRemove = actor.getActiveEffects().stream()
                    .filter(e -> e.isNegative() && e.getSourceType() == SourceType.SPELL)
                    .reduce((a, b) -> b); // letzter (zuletzt hinzugefügt)
            if (toRemove.isPresent()) {
                actor.getActiveEffects().remove(toRemove.get());
                effectNegated = true;
            }
        }

        String desc = success
                ? actorName + " setzt Eisernen Willen ein! (" + total + " vs " + attackTotal + ") "
                  + (effectNegated ? "Magischer Effekt abgewehrt!" : "Erfolg, aber kein aktiver Effekt zum Abwehren.")
                : actorName + " setzt Eisernen Willen ein, scheitert. (" + total + " < " + attackTotal + ")";

        addLog(session, actorName, null, ActionType.IRON_WILL, desc, success);
        sessionRepo.save(session);

        IronWillResult result = IronWillResult.builder()
                .actorName(actorName)
                .rollStep(rollStep)
                .roll(roll)
                .karmaRoll(karmaRoll)
                .attackTotal(attackTotal)
                .success(success)
                .effectNegated(effectNegated)
                .damageTaken(1)
                .description(desc)
                .build();
        broadcastWithModal(session, "IRON_WILL", result);
        return result;
    }

    // --- Helpers ---

    public FreeActionResult performFreeAction(Long sessionId, FreeActionRequest req) {
        CombatSession session = findById(sessionId);
        CombatantState actor = findCombatant(session, req.getActorCombatantId());

        if (actor.isDefeated()) throw new IllegalStateException("Besiegte Kombattanten können nicht handeln.");

        // Talent laden
        CharacterTalent ct = actor.getCharacter().getTalents().stream()
                .filter(t -> t.getTalentDefinition().getId().equals(req.getTalentId()))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Talent nicht auf Charakter: " + req.getTalentId()));

        TalentDefinition talent = ct.getTalentDefinition();
        if (!talent.isFreeAction()) throw new IllegalArgumentException("Talent ist keine freie Aktion: " + talent.getName());

        // Schadenskosten
        int damageTaken = talent.getFreeActionDamageCost();
        if (damageTaken > 0) actor.setCurrentDamage(actor.getCurrentDamage() + damageTaken);

        // Würfelschritt: Attributwert-Stufe + Rang + Bonusstufen
        int attrValue = getAttributeValue(actor.getCharacter(), talent.getAttribute());
        int rollStep = Math.max(1, diceService.attributeToStep(attrValue) + ct.getRank() + req.getBonusSteps() - actor.getWounds());

        // Karma — immer W6 (Stufe 4)
        RollResult karmaRoll = null;
        if (req.isSpendKarma() && actor.getCurrentKarma() > 0) {
            karmaRoll = diceService.roll(4);
            actor.setCurrentKarma(Math.max(0, actor.getCurrentKarma() - 1));
        }

        RollResult roll = diceService.roll(rollStep);
        int total = roll.getTotal() + (karmaRoll != null ? karmaRoll.getTotal() : 0);

        // Ziel-Verteidigung
        CombatantState target = null;
        int defenseValue = 0;
        String targetName = null;
        if (talent.getFreeActionTestStat() != null && req.getTargetCombatantId() != null) {
            target = findCombatant(session, req.getTargetCombatantId());
            targetName = target.getCharacter().getName();
            defenseValue = modifiers.getEffectiveValue(target, talent.getFreeActionTestStat(), TriggerContext.ALWAYS);
        }

        boolean success = defenseValue == 0 || total >= defenseValue;
        int extraSuccesses = success && defenseValue > 0 ? (total - defenseValue) / 5 : (success ? 1 : 0);
        boolean effectApplied = false;

        if (success && talent.getFreeActionModifyStat() != null) {
            // Basisbonus bei Erfolg + je Übererfolg ein weiterer Bonus
            double modValue = (1 + extraSuccesses) * talent.getFreeActionValuePerSuccess();
            ModifierEntry modifier = ModifierEntry.builder()
                    .targetStat(talent.getFreeActionModifyStat())
                    .operation(ModifierOperation.ADD)
                    .value(modValue)
                    .triggerContext(talent.getFreeActionTriggerContext())
                    .build();

            CombatantState effectTarget = talent.getFreeActionEffectTarget() == FreeActionTarget.TARGET && target != null
                    ? target : actor;

            ActiveEffect effect = ActiveEffect.builder()
                    .combatantState(effectTarget)
                    .name(talent.getName())
                    .description(talent.getDescription())
                    .sourceType(SourceType.TALENT)
                    .sourceId(talent.getId())
                    .remainingRounds(talent.getFreeActionDuration())
                    .negative(modValue < 0)
                    .modifiers(List.of(modifier))
                    .build();

            effectTarget.getActiveEffects().add(effect);
            effectApplied = true;
        }

        String actorName = actor.getCharacter().getName();
        String desc = buildFreeActionDescription(actorName, targetName, talent.getName(), total, defenseValue, success, extraSuccesses, effectApplied);
        addLog(session, actorName, targetName, ActionType.FREE_ACTION, desc, success);

        sessionRepo.save(session);
        broadcast(session);

        return FreeActionResult.builder()
                .actorName(actorName)
                .targetName(targetName)
                .talentName(talent.getName())
                .rollStep(rollStep)
                .roll(roll)
                .karmaRoll(karmaRoll)
                .defenseValue(defenseValue)
                .success(success)
                .extraSuccesses(extraSuccesses)
                .effectApplied(effectApplied)
                .damageTaken(damageTaken)
                .description(desc)
                .build();
    }

    private int getAttributeValue(GameCharacter c, AttributeType attr) {
        return switch (attr) {
            case DEXTERITY  -> c.getDexterity();
            case STRENGTH   -> c.getStrength();
            case TOUGHNESS  -> c.getToughness();
            case PERCEPTION -> c.getPerception();
            case WILLPOWER  -> c.getWillpower();
            case CHARISMA   -> c.getCharisma();
        };
    }

    private String buildFreeActionDescription(String actor, String target, String talentName,
            int roll, int defense, boolean success, int extraSuccesses, boolean effectApplied) {
        StringBuilder sb = new StringBuilder();
        sb.append(actor).append(" setzt ").append(talentName).append(" ein");
        if (target != null) sb.append(" gegen ").append(target);
        sb.append(": Wurf ").append(roll);
        if (defense > 0) sb.append(" vs ").append(defense);
        sb.append(" → ").append(success ? "Erfolg" : "Fehlschlag");
        if (success && extraSuccesses > 0) sb.append(" (").append(extraSuccesses).append(" Übererfolge)");
        if (effectApplied) sb.append(". Effekt angewandt.");
        return sb.toString();
    }

    // --- Riposte ---

    public RiposteResult performRiposte(Long sessionId, RiposteRequest req) {
        CombatSession session = findById(sessionId);
        CombatantState defender = findCombatant(session, req.getDefenderCombatantId());

        int attackTotal = defender.getPendingRiposteAttackTotal();
        if (attackTotal < 0) throw new IllegalStateException("Kein ausstehender Angriff für Riposte.");

        Long attackerId = defender.getPendingRiposteAttackerId();
        CombatantState attacker = attackerId != null ? findCombatant(session, attackerId) : null;
        String attackerName = attacker != null ? attacker.getCharacter().getName() : "Unbekannt";
        String defenderName = defender.getCharacter().getName();

        // Ausstehenden Angriff immer zurücksetzen
        int riposteDamage = defender.getPendingRiposteDamage();
        defender.setPendingRiposteAttackTotal(-1);
        defender.setPendingRiposteAttackerId(null);
        defender.setPendingRiposteDamage(0);
        // Falls auch Ausweichen pending war: Dodge-Reaktion verwerfen, da Riposte gewählt wurde
        defender.setPendingDodgeDamage(0);
        defender.setPendingDodgeAttackTotal(0);
        defender.setPendingDamageStep(0);
        defender.setPendingArmorValue(0);
        defender.setPendingDamageRollJson(null);

        if (!req.isRiposteAttempted()) {
            // Kein Riposte — Angriff annehmen, Schaden direkt anwenden
            applyDamageToDefender(session, defender, riposteDamage);
            dischargePendingDamageAmulets(session, defender); // Schaden angewendet → Amulette entladen
            String desc = defenderName + " nimmt den Angriff an (kein Riposte). " + riposteDamage + " Schaden erhalten.";
            addLog(session, defenderName, null, ActionType.RIPOSTE, desc, false);
            sessionRepo.save(session);
            RiposteResult declined = RiposteResult.builder()
                    .defenderName(defenderName).attackerName(attackerName)
                    .attackTotal(attackTotal).success(false).riposteAttempted(false)
                    .incomingNetDamage(riposteDamage).description(desc).build();
            broadcastWithModal(session, "RIPOSTE", declined);
            return declined;
        }

        CharacterTalent ct = defender.getCharacter().getTalents().stream()
                .filter(t -> TalentNames.RIPOSTE.equals(t.getTalentDefinition().getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Riposte-Talent nicht gefunden."));

        // 2 Überanstrengung
        defender.setCurrentDamage(defender.getCurrentDamage() + 2);

        // Würfelschritt: GES-Stufe + Rang + Bonus − Wunden
        int dexStep = Math.max(1, diceService.attributeToStep(defender.getCharacter().getDexterity()) - defender.getWounds());
        int riposteStep = Math.max(1, dexStep + ct.getRank() + req.getBonusSteps());

        RollResult karmaRoll = null;
        if (req.isSpendKarma() && defender.getCurrentKarma() > 0) {
            karmaRoll = diceService.roll(4);
            defender.setCurrentKarma(Math.max(0, defender.getCurrentKarma() - 1));
        }

        RollResult riposteRoll = diceService.roll(riposteStep);
        int riposteTotal = riposteRoll.getTotal() + (karmaRoll != null ? karmaRoll.getTotal() : 0);

        boolean success = riposteTotal >= attackTotal;
        int extraSuccesses = success ? (riposteTotal - attackTotal) / 5 : 0;

        RiposteResult.RiposteResultBuilder result = RiposteResult.builder()
                .defenderName(defenderName).attackerName(attackerName)
                .riposteStep(riposteStep).riposteRoll(riposteRoll).karmaRoll(karmaRoll)
                .attackTotal(attackTotal).success(success).extraSuccesses(extraSuccesses).damageCost(2)
                .riposteAttempted(true);

        StringBuilder desc = new StringBuilder(defenderName)
                .append(" setzt Riposte ein: ").append(riposteTotal)
                .append(" vs Angriff ").append(attackTotal).append(" → ")
                .append(success ? "Pariert!" : "Fehlgeschlagen — Angriff trifft!");

        if (!success) {
            // Parieren fehlgeschlagen: gespeicherten Nettschaden anwenden
            applyDamageToDefender(session, defender, riposteDamage);
            dischargePendingDamageAmulets(session, defender); // Treffer landet → Amulette entladen
            result.incomingNetDamage(riposteDamage);
            desc.append(" ").append(riposteDamage).append(" Schaden erhalten.");
        } else {
            // Erfolgreich pariert → Schaden-Amulette bleiben geladen
            defender.setPendingDamageAmuletIds(null);
            defender.setPendingDamageAmuletAttackerId(null);
        }

        if (success && extraSuccesses > 0 && attacker != null) {
            // Gegenangriff: Riposte-Ergebnis als Angriffswurf vs KV des Angreifers
            int attackerPD = modifiers.getEffectiveValue(attacker, StatType.PHYSICAL_DEFENSE, TriggerContext.ON_MELEE_DEFENSE);
            boolean counterHit = riposteTotal >= attackerPD;
            result.counterAttack(true).counterAttackTotal(riposteTotal).counterAttackHit(counterHit);

            desc.append(" ").append(extraSuccesses).append(" Übererfolg(e) → Gegenangriff! ")
                .append(riposteTotal).append(" vs KV ").append(attackerPD)
                .append(counterHit ? " → TREFFER!" : " → Verfehlt.");

            if (counterHit) {
                int counterExtraSucc = Math.max(0, (riposteTotal - attackerPD) / 5 - 1); // -1 laut Regel
                int strStep = diceService.attributeToStep(defender.getCharacter().getStrength());
                int damageStep = Math.max(1, strStep + defender.getCharacter().getWeaponDamageStep() + counterExtraSucc * 2);
                RollResult counterDmgRoll = diceService.roll(damageStep);
                int armor = modifiers.getEffectiveValue(attacker, StatType.PHYSICAL_ARMOR, TriggerContext.ON_DAMAGE_RECEIVED);
                int net = Math.max(0, counterDmgRoll.getTotal() - armor);

                int wt = modifiers.getEffectiveValue(attacker, StatType.WOUND_THRESHOLD, TriggerContext.ALWAYS);
                int prevWounds = attacker.getWounds();
                KnockdownResult kdr = applyDamageToDefender(session, attacker, net);
                boolean woundDealt = attacker.getWounds() > prevWounds;

                result.counterDamageStep(damageStep).counterDamageRoll(counterDmgRoll)
                      .counterArmorValue(armor).counterNetDamage(net)
                      .counterWoundDealt(woundDealt).counterKnockdown(kdr);

                desc.append(" Schaden: ").append(counterDmgRoll.getTotal())
                    .append(" − ").append(armor).append(" = ").append(net);
                if (woundDealt) desc.append(" (WUNDE!)");
            }
        }

        String description = desc.toString();
        result.description(description);
        addLog(session, defenderName, attackerName, ActionType.RIPOSTE, description, success);
        sessionRepo.save(session);
        RiposteResult built = result.build();
        broadcastWithModal(session, "RIPOSTE", built);
        return built;
    }

    // --- Manövrieren ---

    public ManoeuverResult performManoeuver(Long sessionId, ManoeuverRequest req) {
        CombatSession session = findById(sessionId);
        CombatantState actor  = findCombatant(session, req.getActorCombatantId());
        CombatantState target = findCombatant(session, req.getTargetCombatantId());

        if (actor.isDefeated()) throw new IllegalStateException(actor.getCharacter().getName() + " ist besiegt.");
        if (session.getPhase() != CombatPhase.ACTION) throw new IllegalStateException("Nur in der Aktionsphase möglich.");

        CharacterTalent ct = actor.getCharacter().getTalents().stream()
                .filter(t -> TalentNames.MANOEUVER.equals(t.getTalentDefinition().getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Talent 'Manövrieren' nicht gefunden."));

        // 1 Überanstrengung — Manövrieren ist eine freie Aktion (verbraucht die Handlung nicht)
        actor.setCurrentDamage(actor.getCurrentDamage() + 1);

        int dexStep = Math.max(1, diceService.attributeToStep(actor.getCharacter().getDexterity()) - actor.getWounds());
        int rollStep = Math.max(1, dexStep + ct.getRank() + req.getBonusSteps());

        RollResult karmaRoll = null;
        if (req.isSpendKarma() && actor.getCurrentKarma() > 0) {
            karmaRoll = diceService.roll(4);
            actor.setCurrentKarma(Math.max(0, actor.getCurrentKarma() - 1));
        }

        RollResult roll = diceService.roll(rollStep);
        int total = roll.getTotal() + (karmaRoll != null ? karmaRoll.getTotal() : 0);

        int pd = modifiers.getEffectiveValue(target, StatType.PHYSICAL_DEFENSE, TriggerContext.ALWAYS);
        boolean success = total >= pd;
        int extraSucc = success ? (total - pd) / 5 : 0;
        int successes = success ? 1 + extraSucc : 0;
        int bonus = successes * 2;

        String actorName = actor.getCharacter().getName();
        String targetName = target.getCharacter().getName();

        if (success && bonus > 0) {
            // Verteidigungsbonus als ActiveEffect
            actor.getActiveEffects().add(ActiveEffect.builder()
                    .combatantState(actor)
                    .name(TalentNames.EFFECT_MANOEUVER)
                    .description("+" + bonus + " KV (Manövrieren vs " + targetName + ")")
                    .sourceType(SourceType.TALENT)
                    .remainingRounds(1)
                    .negative(false)
                    .modifiers(List.of(ModifierEntry.builder()
                            .targetStat(StatType.PHYSICAL_DEFENSE)
                            .operation(ModifierOperation.ADD)
                            .value(bonus)
                            .triggerContext(TriggerContext.ON_MELEE_DEFENSE)
                            .build()))
                    .build());
            // Angriffsbonus als pendingAttackBonus
            actor.setPendingAttackBonus(actor.getPendingAttackBonus() + bonus);
        }

        String desc = success
                ? actorName + " manövriert gegen " + targetName + ": " + total + " vs KV " + pd
                  + " → " + successes + " Erfolg(e). +" + bonus + " KV und +" + bonus + " auf nächsten Angriff."
                : actorName + " manövriert gegen " + targetName + ": " + total + " vs KV " + pd + " → Fehlschlag.";

        addLog(session, actorName, targetName, ActionType.MANOEUVER, desc, success);
        sessionRepo.save(session);

        ManoeuverResult result = ManoeuverResult.builder()
                .actorName(actorName).targetName(targetName)
                .rollStep(rollStep).roll(roll).karmaRoll(karmaRoll)
                .defenseValue(pd).success(success).successes(successes)
                .defenseBonus(bonus).attackBonus(bonus).damageTaken(1)
                .description(desc).build();
        broadcastWithModal(session, "MANOEUVER", result);
        return result;
    }

    // --- Tigersprung ---

    public TigersprungResult performTigersprung(Long sessionId, Long actorCombatantId) {
        CombatSession session = findById(sessionId);
        CombatantState actor  = findCombatant(session, actorCombatantId);

        if (actor.isDefeated()) throw new IllegalStateException(actor.getCharacter().getName() + " ist besiegt.");
        if (session.getPhase() != CombatPhase.DECLARATION)
            throw new IllegalStateException("Tigersprung kann nur in der Ansagephase aktiviert werden.");
        if (actor.isTigersprungUsedThisRound()) throw new IllegalStateException("Tigersprung wurde bereits in dieser Runde eingesetzt.");

        CharacterTalent ct = actor.getCharacter().getTalents().stream()
                .filter(t -> TalentNames.TIGERSPRUNG.equals(t.getTalentDefinition().getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Talent 'Tigersprung' nicht gefunden."));

        int rank = ct.getRank();

        // 1 Überanstrengung, kein Würfelwurf
        actor.setCurrentDamage(actor.getCurrentDamage() + 1);
        actor.setTigersprungUsedThisRound(true);

        // Step-Bonus als ActiveEffect — wirkt auf die kommende Initiative-Probe
        actor.getActiveEffects().add(ActiveEffect.builder()
                .combatantState(actor)
                .name("Tigersprung")
                .description("+" + rank + " Stufen auf Initiative-Probe")
                .sourceType(SourceType.TALENT)
                .remainingRounds(1)
                .negative(false)
                .modifiers(List.of(ModifierEntry.builder()
                        .targetStat(StatType.INITIATIVE_STEP)
                        .operation(ModifierOperation.ADD)
                        .value(rank)
                        .triggerContext(TriggerContext.ON_INITIATIVE)
                        .build()))
                .build());

        String actorName = actor.getCharacter().getName();
        String desc = actorName + " aktiviert Tigersprung (Rang " + rank + "): +" + rank + " Stufen auf Initiative-Probe. Kostet 1 Überanstrengung.";

        addLog(session, actorName, null, ActionType.TIGERSPRUNG, desc, true);
        sessionRepo.save(session);

        TigersprungResult result = TigersprungResult.builder()
                .actorName(actorName).rank(rank)
                .initiativeBonus(rank).newInitiative(actor.getInitiative())
                .damageTaken(1).description(desc).build();
        broadcastWithModal(session, "TIGERSPRUNG", result);
        return result;
    }

    // --- Blattschuss ---

    /**
     * Setzt einen weiteren Karmawürfel auf den ausstehenden Blattschuss-Angriff. Wenn der neue
     * Total ≥ Verteidigung des Ziels: Treffer wird abgewickelt (Schadenswurf, Riposte/Dodge-Checks).
     * Sonst: pending bleibt bestehen, falls noch Karma übrig — sonst finaler Fehlschlag.
     */
    public CombatActionResult performBlattschussAddKarma(Long sessionId, Long actorCombatantId) {
        CombatSession session = findById(sessionId);
        CombatantState attacker = findCombatant(session, actorCombatantId);

        if (attacker.isDefeated()) throw new IllegalStateException(attacker.getCharacter().getName() + " ist besiegt.");
        if (attacker.getPendingBlattschussDefenderId() == null || attacker.getPendingBlattschussDefenderId() < 0) {
            throw new IllegalStateException("Kein ausstehender Blattschuss-Angriff.");
        }
        if (attacker.getPendingBlattschussKarmaUsed() >= attacker.getPendingBlattschussRank()) {
            throw new IllegalStateException("Blattschuss-Karma-Maximum erreicht.");
        }
        if (attacker.getCurrentKarma() <= 0) {
            throw new IllegalStateException(attacker.getCharacter().getName() + " hat kein Karma mehr.");
        }

        CombatantState defender = findCombatant(session, attacker.getPendingBlattschussDefenderId());

        // Karmawürfel werfen, vom Karma-Pool abziehen
        RollResult karmaRoll = diceService.roll(4);
        attacker.setCurrentKarma(attacker.getCurrentKarma() - 1);
        int newTotal = attacker.getPendingBlattschussTotal() + karmaRoll.getTotal();
        int karmaUsed = attacker.getPendingBlattschussKarmaUsed() + 1;
        int rank = attacker.getPendingBlattschussRank();
        int defenseValue = attacker.getPendingBlattschussDefense();
        attacker.setPendingBlattschussTotal(newTotal);
        attacker.setPendingBlattschussKarmaUsed(karmaUsed);

        boolean hit = newTotal >= defenseValue;
        boolean canAddMore = !hit && karmaUsed < rank && attacker.getCurrentKarma() > 0;

        CombatActionResult.CombatActionResultBuilder result = CombatActionResult.builder()
                .actorName(attacker.getCharacter().getName())
                .targetName(defender.getCharacter().getName())
                .actionType(ActionType.RANGED_ATTACK)
                .attackStep(0) // initial step ist nicht mehr verfügbar — wir zeigen nur den Karma-Würfel
                .karmaRoll(karmaRoll)
                .defenseValue(defenseValue)
                .hit(hit)
                .blattschussActive(true)
                .blattschussRank(rank)
                .blattschussKarmaUsed(karmaUsed)
                .blattschussCanAddKarma(canAddMore);

        if (hit) {
            // Treffer: Schadenswurf + Reaktionen wie regulärer Angriff
            int extraSuccesses = (newTotal - defenseValue) / 5;
            int damageStep = modifiers.getEffectiveValue(attacker, StatType.DAMAGE_STEP, TriggerContext.ON_DAMAGE_DEALT);
            Long weaponId = attacker.getPendingBlattschussWeaponId();
            boolean weaponIsClaw = false;
            if (weaponId != null && weaponId >= 0) {
                com.earthdawn.model.Equipment weapon = attacker.getCharacter().getEquipment().stream()
                        .filter(e -> e.getId().equals(weaponId))
                        .findFirst().orElse(null);
                if (weapon != null) {
                    damageStep += weapon.getDamageBonus();
                    weaponIsClaw = weapon.isClawWeapon();
                }
            }
            damageStep += extraSuccesses * 2;
            RollResult damageRoll = diceService.roll(damageStep);
            int armor = modifiers.getEffectiveValue(defender, StatType.PHYSICAL_ARMOR, TriggerContext.ON_DAMAGE_RECEIVED);
            int netDamage = Math.max(0, damageRoll.getTotal() - armor);

            // Pending zurücksetzen
            clearBlattschussPending(attacker);

            // Reaktionen: Ausweichen ist erlaubt (Fernkampf), Riposte nicht (nur Nahkampf)
            boolean defenderHasDodge = defender.getCharacter().getTalents().stream()
                    .anyMatch(t -> TalentNames.AUSWEICHEN.equals(t.getTalentDefinition().getName()));
            if (defenderHasDodge) {
                defender.setPendingDodgeDamage(netDamage);
                defender.setPendingDodgeAttackTotal(newTotal);
                defender.setPendingDamageStep(damageStep);
                defender.setPendingArmorValue(armor);
                try { defender.setPendingDamageRollJson(objectMapper.writeValueAsString(damageRoll)); } catch (JsonProcessingException e) { log.error("Fehler beim Serialisieren des Schadenswurfs", e); }
                result.hitPendingDodge(true).dodgeDefenderId(defender.getId()).pendingDodgeDamage(netDamage)
                      .extraSuccesses(extraSuccesses).damageStep(damageStep).damageRoll(damageRoll)
                      .armorValue(armor).netDamage(netDamage);
            } else {
                int wt = modifiers.getEffectiveValue(defender, StatType.WOUND_THRESHOLD, TriggerContext.ALWAYS);
                int prevWounds = defender.getWounds();
                KnockdownResult kdr = applyDamageToDefender(session, defender, netDamage);
                int newWounds = defender.getWounds() - prevWounds;
                result.extraSuccesses(extraSuccesses).damageStep(damageStep).damageRoll(damageRoll)
                      .armorValue(armor).netDamage(netDamage)
                      .woundDealt(newWounds > 0).newWounds(newWounds)
                      .totalWounds(defender.getWounds()).woundThreshold(wt)
                      .targetDefeated(defender.isDefeated()).knockdownResult(kdr);
            }
        } else if (!canAddMore) {
            // Kein weiteres Karma möglich → finaler Fehlschlag
            clearBlattschussPending(attacker);
        }

        CombatActionResult actionResult = result.build();
        String desc = attacker.getCharacter().getName() + " setzt Blattschuss-Karma ein ("
                + karmaUsed + "/" + rank + "): +" + karmaRoll.getTotal()
                + " → Total " + newTotal + " vs " + defenseValue
                + (hit ? " — TREFFER!" : (canAddMore ? " — Fehlschlag, weiter möglich." : " — finaler Fehlschlag."));
        actionResult.setDescription(desc);
        addLog(session, attacker.getCharacter().getName(), defender.getCharacter().getName(),
                ActionType.BLATTSCHUSS_KARMA, desc, hit);
        sessionRepo.save(session);
        broadcast(session);
        return actionResult;
    }

    private void clearBlattschussPending(CombatantState c) {
        c.setPendingBlattschussDefenderId(-1L);
        c.setPendingBlattschussTotal(0);
        c.setPendingBlattschussKarmaUsed(0);
        c.setPendingBlattschussRank(0);
        c.setPendingBlattschussWeaponId(-1L);
        c.setPendingBlattschussDefense(0);
    }

    // --- Lufttanz ---

    /**
     * Aktiviert Lufttanz: freie Aktion in der Ansagephase. +Rang Stufen auf die Initiative-Probe
     * (entspricht "Rang+DEX statt DEX-Stufe"). Kostet 2 Überanstrengung. 1× pro Runde. Ermöglicht
     * einen Bonus-Nahkampfangriff, wenn der Initiative-Vorsprung gegen das Ziel ≥ 10 ist.
     */
    public LufttanzActivationResult performLufttanz(Long sessionId, Long actorCombatantId) {
        CombatSession session = findById(sessionId);
        CombatantState actor  = findCombatant(session, actorCombatantId);

        if (actor.isDefeated()) throw new IllegalStateException(actor.getCharacter().getName() + " ist besiegt.");
        if (session.getPhase() != CombatPhase.DECLARATION)
            throw new IllegalStateException("Lufttanz kann nur in der Ansagephase aktiviert werden.");
        if (actor.isLufttanzActivatedThisRound())
            throw new IllegalStateException("Lufttanz wurde bereits in dieser Runde aktiviert.");

        CharacterTalent ct = actor.getCharacter().getTalents().stream()
                .filter(t -> TalentNames.LUFTTANZ.equals(t.getTalentDefinition().getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Talent 'Lufttanz' nicht gefunden."));

        int rank = ct.getRank();

        // 2 Überanstrengung
        actor.setCurrentDamage(actor.getCurrentDamage() + 2);
        actor.setLufttanzActivatedThisRound(true);

        actor.getActiveEffects().add(ActiveEffect.builder()
                .combatantState(actor)
                .name("Lufttanz")
                .description("+" + rank + " Stufen auf Initiative-Probe (Rang+DEX statt DEX-Stufe)")
                .sourceType(SourceType.TALENT)
                .remainingRounds(1)
                .negative(false)
                .modifiers(List.of(ModifierEntry.builder()
                        .targetStat(StatType.INITIATIVE_STEP)
                        .operation(ModifierOperation.ADD)
                        .value(rank)
                        .triggerContext(TriggerContext.ON_INITIATIVE)
                        .build()))
                .build());

        String actorName = actor.getCharacter().getName();
        String desc = actorName + " aktiviert Lufttanz (Rang " + rank + "): +" + rank
                + " Stufen auf Initiative; Bonusangriff bei Initiative-Vorsprung ≥ 10. Kostet 2 Überanstrengung.";

        addLog(session, actorName, null, ActionType.LUFTTANZ, desc, true);
        sessionRepo.save(session);

        LufttanzActivationResult result = LufttanzActivationResult.builder()
                .actorName(actorName)
                .rank(rank)
                .initiativeBonus(rank)
                .damageTaken(2)
                .description(desc)
                .build();
        broadcastWithModal(session, "LUFTTANZ", result);
        return result;
    }

    /**
     * Lufttanz-Bonusangriff: zusätzlicher Nahkampfangriff mit derselben Waffe wie der
     * auslösende Angriff. Verbraucht keine Hauptaktion. Kostet keine zusätzliche Überanstrengung
     * (die 2 Strain wurden bei Aktivierung bezahlt).
     */
    public CombatActionResult performLufttanzAttack(LufttanzAttackRequest req) {
        CombatSession session = findById(req.getSessionId());
        CombatantState attacker = findCombatant(session, req.getAttackerCombatantId());

        if (attacker.isDefeated()) throw new IllegalStateException(attacker.getCharacter().getName() + " ist besiegt.");
        if (session.getPhase() != CombatPhase.ACTION) throw new IllegalStateException("Nur in der Aktionsphase möglich.");
        if (attacker.getPendingLufttanzTargetId() == null || attacker.getPendingLufttanzTargetId() < 0) {
            throw new IllegalStateException("Kein ausstehender Lufttanz-Bonusangriff.");
        }

        Long defenderId = attacker.getPendingLufttanzTargetId();
        Long weaponId = attacker.getPendingLufttanzWeaponId();

        // Pending sofort zurücksetzen + bonus als verbraucht markieren, damit der Bonus-Angriff
        // selbst keinen weiteren Bonus auslöst
        attacker.setPendingLufttanzTargetId(-1L);
        attacker.setPendingLufttanzWeaponId(-1L);
        attacker.setLufttanzBonusUsedThisRound(true);

        // Regulären Nahkampfangriff mit gleicher Waffe ausführen, ohne hasActedThisRound zu setzen
        boolean originalActed = attacker.isHasActedThisRound();
        attacker.setHasActedThisRound(false);
        try {
            AttackActionRequest attackReq = new AttackActionRequest();
            attackReq.setSessionId(req.getSessionId());
            attackReq.setAttackerCombatantId(req.getAttackerCombatantId());
            attackReq.setDefenderCombatantId(defenderId);
            attackReq.setActionType(ActionType.MELEE_ATTACK);
            attackReq.setWeaponId(weaponId);
            attackReq.setBonusSteps(req.getBonusSteps());
            attackReq.setSpendKarma(req.isSpendKarma());
            attackReq.setSpendKarmaForDamage(req.isSpendKarmaForDamage());
            CombatActionResult result = performAttack(attackReq);
            // Lufttanz-Bonusangriff verbraucht keine Hauptaktion — Status zurücksetzen
            attacker.setHasActedThisRound(originalActed);
            sessionRepo.save(session);
            broadcast(session);
            return result;
        } catch (RuntimeException ex) {
            attacker.setHasActedThisRound(originalActed);
            throw ex;
        }
    }

    // --- Zweitwaffe ---

    public CombatActionResult performZweitwaffe(Long sessionId, ZweitwaffeRequest req) {
        CombatSession session = findById(sessionId);
        CombatantState attacker = findCombatant(session, req.getActorCombatantId());
        CombatantState defender = findCombatant(session, req.getDefenderCombatantId());

        if (attacker.isDefeated()) throw new IllegalStateException(attacker.getCharacter().getName() + " ist besiegt.");
        if (session.getPhase() != CombatPhase.ACTION) throw new IllegalStateException("Nur in der Aktionsphase möglich.");
        if (attacker.isZweitWaffeUsedThisRound()) throw new IllegalStateException("Zweitwaffe wurde bereits in dieser Runde eingesetzt.");

        CharacterTalent ct = attacker.getCharacter().getTalents().stream()
                .filter(t -> TalentNames.ZWEITWAFFE.equals(t.getTalentDefinition().getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Talent 'Zweitwaffe' nicht gefunden."));

        // 1 Überanstrengung — Zweitwaffe ist eine freie Aktion (1× pro Runde)
        attacker.setCurrentDamage(attacker.getCurrentDamage() + 1);
        attacker.setZweitWaffeUsedThisRound(true);

        int dexStep = Math.max(1, diceService.attributeToStep(attacker.getCharacter().getDexterity()) - attacker.getWounds());
        int attackStep = Math.max(1, dexStep + ct.getRank() + req.getBonusSteps());
        // Ausstehende Angriffsboni (z.B. Manövrieren) auch hier verbrauchen
        if (attacker.getPendingAttackBonus() != 0) {
            attackStep = Math.max(1, attackStep + attacker.getPendingAttackBonus());
            attacker.setPendingAttackBonus(0);
        }

        RollResult karmaRoll = null;
        if (req.isSpendKarma() && attacker.getCurrentKarma() > 0) {
            karmaRoll = diceService.roll(4);
            attacker.setCurrentKarma(Math.max(0, attacker.getCurrentKarma() - 1));
        }

        RollResult attackRoll = diceService.roll(attackStep);
        int attackTotal = attackRoll.getTotal() + (karmaRoll != null ? karmaRoll.getTotal() : 0);

        int pd = modifiers.getEffectiveValue(defender, StatType.PHYSICAL_DEFENSE, TriggerContext.ON_MELEE_DEFENSE);
        if (defender.getPendingDefenseBonus() != 0) {
            pd += defender.getPendingDefenseBonus();
            defender.setPendingDefenseBonus(0);
        }
        boolean hit = attackTotal >= pd;

        CombatActionResult.CombatActionResultBuilder result = CombatActionResult.builder()
                .actorName(attacker.getCharacter().getName())
                .targetName(defender.getCharacter().getName())
                .actionType(ActionType.ZWEITE_WAFFE)
                .attackStep(attackStep).attackRoll(attackRoll).karmaRoll(karmaRoll)
                .defenseValue(pd).hit(hit);

        if (hit) {
            int extraSucc = (attackTotal - pd) / 5;
            int strStep = diceService.attributeToStep(attacker.getCharacter().getStrength());
            int weaponBonus = req.getWeaponId() != null
                    ? attacker.getCharacter().getEquipment().stream()
                        .filter(e -> e.getId().equals(req.getWeaponId()))
                        .findFirst().map(com.earthdawn.model.Equipment::getDamageBonus).orElse(0)
                    : 0;
            int damageStep = Math.max(1, strStep + weaponBonus + extraSucc * 2);
            RollResult damageRoll = diceService.roll(damageStep);
            int armor = modifiers.getEffectiveValue(defender, StatType.PHYSICAL_ARMOR, TriggerContext.ON_DAMAGE_RECEIVED);
            int net = Math.max(0, damageRoll.getTotal() - armor);

            // Reaktionsmöglichkeiten des Verteidigers (Riposte und/oder Ausweichen) — wie bei normalem Nahkampfangriff
            boolean defenderHasRiposte = defender.getCharacter().getTalents().stream()
                    .anyMatch(t -> TalentNames.RIPOSTE.equals(t.getTalentDefinition().getName()))
                    && defender.getPendingRiposteAttackTotal() < 0;
            boolean defenderHasDodge = defender.getCharacter().getTalents().stream()
                    .anyMatch(t -> TalentNames.AUSWEICHEN.equals(t.getTalentDefinition().getName()));

            if (defenderHasRiposte || defenderHasDodge) {
                if (defenderHasRiposte) {
                    defender.setPendingRiposteAttackTotal(attackTotal);
                    defender.setPendingRiposteAttackerId(attacker.getId());
                    defender.setPendingRiposteDamage(net);
                    result.hitPendingRiposte(true).riposteDefenderId(defender.getId());
                }
                if (defenderHasDodge) {
                    defender.setPendingDodgeDamage(net);
                    defender.setPendingDodgeAttackTotal(attackTotal);
                    defender.setPendingDamageStep(damageStep);
                    defender.setPendingArmorValue(armor);
                    try { defender.setPendingDamageRollJson(objectMapper.writeValueAsString(damageRoll)); } catch (JsonProcessingException e) { log.error("Fehler beim Serialisieren des Schadenswurfs", e); }
                    result.hitPendingDodge(true).dodgeDefenderId(defender.getId()).pendingDodgeDamage(net);
                }
                result.extraSuccesses(extraSucc).damageStep(damageStep).damageRoll(damageRoll)
                      .armorValue(armor).netDamage(net);
                CombatActionResult actionResult = result.build();
                actionResult.setDescription(buildDescription(actionResult));
                String tag = defenderHasRiposte && defenderHasDodge
                        ? " (Riposte oder Ausweichen möglich!)"
                        : defenderHasRiposte ? " (Riposte möglich!)" : " (Ausweichen möglich!)";
                addLog(session, attacker.getCharacter().getName(), defender.getCharacter().getName(),
                        ActionType.ZWEITE_WAFFE, actionResult.getDescription() + tag, hit);
                sessionRepo.save(session);
                broadcast(session);
                return actionResult;
            }

            int prevWounds = defender.getWounds();
            KnockdownResult kdr = applyDamageToDefender(session, defender, net);
            int newWounds = defender.getWounds() - prevWounds;

            result.extraSuccesses(extraSucc).damageStep(damageStep).damageRoll(damageRoll)
                  .armorValue(armor).netDamage(net)
                  .woundDealt(newWounds > 0).newWounds(newWounds)
                  .totalWounds(defender.getWounds())
                  .targetDefeated(defender.isDefeated()).knockdownResult(kdr);
        }

        CombatActionResult actionResult = result.build();
        actionResult.setDescription(buildDescription(actionResult));
        addLog(session, attacker.getCharacter().getName(), defender.getCharacter().getName(),
                ActionType.ZWEITE_WAFFE, actionResult.getDescription(), hit);
        sessionRepo.save(session);
        broadcast(session);
        return actionResult;
    }

    // --- Nachtreten (zusätzlicher waffenloser Angriff) ---

    public CombatActionResult performNachtreten(Long sessionId, NachtretenRequest req) {
        CombatSession session = findById(sessionId);
        CombatantState attacker = findCombatant(session, req.getActorCombatantId());
        CombatantState defender = findCombatant(session, req.getDefenderCombatantId());

        if (attacker.isDefeated()) throw new IllegalStateException(attacker.getCharacter().getName() + " ist besiegt.");
        if (session.getPhase() != CombatPhase.ACTION) throw new IllegalStateException("Nur in der Aktionsphase möglich.");
        if (attacker.isNachtretenUsedThisRound()) throw new IllegalStateException("Nachtreten wurde bereits in dieser Runde eingesetzt.");

        // Initiative: Anwender muss höhere Initiative als das Ziel haben
        if (attacker.getInitiative() <= defender.getInitiative()) {
            throw new IllegalStateException("Nachtreten ist nur gegen Ziele mit niedrigerer Initiative möglich.");
        }

        CharacterTalent ct = attacker.getCharacter().getTalents().stream()
                .filter(t -> TalentNames.NACHTRETEN.equals(t.getTalentDefinition().getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Talent 'Nachtreten' nicht gefunden."));

        // 1 Überanstrengung — Nachtreten ist eine Einfache Aktion (1× pro Runde, zusätzlich zur Hauptaktion)
        attacker.setCurrentDamage(attacker.getCurrentDamage() + 1);
        attacker.setNachtretenUsedThisRound(true);

        int dexStep = Math.max(1, diceService.attributeToStep(attacker.getCharacter().getDexterity()) - attacker.getWounds());
        int attackStep = Math.max(1, dexStep + ct.getRank() + req.getBonusSteps());
        // Ausstehende Angriffsboni (z.B. Manövrieren) auch hier verbrauchen
        if (attacker.getPendingAttackBonus() != 0) {
            attackStep = Math.max(1, attackStep + attacker.getPendingAttackBonus());
            attacker.setPendingAttackBonus(0);
        }

        RollResult karmaRoll = null;
        if (req.isSpendKarma() && attacker.getCurrentKarma() > 0) {
            karmaRoll = diceService.roll(4);
            attacker.setCurrentKarma(Math.max(0, attacker.getCurrentKarma() - 1));
        }

        RollResult attackRoll = diceService.roll(attackStep);
        int attackTotal = attackRoll.getTotal() + (karmaRoll != null ? karmaRoll.getTotal() : 0);

        int pd = modifiers.getEffectiveValue(defender, StatType.PHYSICAL_DEFENSE, TriggerContext.ON_MELEE_DEFENSE);
        if (defender.getPendingDefenseBonus() != 0) {
            pd += defender.getPendingDefenseBonus();
            defender.setPendingDefenseBonus(0);
        }
        boolean hit = attackTotal >= pd;

        CombatActionResult.CombatActionResultBuilder result = CombatActionResult.builder()
                .actorName(attacker.getCharacter().getName())
                .targetName(defender.getCharacter().getName())
                .actionType(ActionType.NACHTRETEN)
                .attackStep(attackStep).attackRoll(attackRoll).karmaRoll(karmaRoll)
                .defenseValue(pd).hit(hit);

        if (hit) {
            int extraSucc = (attackTotal - pd) / 5;
            // Waffenloser Schaden: reine Stärkestufe + Übererfolge
            int strStep = diceService.attributeToStep(attacker.getCharacter().getStrength());
            int damageStep = Math.max(1, strStep + extraSucc * 2);
            RollResult damageRoll = diceService.roll(damageStep);
            int armor = modifiers.getEffectiveValue(defender, StatType.PHYSICAL_ARMOR, TriggerContext.ON_DAMAGE_RECEIVED);
            int net = Math.max(0, damageRoll.getTotal() - armor);

            // Reaktionsmöglichkeiten des Verteidigers (Riposte und/oder Ausweichen) — wie bei normalem Nahkampfangriff
            boolean defenderHasRiposte = defender.getCharacter().getTalents().stream()
                    .anyMatch(t -> TalentNames.RIPOSTE.equals(t.getTalentDefinition().getName()))
                    && defender.getPendingRiposteAttackTotal() < 0;
            boolean defenderHasDodge = defender.getCharacter().getTalents().stream()
                    .anyMatch(t -> TalentNames.AUSWEICHEN.equals(t.getTalentDefinition().getName()));

            if (defenderHasRiposte || defenderHasDodge) {
                if (defenderHasRiposte) {
                    defender.setPendingRiposteAttackTotal(attackTotal);
                    defender.setPendingRiposteAttackerId(attacker.getId());
                    defender.setPendingRiposteDamage(net);
                    result.hitPendingRiposte(true).riposteDefenderId(defender.getId());
                }
                if (defenderHasDodge) {
                    defender.setPendingDodgeDamage(net);
                    defender.setPendingDodgeAttackTotal(attackTotal);
                    defender.setPendingDamageStep(damageStep);
                    defender.setPendingArmorValue(armor);
                    try { defender.setPendingDamageRollJson(objectMapper.writeValueAsString(damageRoll)); } catch (JsonProcessingException e) { log.error("Fehler beim Serialisieren des Schadenswurfs", e); }
                    result.hitPendingDodge(true).dodgeDefenderId(defender.getId()).pendingDodgeDamage(net);
                }
                result.extraSuccesses(extraSucc).damageStep(damageStep).damageRoll(damageRoll)
                      .armorValue(armor).netDamage(net);
                CombatActionResult actionResult = result.build();
                actionResult.setDescription(buildDescription(actionResult));
                String tag = defenderHasRiposte && defenderHasDodge
                        ? " (Riposte oder Ausweichen möglich!)"
                        : defenderHasRiposte ? " (Riposte möglich!)" : " (Ausweichen möglich!)";
                addLog(session, attacker.getCharacter().getName(), defender.getCharacter().getName(),
                        ActionType.NACHTRETEN, actionResult.getDescription() + tag, hit);
                sessionRepo.save(session);
                broadcast(session);
                return actionResult;
            }

            int prevWounds = defender.getWounds();
            KnockdownResult kdr = applyDamageToDefender(session, defender, net);
            int newWounds = defender.getWounds() - prevWounds;

            result.extraSuccesses(extraSucc).damageStep(damageStep).damageRoll(damageRoll)
                  .armorValue(armor).netDamage(net)
                  .woundDealt(newWounds > 0).newWounds(newWounds)
                  .totalWounds(defender.getWounds())
                  .targetDefeated(defender.isDefeated()).knockdownResult(kdr);
        }

        CombatActionResult actionResult = result.build();
        actionResult.setDescription(buildDescription(actionResult));
        addLog(session, attacker.getCharacter().getName(), defender.getCharacter().getName(),
                ActionType.NACHTRETEN, actionResult.getDescription(), hit);
        sessionRepo.save(session);
        broadcast(session);
        return actionResult;
    }

    CombatantState findCombatant(CombatSession session, Long combatantId) {
        return session.getCombatants().stream()
                .filter(c -> c.getId().equals(combatantId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Kombattant nicht gefunden: " + combatantId));
    }

    void addLog(CombatSession session, String actorName, String targetName,
                ActionType actionType, String description, boolean success) {
        CombatLog entry = CombatLog.builder()
                .combatSession(session)
                .round(session.getRound())
                .loggedAt(LocalDateTime.now())
                .actionType(actionType)
                .actorName(actorName)
                .targetName(targetName)
                .description(description)
                .success(success)
                .build();
        session.getLog().add(entry);
    }

    void broadcast(CombatSession session) {
        try {
            enrichTransientFields(session);
            websocket.convertAndSend("/topic/combat/" + session.getId(), session);
        } catch (Exception e) {
            log.warn("WebSocket broadcast fehlgeschlagen: {}", e.getMessage());
        }
    }

    /**
     * Broadcast mit gleichzeitigem Setzen des synchronisierten Modal-Status (für alle Zuschauer).
     * Wird statt des einfachen broadcast() aufgerufen, wenn die Aktion ein Result-Modal öffnen soll.
     */
    private void broadcastWithModal(CombatSession session, String modalType, Object payload) {
        if (session != null && session.getId() != null && modalType != null) {
            openLiveModal(session.getId(), modalType, payload);
        }
        broadcast(session);
    }

    private String buildDescription(CombatActionResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.getActorName()).append(" → ").append(r.getTargetName()).append(": ");
        sb.append("Angriff ").append(r.getAttackRoll().getTotal())
          .append(" (Step ").append(r.getAttackStep()).append(")")
          .append(" vs VK ").append(r.getDefenseValue()).append(". ");
        if (r.isHit()) {
            if (r.isHitPendingRiposte()) {
                sb.append("TREFFER! (Riposte ausstehend)");
            } else if (r.isHitPendingDodge()) {
                sb.append("TREFFER! Schaden: ").append(r.getNetDamage()).append(" (Ausweichen ausstehend).");
            } else {
                sb.append("TREFFER! Schaden: ").append(r.getDamageRoll().getTotal())
                  .append(" − ").append(r.getArmorValue()).append(" = ").append(r.getNetDamage()).append(". ");
                if (r.isWoundDealt()) sb.append("WUNDE! ");
                if (r.isTargetDefeated()) sb.append(r.getTargetName()).append(" ist besiegt! ");
            }
        } else {
            sb.append("Verfehlt.");
        }
        return sb.toString();
    }
}
