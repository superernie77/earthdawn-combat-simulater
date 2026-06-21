package com.earthdawn.service;

import com.earthdawn.dto.CombatActionResult;
import com.earthdawn.dto.NachtretenRequest;
import com.earthdawn.dto.RollResult;
import com.earthdawn.model.*;
import com.earthdawn.model.enums.*;
import com.earthdawn.repository.CharacterRepository;
import com.earthdawn.repository.CombatSessionRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests für Nachtreten (zusätzlicher waffenloser Angriff) in CombatService.
 */
@ExtendWith(MockitoExtension.class)
class CombatServiceNachtretenTest {

    @Mock CombatSessionRepository sessionRepo;
    @Mock CharacterRepository characterRepo;
    @Mock StepRollService diceService;
    @Mock ModifierAggregator modifiers;
    @Mock SimpMessagingTemplate websocket;
    @Mock ObjectMapper objectMapper;

    @InjectMocks CombatService combatService;

    private CombatantState attacker;
    private CombatantState defender;
    private CombatSession session;

    @BeforeEach
    void setUp() {
        attacker = combatant(10L, "Angreifer", 10, 10, withNachtreten(4));
        defender = combatant(20L, "Ziel", 10, 10, new ArrayList<>());

        session = CombatSession.builder()
                .id(1L).name("Test").round(1)
                .phase(CombatPhase.ACTION)
                .combatants(new ArrayList<>(List.of(attacker, defender)))
                .log(new ArrayList<>())
                .build();
        lenient().when(sessionRepo.findById(1L)).thenReturn(Optional.of(session));
        lenient().when(sessionRepo.save(any(CombatSession.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void higherInitiative_missPath_consumesFlagAndStrain() {
        attacker.setInitiative(10);
        defender.setInitiative(1);
        when(diceService.attributeToStep(10)).thenReturn(5);          // DEX-Step
        when(diceService.roll(anyInt()))
                .thenReturn(RollResult.builder().total(3).diceExpression("W6").build());
        // hohe KV → Fehlschlag, Schadenszweig wird übersprungen
        when(modifiers.getEffectiveValue(eq(defender), eq(StatType.PHYSICAL_DEFENSE), any())).thenReturn(99);

        CombatActionResult result = combatService.performNachtreten(1L, req());

        assertThat(result.getActionType()).isEqualTo(ActionType.NACHTRETEN);
        assertThat(result.getAttackStep()).isEqualTo(9);   // DEX-Step 5 + Rang 4
        assertThat(result.isHit()).isFalse();
        assertThat(attacker.isNachtretenUsedThisRound()).isTrue();
        assertThat(attacker.getCurrentDamage()).isEqualTo(1); // 1 Überanstrengung
    }

    @Test
    void lowerOrEqualInitiative_throws() {
        attacker.setInitiative(1);
        defender.setInitiative(5);

        assertThatThrownBy(() -> combatService.performNachtreten(1L, req()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("niedrigerer Initiative");

        assertThat(attacker.isNachtretenUsedThisRound()).isFalse();
        assertThat(attacker.getCurrentDamage()).isZero();
    }

    @Test
    void alreadyUsedThisRound_throws() {
        attacker.setInitiative(10);
        defender.setInitiative(1);
        attacker.setNachtretenUsedThisRound(true);

        assertThatThrownBy(() -> combatService.performNachtreten(1L, req()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bereits in dieser Runde");
    }

    @Test
    void defeatedAttacker_throws() {
        attacker.setInitiative(10);
        defender.setInitiative(1);
        attacker.setDefeated(true);

        assertThatThrownBy(() -> combatService.performNachtreten(1L, req()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("besiegt");
    }

    // --- Helpers ---

    private NachtretenRequest req() {
        return NachtretenRequest.builder()
                .sessionId(1L).actorCombatantId(10L).defenderCombatantId(20L)
                .bonusSteps(0).spendKarma(false).build();
    }

    private List<CharacterTalent> withNachtreten(int rank) {
        TalentDefinition def = TalentDefinition.builder()
                .id(77L).name(TalentNames.NACHTRETEN).attribute(AttributeType.DEXTERITY).build();
        return new ArrayList<>(List.of(
                CharacterTalent.builder().id(1L).talentDefinition(def).rank(rank).build()));
    }

    private CombatantState combatant(long id, String name, int dex, int str, List<CharacterTalent> talents) {
        GameCharacter c = GameCharacter.builder()
                .id(id).name(name).dexterity(dex).strength(str)
                .equipment(new ArrayList<>()).talents(talents)
                .skills(new ArrayList<>()).spells(new ArrayList<>())
                .build();
        return CombatantState.builder()
                .id(id).character(c).activeEffects(new ArrayList<>())
                .currentDamage(0).wounds(0)
                .build();
    }
}
