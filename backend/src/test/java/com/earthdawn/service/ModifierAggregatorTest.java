package com.earthdawn.service;

import com.earthdawn.model.*;
import com.earthdawn.model.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModifierAggregatorTest {

    private ModifierAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new ModifierAggregator(new StepRollService());
    }

    // --- Helper builders ---

    private GameCharacter charWith(int dex, int str, int tou, int per, int wil, int cha) {
        return GameCharacter.builder()
                .name("Test")
                .dexterity(dex).strength(str).toughness(tou)
                .perception(per).willpower(wil).charisma(cha)
                .equipment(new ArrayList<>())
                .talents(new ArrayList<>())
                .skills(new ArrayList<>())
                .spells(new ArrayList<>())
                .build();
    }

    private CombatantState stateFor(GameCharacter c) {
        return CombatantState.builder()
                .character(c)
                .activeEffects(new ArrayList<>())
                .build();
    }

    private ActiveEffect effectWith(StatType stat, ModifierOperation op, double value, TriggerContext ctx) {
        ModifierEntry entry = ModifierEntry.builder()
                .targetStat(stat)
                .operation(op)
                .value(value)
                .triggerContext(ctx)
                .build();
        return ActiveEffect.builder()
                .name("test")
                .sourceType(SourceType.CONDITION)
                .remainingRounds(-1)
                .modifiers(List.of(entry))
                .build();
    }

    // --- Base value: physical defense ---

    @Test
    void physicalDefense_computedFromDex() {
        // formula: (dex + 3) / 2
        GameCharacter c = charWith(10, 10, 10, 10, 10, 10); // dex=10 → (10+3)/2 = 6
        CombatantState state = stateFor(c);
        assertThat(aggregator.getEffectiveValue(state, StatType.PHYSICAL_DEFENSE, TriggerContext.ALWAYS))
                .isEqualTo(6);
    }

    @Test
    void physicalDefense_overriddenWhenExplicit() {
        GameCharacter c = charWith(10, 10, 10, 10, 10, 10);
        c.setPhysicalDefense(12); // explicit override
        CombatantState state = stateFor(c);
        assertThat(aggregator.getEffectiveValue(state, StatType.PHYSICAL_DEFENSE, TriggerContext.ALWAYS))
                .isEqualTo(12);
    }

    @Test
    void physicalDefense_bonusFieldAdded() {
        GameCharacter c = charWith(10, 10, 10, 10, 10, 10); // base = 6
        c.setPhysicalDefenseBonus(2);
        CombatantState state = stateFor(c);
        assertThat(aggregator.getEffectiveValue(state, StatType.PHYSICAL_DEFENSE, TriggerContext.ALWAYS))
                .isEqualTo(8);
    }

    // --- Modifier: ADD ---

    @Test
    void addModifier_increasesValue() {
        GameCharacter c = charWith(10, 10, 10, 10, 10, 10); // physical def = 6
        CombatantState state = stateFor(c);
        state.getActiveEffects().add(
                effectWith(StatType.PHYSICAL_DEFENSE, ModifierOperation.ADD, 3, TriggerContext.ALWAYS));
        assertThat(aggregator.getEffectiveValue(state, StatType.PHYSICAL_DEFENSE, TriggerContext.ALWAYS))
                .isEqualTo(9);
    }

    @Test
    void addModifier_negativeReducesValue() {
        GameCharacter c = charWith(10, 10, 10, 10, 10, 10); // physical def = 6
        CombatantState state = stateFor(c);
        state.getActiveEffects().add(
                effectWith(StatType.PHYSICAL_DEFENSE, ModifierOperation.ADD, -3, TriggerContext.ALWAYS));
        assertThat(aggregator.getEffectiveValue(state, StatType.PHYSICAL_DEFENSE, TriggerContext.ALWAYS))
                .isEqualTo(3);
    }

    @Test
    void multipleAddModifiers_summed() {
        GameCharacter c = charWith(10, 10, 10, 10, 10, 10); // physical def = 6
        CombatantState state = stateFor(c);
        state.getActiveEffects().add(
                effectWith(StatType.PHYSICAL_DEFENSE, ModifierOperation.ADD, 2, TriggerContext.ALWAYS));
        state.getActiveEffects().add(
                effectWith(StatType.PHYSICAL_DEFENSE, ModifierOperation.ADD, 3, TriggerContext.ALWAYS));
        assertThat(aggregator.getEffectiveValue(state, StatType.PHYSICAL_DEFENSE, TriggerContext.ALWAYS))
                .isEqualTo(11);
    }

    // --- Modifier: OVERRIDE ---

    @Test
    void overrideModifier_replacesCalculatedValue() {
        GameCharacter c = charWith(10, 10, 10, 10, 10, 10); // physical def = 6
        CombatantState state = stateFor(c);
        state.getActiveEffects().add(
                effectWith(StatType.PHYSICAL_DEFENSE, ModifierOperation.OVERRIDE, 20, TriggerContext.ALWAYS));
        assertThat(aggregator.getEffectiveValue(state, StatType.PHYSICAL_DEFENSE, TriggerContext.ALWAYS))
                .isEqualTo(20);
    }

    @Test
    void overrideModifier_takesEffectEvenWithAddModifiers() {
        GameCharacter c = charWith(10, 10, 10, 10, 10, 10);
        CombatantState state = stateFor(c);
        state.getActiveEffects().add(
                effectWith(StatType.PHYSICAL_DEFENSE, ModifierOperation.ADD, 99, TriggerContext.ALWAYS));
        state.getActiveEffects().add(
                effectWith(StatType.PHYSICAL_DEFENSE, ModifierOperation.OVERRIDE, 5, TriggerContext.ALWAYS));
        assertThat(aggregator.getEffectiveValue(state, StatType.PHYSICAL_DEFENSE, TriggerContext.ALWAYS))
                .isEqualTo(5);
    }

    // --- Modifier: SET_MIN / SET_MAX ---

    @Test
    void setMinModifier_enforcesFloor() {
        GameCharacter c = charWith(10, 10, 10, 10, 10, 10); // physical def = 6
        CombatantState state = stateFor(c);
        state.getActiveEffects().add(
                effectWith(StatType.PHYSICAL_DEFENSE, ModifierOperation.SET_MIN, 10, TriggerContext.ALWAYS));
        assertThat(aggregator.getEffectiveValue(state, StatType.PHYSICAL_DEFENSE, TriggerContext.ALWAYS))
                .isEqualTo(10);
    }

    @Test
    void setMaxModifier_enforcesCeiling() {
        GameCharacter c = charWith(10, 10, 10, 10, 10, 10); // physical def = 6
        CombatantState state = stateFor(c);
        state.getActiveEffects().add(
                effectWith(StatType.PHYSICAL_DEFENSE, ModifierOperation.ADD, 10, TriggerContext.ALWAYS));
        state.getActiveEffects().add(
                effectWith(StatType.PHYSICAL_DEFENSE, ModifierOperation.SET_MAX, 8, TriggerContext.ALWAYS));
        assertThat(aggregator.getEffectiveValue(state, StatType.PHYSICAL_DEFENSE, TriggerContext.ALWAYS))
                .isEqualTo(8);
    }

    // --- TriggerContext filtering ---

    @Test
    void modifier_notAppliedWhenContextDoesNotMatch() {
        GameCharacter c = charWith(10, 10, 10, 10, 10, 10); // physical def = 6
        CombatantState state = stateFor(c);
        // Modifier only applies ON_MELEE_ATTACK, not ALWAYS
        state.getActiveEffects().add(
                effectWith(StatType.PHYSICAL_DEFENSE, ModifierOperation.ADD, 5, TriggerContext.ON_MELEE_ATTACK));
        assertThat(aggregator.getEffectiveValue(state, StatType.PHYSICAL_DEFENSE, TriggerContext.ALWAYS))
                .isEqualTo(6); // unchanged
    }

    @Test
    void alwaysContextModifier_appliesInAnyContext() {
        GameCharacter c = charWith(10, 10, 10, 10, 10, 10);
        CombatantState state = stateFor(c);
        state.getActiveEffects().add(
                effectWith(StatType.PHYSICAL_DEFENSE, ModifierOperation.ADD, 2, TriggerContext.ALWAYS));
        assertThat(aggregator.getEffectiveValue(state, StatType.PHYSICAL_DEFENSE, TriggerContext.ON_MELEE_DEFENSE))
                .isEqualTo(8);
    }

    // --- Result floor: never below 0 ---

    @Test
    void result_neverBelowZero() {
        GameCharacter c = charWith(10, 10, 10, 10, 10, 10); // physical def = 6
        CombatantState state = stateFor(c);
        state.getActiveEffects().add(
                effectWith(StatType.PHYSICAL_DEFENSE, ModifierOperation.ADD, -100, TriggerContext.ALWAYS));
        assertThat(aggregator.getEffectiveValue(state, StatType.PHYSICAL_DEFENSE, TriggerContext.ALWAYS))
                .isGreaterThanOrEqualTo(0);
    }

    // --- Wound threshold formula ---

    @Test
    void woundThreshold_computedFromToughness() {
        // formula: (tou / 2) + 4
        GameCharacter c = charWith(10, 10, 10, 10, 10, 10); // tou=10 → 5+4=9
        CombatantState state = stateFor(c);
        assertThat(aggregator.getEffectiveValue(state, StatType.WOUND_THRESHOLD, TriggerContext.ALWAYS))
                .isEqualTo(9);
    }

    // --- Attack step: wounds reduce it ---

    @Test
    void attackStep_reducedByWounds() {
        GameCharacter c = charWith(10, 10, 10, 10, 10, 10); // dex=10 → step=5
        CombatantState state = stateFor(c);
        state.setWounds(2);
        // base = attributeToStep(10) - 2 = 5 - 2 = 3
        assertThat(aggregator.getEffectiveValue(state, StatType.ATTACK_STEP, TriggerContext.ALWAYS))
                .isEqualTo(3);
    }

    @Test
    void attackStep_minimumOne_evenWithManyWounds() {
        GameCharacter c = charWith(10, 10, 10, 10, 10, 10);
        CombatantState state = stateFor(c);
        state.setWounds(99);
        assertThat(aggregator.getEffectiveValue(state, StatType.ATTACK_STEP, TriggerContext.ALWAYS))
                .isGreaterThanOrEqualTo(1);
    }
}
