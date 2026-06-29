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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests für die manuell aktivierten GM-Bedingungen "Toter Winkel" und "Bedrängt"
 * sowie die Unterdrückung aktiver Verteidigungstalente gegen Angriffe aus dem Toten Winkel.
 */
@ExtendWith(MockitoExtension.class)
class CombatServiceGmConditionTest {

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
        attacker = combatant(10L, "Angreifer", new ArrayList<>());
        defender = combatant(20L, "Ziel", new ArrayList<>(List.of(
                talentDef(TalentNames.RIPOSTE), talentDef(TalentNames.AUSWEICHEN))));
        session = CombatSession.builder()
                .id(1L).name("Test").round(1).phase(CombatPhase.ACTION)
                .combatants(new ArrayList<>(List.of(attacker, defender)))
                .log(new ArrayList<>())
                .build();
        lenient().when(sessionRepo.findById(1L)).thenReturn(Optional.of(session));
        lenient().when(sessionRepo.save(any(CombatSession.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // --- applyGmCondition: Toter Winkel ---

    @Test
    void toterWinkel_addsMinusTwoToBothDefenses() {
        combatService.applyGmCondition(1L, 20L, "TOTER_WINKEL", 1);

        ActiveEffect eff = effectByName(defender, TalentNames.EFFECT_TOTER_WINKEL);
        assertThat(eff).isNotNull();
        assertThat(modValue(eff, StatType.PHYSICAL_DEFENSE)).isEqualTo(-2);
        assertThat(modValue(eff, StatType.SPELL_DEFENSE)).isEqualTo(-2);
    }

    @Test
    void toterWinkel_reapplied_refreshesSingleEffect() {
        combatService.applyGmCondition(1L, 20L, "TOTER_WINKEL", 1);
        combatService.applyGmCondition(1L, 20L, "TOTER_WINKEL", 2);

        long count = defender.getActiveEffects().stream()
                .filter(e -> TalentNames.EFFECT_TOTER_WINKEL.equals(e.getName())).count();
        assertThat(count).isEqualTo(1);
        assertThat(effectByName(defender, TalentNames.EFFECT_TOTER_WINKEL).getRemainingRounds()).isEqualTo(2);
    }

    // --- applyGmCondition: Bedrängt (kumulativ) ---

    @Test
    void bedraengt_appliesMinusTwoToActionAndDefenses() {
        combatService.applyGmCondition(1L, 20L, "BEDRAENGT", 1);

        ActiveEffect eff = effectByName(defender, TalentNames.EFFECT_BEDRAENGT);
        assertThat(modValue(eff, StatType.ATTACK_STEP)).isEqualTo(-2);
        assertThat(modValue(eff, StatType.PHYSICAL_DEFENSE)).isEqualTo(-2);
        assertThat(modValue(eff, StatType.SPELL_DEFENSE)).isEqualTo(-2);
    }

    @Test
    void bedraengt_reapplied_stacksCumulativelyByMinusOne() {
        combatService.applyGmCondition(1L, 20L, "BEDRAENGT", 1); // -2
        combatService.applyGmCondition(1L, 20L, "BEDRAENGT", 1); // -3 (überwältigt)
        combatService.applyGmCondition(1L, 20L, "BEDRAENGT", 1); // -4

        long count = defender.getActiveEffects().stream()
                .filter(e -> TalentNames.EFFECT_BEDRAENGT.equals(e.getName())).count();
        assertThat(count).isEqualTo(1);
        ActiveEffect eff = effectByName(defender, TalentNames.EFFECT_BEDRAENGT);
        assertThat(modValue(eff, StatType.ATTACK_STEP)).isEqualTo(-4);
        assertThat(modValue(eff, StatType.PHYSICAL_DEFENSE)).isEqualTo(-4);
        assertThat(modValue(eff, StatType.SPELL_DEFENSE)).isEqualTo(-4);
    }

    @Test
    void unknownCondition_throws() {
        assertThatThrownBy(() -> combatService.applyGmCondition(1L, 20L, "FLIEGEN", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unbekannte GM-Bedingung");
    }

    // --- Unterdrückung aktiver Verteidigung gegen Angriffe aus dem Toten Winkel ---

    @Test
    void blindSpot_suppressesRiposteAndDodge_onHit() {
        stubHit();
        combatService.applyGmCondition(1L, 20L, "TOTER_WINKEL", 1); // Ziel im Toten Winkel

        CombatActionResult r = combatService.performAttack(attack());

        assertThat(r.isHit()).isTrue();
        assertThat(r.isHitPendingRiposte()).isFalse();
        assertThat(r.isHitPendingDodge()).isFalse();
    }

    @Test
    void withoutBlindSpot_riposteIsOffered_onHit() {
        stubHit();

        CombatActionResult r = combatService.performAttack(attack());

        assertThat(r.isHit()).isTrue();
        // Ohne Toten Winkel greift die Riposte-Reaktion (Schaden ausstehend)
        assertThat(r.isHitPendingRiposte() || r.isHitPendingDodge()).isTrue();
    }

    // --- Helpers ---

    private void stubHit() {
        lenient().when(modifiers.getEffectiveValue(any(), any(), any())).thenReturn(0);
        lenient().when(modifiers.getEffectiveValue(eq(attacker), eq(StatType.ATTACK_STEP), any())).thenReturn(10);
        lenient().when(modifiers.getEffectiveValue(eq(defender), eq(StatType.PHYSICAL_DEFENSE), any())).thenReturn(5);
        lenient().when(modifiers.getEffectiveValue(eq(attacker), eq(StatType.DAMAGE_STEP), any())).thenReturn(5);
        lenient().when(modifiers.getEffectiveValue(eq(defender), eq(StatType.WOUND_THRESHOLD), any())).thenReturn(8);
        lenient().when(modifiers.getEffectiveValue(eq(defender), eq(StatType.UNCONSCIOUSNESS_RATING), any())).thenReturn(50);
        lenient().when(modifiers.getEffectiveValue(eq(defender), eq(StatType.DEATH_RATING), any())).thenReturn(60);
        lenient().when(diceService.roll(anyInt()))
                .thenReturn(RollResult.builder().total(12).diceExpression("2W6").dice(List.of()).build());
        lenient().when(diceService.attributeToStep(anyInt())).thenReturn(5);
    }

    private AttackActionRequest attack() {
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

    private ActiveEffect effectByName(CombatantState c, String name) {
        return c.getActiveEffects().stream().filter(e -> name.equals(e.getName())).findFirst().orElse(null);
    }

    private int modValue(ActiveEffect eff, StatType stat) {
        return eff.getModifiers().stream()
                .filter(m -> m.getTargetStat() == stat)
                .mapToInt(m -> (int) m.getValue()).findFirst().orElseThrow();
    }

    private CharacterTalent talentDef(String name) {
        TalentDefinition def = TalentDefinition.builder().id((long) name.hashCode()).name(name).attribute(AttributeType.DEXTERITY).build();
        return CharacterTalent.builder().id((long) name.hashCode()).talentDefinition(def).rank(3).build();
    }

    private CombatantState combatant(long id, String name, List<CharacterTalent> talents) {
        GameCharacter c = GameCharacter.builder()
                .id(id).name(name).dexterity(10).strength(10)
                .equipment(new ArrayList<>()).talents(talents)
                .skills(new ArrayList<>()).spells(new ArrayList<>())
                .build();
        return CombatantState.builder()
                .id(id).character(c).activeEffects(new ArrayList<>())
                .currentDamage(0).wounds(0).pendingRiposteAttackTotal(-1)
                .build();
    }
}
