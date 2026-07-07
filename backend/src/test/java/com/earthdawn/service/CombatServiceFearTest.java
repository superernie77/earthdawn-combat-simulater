package com.earthdawn.service;

import com.earthdawn.dto.FearRequest;
import com.earthdawn.dto.FearResistResult;
import com.earthdawn.dto.FearResult;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests für Verängstigen: WIL + Rang vs. MV, −2/Erfolg auf Aktionsproben für Rang Runden,
 * abschüttelbar per Willenskraftprobe (1×/Runde) gegen die Verängstigen-Stufe.
 */
@ExtendWith(MockitoExtension.class)
class CombatServiceFearTest {

    @Mock CombatSessionRepository sessionRepo;
    @Mock CharacterRepository characterRepo;
    @Mock StepRollService diceService;
    @Mock ModifierAggregator modifiers;
    @Mock SimpMessagingTemplate websocket;
    @Mock ObjectMapper objectMapper;

    @InjectMocks CombatService combatService;

    private CombatantState actor;
    private CombatantState target;
    private CombatSession session;

    @BeforeEach
    void setUp() {
        actor  = combatant(10L, "Geisterbeschwörer", 12, veraengstigen(3)); // WIL 12, Rang 3
        target = combatant(20L, "Ziel", 9, new ArrayList<>());
        session = CombatSession.builder()
                .id(1L).name("Test").round(1).phase(CombatPhase.ACTION)
                .combatants(new ArrayList<>(List.of(actor, target)))
                .log(new ArrayList<>())
                .build();
        lenient().when(sessionRepo.findById(1L)).thenReturn(Optional.of(session));
        lenient().when(sessionRepo.save(any(CombatSession.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(diceService.attributeToStep(12)).thenReturn(5); // Adept-WIL-Step
        lenient().when(diceService.attributeToStep(9)).thenReturn(4);  // Ziel-WIL-Step
        lenient().when(modifiers.getEffectiveValue(eq(target), eq(StatType.SPELL_DEFENSE), any())).thenReturn(8);
    }

    // --- performFear ---

    @Test
    void success_appliesMinusTwoPerSuccess_forRankRounds_withResistTn() {
        // Wurf 13 vs MV 8 → 1 + (13−8)/5 = 2 Erfolge → −4
        when(diceService.roll(8)).thenReturn(roll(13)); // rollStep = 5 + 3 = 8
        FearResult r = combatService.performFear(1L, req());

        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getSuccesses()).isEqualTo(2);
        assertThat(r.getPenalty()).isEqualTo(4);
        assertThat(r.getDuration()).isEqualTo(3);          // = Rang
        assertThat(r.getResistTargetNumber()).isEqualTo(8); // WIL-Step 5 + Rang 3
        assertThat(actor.getCurrentDamage()).isZero();      // 0 Überanstrengung

        ActiveEffect eff = fearEffect();
        assertThat(eff).isNotNull();
        assertThat(eff.getRemainingRounds()).isEqualTo(3);
        assertThat(eff.getResistTargetNumber()).isEqualTo(8);
        assertThat((int) eff.getModifiers().get(0).getValue()).isEqualTo(-4);
        assertThat(eff.getModifiers().get(0).getTargetStat()).isEqualTo(StatType.ATTACK_STEP);
    }

    @Test
    void failure_noEffect() {
        when(diceService.roll(8)).thenReturn(roll(5)); // < MV 8
        FearResult r = combatService.performFear(1L, req());

        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getPenalty()).isZero();
        assertThat(fearEffect()).isNull();
    }

    @Test
    void reapply_replacesExistingEffect() {
        when(diceService.roll(8)).thenReturn(roll(13)).thenReturn(roll(8)); // 2 Erfolge, dann 1
        combatService.performFear(1L, req());
        combatService.performFear(1L, req());

        long count = target.getActiveEffects().stream()
                .filter(e -> TalentNames.EFFECT_VERAENGSTIGT.equals(e.getName())).count();
        assertThat(count).isEqualTo(1);
        assertThat((int) fearEffect().getModifiers().get(0).getValue()).isEqualTo(-2); // letzter Wurf: 1 Erfolg
    }

    @Test
    void missingTalent_throws() {
        actor.getCharacter().getTalents().clear();
        assertThatThrownBy(() -> combatService.performFear(1L, req()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Verängstigen");
    }

    // --- resistFear ---

    @Test
    void resist_success_removesEffect_andSetsRoundFlag() {
        when(diceService.roll(8)).thenReturn(roll(13));
        combatService.performFear(1L, req());
        when(diceService.roll(4)).thenReturn(roll(9)); // Ziel-WIL-Step 4, Wurf 9 >= TN 8

        FearResistResult r = combatService.resistFear(1L, 20L);

        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getTargetNumber()).isEqualTo(8);
        assertThat(fearEffect()).isNull();               // Effekt entfernt
        assertThat(target.isFearResistUsedThisRound()).isTrue();
    }

    @Test
    void resist_failure_keepsEffect_butConsumesAttempt() {
        when(diceService.roll(8)).thenReturn(roll(13));
        combatService.performFear(1L, req());
        when(diceService.roll(4)).thenReturn(roll(5)); // < TN 8

        FearResistResult r = combatService.resistFear(1L, 20L);

        assertThat(r.isSuccess()).isFalse();
        assertThat(fearEffect()).isNotNull();
        assertThat(target.isFearResistUsedThisRound()).isTrue();
    }

    @Test
    void resist_oncePerRound() {
        when(diceService.roll(8)).thenReturn(roll(13));
        combatService.performFear(1L, req());
        target.setFearResistUsedThisRound(true);

        assertThatThrownBy(() -> combatService.resistFear(1L, 20L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bereits abgelegt");
    }

    @Test
    void resist_withoutFearEffect_throws() {
        assertThatThrownBy(() -> combatService.resistFear(1L, 20L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nicht verängstigt");
    }

    // --- Helpers ---

    private FearRequest req() {
        return FearRequest.builder()
                .sessionId(1L).actorCombatantId(10L).targetCombatantId(20L)
                .bonusSteps(0).spendKarma(false).build();
    }

    private ActiveEffect fearEffect() {
        return target.getActiveEffects().stream()
                .filter(e -> TalentNames.EFFECT_VERAENGSTIGT.equals(e.getName()))
                .findFirst().orElse(null);
    }

    private RollResult roll(int total) {
        return RollResult.builder().total(total).diceExpression("W8").build();
    }

    private List<CharacterTalent> veraengstigen(int rank) {
        TalentDefinition def = TalentDefinition.builder()
                .id(88L).name(TalentNames.VERAENGSTIGEN).attribute(AttributeType.WILLPOWER).build();
        return new ArrayList<>(List.of(CharacterTalent.builder().id(88L).talentDefinition(def).rank(rank).build()));
    }

    private CombatantState combatant(long id, String name, int willpower, List<CharacterTalent> talents) {
        GameCharacter c = GameCharacter.builder()
                .id(id).name(name).willpower(willpower).dexterity(10).strength(10)
                .equipment(new ArrayList<>()).talents(talents)
                .skills(new ArrayList<>()).spells(new ArrayList<>())
                .build();
        return CombatantState.builder()
                .id(id).character(c).activeEffects(new ArrayList<>())
                .currentDamage(0).wounds(0).pendingRiposteAttackTotal(-1)
                .build();
    }
}
