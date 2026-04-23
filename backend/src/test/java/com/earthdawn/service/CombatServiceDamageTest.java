package com.earthdawn.service;

import com.earthdawn.dto.KnockdownResult;
import com.earthdawn.dto.RollResult;
import com.earthdawn.model.*;
import com.earthdawn.model.enums.*;
import com.earthdawn.repository.CombatSessionRepository;
import com.earthdawn.repository.CharacterRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the core damage-application path in CombatService.
 * Uses mocks for all external dependencies so no Spring context is needed.
 */
@ExtendWith(MockitoExtension.class)
class CombatServiceDamageTest {

    @Mock CombatSessionRepository sessionRepo;
    @Mock CharacterRepository characterRepo;
    @Mock StepRollService diceService;
    @Mock ModifierAggregator modifiers;
    @Mock SimpMessagingTemplate websocket;
    @Mock ObjectMapper objectMapper;

    @InjectMocks
    CombatService combatService;

    private CombatSession session;
    private CombatantState defender;

    @BeforeEach
    void setUp() {
        GameCharacter character = GameCharacter.builder()
                .id(1L)
                .name("Thorin")
                .strength(10)
                .talents(new ArrayList<>())
                .equipment(new ArrayList<>())
                .build();

        defender = CombatantState.builder()
                .id(10L)
                .character(character)
                .currentDamage(0)
                .wounds(0)
                .activeEffects(new ArrayList<>())
                .build();

        session = CombatSession.builder()
                .id(1L)
                .name("Test Session")
                .round(1)
                .combatants(new ArrayList<>(List.of(defender)))
                .log(new ArrayList<>())
                .build();
    }

    // --- applyDamageToDefender ---

    @Test
    void applyDamage_increasesDamageCounter() {
        // WT=8, UR=30: 5 net damage → no wound, no defeat
        when(modifiers.getEffectiveValue(defender, StatType.WOUND_THRESHOLD, TriggerContext.ALWAYS)).thenReturn(8);
        when(modifiers.getEffectiveValue(defender, StatType.UNCONSCIOUSNESS_RATING, TriggerContext.ALWAYS)).thenReturn(30);

        combatService.applyDamageToDefender(session, defender, 5);

        assertThat(defender.getCurrentDamage()).isEqualTo(5);
    }

    @Test
    void applyDamage_noWoundBelowThreshold() {
        when(modifiers.getEffectiveValue(defender, StatType.WOUND_THRESHOLD, TriggerContext.ALWAYS)).thenReturn(8);
        when(modifiers.getEffectiveValue(defender, StatType.UNCONSCIOUSNESS_RATING, TriggerContext.ALWAYS)).thenReturn(30);

        combatService.applyDamageToDefender(session, defender, 7); // 7 < WT=8

        assertThat(defender.getWounds()).isEqualTo(0);
    }

    @Test
    void applyDamage_exactlyThreshold_oneWound() {
        when(modifiers.getEffectiveValue(defender, StatType.WOUND_THRESHOLD, TriggerContext.ALWAYS)).thenReturn(8);
        when(modifiers.getEffectiveValue(defender, StatType.UNCONSCIOUSNESS_RATING, TriggerContext.ALWAYS)).thenReturn(30);

        combatService.applyDamageToDefender(session, defender, 8); // 8/8 = 1 wound

        assertThat(defender.getWounds()).isEqualTo(1);
    }

    @Test
    void applyDamage_doubleThreshold_twoWounds() {
        when(modifiers.getEffectiveValue(defender, StatType.WOUND_THRESHOLD, TriggerContext.ALWAYS)).thenReturn(8);
        when(modifiers.getEffectiveValue(defender, StatType.UNCONSCIOUSNESS_RATING, TriggerContext.ALWAYS)).thenReturn(30);
        // netDamage=16 >= WT+5=13 triggers knockdown check — stub dice to avoid NPE
        when(diceService.attributeToStep(anyInt())).thenReturn(4);
        RollResult highRoll = RollResult.builder().step(4).total(20).dice(List.of()).diceExpression("d6").build();
        when(diceService.roll(anyInt())).thenReturn(highRoll);

        combatService.applyDamageToDefender(session, defender, 16); // 16/8 = 2 wounds

        assertThat(defender.getWounds()).isEqualTo(2);
    }

    @Test
    void applyDamage_defenderDefeatedWhenDamageReachesUR() {
        when(modifiers.getEffectiveValue(defender, StatType.WOUND_THRESHOLD, TriggerContext.ALWAYS)).thenReturn(8);
        when(modifiers.getEffectiveValue(defender, StatType.UNCONSCIOUSNESS_RATING, TriggerContext.ALWAYS)).thenReturn(20);

        combatService.applyDamageToDefender(session, defender, 20);

        assertThat(defender.isDefeated()).isTrue();
    }

    @Test
    void applyDamage_defenderNotDefeatedBelowUR() {
        when(modifiers.getEffectiveValue(defender, StatType.WOUND_THRESHOLD, TriggerContext.ALWAYS)).thenReturn(8);
        when(modifiers.getEffectiveValue(defender, StatType.UNCONSCIOUSNESS_RATING, TriggerContext.ALWAYS)).thenReturn(20);
        // netDamage=19 >= WT+5=13 and not defeated yet triggers knockdown check — stub dice
        when(diceService.attributeToStep(anyInt())).thenReturn(4);
        RollResult highRoll = RollResult.builder().step(4).total(20).dice(List.of()).diceExpression("d6").build();
        when(diceService.roll(anyInt())).thenReturn(highRoll);

        combatService.applyDamageToDefender(session, defender, 19);

        assertThat(defender.isDefeated()).isFalse();
    }

