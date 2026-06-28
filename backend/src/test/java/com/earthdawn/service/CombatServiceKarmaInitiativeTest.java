package com.earthdawn.service;

import com.earthdawn.model.*;
import com.earthdawn.model.enums.*;
import com.earthdawn.dto.RollResult;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests für "Karma auf Initiative" — Disziplin-Fähigkeit ab dem 3. Kreis
 * (Dieb, Kundschafter, Luftsegler, Schütze): 1 Karma → +W6 (Stufe 4) auf den Initiativewurf.
 */
@ExtendWith(MockitoExtension.class)
class CombatServiceKarmaInitiativeTest {

    @Mock CombatSessionRepository sessionRepo;
    @Mock CharacterRepository characterRepo;
    @Mock StepRollService diceService;
    @Mock ModifierAggregator modifiers;
    @Mock SimpMessagingTemplate websocket;
    @Mock ObjectMapper objectMapper;

    @InjectMocks CombatService combatService;

    private CombatantState combatant;
    private CombatSession session;

    @BeforeEach
    void setUp() {
        combatant = combatant(10L, "Dieb", "Dieb", 3, 5);
        session = CombatSession.builder()
                .id(1L).name("Test").round(1)
                .phase(CombatPhase.DECLARATION)
                .combatants(new ArrayList<>(List.of(combatant)))
                .log(new ArrayList<>())
                .build();
        lenient().when(sessionRepo.findById(1L)).thenReturn(Optional.of(session));
        lenient().when(sessionRepo.save(any(CombatSession.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // --- setKarmaInitiative (Ansagephase-Auswahl) ---

    @Test
    void eligibleDiscipline_thirdCircle_setsFlag() {
        CombatSession s = combatService.setKarmaInitiative(1L, 10L, true);
        assertThat(s.getCombatants().get(0).isKarmaInitiativeThisRound()).isTrue();
    }

    @Test
    void unselect_clearsFlag() {
        combatant.setKarmaInitiativeThisRound(true);
        combatService.setKarmaInitiative(1L, 10L, false);
        assertThat(combatant.isKarmaInitiativeThisRound()).isFalse();
    }

    @Test
    void belowThirdCircle_throws() {
        combatant.getCharacter().setCircle(2);
        assertThatThrownBy(() -> combatService.setKarmaInitiative(1L, 10L, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kein Karma auf Initiative");
    }

    @Test
    void ineligibleDiscipline_throws() {
        combatant.getCharacter().setDiscipline(discipline("Krieger"));
        assertThatThrownBy(() -> combatService.setKarmaInitiative(1L, 10L, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kein Karma auf Initiative");
    }

    @Test
    void noKarma_throws() {
        combatant.setCurrentKarma(0);
        assertThatThrownBy(() -> combatService.setKarmaInitiative(1L, 10L, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kein Karma");
    }

    @Test
    void notDeclarationPhase_throws() {
        session.setPhase(CombatPhase.ACTION);
        assertThatThrownBy(() -> combatService.setKarmaInitiative(1L, 10L, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ansagephase");
    }

    // --- Initiativewurf mit Karma (via declareAction → rerollInitiative) ---

    @Test
    void initiativeRoll_addsKarmaDie_andSpendsKarma() {
        combatant.setKarmaInitiativeThisRound(true);
        when(modifiers.getEffectiveValue(eq(combatant), eq(StatType.INITIATIVE_STEP), any())).thenReturn(6);
        when(diceService.roll(6)).thenReturn(RollResult.builder().total(8).diceExpression("2W6").build());
        when(diceService.roll(4)).thenReturn(RollResult.builder().total(4).diceExpression("W6").build());

        combatService.declareAction(1L, 10L, DeclaredStance.NONE, DeclaredActionType.WEAPON);

        assertThat(combatant.getInitiative()).isEqualTo(12);    // 8 (Basis) + 4 (Karma)
        assertThat(combatant.getCurrentKarma()).isEqualTo(4);   // 1 Karma ausgegeben
    }

    @Test
    void initiativeRoll_withoutFlag_noKarmaDie() {
        combatant.setKarmaInitiativeThisRound(false);
        when(modifiers.getEffectiveValue(eq(combatant), eq(StatType.INITIATIVE_STEP), any())).thenReturn(6);
        when(diceService.roll(6)).thenReturn(RollResult.builder().total(8).diceExpression("2W6").build());

        combatService.declareAction(1L, 10L, DeclaredStance.NONE, DeclaredActionType.WEAPON);

        assertThat(combatant.getInitiative()).isEqualTo(8);
        assertThat(combatant.getCurrentKarma()).isEqualTo(5);
    }

    @Test
    void initiativeRoll_ineligibleDiscipline_ignoresStaleFlag() {
        combatant.getCharacter().setDiscipline(discipline("Krieger"));
        combatant.setKarmaInitiativeThisRound(true); // stale flag
        when(modifiers.getEffectiveValue(eq(combatant), eq(StatType.INITIATIVE_STEP), any())).thenReturn(6);
        when(diceService.roll(6)).thenReturn(RollResult.builder().total(8).diceExpression("2W6").build());

        combatService.declareAction(1L, 10L, DeclaredStance.NONE, DeclaredActionType.WEAPON);

        assertThat(combatant.getInitiative()).isEqualTo(8);
        assertThat(combatant.getCurrentKarma()).isEqualTo(5);
    }

    // --- Helpers ---

    private DisciplineDefinition discipline(String name) {
        return DisciplineDefinition.builder().name(name).karmaStep(4).build();
    }

    private CombatantState combatant(long id, String name, String disc, int circle, int karma) {
        GameCharacter c = GameCharacter.builder()
                .id(id).name(name).discipline(discipline(disc)).circle(circle)
                .dexterity(10).strength(10)
                .equipment(new ArrayList<>()).talents(new ArrayList<>())
                .skills(new ArrayList<>()).spells(new ArrayList<>())
                .build();
        return CombatantState.builder()
                .id(id).character(c).activeEffects(new ArrayList<>())
                .currentKarma(karma).currentDamage(0).wounds(0)
                .build();
    }
}
