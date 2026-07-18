package com.earthdawn.service;

import com.earthdawn.dto.*;
import com.earthdawn.model.*;
import com.earthdawn.model.enums.*;
import com.earthdawn.repository.CombatSessionRepository;
import com.earthdawn.repository.SpellDefinitionRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class SpellService {

    private final CombatSessionRepository sessionRepo;
    private final SpellDefinitionRepository spellRepo;
    private final StepRollService diceService;
    private final ModifierAggregator modifiers;
    private final CombatService combatService;

    /** Fadenweben-Talent pro Disziplin */
    private static final Map<String, String> WEAVING_TALENT_MAP = Map.of(
            "Elementarist",      "Elementarismus",
            "Illusionist",       "Illusionismus",
            "Magier",            "Magie",
            "Geisterbeschwörer", "Geisterbeschwörung"
    );

    // --- Faden weben ---

    public ThreadweaveResult weaveThread(ThreadweaveRequest req) {
        var session = combatService.findById(req.getSessionId());
        var caster = combatService.findCombatant(session, req.getCasterCombatantId());

        if (caster.isDefeated()) throw new IllegalStateException("Besiegte Kombattanten können nicht handeln.");
        if (caster.isHasActedThisRound()) throw new IllegalStateException("Diese Runde wurde bereits gehandelt.");

        SpellDefinition spell = spellRepo.findById(req.getSpellId())
                .orElseThrow(() -> new EntityNotFoundException("Zauber nicht gefunden: " + req.getSpellId()));

        // Auch Sofortzauber (threads == 0) können Zusatzfäden anbieten (z.B. Blitz, Katastrophe).
        if (spell.getThreads() == 0 && spell.getThreadOptions().isEmpty()) {
            throw new IllegalStateException("Dieser Zauber benötigt keine Fäden.");
        }

        // Bereits einen anderen Zauber in Vorbereitung? Vorbereitung abbrechen und neuen starten.
        if (caster.getPreparingSpellId() != null && !caster.getPreparingSpellId().equals(req.getSpellId())) {
            resetPreparation(caster);
        }

        // Vorbereitung starten? Erweiterte Matrize: ein Faden ist bereits gewoben → −1 Aufwand.
        if (caster.getPreparingSpellId() == null) {
            int discount = isInErweiterteMatrize(caster, spell) ? 1 : 0;
            caster.setPreparingSpellId(spell.getId());
            caster.setThreadsWoven(0);
            caster.setThreadsRequired(Math.max(0, spell.getThreads() - discount));
            caster.setExtraThreadChoices(null);
        }

        // Fadenweben-Talent finden
        String weavingTalentName = getWeavingTalentName(caster);
        CharacterTalent weavingTalent = caster.getCharacter().getTalents().stream()
                .filter(t -> t.getTalentDefinition().getName().equals(weavingTalentName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Fadenweben-Talent '" + weavingTalentName + "' nicht gefunden."));

        // Sind alle Pflichtfäden gewoben, ist dies ein Zusatzfaden — dafür muss eine Option
        // gewählt werden, und es sind höchstens `Fadenweben-Rang` viele erlaubt.
        boolean isExtra = caster.getThreadsWoven() >= caster.getThreadsRequired();
        java.util.List<Integer> choices = parseChoices(caster.getExtraThreadChoices());
        int extraMax = weavingTalent.getRank();
        SpellThreadOption chosenOption = null;
        if (isExtra) {
            if (spell.getThreadOptions().isEmpty()) {
                throw new IllegalStateException("'" + spell.getName() + "' bietet keine Zusatzfäden — alle Fäden sind bereits gewoben.");
            }
            if (choices.size() >= extraMax) {
                throw new IllegalStateException("Maximal " + extraMax + " Zusatzfäden (Fadenweben-Rang " + extraMax + ").");
            }
            Integer idx = req.getExtraThreadOptionIndex();
            if (idx == null || idx < 0 || idx >= spell.getThreadOptions().size()) {
                throw new IllegalStateException("Für einen Zusatzfaden muss eine gültige Option gewählt werden.");
            }
            chosenOption = spell.getThreadOptions().get(idx);
        }

        // Würfelstufe: PER-Stufe + Rang - Wunden
        int perStep = Math.max(1, diceService.attributeToStep(caster.getCharacter().getPerception()) - caster.getWounds());
        int rollStep = Math.max(1, perStep + weavingTalent.getRank());

        // Karma
        RollResult karmaRoll = null;
        if (req.isSpendKarma() && caster.getCurrentKarma() > 0) {
            karmaRoll = diceService.roll(4); // W6 = Stufe 4
            caster.setCurrentKarma(Math.max(0, caster.getCurrentKarma() - 1));
        }

        RollResult roll = diceService.roll(rollStep);
        int total = roll.getTotal() + (karmaRoll != null ? karmaRoll.getTotal() : 0);
        int targetNumber = spell.getWeavingDifficulty();
        boolean success = total >= targetNumber;

        if (success) {
            if (isExtra) {
                choices.add(req.getExtraThreadOptionIndex());
                caster.setExtraThreadChoices(formatChoices(choices));
            } else {
                caster.setThreadsWoven(caster.getThreadsWoven() + 1);
            }
        }

        boolean readyToCast = caster.getThreadsWoven() >= caster.getThreadsRequired();
        caster.setHasActedThisRound(true);

        String casterName = caster.getCharacter().getName();
        String desc;
        if (isExtra) {
            desc = success
                    ? casterName + " webt einen Zusatzfaden für " + spell.getName() + ": " + chosenOption.getLabel()
                      + " (" + choices.size() + "/" + extraMax + "). Wurf " + total + " vs " + targetNumber + "."
                    : casterName + " scheitert beim Zusatzfaden für " + spell.getName() + " (" + chosenOption.getLabel() + "). Wurf " + total + " vs " + targetNumber + ".";
        } else {
            desc = success
                    ? casterName + " webt einen Faden für " + spell.getName() + " (" + caster.getThreadsWoven() + "/" + caster.getThreadsRequired() + "). Wurf " + total + " vs " + targetNumber + "."
                    : casterName + " scheitert beim Fadenweben für " + spell.getName() + ". Wurf " + total + " vs " + targetNumber + ".";
            if (readyToCast && success) desc += " Zauber ist bereit!";
        }

        combatService.addLog(session, casterName, null, ActionType.THREADWEAVE, desc, success);
        sessionRepo.save(session);
        combatService.broadcast(session);

        return ThreadweaveResult.builder()
                .casterName(casterName)
                .spellName(spell.getName())
                .rollStep(rollStep)
                .roll(roll)
                .karmaRoll(karmaRoll)
                .targetNumber(targetNumber)
                .success(success)
                .threadsWoven(caster.getThreadsWoven())
                .threadsRequired(caster.getThreadsRequired())
                .readyToCast(readyToCast)
                .extraThread(isExtra)
                .extraThreadLabel(success && chosenOption != null ? chosenOption.getLabel() : null)
                .extraThreadCount(choices.size())
                .extraThreadMax(extraMax)
                .description(desc)
                .build();
    }

    // --- Zusatzfäden: Hilfsfunktionen ---

    /** Setzt die komplette Zaubervorbereitung inkl. gewählter Zusatzfäden zurück. */
    private void resetPreparation(CombatantState caster) {
        caster.setPreparingSpellId(null);
        caster.setThreadsWoven(0);
        caster.setThreadsRequired(0);
        caster.setExtraThreadChoices(null);
    }

    /** "1,1,3" → [1, 1, 3]. Null/leer → leere Liste. */
    static java.util.List<Integer> parseChoices(String csv) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        if (csv == null || csv.isBlank()) return out;
        for (String part : csv.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) continue;
            try {
                out.add(Integer.parseInt(t));
            } catch (NumberFormatException ignored) {
                // defekte Altdaten überspringen
            }
        }
        return out;
    }

    /** [1, 1, 3] → "1,1,3". Leere Liste → null. */
    static String formatChoices(java.util.List<Integer> choices) {
        if (choices == null || choices.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < choices.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(choices.get(i));
        }
        return sb.toString();
    }

    // --- Zauber wirken ---

    public SpellCastResult castSpell(SpellCastRequest req) {
        var session = combatService.findById(req.getSessionId());
        var caster = combatService.findCombatant(session, req.getCasterCombatantId());

        if (caster.isDefeated()) throw new IllegalStateException("Besiegte Kombattanten können nicht handeln.");
        if (caster.isHasActedThisRound()) throw new IllegalStateException("Diese Runde wurde bereits gehandelt.");

        SpellDefinition spell = spellRepo.findById(req.getSpellId())
                .orElseThrow(() -> new EntityNotFoundException("Zauber nicht gefunden: " + req.getSpellId()));

        // Fäden-Check. Erweiterte Matrize: ein Faden ist vorgewoben → effektiver Bedarf −1.
        // Ist der effektive Bedarf 0 (z.B. 1-Faden-Zauber in erweiterter Matrize), kann direkt
        // ohne Vorbereitung gewirkt werden.
        int castDiscount = isInErweiterteMatrize(caster, spell) ? 1 : 0;
        int effectiveRequired = Math.max(0, spell.getThreads() - castDiscount);
        if (spell.getThreads() > 0 && effectiveRequired > 0) {
            if (caster.getPreparingSpellId() == null || !caster.getPreparingSpellId().equals(spell.getId())) {
                throw new IllegalStateException("Dieser Zauber wurde nicht vorbereitet.");
            }
            if (caster.getThreadsWoven() < caster.getThreadsRequired()) {
                throw new IllegalStateException("Noch nicht genug Fäden gewirkt (" + caster.getThreadsWoven() + "/" + caster.getThreadsRequired() + ").");
            }
        }

        // Zusatzfäden auflösen — nur wenn genau dieser Zauber vorbereitet wurde.
        // Verrechnet wird ausschließlich EFFECT_STEP; alles andere ist Anzeige für den Spielleiter.
        java.util.List<String> extraThreadLabels = new java.util.ArrayList<>();
        int extraThreadEffectStep = 0;
        int extraThreadBuffValue = 0;
        int[] freeThreadEffectStep = {0};
        if (caster.getPreparingSpellId() != null && caster.getPreparingSpellId().equals(spell.getId())) {
            for (int idx : parseChoices(caster.getExtraThreadChoices())) {
                if (idx < 0 || idx >= spell.getThreadOptions().size()) continue;
                SpellThreadOption opt = spell.getThreadOptions().get(idx);
                extraThreadLabels.add(opt.getLabel());
                if (opt.getType() == SpellThreadOptionType.EFFECT_STEP) {
                    extraThreadEffectStep += opt.getValue();
                } else if (opt.getType() == SpellThreadOptionType.BUFF_VALUE) {
                    extraThreadBuffValue += opt.getValue();
                }
            }
        }

        // Erweiterte Matrize + Sofortzauber: der vorgewobene Faden der Matrize hat keinen
        // Pflichtfaden zu decken und wird zum freien Zusatzfaden — immer "Wirkungsstufe +2".
        // Er kostet weder Wurf noch Aktion und zählt NICHT gegen den Fadenweben-Rang.
        // Zauber ohne EFFECT_STEP-Option (z.B. Katastrophe, ein BUFF ohne Wirkungsstufe)
        // erhalten nichts — dort gäbe es keine Wirkungsstufe zu erhöhen.
        if (spell.getThreads() == 0 && isInErweiterteMatrize(caster, spell)) {
            spell.getThreadOptions().stream()
                    .filter(o -> o.getType() == SpellThreadOptionType.EFFECT_STEP)
                    .findFirst()
                    .ifPresent(free -> {
                        extraThreadLabels.add(free.getLabel() + " — frei (Erweiterte Matrize)");
                        freeThreadEffectStep[0] = free.getValue();
                    });
            extraThreadEffectStep += freeThreadEffectStep[0];
        }

        // Ziel bestimmen
        CombatantState target = null;
        if (req.getTargetCombatantId() != null) {
            target = combatService.findCombatant(session, req.getTargetCombatantId());
        }

        // Spruchzauberei-Talent finden
        CharacterTalent castTalent = caster.getCharacter().getTalents().stream()
                .filter(t -> "Spruchzauberei".equals(t.getTalentDefinition().getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Talent 'Spruchzauberei' nicht gefunden."));

        // Würfelstufe: PER-Stufe + Spruchzauberei-Rang - Wunden
        int perStep = Math.max(1, diceService.attributeToStep(caster.getCharacter().getPerception()) - caster.getWounds());
        int castStep = Math.max(1, perStep + castTalent.getRank());

        // Karma
        RollResult karmaRoll = null;
        if (req.isSpendKarma() && caster.getCurrentKarma() > 0) {
            karmaRoll = diceService.roll(4); // W6 = Stufe 4
            caster.setCurrentKarma(Math.max(0, caster.getCurrentKarma() - 1));
        }

        // Verzweiflungsschlag-Amulette (Zauber) auf den Zauberwurf — werden hier entladen
        List<String> amuletNotes = new java.util.ArrayList<>();
        int amuletCastBonus = combatService.applyAmulets(caster, req.getAmuletCastIds(), true, "Zauber", amuletNotes);
        castStep += amuletCastBonus;

        RollResult castRoll = diceService.roll(castStep);
        int total = castRoll.getTotal() + (karmaRoll != null ? karmaRoll.getTotal() : 0);

        // Verteidigung bestimmen
        int defenseValue;
        if (spell.getCastingDifficulty() > 0) {
            defenseValue = spell.getCastingDifficulty();
        } else if (target != null) {
            defenseValue = modifiers.getEffectiveValue(target, StatType.SPELL_DEFENSE, TriggerContext.ON_SPELL_DEFENSE);
        } else {
            defenseValue = 0; // Selbstzauber ohne feste Schwierigkeit = auto-Erfolg
        }

        boolean success = defenseValue == 0 || total >= defenseValue;
        int extraSuccesses = success && defenseValue > 0 ? (total - defenseValue) / 5 : 0;

        String casterName = caster.getCharacter().getName();
        String targetName = target != null ? target.getCharacter().getName() : casterName;

        SpellCastResult.SpellCastResultBuilder result = SpellCastResult.builder()
                .casterName(casterName)
                .targetName(targetName)
                .spellName(spell.getName())
                .effectType(spell.getEffectType())
                .castStep(castStep)
                .castRoll(castRoll)
                .karmaRoll(karmaRoll)
                .defenseValue(defenseValue)
                .success(success)
                .extraSuccesses(extraSuccesses)
                .extraThreadLabels(extraThreadLabels)
                .extraThreadEffectStep(extraThreadEffectStep);

        if (success) {
            switch (spell.getEffectType()) {
                case DAMAGE -> {
                    // Schadens-Amulette (Zauber) hier entladen und +6 je Amulett auf den Schadenswurf
                    int amuletDmg = combatService.applyAmulets(caster, req.getAmuletDamageIds(), true, "Schaden", amuletNotes);
                    applySpellDamage(session, caster, target, spell, extraSuccesses,
                            amuletDmg, extraThreadEffectStep, result);
                }
                case BUFF   -> applySpellBuff(session, caster, target, spell, extraSuccesses, extraThreadBuffValue, result);
                case DEBUFF -> applySpellDebuff(session, target, spell, extraSuccesses, extraThreadBuffValue, result);
                case HEAL   -> applySpellHeal(target != null ? target : caster, spell, extraThreadEffectStep, result);
            }
        }
        result.amuletNotes(amuletNotes);

        // Vorbereitung zurücksetzen
        resetPreparation(caster);
        caster.setHasActedThisRound(true);

        SpellCastResult castResult = result.build();
        castResult.setDescription(buildSpellDescription(castResult, spell));

        combatService.addLog(session, casterName, targetName,
                spell.getEffectType() == SpellEffectType.DAMAGE ? ActionType.SPELL_ATTACK : ActionType.SPELL_CAST,
                castResult.getDescription(), success);

        sessionRepo.save(session);
        combatService.broadcast(session);
        return castResult;
    }

    // --- Zaubervorbereitung abbrechen ---

    public void cancelSpellPreparation(Long sessionId, Long combatantId) {
        var session = combatService.findById(sessionId);
        var caster = combatService.findCombatant(session, combatantId);

        resetPreparation(caster);

        combatService.addLog(session, caster.getCharacter().getName(), null,
                ActionType.SPELL_CAST, caster.getCharacter().getName() + " bricht die Zaubervorbereitung ab.", false);
        sessionRepo.save(session);
        combatService.broadcast(session);
    }

    // --- Spell-Effekte ---

    private void applySpellDamage(CombatSession session, CombatantState caster, CombatantState target,
                                   SpellDefinition spell, int extraSuccesses, int amuletDamageBonus,
                                   int extraThreadEffectStep,
                                   SpellCastResult.SpellCastResultBuilder result) {
        if (target == null) throw new IllegalStateException("Schadenszauber benötigt ein Ziel.");

        int wilStep = Math.max(1, diceService.attributeToStep(caster.getCharacter().getWillpower()) - caster.getWounds());
        int damageBonus = "DAMAGE".equals(spell.getExtraSuccessEffect()) ? extraSuccesses * 2 : 0;
        int damageStep = spell.getEffectStep() + wilStep + damageBonus + amuletDamageBonus + extraThreadEffectStep;
        RollResult damageRoll = diceService.roll(damageStep);

        StatType armorStat = spell.isUseMysticArmor() ? StatType.MYSTIC_ARMOR : StatType.PHYSICAL_ARMOR;
        int armor = modifiers.getEffectiveValue(target, armorStat, TriggerContext.ON_DAMAGE_RECEIVED);
        int netDamage = Math.max(0, damageRoll.getTotal() - armor);

        int wt = modifiers.getEffectiveValue(target, StatType.WOUND_THRESHOLD, TriggerContext.ALWAYS);
        int prevWounds = target.getWounds();
        KnockdownResult kdr = combatService.applyDamageToDefender(session, target, netDamage);
        int newWounds = target.getWounds() - prevWounds;

        result.damageStep(damageStep)
              .damageStepBonus(damageBonus)
              .damageRoll(damageRoll)
              .armorValue(armor)
              .netDamage(netDamage)
              .woundDealt(newWounds > 0)
              .newWounds(newWounds)
              .totalWounds(target.getWounds())
              .woundThreshold(wt)
              .targetDefeated(target.isDefeated())
              .knockdownResult(kdr);
    }

    private void applySpellBuff(CombatSession session, CombatantState caster, CombatantState target,
                                 SpellDefinition spell, int extraSuccesses, int buffBonus,
                                 SpellCastResult.SpellCastResultBuilder result) {
        CombatantState effectTarget = target != null ? target : caster;
        int duration = effectiveDuration(spell, extraSuccesses);
        // Utility buffs have no modifyStat – just log the effect, no active modifier needed
        if (spell.getModifyStat() != null) {
            ActiveEffect effect = createSpellEffect(spell, effectTarget, false, buffBonus, duration);
            effectTarget.getActiveEffects().add(effect);
        }

        // Phantomkrieger: zusätzlicher Effekt — Angreifer gegen das Ziel erleiden −3 auf Angriffsstufe
        if ("Phantomkrieger".equals(spell.getName()) && target != null) {
            ActiveEffect attackPenalty = ActiveEffect.builder()
                    .combatantState(target)
                    .name("Phantomkrieger (Angreifer −3)")
                    .description("Angriffe gegen " + target.getCharacter().getName() + " erleiden −3 auf Angriffsstufe")
                    .sourceType(SourceType.SPELL)
                    .sourceId(spell.getId())
                    .remainingRounds(duration)
                    .negative(false)
                    .modifiers(List.of(
                            com.earthdawn.model.ModifierEntry.builder()
                                    .targetStat(com.earthdawn.model.enums.StatType.ATTACK_STEP)
                                    .operation(com.earthdawn.model.enums.ModifierOperation.ADD)
                                    .value(-3 - buffBonus)
                                    .triggerContext(com.earthdawn.model.enums.TriggerContext.ON_INCOMING_ATTACK)
                                    .build()
                    ))
                    .build();
            target.getActiveEffects().add(attackPenalty);
        }

        result.effectApplied(spell.getEffectDescription()
                      + (buffBonus > 0 ? " — um " + buffBonus + " verstärkt (Zusatzfäden)" : ""))
              .effectDuration(duration);
    }

    private void applySpellDebuff(CombatSession session, CombatantState target, SpellDefinition spell,
                                   int extraSuccesses, int buffBonus,
                                   SpellCastResult.SpellCastResultBuilder result) {
        if (target == null) throw new IllegalStateException("Debuff-Zauber benötigt ein Ziel.");
        int duration = effectiveDuration(spell, extraSuccesses);
        ActiveEffect effect = createSpellEffect(spell, target, true, buffBonus, duration);
        target.getActiveEffects().add(effect);

        result.effectApplied(spell.getEffectDescription()
                      + (buffBonus > 0 ? " — um " + buffBonus + " verstärkt (Zusatzfäden)" : ""))
              .effectDuration(duration);
    }

    /**
     * Effektive Dauer in Runden: extraSuccessEffect "DURATION" verlängert um 2 Runden je
     * Übererfolg. Permanente Effekte (duration = -1) bleiben permanent.
     */
    private int effectiveDuration(SpellDefinition spell, int extraSuccesses) {
        if (spell.getDuration() < 0) return spell.getDuration();
        int bonus = "DURATION".equals(spell.getExtraSuccessEffect()) ? extraSuccesses * 2 : 0;
        return spell.getDuration() + bonus;
    }

    private void applySpellHeal(CombatantState target, SpellDefinition spell, int extraThreadEffectStep,
                                 SpellCastResult.SpellCastResultBuilder result) {
        RollResult healRoll = diceService.roll(Math.max(1, spell.getEffectStep() + extraThreadEffectStep));
        int healAmount = healRoll.getTotal();
        target.setCurrentDamage(Math.max(0, target.getCurrentDamage() - healAmount));

        result.damageRoll(healRoll)
              .healedAmount(healAmount)
              .effectApplied("Heilt " + healAmount + " Schaden");
    }

    private ActiveEffect createSpellEffect(SpellDefinition spell, CombatantState target, boolean negative) {
        return createSpellEffect(spell, target, negative, 0, spell.getDuration());
    }

    /**
     * Variante mit Zusatzfaden-Verstärkung und (ggf. durch Übererfolge verlängerter) Dauer.
     * Die Verstärkung wirkt in Wirkrichtung: positive Modifikatoren steigen, negative sinken.
     */
    private ActiveEffect createSpellEffect(SpellDefinition spell, CombatantState target, boolean negative,
                                            int valueBonus, int durationRounds) {
        double value = spell.getModifyValue();
        if (valueBonus != 0) {
            value += spell.getModifyValue() >= 0 ? valueBonus : -valueBonus;
        }
        ModifierEntry modifier = ModifierEntry.builder()
                .targetStat(spell.getModifyStat())
                .operation(spell.getModifyOperation())
                .value(value)
                .triggerContext(spell.getModifyTrigger())
                .build();

        return ActiveEffect.builder()
                .combatantState(target)
                .name(spell.getName())
                .description(spell.getEffectDescription())
                .sourceType(SourceType.SPELL)
                .sourceId(spell.getId())
                .remainingRounds(durationRounds)
                .negative(negative)
                .modifiers(List.of(modifier))
                .build();
    }

    private String getWeavingTalentName(CombatantState caster) {
        DisciplineDefinition disc = caster.getCharacter().getDiscipline();
        if (disc != null) {
            String talent = WEAVING_TALENT_MAP.get(disc.getName());
            if (talent != null) return talent;
        }
        return "Fadenmagie"; // Fallback
    }

    /** True, wenn der Zauber in einer "Erweiterte Matrize" des Charakters liegt (→ 1 Faden vorgewoben). */
    private boolean isInErweiterteMatrize(CombatantState caster, SpellDefinition spell) {
        return caster.getCharacter().getTalents().stream()
                .filter(t -> TalentNames.ERWEITERTE_MATRIZE.equals(t.getTalentDefinition().getName()))
                .anyMatch(t -> t.getAssignedSpell() != null
                            && t.getAssignedSpell().getId().equals(spell.getId()));
    }

    private String buildSpellDescription(SpellCastResult r, SpellDefinition spell) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.getCasterName()).append(" wirkt ").append(r.getSpellName());
        if (!r.getTargetName().equals(r.getCasterName())) {
            sb.append(" auf ").append(r.getTargetName());
        }
        sb.append(": Wurf ").append(r.getCastRoll().getTotal());
        if (r.getDefenseValue() > 0) sb.append(" vs ZV ").append(r.getDefenseValue());
        sb.append(". ");

        if (!r.isSuccess()) {
            sb.append("Fehlschlag!");
            appendExtraThreads(sb, r);
            return sb.toString();
        }

        switch (spell.getEffectType()) {
            case DAMAGE -> {
                sb.append("TREFFER! Schaden: ").append(r.getDamageRoll().getTotal())
                  .append(" − ").append(r.getArmorValue()).append(" MR = ").append(r.getNetDamage()).append(". ");
                if (r.isWoundDealt()) sb.append("WUNDE! ");
                if (r.isTargetDefeated()) sb.append(r.getTargetName()).append(" ist besiegt! ");
            }
            case BUFF, DEBUFF -> {
                sb.append("Effekt: ").append(r.getEffectApplied())
                  .append(" (").append(r.getEffectDuration()).append(" Runden).");
            }
            case HEAL -> {
                sb.append("Heilt ").append(r.getHealedAmount()).append(" Schaden.");
            }
        }
        appendExtraThreads(sb, r);
        return sb.toString();
    }

    private void appendExtraThreads(StringBuilder sb, SpellCastResult r) {
        if (r.getExtraThreadLabels() == null || r.getExtraThreadLabels().isEmpty()) return;
        sb.append(" Zusatzfäden: ").append(String.join(", ", r.getExtraThreadLabels())).append(".");
    }
}
