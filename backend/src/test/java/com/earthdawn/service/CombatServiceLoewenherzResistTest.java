package com.earthdawn.service;

import com.earthdawn.dto.RollResult;
import com.earthdawn.dto.TauntRequest;
import com.earthdawn.dto.TauntResult;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Löwenherz greift in die Starrsinn-Gegenprobe gegen Verspotten ein: die Löwenherzstufe
 * (WIL + Rang) tritt an die Stelle der reinen WIL-Stufe. Deterministisch mit festen Würfen.
 */
@ExtendWith(MockitoExtension.class)
class CombatServiceLoewenherzResistTest {

    @Mock CombatSessionRepository sessionRepo;
    @Mock CharacterRepository characterRepo;
    @Mock StepRollService diceService;
    @Mock ModifierAggregator modifiers;
    @Mock SimpMessagingTemplate websocket;
    @Mock ObjectMapper objectMapper;

    @InjectMocks CombatService combatService;

    private CombatSession session;
    private CombatantState spotter;   // verspottet
    private CombatantState target;    // hat Starrsinn (+ ggf. Löwenherz)

    @BeforeEach
    void setUp() {
        spotter = combatant(10L, "Troubadour", TalentNames.VERSPOTTEN, 6);
        target  = combatant(20L, "Sarin",      TalentNames.STARRSINN,  5);

        session = CombatSession.builder()
                .id(1L).round(1)
                .phase(CombatPhase.ACTION)
                .status(CombatStatus.ACTIVE)
                .combatants(new ArrayList<>(List.of(spotter, target)))
                .log(new ArrayList<>())
                .build();

        lenient().when(sessionRepo.findById(anyLong())).thenReturn(Optional.of(session));
        lenient().when(sessionRepo.save(any(CombatSession.class))).thenAnswer(i -> i.getArgument(0));
        // WIL/CHA 14 → Stufe 6
        lenient().when(diceService.attributeToStep(anyInt())).thenReturn(6);
        // Verspotten gelingt sicher (SV 10, Wurf 20)
        lenient().when(modifiers.getEffectiveValue(any(), any(), any())).thenReturn(10);
        lenient().when(diceService.roll(anyInt())).thenAnswer(i -> RollResult.builder()
                .step(i.getArgument(0)).total(20).dice(new ArrayList<>()).build());
    }

    @Test
    void ohneLoewenherz_gegenprobeNutztReineWilStufe() {
        TauntResult r = combatService.performTaunt(1L, taunt());

        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getResistStep()).isEqualTo(11);   // WIL-Stufe 6 + Starrsinn 5
    }

    @Test
    void mitLoewenherz_gegenprobeNutztLoewenherzstufe() {
        gibLoewenherz(target, 4);

        TauntResult r = combatService.performTaunt(1L, taunt());

        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getResistStep()).isEqualTo(15);   // (6 + 4) + Starrsinn 5
    }

    // --- Helfer ---

    private TauntRequest taunt() {
        TauntRequest req = new TauntRequest();
        req.setSessionId(1L);
        req.setActorCombatantId(10L);
        req.setTargetCombatantId(20L);
        req.setBonusSteps(0);
        req.setSpendKarma(false);
        return req;
    }

    private void gibLoewenherz(CombatantState c, int rank) {
        c.getActiveEffects().add(ActiveEffect.builder()
                .combatantState(c)
                .name(TalentNames.LOEWENHERZ)
                .sourceType(SourceType.TALENT)
                .remainingRounds(1)
                .negative(false)
                .modifiers(List.of(ModifierEntry.builder()
                        .targetStat(StatType.WILLPOWER_RESIST_STEP)
                        .operation(ModifierOperation.ADD)
                        .value(rank)
                        .triggerContext(TriggerContext.ALWAYS)
                        .build()))
                .build());
    }

    private CombatantState combatant(Long id, String name, String talentName, int rank) {
        List<CharacterTalent> talents = new ArrayList<>();
        talents.add(CharacterTalent.builder()
                .id(id).rank(rank)
                .talentDefinition(TalentDefinition.builder()
                        .id(id).name(talentName)
                        .attribute(AttributeType.CHARISMA).build())
                .build());
        GameCharacter c = GameCharacter.builder()
                .id(id).name(name).charisma(14).willpower(14)
                .talents(talents)
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