    @Test
    void applyDamage_knockdownCheckNotTriggeredWithoutWound() {
        // netDamage < WT → no wound → no knockdown check
        when(modifiers.getEffectiveValue(defender, StatType.WOUND_THRESHOLD, TriggerContext.ALWAYS)).thenReturn(8);
        when(modifiers.getEffectiveValue(defender, StatType.UNCONSCIOUSNESS_RATING, TriggerContext.ALWAYS)).thenReturn(30);

        KnockdownResult result = combatService.applyDamageToDefender(session, defender, 7);

        assertThat(result).isNull();
    }

    @Test
    void applyDamage_knockdownCheckNotTriggeredWhenDamageTooSmall() {
        // wound occurred (8 >= WT=8) but netDamage < WT+5 (need >= 13)
        when(modifiers.getEffectiveValue(defender, StatType.WOUND_THRESHOLD, TriggerContext.ALWAYS)).thenReturn(8);
        when(modifiers.getEffectiveValue(defender, StatType.UNCONSCIOUSNESS_RATING, TriggerContext.ALWAYS)).thenReturn(30);

        KnockdownResult result = combatService.applyDamageToDefender(session, defender, 8); // 8 = WT, not >= WT+5=13

        assertThat(result).isNull();
    }

    @Test
    void applyDamage_knockdownCheckTriggeredOnBigHit() {
        // WT=8, need netDamage >= 8+5=13 and >= 1 wound
        when(modifiers.getEffectiveValue(defender, StatType.WOUND_THRESHOLD, TriggerContext.ALWAYS)).thenReturn(8);
        when(modifiers.getEffectiveValue(defender, StatType.UNCONSCIOUSNESS_RATING, TriggerContext.ALWAYS)).thenReturn(30);
        // Knockdown check needs a dice roll: mock attributeToStep + roll
        when(diceService.attributeToStep(anyInt())).thenReturn(4);
        RollResult knockRoll = RollResult.builder().step(4).total(10).dice(List.of()).diceExpression("d6").build();
        when(diceService.roll(anyInt())).thenReturn(knockRoll);

        KnockdownResult result = combatService.applyDamageToDefender(session, defender, 13);

        assertThat(result).isNotNull();
        assertThat(result.getTargetName()).isEqualTo("Thorin");
    }

    @Test
    void applyDamage_knockdownOccursWhenRollBelowTN() {
        int wt = 8;
        int netDamage = 13; // TN = netDamage - wt = 5
        when(modifiers.getEffectiveValue(defender, StatType.WOUND_THRESHOLD, TriggerContext.ALWAYS)).thenReturn(wt);
        when(modifiers.getEffectiveValue(defender, StatType.UNCONSCIOUSNESS_RATING, TriggerContext.ALWAYS)).thenReturn(30);
        when(diceService.attributeToStep(anyInt())).thenReturn(4);
        // Roll total=3 < TN=5 → knocked down
        RollResult failRoll = RollResult.builder().step(4).total(3).dice(List.of()).diceExpression("d6").build();
        when(diceService.roll(anyInt())).thenReturn(failRoll);

        combatService.applyDamageToDefender(session, defender, netDamage);

        assertThat(defender.isKnockedDown()).isTrue();
    }

    @Test
    void applyDamage_noKnockdownWhenRollMeetsOrExceedsTN() {
        int wt = 8;
        int netDamage = 13; // TN = 5
        when(modifiers.getEffectiveValue(defender, StatType.WOUND_THRESHOLD, TriggerContext.ALWAYS)).thenReturn(wt);
        when(modifiers.getEffectiveValue(defender, StatType.UNCONSCIOUSNESS_RATING, TriggerContext.ALWAYS)).thenReturn(30);
        when(diceService.attributeToStep(anyInt())).thenReturn(4);
        // Roll total=5 >= TN=5 → stays standing
        RollResult successRoll = RollResult.builder().step(4).total(5).dice(List.of()).diceExpression("d6").build();
        when(diceService.roll(anyInt())).thenReturn(successRoll);

        combatService.applyDamageToDefender(session, defender, netDamage);

        assertThat(defender.isKnockedDown()).isFalse();
    }

    @Test
    void applyDamage_knockdownRemovesAkrobatischeVerteidigung() {
        int wt = 8;
        int netDamage = 13; // triggers knockdown check
        when(modifiers.getEffectiveValue(defender, StatType.WOUND_THRESHOLD, TriggerContext.ALWAYS)).thenReturn(wt);
        when(modifiers.getEffectiveValue(defender, StatType.UNCONSCIOUSNESS_RATING, TriggerContext.ALWAYS)).thenReturn(30);
        when(diceService.attributeToStep(anyInt())).thenReturn(4);
        RollResult failRoll = RollResult.builder().step(4).total(1).dice(List.of()).diceExpression("d6").build();
        when(diceService.roll(anyInt())).thenReturn(failRoll);

        ActiveEffect akroEffect = ActiveEffect.builder()
                .name(TalentNames.AKROBATISCHE_VERTEIDIGUNG)
                .sourceType(SourceType.TALENT)
                .modifiers(new ArrayList<>())
                .build();
        defender.getActiveEffects().add(akroEffect);

        combatService.applyDamageToDefender(session, defender, netDamage);

        assertThat(defender.getActiveEffects())
                .noneMatch(e -> TalentNames.AKROBATISCHE_VERTEIDIGUNG.equals(e.getName()));
    }
}
