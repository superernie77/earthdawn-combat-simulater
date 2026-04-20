package com.earthdawn.service;

import com.earthdawn.model.*;
import com.earthdawn.model.enums.*;
import com.earthdawn.model.enums.EquipmentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Berechnet effektive Werte für einen Kombattanten unter Berücksichtigung
 * aller aktiven Modifikatoren (Zauber, Talente, Zustände, Ausrüstung etc.).
 *
 * Reihenfolge der Modifikatoren:
 * 1. ADD (alle addieren)
 * 2. MULTIPLY (auf Summe anwenden)
 * 3. OVERRIDE (überschreibt alles)
 * 4. SET_MIN / SET_MAX (Grenzen anwenden)
 * Minimum: immer 1 (außer Werte die 0 sein dürfen wie Rüstung)
 */
@Service
@RequiredArgsConstructor
public class ModifierAggregator {

    private final StepRollService stepRoll;

    public int getEffectiveValue(CombatantState combatant, StatType stat, TriggerContext context) {
        int base = getBaseValue(combatant, stat);
        return applyModifiers(base, combatant.getActiveEffects(), stat, context);
    }

    private int applyModifiers(int base, List<ActiveEffect> effects, StatType stat, TriggerContext context) {
        int addTotal = 0;
        double multTotal = 1.0;
        Integer override = null;
        int setMin = Integer.MIN_VALUE;
        int setMax = Integer.MAX_VALUE;

        for (ActiveEffect effect : effects) {
            for (ModifierEntry mod : effect.getModifiers()) {
                if (mod.getTargetStat() != stat) continue;
                TriggerContext tc = mod.getTriggerContext();
                if (tc != TriggerContext.ALWAYS && tc != context) continue;

                switch (mod.getOperation()) {
                    case ADD      -> addTotal += (int) mod.getValue();
                    case MULTIPLY -> multTotal *= mod.getValue();
                    case OVERRIDE -> override = (int) mod.getValue();
                    case SET_MIN  -> setMin = Math.max(setMin, (int) mod.getValue());
                    case SET_MAX  -> setMax = Math.min(setMax, (int) mod.getValue());
                }
            }
        }

        if (override != null) return Math.max(0, override);

        int result = (int) Math.round((base + addTotal) * multTotal);
        if (setMin != Integer.MIN_VALUE) result = Math.max(result, setMin);
        if (setMax != Integer.MAX_VALUE) result = Math.min(result, setMax);
        return Math.max(0, result);
    }

    /** Berechnet den Basiswert aus Charakter-Attributen (ohne aktive Modifikatoren). */
    public int getBaseValue(CombatantState combatant, StatType stat) {
        GameCharacter c = combatant.getCharacter();
        return switch (stat) {
            case PHYSICAL_DEFENSE     -> computeOrOverride(c.getPhysicalDefense(),     (c.getDexterity() + 3) / 2) + c.getPhysicalDefenseBonus()
                    + c.getEquipment().stream().filter(e -> e.getType() == EquipmentType.SHIELD).mapToInt(Equipment::getPhysicalDefenseBonus).sum();
            case SPELL_DEFENSE        -> computeOrOverride(c.getSpellDefense(),        (c.getPerception() + 3) / 2) + c.getSpellDefenseBonus()
                    + c.getEquipment().stream().filter(e -> e.getType() == EquipmentType.SHIELD).mapToInt(Equipment::getMysticDefenseBonus).sum();
            case SOCIAL_DEFENSE       -> computeOrOverride(c.getSocialDefense(),       (c.getCharisma() + 3) / 2) + c.getSocialDefenseBonus();
            case PHYSICAL_ARMOR       -> computeOrOverride(c.getPhysicalArmor(), 0)
                    + c.getEquipment().stream().filter(e -> e.getType() == EquipmentType.ARMOR).mapToInt(Equipment::getPhysicalArmor).sum();
            case MYSTIC_ARMOR         -> computeOrOverride(c.getMysticArmor(), 0)
                    + c.getEquipment().stream().filter(e -> e.getType() == EquipmentType.ARMOR).mapToInt(Equipment::getMysticalArmor).sum();
            case INITIATIVE_STEP      -> Math.max(1, stepRoll.attributeToStep(c.getDexterity()) - combatant.getWounds()
                    - c.getEquipment().stream().filter(e -> e.getType() == EquipmentType.ARMOR || e.getType() == EquipmentType.SHIELD).mapToInt(Equipment::getInitiativePenalty).sum());
            case ATTACK_STEP          -> Math.max(1, stepRoll.attributeToStep(c.getDexterity()) - combatant.getWounds());
            case DAMAGE_STEP          -> Math.max(1, stepRoll.attributeToStep(c.getStrength())  - combatant.getWounds());
            case WOUND_THRESHOLD      -> computeOrOverride(c.getWoundThreshold(),      (c.getToughness() / 2) + 4);
            case UNCONSCIOUSNESS_RATING -> computeOrOverride(c.getUnconsciousnessRating(), c.getToughness() * 2 + (c.getDiscipline() != null ? c.getDiscipline().getBwBonusPerCircle() * Math.max(0, c.getCircle() - 1) : 0));
            case DEATH_RATING         -> computeOrOverride(c.getDeathRating(),         c.getToughness() * 2 + 10 + (c.getDiscipline() != null ? c.getDiscipline().getTdBonusPerCircle() * Math.max(0, c.getCircle() - 1) : 0));
            case KARMA_STEP           -> c.getDiscipline() != null ? c.getDiscipline().getKarmaStep() : 4;
            case RECOVERY_STEP        -> stepRoll.attributeToStep(c.getToughness());
            case CARRYING_CAPACITY    -> c.getStrength() * 10;
        };
    }

