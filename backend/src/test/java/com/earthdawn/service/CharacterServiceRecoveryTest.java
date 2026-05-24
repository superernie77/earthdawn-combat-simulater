package com.earthdawn.service;

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

    // --- ZÄH → max recovery tests per day ---

    @ParameterizedTest(name = "ZÄH={0} → maxTests={1}")
    @CsvSource({
        "1,  1",   // floor of range 1–6
        "6,  1",   // boundary 6 → still 1
        "7,  2",   // boundary 7 → 2
        "12, 2",   // boundary 12 → still 2
        "13, 3",   // boundary 13 → 3
        "18, 3",
        "19, 4",
        "24, 4",
        "25, 5",
        "30, 5",
    })
    void recoveryTestsMax_followsZähigkeitTable(int toughness, int expectedMax) {
        GameCharacter c = character(toughness, 0, 10, null);
        stubFindById(c);
        stubRoll(5);
        when(stepRollService.attributeToStep(toughness)).thenReturn(5);

        RecoveryTestResult result = characterService.performRecoveryTest(1L, null);

        assertThat(result.getRecoveryTestsMax()).isEqualTo(expectedMax);
    }

    // --- NULL remaining treated as full (max) ---

    @Test
    void nullRemaining_treatedAsFull() {
        GameCharacter c = character(10, 0, 8, null); // null = uninitialized = full
        stubFindById(c);
        stubAttrToStep(10, 5);
        stubRoll(4);

        RecoveryTestResult result = characterService.performRecoveryTest(1L, null);

        // ZÄH=10 → max=2; null means full, so starts at 2, decrements to 1
        assertThat(result.getRecoveryTestsMax()).isEqualTo(2);
        assertThat(result.getRecoveryTestsRemaining()).isEqualTo(1);
    }

    // --- Normal test: slot consumption ---

    @Test
    void normalTest_decrementsRemainingByOne() {
        GameCharacter c = character(10, 0, 8, 2); // 2 remaining
        stubFindById(c);
        stubAttrToStep(10, 5);
        stubRoll(6);

        RecoveryTestResult result = characterService.performRecoveryTest(1L, null);

        assertThat(result.getRecoveryTestsRemaining()).isEqualTo(1);
    }

    @Test
    void normalTest_lastSlot_decrementsToZero() {
        GameCharacter c = character(10, 0, 8, 1); // 1 remaining
        stubFindById(c);
        stubAttrToStep(10, 5);
        stubRoll(3);

        RecoveryTestResult result = characterService.performRecoveryTest(1L, null);

        assertThat(result.getRecoveryTestsRemaining()).isEqualTo(0);
    }

    @Test
    void normalTest_noSlotsLeft_throwsException() {
        GameCharacter c = character(10, 0, 8, 0); // 0 remaining
        stubFindById(c);

        assertThatThrownBy(() -> characterService.performRecoveryTest(1L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Keine Erholungsproben");
    }

    // --- Healing amount ---

    @Test
    void normalTest_healedEqualsRollTotal_whenLessThanDamage() {
        GameCharacter c = character(10, 0, 15, 2);
        stubFindById(c);
        stubAttrToStep(10, 5);
        stubRoll(8);

        RecoveryTestResult result = characterService.performRecoveryTest(1L, null);

        assertThat(result.getHealed()).isEqualTo(8);
        assertThat(result.getRemainingDamage()).isEqualTo(7); // 15 - 8
    }

    @Test
    void healing_clampsToCurrentDamage() {
        GameCharacter c = character(10, 0, 3, 2); // only 3 damage
        stubFindById(c);
        stubAttrToStep(10, 5);
        stubRoll(20); // roll 20, but only 3 damage to heal

        RecoveryTestResult result = characterService.performRecoveryTest(1L, null);

        assertThat(result.getHealed()).isEqualTo(3);
        assertThat(result.getRemainingDamage()).isEqualTo(0);
    }

    @Test
    void healing_withZeroDamage_healsNothing() {
        GameCharacter c = character(10, 0, 0, 2); // no damage
        stubFindById(c);
        stubAttrToStep(10, 5);
        stubRoll(8);

        RecoveryTestResult result = characterService.performRecoveryTest(1L, null);

        assertThat(result.getHealed()).isEqualTo(0);
        assertThat(result.getRemainingDamage()).isEqualTo(0);
    }

    // --- Wound penalty ---

    @Test
    void woundPenalty_subtractedFromRollStep() {
        // ZÄH=10 → attrStep=5, wounds=2 → rollStep=3
        GameCharacter c = character(10, 2, 12, 2);
        stubFindById(c);
        stubAttrToStep(10, 5);
        stubRoll(5);

        RecoveryTestResult result = characterService.performRecoveryTest(1L, null);

        assertThat(result.getToughnessStep()).isEqualTo(5);
        assertThat(result.getWoundPenalty()).isEqualTo(2);
        assertThat(result.getRollStep()).isEqualTo(3);
    }

    @Test
    void woundPenalty_clampsRollStepToMinimumOne() {
        // ZÄH=7 → attrStep=4, wounds=10 → would be -6, clamped to 1
        GameCharacter c = character(7, 10, 20, 2);
        stubFindById(c);
        stubAttrToStep(7, 4);
        stubRoll(3);

        RecoveryTestResult result = characterService.performRecoveryTest(1L, null);

        assertThat(result.getRollStep()).isEqualTo(1);
    }

    // --- Erholungstrank (extraRecovery=false): uses a slot, adds bonus steps ---

    @Test
    void erholungstrank_usesSlotAndAddsBonusSteps() {
        Equipment trank = potion(42L, 7, false, 3); // Erholungstrank: +7, qty=3
        GameCharacter c = character(10, 0, 12, 2, trank);
        stubFindById(c);
        stubAttrToStep(10, 5);
        stubRoll(10); // rolled with step 5+7=12

        RecoveryTestResult result = characterService.performRecoveryTest(1L, 42L);

        assertThat(result.isUsedExtraSlot()).isFalse();
        assertThat(result.getBonusSteps()).isEqualTo(7);
        assertThat(result.getRollStep()).isEqualTo(5);
        assertThat(result.getRecoveryTestsRemaining()).isEqualTo(1); // 2 → 1
        assertThat(result.getPotionName()).isEqualTo("Erholungstrank");
        assertThat(trank.getQuantity()).isEqualTo(2); // decremented
    }

    @Test
    void erholungstrank_noSlotsLeft_throwsException() {
        Equipment trank = potion(42L, 7, false, 2);
        GameCharacter c = character(10, 0, 8, 0, trank); // 0 remaining
        stubFindById(c);

        assertThatThrownBy(() -> characterService.performRecoveryTest(1L, 42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Keine Erholungsproben");
    }

    // --- Heiltrank (extraRecovery=true): extra slot, doesn't consume daily tests ---

    @Test
    void heiltrank_doesNotConsumeSlot() {
        Equipment heiltrank = potion(99L, 7, true, 1); // Heiltrank: extra slot
        GameCharacter c = character(10, 0, 12, 1, heiltrank); // 1 remaining
        stubFindById(c);
        stubAttrToStep(10, 5);
        stubRoll(9);

        RecoveryTestResult result = characterService.performRecoveryTest(1L, 99L);

        assertThat(result.isUsedExtraSlot()).isTrue();
        assertThat(result.getRecoveryTestsRemaining()).isEqualTo(1); // unchanged
        assertThat(heiltrank.getQuantity()).isEqualTo(0); // decremented
    }

    @Test
    void heiltrank_worksEvenWithZeroSlotsRemaining() {
        Equipment heiltrank = potion(99L, 7, true, 1);
        GameCharacter c = character(10, 0, 10, 0, heiltrank); // 0 daily slots, but Heiltrank ok
        stubFindById(c);
        stubAttrToStep(10, 5);
        stubRoll(8);

        RecoveryTestResult result = characterService.performRecoveryTest(1L, 99L);

        assertThat(result.isUsedExtraSlot()).isTrue();
        assertThat(result.getRecoveryTestsRemaining()).isEqualTo(0); // still 0
        assertThat(result.getHealed()).isEqualTo(8);
    }

    // --- Potion error cases ---

    @Test
    void potion_notFound_throwsException() {
        GameCharacter c = character(10, 0, 8, 2); // no potions in equipment
        stubFindById(c);

        assertThatThrownBy(() -> characterService.performRecoveryTest(1L, 999L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Trank nicht gefunden");
    }

    @Test
    void potion_quantityZero_throwsException() {
        Equipment depleted = potion(42L, 7, false, 0); // qty=0
        GameCharacter c = character(10, 0, 8, 2, depleted);
        stubFindById(c);

        assertThatThrownBy(() -> characterService.performRecoveryTest(1L, 42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Trank nicht gefunden oder aufgebraucht");
    }

    // --- resetRecoveryTests ---

    @Test
    void resetRecoveryTests_setsRemainingToMax() {
        GameCharacter c = character(10, 0, 5, 0); // ZÄH=10 → max=2, currently 0
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

    // --- Result DTO fields ---

    @Test
    void result_containsCorrectRollAndDiceInfo() {
        GameCharacter c = character(10, 0, 20, 2);
        stubFindById(c);
        stubAttrToStep(10, 5);
        RollResult roll = RollResult.builder().step(5).total(12).diceExpression("2W6").build();
        when(stepRollService.roll(5)).thenReturn(roll);

        RecoveryTestResult result = characterService.performRecoveryTest(1L, null);

        assertThat(result.getRoll()).isEqualTo(roll);
        assertThat(result.getRoll().getDiceExpression()).isEqualTo("2W6");
        assertThat(result.getHealed()).isEqualTo(12);
    }

    @Test
    void result_potionNameIsNullWithoutPotion() {
        GameCharacter c = character(10, 0, 8, 2);
        stubFindById(c);
        stubAttrToStep(10, 5);
        stubRoll(5);

        RecoveryTestResult result = characterService.performRecoveryTest(1L, null);

        assertThat(result.getPotionName()).isNull();
    }

    // --- Helper methods ---

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
