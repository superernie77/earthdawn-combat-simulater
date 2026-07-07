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
 * Tests für die Arzt-Fertigkeit in CharacterService — zwei Behandlungsmodi:
 *  - VERLETZUNG: behandelt verlorene LP, 1× pro Erholungsprobe, Erfolg → +Rang auf den Erholungswurf.
 *  - WUNDE: versorgt eine Wunde (−1-Wundmalus bei Erholungsproben unterdrückt), mehrfach bis alle versorgt.
 * Beide: Wurf WAH-Stufe + Rang vs. MW 5; Verbandszeug wird auch bei Fehlschlag verbraucht.
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
    // Modus VERLETZUNG — verlorene LP behandeln (1× pro Erholungsprobe)
    // =========================================================================

    @Test
    void verletzung_success_grantsRankBonus_setsInjuryFlag_consumesVerbandszeug() {
        GameCharacter wounded = wounded(0, 12, 0); // keine Wunden nötig, 12 Schaden
        GameCharacter healer  = healerWithArzt(15, 3, 3);

        stubFindById(wounded, healer);
        when(stepRollService.attributeToStep(15)).thenReturn(6);
        stubRoll(9); // >= 5 → Erfolg

        ArztResult result = characterService.applyArzt(1L, 2L, "VERLETZUNG");

        assertThat(result.getMode()).isEqualTo("VERLETZUNG");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBonusGranted()).isEqualTo(3); // = Rang
        assertThat(result.getNewPendingBonus()).isEqualTo(3);
        assertThat(wounded.getPendingRecoveryBonus()).isEqualTo(3);
        assertThat(wounded.isArztInjuryTreated()).isTrue();
        assertThat(result.getVerbandszeugRemaining()).isEqualTo(2); // 3 → 2
        verify(characterRepo).save(wounded);
        verify(characterRepo).save(healer);
    }

    @Test
    void verletzung_success_accumulatesOnExistingPendingBonus() {
        GameCharacter wounded = wounded(0, 8, 5); // 5 Bonus schon vorhanden (z.B. Erholungstrank)
        GameCharacter healer  = healerWithArzt(10, 2, 1);

        stubFindById(wounded, healer);
        when(stepRollService.attributeToStep(10)).thenReturn(5);
        stubRoll(8);

        ArztResult result = characterService.applyArzt(1L, 2L, "VERLETZUNG");

        assertThat(result.getBonusGranted()).isEqualTo(2);
        assertThat(result.getNewPendingBonus()).isEqualTo(7); // 5 + 2
    }

    @Test
    void verletzung_alreadyTreated_throws_andConsumesNoVerbandszeug() {
        GameCharacter wounded = wounded(0, 8, 0);
        wounded.setArztInjuryTreated(true); // schon behandelt seit letzter Erholungsprobe
        GameCharacter healer  = healerWithArzt(10, 2, 3);

        stubFindById(wounded, healer);

        assertThatThrownBy(() -> characterService.applyArzt(1L, 2L, "VERLETZUNG"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bereits behandelt");
        assertThat(verbandszeugQty(healer)).isEqualTo(3); // Vorbedingung → nichts verbraucht
    }

    @Test
    void verletzung_noDamage_throws() {
        GameCharacter wounded = wounded(2, 0, 0); // Wunden, aber keine Verletzungen (0 Schaden)
        GameCharacter healer  = healerWithArzt(10, 2, 1);

        stubFindById(wounded, healer);

        assertThatThrownBy(() -> characterService.applyArzt(1L, 2L, "VERLETZUNG"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("keine Verletzungen");
    }

    @Test
    void verletzung_failure_noBonusNoFlag_butVerbandszeugConsumed() {
        GameCharacter wounded = wounded(0, 8, 0);
        GameCharacter healer  = healerWithArzt(10, 2, 3);

        stubFindById(wounded, healer);
        when(stepRollService.attributeToStep(10)).thenReturn(5);
        stubRoll(3); // < 5

        ArztResult result = characterService.applyArzt(1L, 2L, "VERLETZUNG");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getBonusGranted()).isZero();
        assertThat(wounded.getPendingRecoveryBonus()).isZero();
        assertThat(wounded.isArztInjuryTreated()).isFalse(); // Fehlschlag → erneuter Versuch erlaubt
        assertThat(result.getVerbandszeugRemaining()).isEqualTo(2); // trotzdem verbraucht
    }

    // =========================================================================
    // Modus WUNDE — Wundversorgung (mehrfach bis alle Wunden versorgt)
    // =========================================================================

    @Test
    void wunde_success_incrementsTreatedCounter_consumesVerbandszeug() {
        GameCharacter wounded = wounded(2, 0, 0);
        GameCharacter healer  = healerWithArzt(12, 2, 3);

        stubFindById(wounded, healer);
        when(stepRollService.attributeToStep(12)).thenReturn(5);
        stubRoll(7);

        ArztResult result = characterService.applyArzt(1L, 2L, "WUNDE");

        assertThat(result.getMode()).isEqualTo("WUNDE");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getWoundsTreated()).isEqualTo(1);
        assertThat(wounded.getArztWoundsTreated()).isEqualTo(1);
        assertThat(result.getBonusGranted()).isZero(); // kein Erholungsbonus im Wund-Modus
        assertThat(wounded.getPendingRecoveryBonus()).isZero();
        assertThat(result.getVerbandszeugRemaining()).isEqualTo(2);
    }

    @Test
    void wunde_multipleTimes_untilAllTreated_thenThrows() {
        GameCharacter wounded = wounded(2, 0, 0);
        GameCharacter healer  = healerWithArzt(12, 2, 5);

        stubFindById(wounded, healer);
        when(stepRollService.attributeToStep(12)).thenReturn(5);
        stubRoll(9);

        characterService.applyArzt(1L, 2L, "WUNDE"); // 1/2
        characterService.applyArzt(1L, 2L, "WUNDE"); // 2/2
        assertThat(wounded.getArztWoundsTreated()).isEqualTo(2);

        assertThatThrownBy(() -> characterService.applyArzt(1L, 2L, "WUNDE"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bereits versorgt");
        assertThat(verbandszeugQty(healer)).isEqualTo(3); // nur 2 verbraucht
    }

    @Test
    void wunde_noWounds_throws() {
        GameCharacter wounded = wounded(0, 10, 0);
        GameCharacter healer  = healerWithArzt(10, 2, 1);

        stubFindById(wounded, healer);

        assertThatThrownBy(() -> characterService.applyArzt(1L, 2L, "WUNDE"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("keine Wunden");
    }

    @Test
    void wunde_failure_counterUnchanged_butVerbandszeugConsumed() {
        GameCharacter wounded = wounded(2, 0, 0);
        GameCharacter healer  = healerWithArzt(10, 2, 3);

        stubFindById(wounded, healer);
        when(stepRollService.attributeToStep(10)).thenReturn(5);
        stubRoll(4); // < 5

        ArztResult result = characterService.applyArzt(1L, 2L, "WUNDE");

        assertThat(result.isSuccess()).isFalse();
        assertThat(wounded.getArztWoundsTreated()).isZero();
        assertThat(result.getVerbandszeugRemaining()).isEqualTo(2); // trotzdem verbraucht
    }

    // =========================================================================
    // Gemeinsame Mechanik — MW 5, Wurfstufe, Fehler-Cases
    // =========================================================================

    @Test
    void targetNumber_isFixed5() {
        GameCharacter wounded = wounded(3, 5, 0);
        GameCharacter healer  = healerWithArzt(12, 2, 2);

        stubFindById(wounded, healer);
        when(stepRollService.attributeToStep(12)).thenReturn(5);
        stubRoll(6);

        assertThat(characterService.applyArzt(1L, 2L, "WUNDE").getTargetNumber()).isEqualTo(5);
    }

    @Test
    void exactly5_isSuccess_below5_isFailure() {
        GameCharacter wounded = wounded(1, 5, 0);
        GameCharacter healer  = healerWithArzt(10, 1, 5);

        stubFindById(wounded, healer);
        when(stepRollService.attributeToStep(10)).thenReturn(5);

        stubRoll(5);
        assertThat(characterService.applyArzt(1L, 2L, "VERLETZUNG").isSuccess()).isTrue();

        wounded.setArztInjuryTreated(false); // zurücksetzen für zweiten Versuch
        stubRoll(4);
        assertThat(characterService.applyArzt(1L, 2L, "VERLETZUNG").isSuccess()).isFalse();
    }

    @Test
    void rollStep_equals_perStepPlusRank() {
        GameCharacter wounded = wounded(1, 5, 0);
        GameCharacter healer  = healerWithArzt(14, 3, 1); // WAH=14 → step 6, Rang 3 → rollStep 9

        stubFindById(wounded, healer);
        when(stepRollService.attributeToStep(14)).thenReturn(6);
        stubRoll(10);

        ArztResult result = characterService.applyArzt(1L, 2L, "WUNDE");

        assertThat(result.getPerStep()).isEqualTo(6);
        assertThat(result.getSkillRank()).isEqualTo(3);
        assertThat(result.getRollStep()).isEqualTo(9);
        verify(stepRollService).roll(9);
    }

    @Test
    void unknownMode_throws() {
        GameCharacter wounded = wounded(1, 5, 0);
        GameCharacter healer  = healerWithArzt(10, 1, 1);

        stubFindById(wounded, healer);

        assertThatThrownBy(() -> characterService.applyArzt(1L, 2L, "MASSAGE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unbekannter Arzt-Modus");
    }

    @Test
    void noVerbandszeug_throws() {
        GameCharacter wounded = wounded(2, 5, 0);
        GameCharacter healer  = healerWithArzt(10, 2, 0);

        stubFindById(wounded, healer);

        assertThatThrownBy(() -> characterService.applyArzt(1L, 2L, "WUNDE"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Verbandszeug");
    }

    @Test
    void healerHasNoArztSkill_throws() {
        GameCharacter wounded = wounded(2, 5, 0);
        GameCharacter healer  = GameCharacter.builder()
                .id(2L).name("Heiler").perception(10).toughness(10).wounds(0)
                .equipment(new ArrayList<>()).talents(new ArrayList<>())
                .skills(new ArrayList<>()).spells(new ArrayList<>())
                .build();

        stubFindById(wounded, healer);

        assertThatThrownBy(() -> characterService.applyArzt(1L, 2L, "WUNDE"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Arzt-Fertigkeit");
    }

    @Test
    void result_containsNamesAndMode() {
        GameCharacter wounded = wounded(1, 5, 0);
        GameCharacter healer  = healerWithArzt(10, 1, 1);

        stubFindById(wounded, healer);
        when(stepRollService.attributeToStep(10)).thenReturn(5);
        stubRoll(6);

        ArztResult result = characterService.applyArzt(1L, 2L, "wunde"); // case-insensitive

        assertThat(result.getWoundedName()).isEqualTo("Verwundeter");
        assertThat(result.getHealerName()).isEqualTo("Heiler");
        assertThat(result.getMode()).isEqualTo("WUNDE");
    }

    // =========================================================================
    // Hilfsmethoden
    // =========================================================================

    private GameCharacter wounded(int wounds, int currentDamage, int pendingBonus) {
        return GameCharacter.builder()
                .id(1L).name("Verwundeter")
                .wounds(wounds).currentDamage(currentDamage).toughness(10).perception(10)
                .pendingRecoveryBonus(pendingBonus)
                .equipment(new ArrayList<>()).talents(new ArrayList<>())
                .skills(new ArrayList<>()).spells(new ArrayList<>())
                .build();
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

    private int verbandszeugQty(GameCharacter healer) {
        return healer.getEquipment().stream()
                .filter(e -> e.getType() == EquipmentType.VERBANDSZEUG)
                .mapToInt(Equipment::getQuantity).sum();
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
