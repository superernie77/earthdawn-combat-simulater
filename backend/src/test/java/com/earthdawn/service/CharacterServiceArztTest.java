package com.earthdawn.service;

import com.earthdawn.dto.ArztResult;
import com.earthdawn.dto.RollResult;
import com.earthdawn.model.CharacterSkill;
import com.earthdawn.model.Equipment;
import com.earthdawn.model.GameCharacter;
import com.earthdawn.model.SkillDefinition;
import com.earthdawn.model.enums.AttributeType;
import com.earthdawn.model.enums.EquipmentType;
import com.earthdawn.repository.CharacterRepository;
import com.earthdawn.repository.SkillDefinitionRepository;
import com.earthdawn.repository.SpellDefinitionRepository;
import com.earthdawn.repository.TalentDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Tests für die Arzt-Fertigkeit in CharacterService (ED4-Spec: Verletzungen und Wunden).
 * Fester Mindestwurf 5, Verbandszeug-Verbrauch, Aufhebung des Wundabzugs.
 * Plain Mockito — kein Spring-Kontext.
 */
@ExtendWith(MockitoExtension.class)
class CharacterServiceArztTest {

    @Mock CharacterRepository characterRepo;
    @Mock TalentDefinitionRepository talentDefRepo;
    @Mock SkillDefinitionRepository skillDefRepo;
    @Mock SpellDefinitionRepository spellDefRepo;
    @Mock ModifierAggregator modifierAggregator;
    @Mock StepRollService stepRollService;

    @InjectMocks CharacterService characterService;

