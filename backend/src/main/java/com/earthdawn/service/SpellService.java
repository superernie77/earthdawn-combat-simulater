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

        if (spell.getThreads() == 0) throw new IllegalStateException("Dieser Zauber benötigt keine Fäden.");

        // Bereits einen anderen Zauber in Vorbereitung?
        if (caster.getPreparingSpellId() != null && !caster.getPreparingSpellId().equals(req.getSpellId())) {
            // Vorbereitung abbrechen und neuen Zauber starten
            caster.setPreparingSpellId(null);
            caster.setThreadsWoven(0);
            caster.setThreadsRequired(0);
        }

        // Vorbereitung starten?
        if (caster.getPreparingSpellId() == null) {
            caster.setPreparingSpellId(spell.getId());
            caster.setThreadsWoven(0);
            caster.setThreadsRequired(spell.getThreads());
        }

        // Fadenweben-Talent finden
        String weavingTalentName = getWeavingTalentName(caster);
        CharacterTalent weavingTalent = caster.getCharacter().getTalents().stream()
                .filter(t -> t.getTalentDefinition().getName().equals(weavingTalentName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Fadenweben-Talent '" + weavingTalentName + "' nicht gefunden."));

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
            caster.setThreadsWoven(caster.getThreadsWoven() + 1);
        }

        boolean readyToCast = caster.getThreadsWoven() >= caster.getThreadsRequired();
        caster.setHasActedThisRound(true);

        String casterName = caster.getCharacter().getName();
        String desc = success
                ? casterName + " webt einen Faden für " + spell.getName() + " (" + caster.getThreadsWoven() + "/" + caster.getThreadsRequired() + "). Wurf " + total + " vs " + targetNumber + "."
                : casterName + " scheitert beim Fadenweben für " + spell.getName() + ". Wurf " + total + " vs " + targetNumber + ".";
        if (readyToCast && success) desc += " Zauber ist bereit!";

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
                .description(desc)
                .build();
    }

    // --- Zauber wirken ---

    public SpellCastResult castSpell(SpellCastRequest req) {
        var session = combatService.findById(req.getSessionId());
        var caster = combatService.findCombatant(session, req.getCasterCombatantId());

        if (caster.isDefeated()) throw new IllegalStateException("Besiegte Kombattanten können nicht handeln.");
        if (caster.isHasActedThisRound()) throw new IllegalStateException("Diese Runde wurde bereits gehandelt.");

        SpellDefinition spell = spellRepo.findById(req.getSpellId())
                .orElseThrow(() -> new EntityNotFoundException("Zauber nicht gefunden: " + req.getSpellId()));

        // Fäden-Check
        if (spell.getThreads() > 0) {
            if (caster.getPreparingSpellId() == null || !caster.getPreparingSpellId().equals(spell.getId())) {
                throw new IllegalStateException("Dieser Zauber wurde nicht vorbereitet.");
            }
            if (caster.getThreadsWoven() < caster.getThreadsRequired()) {
                throw new IllegalStateException("Noch nicht genug Fäden gewirkt (" + caster.getThreadsWoven() + "/" + caster.getThreadsRequired() + ").");
            }
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

        boolean success = defenseValue == 0 || total > defenseValue;
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
                .extraSuccesses(extraSuccesses);

        if (success) {
            switch (spell.getEffectType()) {
                case DAMAGE -> applySpellDamage(session, caster, target, spell, extraSuccesses, result);
                case BUFF   -> applySpellBuff(session, caster, target, spell, result);
                case DEBUFF -> applySpellDebuff(session, target, spell, result);
                case HEAL   -> applySpellHeal(target != null ? target : caster, spell, result);
            }
        }

        // Vorbereitung zurücksetzen
        caster.setPreparingSpellId(null);
        caster.setThreadsWoven(0);
        caster.setThreadsRequired(0);
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

        caster.setPreparingSpellId(null);
        caster.setThreadsWoven(0);
        caster.setThreadsRequired(0);

        combatService.addLog(session, caster.getCharacter().getName(), null,
                ActionType.SPELL_CAST, caster.getCharacter().getName() + " bricht die Zaubervorbereitung ab.", false);
        sessionRepo.save(session);
        combatService.broadcast(session);
    }

    // --- Spell-Effekte ---

    private void applySpellDamage(CombatSession session, CombatantState caster, CombatantState target,
                                   SpellDefinition spell, int extraSuccesses,
                                   SpellCastResult.SpellCastResultBuilder result) {
        if (target == null) throw new IllegalStateException("Schadenszauber benötigt ein Ziel.");

        int wilStep = Math.max(1, diceService.attributeToStep(caster.getCharacter().getWillpower()) - caster.getWounds());
        int damageBonus = "DAMAGE".equals(spell.getExtraSuccessEffect()) ? extraSuccesses * 2 : 0;
        int damageStep = spell.getEffectStep() + wilStep + damageBonus;
        RollResult damageRoll = diceService.roll(damageStep);

        StatType armorStat = spell.isUseMysticArmor() ? StatType.MYSTIC_ARMOR : StatType.PHYSICAL_ARMOR;
        int armor = modifiers.getEffectiveValue(target, armorStat, TriggerContext.ON_DAMAGE_RECEIVED);
        int netDamage = Math.max(0, damageRoll.getTotal() - armor);

        int wt = modifiers.getEffectiveValue(target, StatType.WOUND_THRESHOLD, TriggerContext.ALWAYS);
        int prevWounds = target.getWounds();
        KnockdownResult kdr = combatService.applyDamageToDefender(session, target, netDamage);
        int newWounds = target.getWounds() - prevWounds;

        result.damageStep(damageStep)
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
                                 SpellDefinition spell, SpellCastResult.SpellCastResultBuilder result) {
        CombatantState effectTarget = target != null ? target : caster;
        // Utility buffs have no modifyStat – just log the effect, no active modifier needed
        if (spell.getModifyStat() != null) {
            ActiveEffect effect = createSpellEffect(spell, effectTarget, false);
            effectTarget.getActiveEffects().add(effect);
        }

        result.effectApplied(spell.getEffectDescription())
              .effectDuration(spell.getDuration());
    }

    private void applySpellDebuff(CombatSession session, CombatantState target, SpellDefinition spell,
                                   SpellCastResult.SpellCastResultBuilder result) {
        if (target == null) throw new IllegalStateException("Debuff-Zauber benötigt ein Ziel.");
        ActiveEffect effect = createSpellEffect(spell, target, true);
        target.getActiveEffects().add(effect);

        result.effectApplied(spell.getEffectDescription())
              .effectDuration(spell.getDuration());
    }

    private void applySpellHeal(CombatantState target, SpellDefinition spell,
                                 SpellCastResult.SpellCastResultBuilder result) {
        RollResult healRoll = diceService.roll(spell.getEffectStep());
        int healAmount = healRoll.getTotal();
        target.setCurrentDamage(Math.max(0, target.getCurrentDamage() - healAmount));

        result.damageRoll(healRoll)
              .healedAmount(healAmount)
              .effectApplied("Heilt " + healAmount + " Schaden");
    }

    private ActiveEffect createSpellEffect(SpellDefinition spell, CombatantState target, boolean negative) {
        ModifierEntry modifier = ModifierEntry.builder()
                .targetStat(spell.getModifyStat())
                .operation(spell.getModifyOperation())
                .value(spell.getModifyValue())
                .triggerContext(spell.getModifyTrigger())
                .build();

        return ActiveEffect.builder()
                .combatantState(target)
                .name(spell.getName())
                .description(spell.getEffectDescription())
                .sourceType(SourceType.SPELL)
                .sourceId(spell.getId())
                .remainingRounds(spell.getDuration())
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
        return sb.toString();
    }
}
