package com.earthdawn.service;

import com.earthdawn.dto.CombatActionResult;
import com.earthdawn.dto.RollResult;
import com.earthdawn.dto.SchwanzangriffRequest;
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
 * Tests für den T'skrang-Schwanzangriff (zusätzlicher waffenloser Angriff, 1×/Runde, −2 auf alle Proben).
 */
@ExtendWith(MockitoExtension.class)
class CombatServiceSchwanzangriffTest {

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
        attacker = combatant(10L, "T'skrang", Race.TSKRANG, 3); // Waffenloser Kampf Rang 3
        defender = combatant(20L, "Ziel", Race.MENSCHEN, 0);
        session = CombatSession.builder()
                .id(1L).name("Test").round(1).phase(CombatPhase.ACTION)
                .combatants(new ArrayList<>(List.of(attacker, defender)))
                .log(new ArrayList<>())
                .build();
        lenient().when(sessionRepo.findById(1L)).thenReturn(Optional.of(session));
        lenient().when(sessionRepo.save(any(CombatSession.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void nonTskrang_throws() {
        attacker.getCharacter().setRace(Race.MENSCHEN);
        assertThatThrownBy(() -> combatService.performSchwanzangriff(1L, req(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("T'skrang");
    }

    @Test
    void alreadyUsedThisRound_throws() {
        attacker.setSchwanzangriffUsedThisRound(true);
        assertThatThrownBy(() -> combatService.performSchwanzangriff(1L, req(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bereits in dieser Runde");
    }

    @Test
    void defeatedAttacker_throws() {
        attacker.setDefeated(true);
        assertThatThrownBy(() -> combatService.performSchwanzangriff(1L, req(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("besiegt");
    }

    @Test
    void miss_appliesMinusTwoMalusEffect_setsFlag_noStrain() {
        stubMiss();

        CombatActionResult r = combatService.performSchwanzangriff(1L, req(null));

        assertThat(r.getActionType()).isEqualTo(ActionType.SCHWANZANGRIFF);
        assertThat(r.getAttackStep()).isEqualTo(6);   // DEX-Step 5 + WK 3 − 2 (Malus) = 6
        assertThat(r.isHit()).isFalse();
        assertThat(attacker.isSchwanzangriffUsedThisRound()).isTrue();
        assertThat(attacker.getCurrentDamage()).isZero(); // keine Ueberanstrengung
        ActiveEffect eff = attacker.getActiveEffects().stream()
                .filter(e -> TalentNames.EFFECT_SCHWANZANGRIFF.equals(e.getName())).findFirst().orElse(null);
        assertThat(eff).isNotNull();
        assertThat(eff.getModifiers().get(0).getTargetStat()).isEqualTo(StatType.ATTACK_STEP);
        assertThat((int) eff.getModifiers().get(0).getValue()).isEqualTo(-2);
    }

    @Test
    void nonTailWeapon_throws() {
        stubMiss();
        Equipment sword = Equipment.builder().id(77L).name("Schwert").type(EquipmentType.WEAPON)
                .damageBonus(3).tailWeapon(false).build();
        attacker.getCharacter().getEquipment().add(sword);

        assertThatThrownBy(() -> combatService.performSchwanzangriff(1L, req(77L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Schwanzwaffe");
    }

    // --- Helpers ---

    private void stubMiss() {
        when(diceService.attributeToStep(10)).thenReturn(5);
        when(diceService.roll(anyInt())).thenReturn(RollResult.builder().total(3).diceExpression("W6").build());
        when(modifiers.getEffectiveValue(eq(defender), eq(StatType.PHYSICAL_DEFENSE), any())).thenReturn(99);
    }

    private SchwanzangriffRequest req(Long weaponId) {
        return SchwanzangriffRequest.builder()
                .sessionId(1L).actorCombatantId(10L).defenderCombatantId(20L)
                .weaponId(weaponId).bonusSteps(0).spendKarma(false).build();
    }

    private CombatantState combatant(long id, String name, Race race, int waffenloserKampfRank) {
        List<CharacterTalent> talents = new ArrayList<>();
        if (waffenloserKampfRank > 0) {
            TalentDefinition def = TalentDefinition.builder().id(99L).name("Waffenloser Kampf").attribute(AttributeType.DEXTERITY).build();
            talents.add(CharacterTalent.builder().id(99L).talentDefinition(def).rank(waffenloserKampfRank).build());
        }
        GameCharacter c = GameCharacter.builder()
                .id(id).name(name).race(race).dexterity(10).strength(10)
                .equipment(new ArrayList<>()).talents(talents)
                .skills(new ArrayList<>()).spells(new ArrayList<>())
                .build();
        return CombatantState.builder()
                .id(id).character(c).activeEffects(new ArrayList<>())
                .currentDamage(0).wounds(0).pendingRiposteAttackTotal(-1)
                .build();
    }
}
