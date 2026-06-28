package com.earthdawn.service;

import com.earthdawn.dto.AttackActionRequest;
import com.earthdawn.dto.CombatActionResult;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Regressionstests für den Lufttanz-Bonusangriff-Trigger in {@code performAttack}.
 *
 * Bug: Der Zusatzangriff wurde nur bei einem Treffer angeboten. Korrekt ist, dass er allein
 * durch den Initiative-Vorsprung ≥ 10 gegen das Ziel gewährt wird — auch bei einem Fehlschlag.
 */
@ExtendWith(MockitoExtension.class)
class CombatServiceLufttanzTest {

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
        attacker = combatant(10L, "Lufttänzer", 10, 10);
        defender = combatant(20L, "Ziel", 10, 10);
        attacker.setLufttanzActivatedThisRound(true);

        session = CombatSession.builder()
                .id(1L).name("Test").round(1)
                .phase(CombatPhase.ACTION)
                .combatants(new ArrayList<>(List.of(attacker, defender)))
                .log(new ArrayList<>())
                .build();
        lenient().when(sessionRepo.findById(1L)).thenReturn(Optional.of(session));
        lenient().when(sessionRepo.save(any(CombatSession.class))).thenAnswer(inv -> inv.getArgument(0));
        // Angriffsstufe + Fehlschlag (sehr hohe KV → Treffer ausgeschlossen)
        lenient().when(modifiers.getEffectiveValue(eq(attacker), eq(StatType.ATTACK_STEP), any())).thenReturn(5);
        lenient().when(modifiers.getEffectiveValue(eq(defender), eq(StatType.PHYSICAL_DEFENSE), any())).thenReturn(99);
        lenient().when(diceService.roll(anyInt()))
                .thenReturn(RollResult.builder().total(3).diceExpression("W6").build());
    }

    @Test
    void initiativeAdvantage_onMiss_offersBonusAttack() {
        attacker.setInitiative(18);
        defender.setInitiative(3); // Vorsprung 15 ≥ 10

        CombatActionResult result = combatService.performAttack(req());

        assertThat(result.isHit()).isFalse();
        assertThat(result.isLufttanzBonusReady()).isTrue();
        assertThat(result.getLufttanzInitiativeDiff()).isEqualTo(15);
        assertThat(attacker.getPendingLufttanzTargetId()).isEqualTo(20L);
        assertThat(attacker.getPendingLufttanzWeaponId()).isEqualTo(-1L); // keine Waffe → -1
    }

    @Test
    void initiativeAdvantageBelowTen_noBonusAttack() {
        attacker.setInitiative(8);
        defender.setInitiative(3); // Vorsprung 5 < 10

        CombatActionResult result = combatService.performAttack(req());

        assertThat(result.isLufttanzBonusReady()).isFalse();
        assertThat(attacker.getPendingLufttanzTargetId()).isEqualTo(-1L);
    }

    @Test
    void lufttanzNotActivated_noBonusAttack() {
        attacker.setLufttanzActivatedThisRound(false);
        attacker.setInitiative(18);
        defender.setInitiative(3);

        CombatActionResult result = combatService.performAttack(req());

        assertThat(result.isLufttanzBonusReady()).isFalse();
        assertThat(attacker.getPendingLufttanzTargetId()).isEqualTo(-1L);
    }

    @Test
    void bonusAlreadyUsedThisRound_noRetrigger() {
        attacker.setInitiative(18);
        defender.setInitiative(3);
        attacker.setLufttanzBonusUsedThisRound(true);

        CombatActionResult result = combatService.performAttack(req());

        assertThat(result.isLufttanzBonusReady()).isFalse();
        assertThat(attacker.getPendingLufttanzTargetId()).isEqualTo(-1L);
    }

    // --- Helpers ---

    private AttackActionRequest req() {
        AttackActionRequest r = new AttackActionRequest();
        r.setSessionId(1L);
        r.setAttackerCombatantId(10L);
        r.setDefenderCombatantId(20L);
        r.setActionType(ActionType.MELEE_ATTACK);
        r.setBonusSteps(0);
        r.setSpendKarma(false);
        r.setAmuletAttackIds(new ArrayList<>());
        r.setAmuletDamageIds(new ArrayList<>());
        return r;
    }

    private CombatantState combatant(long id, String name, int dex, int str) {
        GameCharacter c = GameCharacter.builder()
                .id(id).name(name).dexterity(dex).strength(str)
                .equipment(new ArrayList<>()).talents(new ArrayList<>())
                .skills(new ArrayList<>()).spells(new ArrayList<>())
                .build();
        return CombatantState.builder()
                .id(id).character(c).activeEffects(new ArrayList<>())
                .currentDamage(0).wounds(0)
                .build();
    }
}
