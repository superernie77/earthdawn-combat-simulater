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
import static org.mockito.Mockito.*;

/**
 * Tests für Angriffe mit Waffen-Fertigkeit (CombatService.performAttack, skillId).
 * Fertigkeiten geben den Rang auf die Angriffsstufe, erlauben aber KEIN Karma.
 * Alle Tests nutzen einen Fehlschlag (hohe KV), um den Schadenszweig zu überspringen.
 */
@ExtendWith(MockitoExtension.class)
class CombatServiceWeaponSkillTest {

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
        attacker = combatant(10L, "Angreifer");
        defender = combatant(20L, "Ziel");
        attacker.setCurrentKarma(5);

        session = CombatSession.builder()
                .id(1L).name("Test").round(1)
                .phase(CombatPhase.ACTION)
                .combatants(new ArrayList<>(List.of(attacker, defender)))
                .log(new ArrayList<>())
                .build();
        lenient().when(sessionRepo.findById(1L)).thenReturn(Optional.of(session));
        lenient().when(sessionRepo.save(any(CombatSession.class))).thenAnswer(inv -> inv.getArgument(0));

        // Basis-Angriffsstufe und hohe KV (Fehlschlag)
        lenient().when(modifiers.getEffectiveValue(eq(attacker), eq(StatType.ATTACK_STEP), any())).thenReturn(5);
        lenient().when(modifiers.getEffectiveValue(eq(defender), eq(StatType.PHYSICAL_DEFENSE), any())).thenReturn(99);
        lenient().when(diceService.roll(anyInt()))
                .thenReturn(RollResult.builder().total(1).diceExpression("W6").build());
    }

    @Test
    void skillAttack_addsRank_andSpendsNoKarma() {
        attacker.getCharacter().getSkills().add(skill(14L, "Nahkampfwaffen", 5));

        AttackActionRequest req = new AttackActionRequest();
        req.setSessionId(1L);
        req.setAttackerCombatantId(10L);
        req.setDefenderCombatantId(20L);
        req.setActionType(ActionType.MELEE_ATTACK);
        req.setSkillId(14L);
        req.setBonusSteps(0);
        req.setSpendKarma(true); // spendKarma true, muss ignoriert werden

        CombatActionResult result = combatService.performAttack(req);

        assertThat(result.getAttackStep()).isEqualTo(10);   // Basis 5 + Fertigkeitsrang 5
        assertThat(result.getKarmaRoll()).isNull();          // kein Karma trotz spendKarma=true
        assertThat(attacker.getCurrentKarma()).isEqualTo(5); // Karma unverändert
        assertThat(result.isHit()).isFalse();
    }

    @Test
    void talentAttack_spendsKarma_forContrast() {
        TalentDefinition def = TalentDefinition.builder()
                .id(60L).name("Nahkampfwaffen").attribute(AttributeType.DEXTERITY).attackTalent(true).build();
        attacker.getCharacter().getTalents().add(
                CharacterTalent.builder().id(2L).talentDefinition(def).rank(5).build());

        AttackActionRequest req = new AttackActionRequest();
        req.setSessionId(1L);
        req.setAttackerCombatantId(10L);
        req.setDefenderCombatantId(20L);
        req.setActionType(ActionType.MELEE_ATTACK);
        req.setTalentId(60L);
        req.setBonusSteps(0);
        req.setSpendKarma(true);

        CombatActionResult result = combatService.performAttack(req);

        assertThat(result.getAttackStep()).isEqualTo(10);    // Basis 5 + Talentrang 5
        assertThat(result.getKarmaRoll()).isNotNull();        // Talent: Karma erlaubt
        assertThat(attacker.getCurrentKarma()).isEqualTo(4);  // Karma verbraucht
    }

    // --- Helpers ---

    private CharacterSkill skill(long defId, String name, int rank) {
        SkillDefinition def = SkillDefinition.builder()
                .id(defId).name(name).attribute(AttributeType.DEXTERITY).build();
        return CharacterSkill.builder().id(defId).skillDefinition(def).rank(rank).build();
    }

    private CombatantState combatant(long id, String name) {
        GameCharacter c = GameCharacter.builder()
                .id(id).name(name).dexterity(10).strength(10)
                .equipment(new ArrayList<>()).talents(new ArrayList<>())
                .skills(new ArrayList<>()).spells(new ArrayList<>())
                .build();
        return CombatantState.builder()
                .id(id).character(c).activeEffects(new ArrayList<>())
                .currentDamage(0).wounds(0)
                .build();
    }
}