    @BeforeEach
    void setUp() {
        lenient().when(characterRepo.save(any(GameCharacter.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // =========================================================================
    // Erfolg
    // =========================================================================

    @Test
    void success_addsBonusAndNegatesWoundPenalty_andConsumesVerbandszeug() {
        GameCharacter wounded = wounded(2, 0);
        GameCharacter healer  = healerWithArzt(15, 3, 3); // WAH=15, Rang 3, 3 Verbandszeug

        stubFindById(wounded, healer);
        when(stepRollService.attributeToStep(15)).thenReturn(6);
        stubRoll(9); // >= 5 → Erfolg

        ArztResult result = characterService.applyArzt(1L, 2L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBonusGranted()).isEqualTo(3); // = Rang
        assertThat(result.getNewPendingBonus()).isEqualTo(3);
        assertThat(wounded.getPendingRecoveryBonus()).isEqualTo(3);
        assertThat(result.isWoundPenaltyNegated()).isTrue();
        assertThat(wounded.isArztWoundPenaltyNegated()).isTrue();
        assertThat(result.getVerbandszeugRemaining()).isEqualTo(2); // 3 → 2
        verify(characterRepo).save(wounded);
        verify(characterRepo).save(healer); // Verbandszeug-Verbrauch persistiert
    }

    @Test
    void success_accumulatesOnExistingPendingBonus() {
        GameCharacter wounded = woundedWithPendingBonus(1, 5);
        GameCharacter healer  = healerWithArzt(10, 2, 1);

        stubFindById(wounded, healer);
        when(stepRollService.attributeToStep(10)).thenReturn(5);
        stubRoll(8);

        ArztResult result = characterService.applyArzt(1L, 2L);

        assertThat(result.getBonusGranted()).isEqualTo(2);
        assertThat(result.getNewPendingBonus()).isEqualTo(7); // 5 + 2
    }

    // =========================================================================
    // Mindestwurf — fest 5 (nicht 6 × Wunden)
    // =========================================================================

    @Test
    void targetNumber_isFixed5_regardlessOfWounds() {
        GameCharacter wounded = wounded(3, 0);
        GameCharacter healer  = healerWithArzt(12, 2, 2);

        stubFindById(wounded, healer);
        when(stepRollService.attributeToStep(12)).thenReturn(5);
        stubRoll(6);

        ArztResult result = characterService.applyArzt(1L, 2L);

        assertThat(result.getTargetNumber()).isEqualTo(5);
        assertThat(result.getWounds()).isEqualTo(3);
    }

    @Test
    void exactly5_isSuccess() {
        GameCharacter wounded = wounded(2, 0);
        GameCharacter healer  = healerWithArzt(10, 1, 1);

        stubFindById(wounded, healer);
        when(stepRollService.attributeToStep(10)).thenReturn(5);
        stubRoll(5); // genau MW 5

        assertThat(characterService.applyArzt(1L, 2L).isSuccess()).isTrue();
    }

    @Test
    void below5_isFailure() {
        GameCharacter wounded = wounded(2, 0);
        GameCharacter healer  = healerWithArzt(10, 1, 1);

        stubFindById(wounded, healer);
        when(stepRollService.attributeToStep(10)).thenReturn(5);
        stubRoll(4); // < 5

        assertThat(characterService.applyArzt(1L, 2L).isSuccess()).isFalse();
    }

    // =========================================================================
    // Fehlschlag — Verbandszeug trotzdem verbraucht, kein Bonus/keine Wundpflege
    // =========================================================================

    @Test
    void failure_consumesVerbandszeug_butNoBonusOrWoundCare() {
        GameCharacter wounded = wounded(2, 0);
        GameCharacter healer  = healerWithArzt(10, 2, 3);

        stubFindById(wounded, healer);
        when(stepRollService.attributeToStep(10)).thenReturn(5);
        stubRoll(3); // < 5

        ArztResult result = characterService.applyArzt(1L, 2L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getBonusGranted()).isZero();
        assertThat(wounded.getPendingRecoveryBonus()).isZero();
        assertThat(wounded.isArztWoundPenaltyNegated()).isFalse();
        assertThat(result.isWoundPenaltyNegated()).isFalse();
        assertThat(result.getVerbandszeugRemaining()).isEqualTo(2); // trotzdem verbraucht
        verify(characterRepo).save(healer);
    }

    // =========================================================================
    // Verbandszeug-Pflicht
    // =========================================================================

    @Test
    void noVerbandszeug_throwsException() {
        GameCharacter wounded = wounded(2, 0);
        GameCharacter healer  = healerWithArzt(10, 2, 0); // 0 Verbandszeug → keins vorhanden

        stubFindById(wounded, healer);

        assertThatThrownBy(() -> characterService.applyArzt(1L, 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Verbandszeug");
    }

    // =========================================================================
    // Rollwurf-Details
    // =========================================================================

    @Test
    void rollStep_equals_perStepPlusRank() {
        GameCharacter wounded = wounded(1, 0);
        GameCharacter healer  = healerWithArzt(14, 3, 1); // WAH=14 → step 6, Rang 3 → rollStep 9

        stubFindById(wounded, healer);
        when(stepRollService.attributeToStep(14)).thenReturn(6);
        stubRoll(10);

        ArztResult result = characterService.applyArzt(1L, 2L);

        assertThat(result.getPerStep()).isEqualTo(6);
        assertThat(result.getSkillRank()).isEqualTo(3);
        assertThat(result.getRollStep()).isEqualTo(9);
        verify(stepRollService).roll(9);
    }

    @Test
    void result_containsHealerAndWoundedNames() {
        GameCharacter wounded = wounded(1, 0);
        GameCharacter healer  = healerWithArzt(10, 1, 1);

        stubFindById(wounded, healer);
        when(stepRollService.attributeToStep(10)).thenReturn(5);
        stubRoll(6);

        ArztResult result = characterService.applyArzt(1L, 2L);

        assertThat(result.getWoundedName()).isEqualTo("Verwundeter");
        assertThat(result.getHealerName()).isEqualTo("Heiler");
    }

    // =========================================================================
    // Fehler-Cases
    // =========================================================================

    @Test
    void noWounds_throwsException() {
        GameCharacter wounded = wounded(0, 0);
        GameCharacter healer  = healerWithArzt(10, 1, 1);

        stubFindById(wounded, healer);

        assertThatThrownBy(() -> characterService.applyArzt(1L, 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("keine Wunden");
    }

    @Test
    void healerHasNoArztSkill_throwsException() {
        GameCharacter wounded = wounded(2, 0);
        GameCharacter healer  = character(10, 0);
        healer.setId(2L);
        healer.setName("Heiler");

        stubFindById(wounded, healer);

        assertThatThrownBy(() -> characterService.applyArzt(1L, 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Arzt-Fertigkeit");
    }

    // =========================================================================
    // Hilfsmethoden
    // =========================================================================

    private GameCharacter wounded(int wounds, int pendingBonus) {
        return GameCharacter.builder()
                .id(1L).name("Verwundeter")
                .wounds(wounds).toughness(10).perception(10)
                .pendingRecoveryBonus(pendingBonus)
                .equipment(new ArrayList<>()).talents(new ArrayList<>())
                .skills(new ArrayList<>()).spells(new ArrayList<>())
                .build();
    }

    private GameCharacter woundedWithPendingBonus(int wounds, int pendingBonus) {
        return wounded(wounds, pendingBonus);
    }

    private GameCharacter healerWithArzt(int perception, int arztRank, int verbandszeugQty) {
        SkillDefinition arztDef = SkillDefinition.builder()
                .id(99L).name("Arzt").attribute(AttributeType.PERCEPTION).build();
        CharacterSkill arztSkill = CharacterSkill.builder()
                .id(1L).skillDefinition(arztDef).rank(arztRank).build();

        List<Equipment> equip = new ArrayList<>();
        if (verbandszeugQty > 0) {
            equip.add(Equipment.builder()
                    .id(50L).name("Verbandszeug").type(EquipmentType.VERBANDSZEUG)
                    .quantity(verbandszeugQty).build());
        }

        return GameCharacter.builder()
                .id(2L).name("Heiler")
                .perception(perception).toughness(10).wounds(0)
                .equipment(equip).talents(new ArrayList<>())
                .skills(new ArrayList<>(List.of(arztSkill))).spells(new ArrayList<>())
                .build();
    }

    private GameCharacter character(int perception, int wounds) {
        return GameCharacter.builder()
                .id(99L).name("Unbekannt")
                .perception(perception).wounds(wounds).toughness(10)
                .equipment(new ArrayList<>()).talents(new ArrayList<>())
                .skills(new ArrayList<>()).spells(new ArrayList<>())
                .build();
    }

    private void stubFindById(GameCharacter wounded, GameCharacter healer) {
        when(characterRepo.findById(1L)).thenReturn(Optional.of(wounded));
        when(characterRepo.findById(2L)).thenReturn(Optional.of(healer));
    }

    private void stubRoll(int total) {
        when(stepRollService.roll(anyInt()))
                .thenReturn(RollResult.builder().total(total).diceExpression("W6").build());
    }
}