    /** Berechnet DerivedStats direkt vom Charakter (ohne CombatantState). */
    public int getBaseValueFromCharacter(GameCharacter c, StatType stat) {
        return switch (stat) {
            case PHYSICAL_DEFENSE     -> computeOrOverride(c.getPhysicalDefense(),     (c.getDexterity() + 3) / 2) + c.getPhysicalDefenseBonus()
                    + c.getEquipment().stream().filter(e -> e.getType() == EquipmentType.SHIELD).mapToInt(Equipment::getPhysicalDefenseBonus).sum();
            case SPELL_DEFENSE        -> computeOrOverride(c.getSpellDefense(),        (c.getPerception() + 3) / 2) + c.getSpellDefenseBonus()
                    + c.getEquipment().stream().filter(e -> e.getType() == EquipmentType.SHIELD).mapToInt(Equipment::getMysticDefenseBonus).sum();
            case SOCIAL_DEFENSE       -> computeOrOverride(c.getSocialDefense(),       (c.getCharisma() + 3) / 2) + c.getSocialDefenseBonus();
            case PHYSICAL_ARMOR       -> computeOrOverride(c.getPhysicalArmor(), 0)
                    + c.getEquipment().stream().filter(e -> e.getType() == EquipmentType.ARMOR).mapToInt(Equipment::getPhysicalArmor).sum();
            case MYSTIC_ARMOR         -> computeOrOverride(c.getMysticArmor(), 0)
                    + c.getEquipment().stream().filter(e -> e.getType() == EquipmentType.ARMOR).mapToInt(Equipment::getMysticalArmor).sum();
            case INITIATIVE_STEP      -> Math.max(1, stepRoll.attributeToStep(c.getDexterity())
                    - c.getEquipment().stream().filter(e -> e.getType() == EquipmentType.ARMOR || e.getType() == EquipmentType.SHIELD).mapToInt(Equipment::getInitiativePenalty).sum());
            case ATTACK_STEP          -> stepRoll.attributeToStep(c.getDexterity());
            case DAMAGE_STEP          -> stepRoll.attributeToStep(c.getStrength());
            case WOUND_THRESHOLD      -> computeOrOverride(c.getWoundThreshold(),      (c.getToughness() / 2) + 4);
            case UNCONSCIOUSNESS_RATING -> computeOrOverride(c.getUnconsciousnessRating(), c.getToughness() * 2 + (c.getDiscipline() != null ? c.getDiscipline().getBwBonusPerCircle() * Math.max(0, c.getCircle() - 1) : 0));
            case DEATH_RATING         -> computeOrOverride(c.getDeathRating(),         c.getToughness() * 2 + 10 + (c.getDiscipline() != null ? c.getDiscipline().getTdBonusPerCircle() * Math.max(0, c.getCircle() - 1) : 0));
            case KARMA_STEP           -> c.getDiscipline() != null ? c.getDiscipline().getKarmaStep() : 4;
            case RECOVERY_STEP        -> stepRoll.attributeToStep(c.getToughness());
            case CARRYING_CAPACITY    -> c.getStrength() * 10;
        };
    }

    private int computeOrOverride(Integer override, int computed) {
        return override != null ? override : computed;
    }
}
