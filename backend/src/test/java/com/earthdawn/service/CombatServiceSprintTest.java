package com.earthdawn.service;

import com.earthdawn.dto.SprintResult;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Sprint: einfache Aktion ohne Würfelwurf, 1 Überanstrengung, +Rang Bewegungsrate
 * für die aktuelle Runde. Rang aus dem Talent oder der weltlichen Fertigkeit.
 */
@ExtendWith(MockitoExtension.class)
class CombatServiceSprintTest {

    @Mock CombatSessionRepository sessionRepo;
    @Mock CharacterRepository characterRepo;
    @Mock StepRollService diceService;
    @Mock ModifierAggregator modifiers;
    @Mock SimpMessagingTemplate websocket;
    @Mock ObjectMapper objectMapper;

    @InjectMocks CombatService combatService;

    private CombatSession session;
    private CombatantState actor;

    @BeforeEach
    void setUp() {
        actor = combatant(10L, "Sarin");
        session = CombatSession.builder()
                .id(1L).round(1)
                .phase(CombatPhase.ACTION)
                .status(CombatStatus.ACTIVE)
                .combatants(new ArrayList<>(List.of(actor)))
                .log(new ArrayList<>())
                .build();

        lenient().when(sessionRepo.findById(anyLong())).thenReturn(Optional.of(session));
        lenient().when(sessionRepo.save(any(CombatSession.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void talent_erhoehtBewegungsrateUmRang() {
        gibTalent(3);
        when(modifiers.getEffectiveValue(actor, StatType.MOVEMENT_HEXES, TriggerContext.ALWAYS)).thenReturn(9);

        SprintResult r = combatService.performSprint(1L, 10L);

        assertThat(r.getRank()).isEqualTo(3);
        assertThat(r.getMovementBonus()).isEqualTo(3);
        assertThat(r.getNewMovement()).isEqualTo(9);
        assertThat(r.getDamageTaken()).isEqualTo(1);
        assertThat(actor.getCurrentDamage()).isEqualTo(1);

        ActiveEffect effect = actor.getActiveEffects().stream()
                .filter(e -> TalentNames.SPRINT.equals(e.getName()))
                .findFirst().orElseThrow();
        ModifierEntry mod = effect.getModifiers().get(0);
        assertThat(mod.getTargetStat()).isEqualTo(StatType.MOVEMENT_HEXES);
        assertThat(mod.getOperation()).isEqualTo(ModifierOperation.ADD);
        assertThat(mod.getValue()).isEqualTo(3);
        assertThat(mod.getTriggerContext()).isEqualTo(TriggerContext.ALWAYS);
        assertThat(effect.getRemainingRounds()).isEqualTo(1);   // nur die aktuelle Runde
    }

    @Test
    void einfacheAktion_verbrauchtKeineHauptaktion() {
        gibTalent(3);
        combatService.performSprint(1L, 10L);
        assertThat(actor.isHasActedThisRound()).isFalse();
    }

    @Test
    void ohneTalent_greiftDieWeltlicheFertigkeit() {
        gibFertigkeit(2);

        SprintResult r = combatService.performSprint(1L, 10L);

        assertThat(r.getRank()).isEqualTo(2);
        assertThat(r.getMovementBonus()).isEqualTo(2);
    }

    @Test
    void talentSchlaegtFertigkeit() {
        gibTalent(5);
        gibFertigkeit(2);

        assertThat(combatService.performSprint(1L, 10L).getRank()).isEqualTo(5);
    }

    @Test
    void erneutesWirken_ersetztStattZuStapeln() {
        gibTalent(3);
        combatService.performSprint(1L, 10L);
        combatService.performSprint(1L, 10L);

        long count = actor.getActiveEffects().stream()
                .filter(e -> TalentNames.SPRINT.equals(e.getName())).count();
        assertThat(count).isEqualTo(1);
        assertThat(actor.getCurrentDamage()).isEqualTo(2);
    }

    @Test
    void ohneTalentUndFertigkeit_schlaegtFehl() {
        assertThatThrownBy(() -> combatService.performSprint(1L, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Sprint");
    }

    @Test
    void besiegt_schlaegtFehl() {
        gibTalent(3);
        actor.setDefeated(true);
        assertThatThrownBy(() -> combatService.performSprint(1L, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("besiegt");
    }

    // --- Helfer ---

    private void gibTalent(int rank) {
        actor.getCharacter().getTalents().add(CharacterTalent.builder()
                .id(1L).rank(rank)
                .talentDefinition(TalentDefinition.builder()
                        .id(1L).name(TalentNames.SPRINT)
                        .attribute(AttributeType.DEXTERITY).build())
                .build());
    }

    private void gibFertigkeit(int rank) {
        actor.getCharacter().getSkills().add(CharacterSkill.builder()
                .id(2L).rank(rank)
                .skillDefinition(SkillDefinition.builder()
                        .id(2L).name(TalentNames.SPRINT)
                        .attribute(AttributeType.DEXTERITY).build())
                .build());
    }

    private CombatantState combatant(Long id, String name) {
        GameCharacter c = GameCharacter.builder()
                .id(id).name(name).dexterity(14)
                .talents(new ArrayList<>())
                .skills(new ArrayList<>())
                .equipment(new ArrayList<>())
                .spells(new ArrayList<>())
                .build();
        return CombatantState.builder()
                .id(id).character(c)
                .activeEffects(new ArrayList<>())
                .build();
    }
}
