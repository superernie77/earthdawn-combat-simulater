package com.earthdawn.service;

import com.earthdawn.dto.DrinkPotionResult;
import com.earthdawn.dto.RecoveryTestResult;
import com.earthdawn.dto.RollResult;
import com.earthdawn.model.Equipment;
import com.earthdawn.model.GameCharacter;
import com.earthdawn.model.enums.EquipmentType;
import com.earthdawn.repository.CharacterRepository;
import com.earthdawn.repository.SkillDefinitionRepository;
import com.earthdawn.repository.SpellDefinitionRepository;
import com.earthdawn.repository.TalentDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for recovery test (Erholungsprobe) logic in CharacterService.
 * Plain Mockito — no Spring context needed.
 */
@ExtendWith(MockitoExtension.class)
class CharacterServiceRecoveryTest {

    @Mock CharacterRepository characterRepo;
    @Mock TalentDefinitionRepository talentDefRepo;
    @Mock SkillDefinitionRepository skillDefRepo;
    @Mock SpellDefinitionRepository spellDefRepo;
    @Mock ModifierAggregator modifierAggregator;
    @Mock StepRollService stepRollService;

    @InjectMocks
    CharacterService characterService;

    @BeforeEach
    void setUp() {
        lenient().when(characterRepo.save(any(GameCharacter.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // =========================================================================
    // ZÄH → max recovery tests per day
    // =========================================================================

    @ParameterizedTest(name = "ZÄH={0} → maxTests={1}")
    @CsvSource({
        "1,  1",   // Untergrenze Bereich 1–6
        "6,  1",   // Grenzwert 6 → noch 1
        "7,  2",   // Grenzwert 7 → 2
        "12, 2",
        "13, 3",
        "18, 3",
        "19, 4",
        "24, 4",
        "25, 5",
        "30, 5",
    })
    void recoveryTestsMax_followsZähigkeitTable(int toughness, int expectedMax) {
        GameCharacter c = character(toughness, 0, 10, null);
        stubFindById(c);
        stubAttrToStep(toughness, 5);
        stubRoll(5);

        RecoveryTestResult result = characterService.performRecoveryTest(1L);

        assertThat(result.getRecoveryTestsMax()).isEqualTo(expectedMax);
    }

    // =========================================================================
    // performRecoveryTest — reguläre Erholungsprobe
    // =========================================================================

    @Test
    void nullRemaining_treatedAsFull() {
        // null = uninitialisiert = voll (= max)
        GameCharacter c = character(10, 0, 8, null);
        stubFindById(c);
        stubAttrToStep(10, 5);
        stubRoll(4);

        RecoveryTestResult result = characterService.performRecoveryTest(1L);

        // ZÄH=10 → max=2; null → startet bei 2, dekrementiert auf 1
        assertThat(result.getRecoveryTestsMax()).isEqualTo(2);
        assertThat(result.getRecoveryTestsRemaining()).isEqualTo(1);
    }

    @Test
    void normalTest_decrementsRemainingByOne() {
        GameCharacter c = character(10, 0, 8, 2);
        stubFindById(c);
        stubAttrToStep(10, 5);
        stubRoll(6);

        RecoveryTestResult result = characterService.performRecoveryTest(1L);

        assertThat(result.getRecoveryTestsRemaining()).isEqualTo(1);
    }

    @Test
    void normalTest_lastSlot_decrementsToZero() {
        GameCharacter c = character(10, 0, 8, 1);
        stubFindById(c);
        stubAttrToStep(10, 5);
        stubRoll(3);

        RecoveryTestResult result = characterService.performRecoveryTest(1L);

        assertThat(result.getRecoveryTestsRemaining()).isEqualTo(0);
    }

    @Test
    void normalTest_noSlotsLeft_throwsException() {
        GameCharacter c = character(10, 0, 8, 0);
        stubFindById(c);

        assertThatThrownBy(() -> characterService.performRecoveryTest(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Keine Erholungsproben");
    }

    @Test
    void normalTest_healedEqualsRollTotal_whenLessThanDamage() {
        GameCharacter c = character(10, 0, 15, 2);
        stubFindById(c);
        stubAttrToStep(10, 5);
        stubRoll(8);

        RecoveryTestResult result = characterService.performRecoveryTest(1L);

        assertThat(result.getHealed()).isEqualTo(8);
        assertThat(result.getRemainingDamage()).isEqualTo(7);
    }

    @Test
    void healing_clampsToCurrentDamage() {
        GameCharacter c = character(10, 0, 3, 2);
        stubFindById(c);
        stubAttrToStep(10, 5);
        stubRoll(20);

        RecoveryTestResult result = characterService.performRecoveryTest(1L);

        assertThat(result.getHealed()).isEqualTo(3);
        assertThat(result.getRemainingDamage()).isEqualTo(0);
    }

    @Test
    void healing_withZeroDamage_healsNothing() {
        GameCharacter c = character(10, 0, 0, 2);
        stubFindById(c);
        stubAttrToStep(10, 5);
        stubRoll(8);

        RecoveryTestResult result = characterService.performRecoveryTest(1L);

        assertThat(result.getHealed()).isEqualTo(0);
    }

    // =========================================================================
    // Wunden-Abzug
    // =========================================================================

    @Test
    void woundPenalty_subtractedFromRollStep() {
        // ZÄH=10 → step=5, Wunden=2 → rollStep=3
        GameCharacter c = character(10, 2, 12, 2);
        stubFindById(c);
        stubAttrToStep(10, 5);
        stubRoll(5);

        RecoveryTestResult result = characterService.performRecoveryTest(1L);

        assertThat(result.getToughnessStep()).isEqualTo(5);
        assertThat(result.getWoundPenalty()).isEqualTo(2);
        assertThat(result.getRollStep()).isEqualTo(3);
    }

    @Test
    void woundPenalty_clampsRollStepToMinimumOne() {
        // ZÄH=7 → step=4, Wunden=10 → wäre -6, wird auf 1 geklemmt
        GameCharacter c = character(7, 10, 20, 2);
        stubFindById(c);
        stubAttrToStep(7, 4);
        stubRoll(3);

        RecoveryTestResult result = characterService.performRecoveryTest(1L);

        assertThat(result.getRollStep()).isEqualTo(1);
    }

    // =========================================================================
    // Arzt-Wundpflege — hebt den Wundabzug für eine Erholungsprobe auf
    // =========================================================================

    @Test
    void arztWoundCare_negatesWoundPenalty_andConsumesFlag() {
        // ZÄH=10 → step 5, Wunden=3, aber Wundpflege aktiv → woundPenalty 0 → rollStep 5
        GameCharacter c = character(10, 3, 12, 2);
        c.setArztWoundPenaltyNegated(true);
        stubFindById(c);
        stubAttrToStep(10, 5);
        stubRoll(8);

        RecoveryTestResult result = characterService.performRecoveryTest(1L);

        assertThat(result.getWoundPenalty()).isZero();
        assertThat(result.getRollStep()).isEqualTo(5);
        assertThat(c.isArztWoundPenaltyNegated()).isFalse(); // verbraucht
    }

    @Test
    void arztWoundCare_appliesOnlyToNextTest() {
        // Erste Probe ohne Abzug (Pflege aktiv), zweite wieder mit Abzug
        GameCharacter c = character(10, 2, 40, 2);
        c.setArztWoundPenaltyNegated(true);
        stubFindById(c);
        stubAttrToStep(10, 5);
        stubRoll(6);

        RecoveryTestResult first  = characterService.performRecoveryTest(1L);
        RecoveryTestResult second = characterService.performRecoveryTest(1L);

        assertThat(first.getWoundPenalty()).isZero();      // Pflege greift
        assertThat(second.getWoundPenalty()).isEqualTo(2); // danach wieder Abzug
    }

    // =========================================================================
    // pendingRecoveryBonus — Erholungstrank-Bonus auf reguläre Probe
    // =========================================================================

    @Test
    void normalTest_appliesPendingBonus_andClearsIt() {
        // Charakter hat bereits einen ausstehenden Bonus durch Erholungstrank
        GameCharacter c = characterWithPendingBonus(10, 0, 12, 2, 7);
        stubFindById(c);
        stubAttrToStep(10, 5);
        stubRoll(10); // wird mit step 5+7=12 gerollt

        RecoveryTestResult result = characterService.performRecoveryTest(1L);

        assertThat(result.getBonusSteps()).isEqualTo(7);
        assertThat(result.getRollStep()).isEqualTo(5);
        assertThat(c.getPendingRecoveryBonus()).isEqualTo(0); // danach gelöscht
    }

    @Test
    void normalTest_withoutPendingBonus_bonusStepsIsZero() {
        GameCharacter c = character(10, 0, 8, 2); // kein Pending-Bonus
        stubFindById(c);
        stubAttrToStep(10, 5);
        stubRoll(5);

        RecoveryTestResult result = characterService.performRecoveryTest(1L);

        assertThat(result.getBonusSteps()).isEqualTo(0);
    }

    @Test
    void normalTest_potionNameIsNull() {
        GameCharacter c = character(10, 0, 8, 2);
        stubFindById(c);
        stubAttrToStep(10, 5);
        stubRoll(5);

        RecoveryTestResult result = characterService.performRecoveryTest(1L);

        assertThat(result.getPotionName()).isNull();
        assertThat(result.isUsedExtraSlot()).isFalse();
    }

    // =========================================================================
    // drinkPotion — Erholungstrank (extraRecovery=false)
    // =========================================================================

    @Test
    void erholungstrank_setsPendingBonus_noRoll() {
        Equipment trank = potion(42L, 7, false, 3);
        GameCharacter c = character(10, 0, 12, 2, trank);
        stubFindById(c);

        DrinkPotionResult result = characterService.drinkPotion(1L, 42L);

        assertThat(result.isExtraRecovery()).isFalse();
        assertThat(result.getPendingBonus()).isEqualTo(7);
        assertThat(result.getRecovery()).isNull();
        assertThat(result.getPotionName()).isEqualTo("Erholungstrank");
        assertThat(trank.getQuantity()).isEqualTo(2); // dekrementiert
        assertThat(c.getPendingRecoveryBonus()).isEqualTo(7); // auf Charakter gesetzt
        verifyNoInteractions(stepRollService); // kein Würfelwurf
    }

    @Test
    void erholungstrank_accumulatesBonus_whenDrunkMultipleTimes() {
        Equipment trank = potion(42L, 7, false, 2);
        GameCharacter c = characterWithPendingBonus(10, 0, 12, 2, 7, trank); // already 7 pending
        stubFindById(c);

        DrinkPotionResult result = characterService.drinkPotion(1L, 42L);

        assertThat(result.getPendingBonus()).isEqualTo(14); // 7 + 7
        assertThat(c.getPendingRecoveryBonus()).isEqualTo(14);
    }

    @Test
    void erholungstrank_worksEvenWithZeroSlotsRemaining() {
        // Erholungstrank braucht keinen Slot — setzt nur Pending-Bonus
        Equipment trank = potion(42L, 7, false, 1);
        GameCharacter c = character(10, 0, 8, 0, trank); // 0 Slots übrig
        stubFindById(c);

        DrinkPotionResult result = characterService.drinkPotion(1L, 42L);

        assertThat(result.getPendingBonus()).isEqualTo(7); // trotzdem gesetzt
        verifyNoInteractions(stepRollService);
    }

    // =========================================================================
    // drinkPotion — Heiltrank (extraRecovery=true)
    // =========================================================================

    @Test
    void heiltrank_rollsImmediately_doesNotConsumeSlot() {
        Equipment heiltrank = potion(99L, 7, true, 1);
        GameCharacter c = character(10, 0, 12, 1, heiltrank); // 1 Slot übrig
        stubFindById(c);
        stubAttrToStep(10, 5);
        stubRoll(9);

        DrinkPotionResult result = characterService.drinkPotion(1L, 99L);

        assertThat(result.isExtraRecovery()).isTrue();
        assertThat(result.getRecovery()).isNotNull();
        assertThat(result.getRecovery().isUsedExtraSlot()).isTrue();
        assertThat(result.getRecovery().getBonusSteps()).isEqualTo(7);
        assertThat(result.getRecovery().getRecoveryTestsRemaining()).isEqualTo(1); // unverändert
        assertThat(heiltrank.getQuantity()).isEqualTo(0);
    }

    @Test
    void heiltrank_worksEvenWithZeroSlotsRemaining() {
        Equipment heiltrank = potion(99L, 7, true, 1);
        GameCharacter c = character(10, 0, 10, 0, heiltrank); // 0 Slots, Heiltrank ok
        stubFindById(c);
        stubAttrToStep(10, 5);
        stubRoll(8);

        DrinkPotionResult result = characterService.drinkPotion(1L, 99L);

        assertThat(result.getRecovery()).isNotNull();
        assertThat(result.getRecovery().getHealed()).isEqualTo(8);
        assertThat(result.getRecovery().getRecoveryTestsRemaining()).isEqualTo(0); // weiterhin 0
    }

    @Test
    void heiltrank_healsCorrectly() {
        Equipment heiltrank = potion(99L, 7, true, 2);
        GameCharacter c = character(10, 0, 20, 2, heiltrank);
        stubFindById(c);
        stubAttrToStep(10, 5);
        stubRoll(11); // step 5+7=12, ergibt 11

        DrinkPotionResult result = characterService.drinkPotion(1L, 99L);

        assertThat(result.getRecovery().getHealed()).isEqualTo(11);
        assertThat(result.getRecovery().getRemainingDamage()).isEqualTo(9); // 20 - 11
    }

    // =========================================================================
    // drinkPotion — Fehler-Cases
    // =========================================================================

    @Test
    void potion_notFound_throwsException() {
        GameCharacter c = character(10, 0, 8, 2); // kein Trank im Inventar
        stubFindById(c);

        assertThatThrownBy(() -> characterService.drinkPotion(1L, 999L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Trank nicht gefunden");
    }

    @Test
    void potion_quantityZero_throwsException() {
        Equipment depleted = potion(42L, 7, false, 0); // qty=0
        GameCharacter c = character(10, 0, 8, 2, depleted);
        stubFindById(c);

        assertThatThrownBy(() -> characterService.drinkPotion(1L, 42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Trank nicht gefunden oder aufgebraucht");
    }

    // =========================================================================
    // resetRecoveryTests
    // =========================================================================

    @Test
    void resetRecoveryTests_setsRemainingToMax() {
        GameCharacter c = character(10, 0, 5, 0); // ZÄH=10 → max=2
        stubFindById(c);

        GameCharacter saved = characterService.resetRecoveryTests(1L);

        assertThat(saved.getRecoveryTestsRemaining()).isEqualTo(2);
    }

    @Test
    void resetRecoveryTests_highZäh_setsCorrectMax() {
        GameCharacter c = character(20, 0, 5, 1); // ZÄH=20 → max=4
        stubFindById(c);

        GameCharacter saved = characterService.resetRecoveryTests(1L);

        assertThat(saved.getRecoveryTestsRemaining()).isEqualTo(4);
    }

    @Test
    void resetRecoveryTests_savesCharacter() {
        GameCharacter c = character(10, 0, 5, 0);
        stubFindById(c);

        characterService.resetRecoveryTests(1L);

        verify(characterRepo).save(c);
    }

    // =========================================================================
    // Result-DTO Vollständigkeit
    // =========================================================================

    @Test
    void result_containsCorrectRollAndDiceInfo() {
        GameCharacter c = character(10, 0, 20, 2);
        stubFindById(c);
        stubAttrToStep(10, 5);
        RollResult roll = RollResult.builder().step(5).total(12).diceExpression("2W6").build();
        when(stepRollService.roll(5)).thenReturn(roll);

        RecoveryTestResult result = characterService.performRecoveryTest(1L);

        assertThat(result.getRoll()).isEqualTo(roll);
        assertThat(result.getRoll().getDiceExpression()).isEqualTo("2W6");
        assertThat(result.getHealed()).isEqualTo(12);
    }

    // =========================================================================
    // Helper-Methoden
    // =========================================================================

    private GameCharacter character(int toughness, int wounds, int currentDamage, Integer remaining) {
        return GameCharacter.builder()
                .id(1L).name("Tester")
                .toughness(toughness).wounds(wounds).currentDamage(currentDamage)
                .recoveryTestsRemaining(remaining)
                .equipment(new ArrayList<>()).talents(new ArrayList<>())
                .skills(new ArrayList<>()).spells(new ArrayList<>())
                .build();
    }

    private GameCharacter character(int toughness, int wounds, int currentDamage, Integer remaining, Equipment... potions) {
        GameCharacter c = character(toughness, wounds, currentDamage, remaining);
        c.getEquipment().addAll(List.of(potions));
        return c;
    }

    private GameCharacter characterWithPendingBonus(int toughness, int wounds, int currentDamage, Integer remaining, int pendingBonus) {
        GameCharacter c = character(toughness, wounds, currentDamage, remaining);
        c.setPendingRecoveryBonus(pendingBonus);
        return c;
    }

    private GameCharacter characterWithPendingBonus(int toughness, int wounds, int currentDamage, Integer remaining, int pendingBonus, Equipment... potions) {
        GameCharacter c = characterWithPendingBonus(toughness, wounds, currentDamage, remaining, pendingBonus);
        c.getEquipment().addAll(List.of(potions));
        return c;
    }

    private Equipment potion(Long id, int healStep, boolean extraRecovery, int quantity) {
        return Equipment.builder()
                .id(id).name(extraRecovery ? "Heiltrank" : "Erholungstrank")
                .type(EquipmentType.POTION)
                .healStep(healStep).extraRecovery(extraRecovery).quantity(quantity)
                .build();
    }

    private void stubFindById(GameCharacter c) {
        when(characterRepo.findById(1L)).thenReturn(Optional.of(c));
    }

    private void stubAttrToStep(int attr, int step) {
        when(stepRollService.attributeToStep(attr)).thenReturn(step);
    }

    private void stubRoll(int total) {
        when(stepRollService.roll(anyInt()))
                .thenReturn(RollResult.builder().total(total).diceExpression("W6").build());
    }
}
