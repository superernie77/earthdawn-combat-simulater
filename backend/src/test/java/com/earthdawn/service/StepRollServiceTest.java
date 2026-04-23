package com.earthdawn.service;

import com.earthdawn.dto.RollResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class StepRollServiceTest {

    private StepRollService service;

    @BeforeEach
    void setUp() {
        service = new StepRollService();
    }

    // --- attributeToStep ---

    @ParameterizedTest(name = "attr={0} → step={1}")
    @CsvSource({
        "1,  2",   // boundary: <= 3 → 2
        "3,  2",
        "4,  3",   // boundary: <= 6 → 3
        "6,  3",
        "7,  4",   // boundary: <= 9 → 4
        "9,  4",
        "10, 5",   // boundary: <= 12 → 5
        "12, 5",
        "13, 6",
        "15, 6",
        "16, 7",
        "18, 7",
        "19, 8",
        "21, 8",
        "22, 9",
        "24, 9",
        "25, 10",
        "27, 10",
        "28, 11",
        "30, 11",
    })
    void attributeToStep_knownValues(int attr, int expectedStep) {
        assertThat(service.attributeToStep(attr)).isEqualTo(expectedStep);
    }

    // --- roll() result invariants ---

    @Test
    void roll_totalIsAtLeastOne() {
        // Steps 1–3 have negative modifiers; total must still be >= 1
        for (int step = 1; step <= 3; step++) {
            for (int i = 0; i < 50; i++) {
                RollResult result = service.roll(step);
                assertThat(result.getTotal())
                    .as("step=%d total must be >= 1", step)
                    .isGreaterThanOrEqualTo(1);
            }
        }
    }

    @Test
    void roll_stepStoredOnResult() {
        RollResult result = service.roll(8);
        assertThat(result.getStep()).isEqualTo(8);
    }

    @Test
    void roll_diceListNotEmpty() {
        RollResult result = service.roll(4);
        assertThat(result.getDice()).isNotEmpty();
    }

    @Test
    void roll_diceExpressionSet() {
        RollResult result = service.roll(4);
        assertThat(result.getDiceExpression()).isNotBlank();
    }

    @Test
    void roll_step4_singleD6() {
        // Step 4 = 1d6
        RollResult result = service.roll(4);
        assertThat(result.getDice()).hasSize(1);
        assertThat(result.getDice().get(0).getSides()).isEqualTo(6);
        assertThat(result.getDiceExpression()).isEqualTo("d6");
    }

    @Test
    void roll_step8_twoD6() {
        // Step 8 = 2d6
        RollResult result = service.roll(8);
        assertThat(result.getDice()).hasSize(2);
        result.getDice().forEach(d -> assertThat(d.getSides()).isEqualTo(6));
        assertThat(result.getDiceExpression()).isEqualTo("2d6");
    }

    @Test
    void roll_step12_twoD10() {
        // Step 12 = 2d10
        RollResult result = service.roll(12);
        assertThat(result.getDice()).hasSize(2);
        result.getDice().forEach(d -> assertThat(d.getSides()).isEqualTo(10));
    }

    @Test
    void roll_negativeStepClampedToOne() {
        // Negative steps are clamped to 1, result still valid
        RollResult result = service.roll(-5);
        assertThat(result.getStep()).isEqualTo(1);
        assertThat(result.getTotal()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void roll_step1_modifierMinus3_expressionContainsMinus3() {
        RollResult result = service.roll(1);
        assertThat(result.getDiceExpression()).contains("-3");
    }

    @Test
    void roll_explodedFlagSetWhenDieExploded() {
        // Run enough iterations that explosions statistically occur; just verify
        // the flag is consistent: exploded=true iff any die exploded
        for (int i = 0; i < 200; i++) {
            RollResult result = service.roll(4);
            boolean anyDieExploded = result.getDice().stream().anyMatch(d -> d.getRolls().size() > 1);
            assertThat(result.isExploded()).isEqualTo(anyDieExploded);
        }
    }
}
