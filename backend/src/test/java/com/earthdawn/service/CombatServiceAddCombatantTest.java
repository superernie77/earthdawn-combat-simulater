package com.earthdawn.service;

import com.earthdawn.dto.RollResult;
import com.earthdawn.model.*;
import com.earthdawn.model.enums.*;
import com.earthdawn.repository.CharacterRepository;
import com.earthdawn.repository.CombatSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests für das Beitreten von Kombattanten während eines laufenden Kampfes.
 */
@ExtendWith(MockitoExtension.class)
class CombatServiceAddCombatantTest {

    @Mock CombatSessionRepository sessionRepo;
    @Mock CharacterRepository characterRepo;
    @Mock StepRollService diceService;
    @Mock ModifierAggregator modifiers;
    @Mock SimpMessagingTemplate websocket;
    @Mock ObjectMapper objectMapper;

    @InjectMocks CombatService combatService;

    @Test
    void addDuringActionPhase_rollsInitiative_marksDeclared_andSortsIn() {
        CombatSession session = activeSession(CombatPhase.ACTION,
                combatant(10L, "Alt-A", 20), combatant(11L, "Alt-B", 10));
        stub(session, 99L, "Neuling", 14);

        CombatSession result = combatService.addCombatant(1L, 99L, true);

        CombatantState neu = result.getCombatants().stream()
                .filter(c -> c.getCharacter().getId().equals(99L)).findFirst().orElseThrow();
        assertThat(neu.getInitiative()).isEqualTo(14);     // gewürfelt
        assertThat(neu.isHasDeclared()).isTrue();          // Ansage vorbei
        // Reihenfolge nach Initiative: 20, 14 (neu), 10
        assertThat(result.getCombatants()).extracting(CombatantState::getInitiative)
                .containsExactly(20, 14, 10);
        assertThat(result.getCombatants().indexOf(neu)).isEqualTo(1);
        assertThat(neu.getInitiativeOrder()).isEqualTo(1);
    }

    @Test
    void addDuringDeclarationPhase_mustStillDeclare_noInitiativeYet() {
        CombatSession session = activeSession(CombatPhase.DECLARATION,
                combatant(10L, "Alt-A", 0));
        stub(session, 99L, "Neuling", 14);

        CombatSession result = combatService.addCombatant(1L, 99L, false);

        CombatantState neu = result.getCombatants().stream()
                .filter(c -> c.getCharacter().getId().equals(99L)).findFirst().orElseThrow();
        assertThat(neu.isHasDeclared()).isFalse();   // muss noch ansagen
        assertThat(neu.getInitiative()).isZero();    // Initiative erst beim Phasenübergang
    }

    // --- Helpers ---

    private void stub(CombatSession session, long charId, String name, int initRoll) {
        GameCharacter c = GameCharacter.builder()
                .id(charId).name(name).dexterity(10)
                .equipment(new ArrayList<>()).talents(new ArrayList<>())
                .skills(new ArrayList<>()).spells(new ArrayList<>())
                .build();
        when(sessionRepo.findById(1L)).thenReturn(Optional.of(session));
        when(characterRepo.findById(charId)).thenReturn(Optional.of(c));
        when(sessionRepo.save(any(CombatSession.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(modifiers.getEffectiveValue(any(), eq(StatType.INITIATIVE_STEP), any())).thenReturn(5);
        lenient().when(diceService.roll(anyInt()))
                .thenReturn(RollResult.builder().total(initRoll).diceExpression("W6").dice(List.of()).build());
    }

    private CombatSession activeSession(CombatPhase phase, CombatantState... combatants) {
        return CombatSession.builder()
                .id(1L).name("Test").round(2)
                .status(CombatStatus.ACTIVE).phase(phase)
                .combatants(new ArrayList<>(List.of(combatants)))
                .log(new ArrayList<>())
                .build();
    }

    private CombatantState combatant(long id, String name, int initiative) {
        GameCharacter c = GameCharacter.builder()
                .id(id).name(name).dexterity(10)
                .equipment(new ArrayList<>()).talents(new ArrayList<>())
                .skills(new ArrayList<>()).spells(new ArrayList<>())
                .build();
        return CombatantState.builder()
                .id(id).character(c).activeEffects(new ArrayList<>())
                .initiative(initiative).isNpc(false)
                .build();
    }
}
