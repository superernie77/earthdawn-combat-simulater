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

    // --- Session Management ---

    public List<CombatSession> findAll() {
        return sessionRepo.findByOrderByCreatedAtDesc();
    }

    public CombatSession findById(Long id) {
        return sessionRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Kampfsession nicht gefunden: " + id));
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

        CombatantState combatant = CombatantState.builder()
                .combatSession(session)
                .character(character)
                .currentDamage(character.getCurrentDamage())
                .wounds(character.getWounds())
                .currentKarma(character.getKarmaCurrent())
                .isNpc(isNpc)
                .build();

        session.getCombatants().add(combatant);
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

    public CombatSession rollInitiative(Long sessionId) {
        CombatSession session = findById(sessionId);
        String summary = rerollInitiative(session);
        session.setStatus(CombatStatus.ACTIVE);
        session.setRound(1);
        addLog(session, null, null, ActionType.INITIATIVE, "Initiative gewürfelt. Runde 1 beginnt! " + summary, true);
        CombatSession saved = sessionRepo.save(session);
        broadcast(saved);
        return saved;
    }

    private String rerollInitiative(CombatSession session) {
        for (CombatantState combatant : session.getCombatants()) {
            if (combatant.isDefeated()) continue;
            int initStep = modifiers.getEffectiveValue(combatant, StatType.INITIATIVE_STEP, TriggerContext.ON_INITIATIVE);
            RollResult roll = diceService.roll(initStep);
            combatant.setInitiative(roll.getTotal());
        }
        session.getCombatants().sort(Comparator.comparingInt(CombatantState::getInitiative).reversed());
        for (int i = 0; i < session.getCombatants().size(); i++) {
            session.getCombatants().get(i).setInitiativeOrder(i);
        }
        // Zusammenfassung für den Log
        StringBuilder sb = new StringBuilder("Reihenfolge: ");
        session.getCombatants().stream()
                .filter(c -> !c.isDefeated())
                .forEach(c -> sb.append(c.getCharacter().getName()).append(" (").append(c.getInitiative()).append("), "));
        if (sb.toString().endsWith(", ")) sb.setLength(sb.length() - 2);
        return sb.toString();
    }

    // --- Attack ---

    public CombatActionResult performAttack(AttackActionRequest req) {
        CombatSession session = findById(req.getSessionId());
        CombatantState attacker = findCombatant(session, req.getAttackerCombatantId());
        CombatantState defender = findCombatant(session, req.getDefenderCombatantId());

        if (attacker.isDefeated()) {
            throw new IllegalStateException(attacker.getCharacter().getName() + " ist bewusstlos/besiegt und kann nicht handeln!");
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
        int attackStep = modifiers.getEffectiveValue(attacker, StatType.ATTACK_STEP, atkCtx);

        if (req.getTalentId() != null) {
            attackStep += attacker.getCharacter().getTalents().stream()
                    .filter(t -> t.getTalentDefinition().getId().equals(req.getTalentId()))
                    .findFirst().map(CharacterTalent::getRank).orElse(0);
        }
        attackStep += req.getBonusSteps();

        // Aggressiver Angriff: +3 Stufen, 1 Schaden, -3 Verteidigung
        boolean wasAggressive = req.isAggressiveAttack();
        if (wasAggressive) {
            attackStep += 3;
            attacker.setCurrentDamage(attacker.getCurrentDamage() + 1);
            ActiveEffect penalty = ActiveEffect.builder()
                    .combatantState(attacker)
                    .name("Aggressiver Angriff")
                    .description("-3 auf alle Verteidigungswerte")
                    .sourceType(SourceType.CONDITION)
                    .remainingRounds(1)
                    .negative(true)
                    .modifiers(List.of(
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
            attacker.getActiveEffects().add(penalty);
        }

        // Defensive Haltung: -3 Stufen, +3 Verteidigung
        if (req.isDefensiveStance()) {
            attackStep = Math.max(1, attackStep - 3);
            attacker.setPendingDefenseBonus(attacker.getPendingDefenseBonus() + 3);
            ActiveEffect bonus = ActiveEffect.builder()
                    .combatantState(attacker)
                    .name("Defensive Haltung")
                    .description("+3 auf alle Verteidigungswerte")
                    .sourceType(SourceType.CONDITION)
                    .remainingRounds(1)
                    .negative(false)
                    .modifiers(List.of(
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
            attacker.getActiveEffects().add(bonus);
        }

        // 2. Karma
        RollResult karmaRoll = null;
        if (req.isSpendKarma() && attacker.getCurrentKarma() > 0) {
            karmaRoll = diceService.roll(4); // Karma ist immer W6 (Step 4 = W6)
            attacker.setCurrentKarma(attacker.getCurrentKarma() - 1);
        }

        // 3. Angriffswurf
        RollResult attackRoll = diceService.roll(attackStep);
        int attackTotal = attackRoll.getTotal() + (karmaRoll != null ? karmaRoll.getTotal() : 0);

        // 4. Verteidigung
        TriggerContext defCtx = req.getActionType() == ActionType.RANGED_ATTACK
                ? TriggerContext.ON_RANGED_DEFENSE : TriggerContext.ON_MELEE_DEFENSE;
        int pd = modifiers.getEffectiveValue(defender, StatType.PHYSICAL_DEFENSE, defCtx);

        // Ausstehenden Verteidigungsbonus (z.B. Defensive Haltung) verbrauchen
        if (defender.getPendingDefenseBonus() != 0) {
            pd += defender.getPendingDefenseBonus();
            defender.setPendingDefenseBonus(0);
        }

        boolean hit = attackTotal > pd;

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
                .attackBonusNotes(attackBonusNotes);

        if (hit) {
            // 5. Übererfolge: je 5 über VK → +2 Schadensstufen
            int extraSuccesses = (attackTotal - pd) / 5;

            // 6. Schadensstufe = Stärke-Stufe + Waffe + Übererfolge
            int damageStep = modifiers.getEffectiveValue(attacker, StatType.DAMAGE_STEP, TriggerContext.ON_DAMAGE_DEALT);
            if (req.getWeaponId() != null) {
                int weaponBonus = attacker.getCharacter().getEquipment().stream()
                        .filter(e -> e.getId().equals(req.getWeaponId()))
                        .findFirst()
                        .map(com.earthdawn.model.Equipment::getDamageBonus)
                        .orElse(0);
                damageStep += weaponBonus;
            }
            damageStep += extraSuccesses * 2;

            RollResult damageRoll = diceService.roll(damageStep);

            // 6. Rüstung
            int armor = modifiers.getEffectiveValue(defender, StatType.PHYSICAL_ARMOR, TriggerContext.ON_DAMAGE_RECEIVED);
            int netDamage = Math.max(0, damageRoll.getTotal() - armor);

            // 7. Hat Ziel Ausweichen-Talent? → Schaden zurückhalten
            boolean defenderHasDodge = defender.getCharacter().getTalents().stream()
                    .anyMatch(t -> "Ausweichen".equals(t.getTalentDefinition().getName()));

            if (defenderHasDodge) {
                defender.setPendingDodgeDamage(netDamage);
                defender.setPendingDodgeAttackTotal(attackTotal);
                defender.setPendingDamageStep(damageStep);
                defender.setPendingArmorValue(armor);
                try { defender.setPendingDamageRollJson(objectMapper.writeValueAsString(damageRoll)); } catch (JsonProcessingException ignored) {}
                result.hitPendingDodge(true)
                      .dodgeDefenderId(defender.getId())
                      .pendingDodgeDamage(netDamage);
            } else {
                int wt2 = modifiers.getEffectiveValue(defender, StatType.WOUND_THRESHOLD, TriggerContext.ALWAYS);
                int prevWounds = defender.getWounds();
                KnockdownResult kdr = applyDamageToDefender(session, defender, netDamage);
                int newWounds = defender.getWounds() - prevWounds;
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
                  .armorValue(armor)
                  .netDamage(netDamage);
        }

        // Angreifer hat seine Aktion für diese Runde verbraucht
        attacker.setHasActedThisRound(true);

        CombatActionResult actionResult = result.build();
        actionResult.setDescription(buildDescription(actionResult));

        addLog(session, attacker.getCharacter().getName(), defender.getCharacter().getName(),
                req.getActionType(), actionResult.getDescription(), hit);

        sessionRepo.save(session);
        broadcast(session);
        return actionResult;
    }

    // --- Runden-Management ---

    public CombatSession nextRound(Long sessionId) {
        CombatSession session = findById(sessionId);
        session.setRound(session.getRound() + 1);

        // Effekt-Dauer reduzieren, ausstehende Boni zurücksetzen
        for (CombatantState combatant : session.getCombatants()) {
            combatant.setHasActedThisRound(false);
            combatant.setPendingAttackBonus(0);
            combatant.setPendingDefenseBonus(0);
            combatant.getActiveEffects().removeIf(effect -> {
                if (effect.getRemainingRounds() == -1) return false;
                effect.setRemainingRounds(effect.getRemainingRounds() - 1);
                return effect.getRemainingRounds() <= 0;
            });
        }

        // Initiative neu würfeln und Reihenfolge neu sortieren
        String initiativeSummary = rerollInitiative(session);

        addLog(session, null, null, ActionType.ROUND_CHANGE,
                "Runde " + session.getRound() + " beginnt! " + initiativeSummary, true);
        CombatSession saved = sessionRepo.save(session);
        broadcast(saved);
        return saved;
    }

    public void deleteSession(Long sessionId) {
        sessionRepo.deleteById(sessionId);
    }

    public CombatSession endCombat(Long sessionId) {
        CombatSession session = findById(sessionId);
        session.setStatus(CombatStatus.FINISHED);
        addLog(session, null, null, ActionType.ROUND_CHANGE, "Kampf beendet!", true);
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
            case "AGGRESSIVE_ATTACK" -> {
                // +3 auf nächsten Angriffswurf
                combatant.setPendingAttackBonus(combatant.getPendingAttackBonus() + 3);

                // 1 Schadenspunkt sofort
                combatant.setCurrentDamage(combatant.getCurrentDamage() + 1);

                // -3 auf alle Verteidigungswerte für diese Runde
                ActiveEffect penalty = ActiveEffect.builder()
                        .combatantState(combatant)
                        .name("Aggressiver Angriff")
                        .description("-3 auf alle Verteidigungswerte, +3 auf nächsten Angriff")
                        .sourceType(SourceType.CONDITION)
                        .remainingRounds(1)
                        .negative(true)
                        .modifiers(List.of(
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
                combatant.getActiveEffects().add(penalty);

                addLog(session, name, null, ActionType.COMBAT_OPTION,
                        name + " wählt Aggressiven Angriff: +3 Angriff (nächster Wurf), -3 Verteidigung, 1 Schaden.", true);
            }
            case "USE_ACTION" -> {
                if (combatant.isHasActedThisRound()) {
                    throw new IllegalStateException(name + " hat diese Runde bereits gehandelt!");
                }
                combatant.setHasActedThisRound(true);
                addLog(session, name, null, ActionType.COMBAT_OPTION,
                        name + " nutzt eine Aktion (Zauber / Faden weben / Sonstiges).", true);
            }
            case "DEFENSIVE_STANCE" -> {
                // -3 auf nächsten Angriffswurf
                combatant.setPendingAttackBonus(combatant.getPendingAttackBonus() - 3);

                // +3 auf alle Verteidigungswerte bis zum nächsten Treffer oder Rundenende
                combatant.setPendingDefenseBonus(combatant.getPendingDefenseBonus() + 3);

                // +3 auf alle Verteidigungswerte als Effekt (für Anzeige)
                ActiveEffect bonus = ActiveEffect.builder()
                        .combatantState(combatant)
                        .name("Defensive Haltung")
                        .description("+3 auf alle Verteidigungswerte, -3 auf nächsten Angriff")
                        .sourceType(SourceType.CONDITION)
                        .remainingRounds(1)
                        .negative(false)
                        .modifiers(List.of(
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
                combatant.getActiveEffects().add(bonus);

                addLog(session, name, null, ActionType.COMBAT_OPTION,
                        name + " wählt Defensive Haltung: -3 Angriff (nächster Wurf), +3 Verteidigung.", true);
            }
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
        } catch (JsonProcessingException ignored) {}

        // Ausstehende Werte zurücksetzen
        defender.setPendingDodgeDamage(0);
        defender.setPendingDodgeAttackTotal(0);
        defender.setPendingDamageStep(0);
        defender.setPendingArmorValue(0);
        defender.setPendingDamageRollJson(null);

        if (!req.isDodgeAttempted()) {
            // Kein Ausweichen — Schaden direkt anwenden
            int wtSkip = modifiers.getEffectiveValue(defender, StatType.WOUND_THRESHOLD, TriggerContext.ALWAYS);
            int prevWounds = defender.getWounds();
            KnockdownResult kdr = applyDamageToDefender(session, defender, netDamage);
            int newWounds = defender.getWounds() - prevWounds;
            addLog(session, defName, null, ActionType.DODGE, defName + " nimmt " + netDamage + " Schaden an (kein Ausweichen).", false);
            sessionRepo.save(session);
            broadcast(session);
            return DodgeResult.builder()
                    .defenderName(defName)
                    .rollStep(0).roll(null).karmaRoll(null)
                    .attackTotal(attackTotal).success(false)
                    .damageCost(0).damageStep(pendingDamageStep).damageRoll(pendingDamageRoll).armorValue(pendingArmorValue).netDamageApplied(netDamage)
                    .newWounds(newWounds).totalWounds(defender.getWounds()).woundThreshold(wtSkip)
                    .targetDefeated(defender.isDefeated())
                    .knockdownResult(kdr)
                    .description(defName + " nimmt " + netDamage + " Schaden an.")
                    .build();
        }

        // Ausweichen-Probe
        CharacterTalent dodgeTalent = defender.getCharacter().getTalents().stream()
                .filter(t -> "Ausweichen".equals(t.getTalentDefinition().getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Ausweichen-Talent nicht gefunden"));

        // Kosten: 1 Schaden
        int damageCost = 1;
        defender.setCurrentDamage(defender.getCurrentDamage() + damageCost);

        // Karma
        RollResult karmaRoll = null;
        if (req.isSpendKarma() && defender.getCurrentKarma() > 0) {
            karmaRoll = diceService.roll(4); // Step 4 = W6
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
        }

        String desc = success
                ? defName + " weicht aus! (Wurf " + total + " vs Angriff " + attackTotal + ")"
                : defName + " kann nicht ausweichen. (Wurf " + total + " vs Angriff " + attackTotal + ") — " + netDamage + " Schaden.";
        addLog(session, defName, null, ActionType.DODGE, desc, success);

        sessionRepo.save(session);
        broadcast(session);

        return DodgeResult.builder()
                .defenderName(defName)
                .rollStep(rollStep).roll(roll).karmaRoll(karmaRoll)
                .attackTotal(attackTotal).success(success)
                .damageCost(damageCost).damageStep(pendingDamageStep).damageRoll(pendingDamageRoll).armorValue(pendingArmorValue).netDamageApplied(netDamageApplied)
                .newWounds(newWounds).totalWounds(defender.getWounds()).woundThreshold(wtDodge)
                .targetDefeated(defender.isDefeated())
                .knockdownResult(knockdownResult)
                .description(desc)
                .build();
    }

    private KnockdownResult applyDamageToDefender(CombatSession session, CombatantState defender, int netDamage) {
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
        int strStep = Math.max(1, diceService.attributeToStep(defender.getCharacter().getStrength()) - defender.getWounds());
        RollResult roll = diceService.roll(strStep);
        boolean knocked = roll.getTotal() < targetNumber;
        String desc;
        if (knocked) {
            defender.setKnockedDown(true);
            applyNiedergeschlagenEffect(defender);
            desc = name + " ist niedergeschlagen! (STR " + roll.getTotal() + " < " + targetNumber + ")";
        } else {
            desc = name + " bleibt stehen. (STR " + roll.getTotal() + " vs " + targetNumber + ")";
        }
        addLog(session, name, null, ActionType.VALUE_CHANGED, desc, !knocked);
        return KnockdownResult.builder()
                .targetName(name)
                .rollStep(strStep)
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
                .description("−3 auf alle Proben, −3 KVK/ZVK")
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
            karmaRoll = diceService.roll(4);
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

        // Karma
        RollResult karmaRoll = null;
        if (req.isSpendKarma() && actor.getCurrentKarma() > 0) {
            karmaRoll = diceService.roll(4); // Step 4 = W6
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

        boolean success = defenseValue == 0 || total > defenseValue;
        int extraSuccesses = success && defenseValue > 0 ? (total - defenseValue) / 5 : (success ? 1 : 0);
        boolean effectApplied = false;

        if (success && talent.getFreeActionModifyStat() != null && extraSuccesses > 0) {
            double modValue = extraSuccesses * talent.getFreeActionValuePerSuccess();
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

    private CombatantState findCombatant(CombatSession session, Long combatantId) {
        return session.getCombatants().stream()
                .filter(c -> c.getId().equals(combatantId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Kombattant nicht gefunden: " + combatantId));
    }

    private void addLog(CombatSession session, String actorName, String targetName,
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

    private void broadcast(CombatSession session) {
        try {
            websocket.convertAndSend("/topic/combat/" + session.getId(), session);
        } catch (Exception e) {
            log.warn("WebSocket broadcast fehlgeschlagen: {}", e.getMessage());
        }
    }

    private String buildDescription(CombatActionResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.getActorName()).append(" → ").append(r.getTargetName()).append(": ");
        sb.append("Angriff ").append(r.getAttackRoll().getTotal())
          .append(" (Step ").append(r.getAttackStep()).append(")")
          .append(" vs VK ").append(r.getDefenseValue()).append(". ");
        if (r.isHit()) {
            sb.append("TREFFER! Schaden: ").append(r.getDamageRoll().getTotal())
              .append(" − ").append(r.getArmorValue()).append(" = ").append(r.getNetDamage()).append(". ");
            if (r.isWoundDealt()) sb.append("WUNDE! ");
            if (r.isTargetDefeated()) sb.append(r.getTargetName()).append(" ist besiegt! ");
        } else {
            sb.append("Verfehlt.");
        }
        return sb.toString();
    }
}
