package com.earthdawn.service;

import com.earthdawn.dto.DieRollDetail;
import com.earthdawn.dto.RollResult;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.*;

/**
 * Implementiert das Earthdawn 4 (FASA) Würfelsystem.
 * Jede Step-Zahl entspricht einer bestimmten Würfelkombination.
 * Würfel explodieren: Bei Maximalwurf wird nochmals gewürfelt und addiert.
 * Steps 1 und 2 haben Abzüge (-2 bzw. -1), Minimum ist immer 1.
 */
@Service
public class StepRollService {

    private static final Random RANDOM = new SecureRandom();

    // Step → Würfelseiten-Array (ED4 FASA Tabelle)
    private static final Map<Integer, int[]> STEP_TABLE = new LinkedHashMap<>();

    static {
        // Offizielle ED4 FASA Stufen-Aktionswürfel-Tabelle (W4/W20 ausgeschlossen)
        STEP_TABLE.put(1,  new int[]{6});          // W6-3
        STEP_TABLE.put(2,  new int[]{6});          // W6-2
        STEP_TABLE.put(3,  new int[]{6});          // W6-1
        STEP_TABLE.put(4,  new int[]{6});          // W6
        STEP_TABLE.put(5,  new int[]{8});          // W8
        STEP_TABLE.put(6,  new int[]{10});         // W10
        STEP_TABLE.put(7,  new int[]{12});         // W12
        STEP_TABLE.put(8,  new int[]{6, 6});       // 2W6
        STEP_TABLE.put(9,  new int[]{8, 6});       // W8+W6
        STEP_TABLE.put(10, new int[]{8, 8});       // 2W8
        STEP_TABLE.put(11, new int[]{10, 8});      // W10+W8
        STEP_TABLE.put(12, new int[]{10, 10});     // 2W10
        STEP_TABLE.put(13, new int[]{12, 10});     // W12+W10
        STEP_TABLE.put(14, new int[]{12, 12});     // 2W12
        STEP_TABLE.put(15, new int[]{12, 6, 6});   // W12+2W6
        STEP_TABLE.put(16, new int[]{12, 8, 6});   // W12+W8+W6
        STEP_TABLE.put(17, new int[]{12, 8, 8});   // W12+2W8
        STEP_TABLE.put(18, new int[]{12, 10, 8});  // W12+W10+W8
        STEP_TABLE.put(19, new int[]{12, 10, 10}); // W12+2W10
        STEP_TABLE.put(20, new int[]{12, 12, 10}); // 2W12+W10
        STEP_TABLE.put(21, new int[]{12, 12, 12}); // 3W12
        STEP_TABLE.put(22, new int[]{12, 12, 6, 6});   // 2W12+2W6
        STEP_TABLE.put(23, new int[]{12, 12, 8, 6});   // 2W12+W8+W6
        STEP_TABLE.put(24, new int[]{12, 12, 8, 8});   // 2W12+2W8
        STEP_TABLE.put(25, new int[]{12, 12, 10, 8});  // 2W12+W10+W8
        STEP_TABLE.put(26, new int[]{12, 12, 10, 10}); // 2W12+2W10
        STEP_TABLE.put(27, new int[]{12, 12, 12, 10}); // 3W12+W10
        STEP_TABLE.put(28, new int[]{12, 12, 12, 12}); // 4W12
        STEP_TABLE.put(29, new int[]{12, 12, 12, 6, 6});   // 3W12+2W6
        STEP_TABLE.put(30, new int[]{12, 12, 12, 8, 6});   // 3W12+W8+W6
        STEP_TABLE.put(31, new int[]{12, 12, 12, 8, 8});   // 3W12+2W8
        STEP_TABLE.put(32, new int[]{12, 12, 12, 10, 8});  // 3W12+W10+W8
        STEP_TABLE.put(33, new int[]{12, 12, 12, 10, 10}); // 3W12+2W10
        STEP_TABLE.put(34, new int[]{12, 12, 12, 12, 10}); // 4W12+W10
        STEP_TABLE.put(35, new int[]{12, 12, 12, 12, 12}); // 5W12
        STEP_TABLE.put(36, new int[]{12, 12, 12, 12, 6, 6});   // 4W12+2W6
        STEP_TABLE.put(37, new int[]{12, 12, 12, 12, 8, 6});   // 4W12+W8+W6
        STEP_TABLE.put(38, new int[]{12, 12, 12, 12, 8, 8});   // 4W12+2W8
        STEP_TABLE.put(39, new int[]{12, 12, 12, 12, 10, 8});  // 4W12+W10+W8
        STEP_TABLE.put(40, new int[]{12, 12, 12, 12, 10, 10}); // 4W12+2W10
    }

    public RollResult roll(int step) {
        int effectiveStep = Math.max(1, step);
        int[] dice = getStepDice(effectiveStep);
        int modifier = getStepModifier(effectiveStep);

        List<DieRollDetail> dieRolls = new ArrayList<>();
        int total = 0;

        for (int sides : dice) {
            DieRollDetail detail = rollExploding(sides);
            dieRolls.add(detail);
            total += detail.getTotal();
        }

        total = Math.max(1, total + modifier);
        boolean anyExploded = dieRolls.stream().anyMatch(DieRollDetail::isExploded);

        return RollResult.builder()
                .step(effectiveStep)
                .diceExpression(buildDiceExpression(dice, modifier))
                .dice(dieRolls)
                .total(total)
                .exploded(anyExploded)
                .build();
    }

    private DieRollDetail rollExploding(int sides) {
        List<Integer> rolls = new ArrayList<>();
        int total = 0;
        int current;
        do {
            current = RANDOM.nextInt(sides) + 1;
            rolls.add(current);
            total += current;
        } while (current == sides); // Explosion bei Maximum

        return DieRollDetail.builder()
                .sides(sides)
                .rolls(rolls)
                .total(total)
                .exploded(rolls.size() > 1)
                .build();
    }

    private int[] getStepDice(int step) {
        if (STEP_TABLE.containsKey(step)) {
            return STEP_TABLE.get(step);
        }
        // Extrapolation für Steps > 40: Muster wiederholt sich mit +1 W12 alle 7 Steps
        int[] base = STEP_TABLE.get(40);
        int extra = step - 40;
        int addDice = extra / 7 + 1;
        int[] result = new int[base.length + addDice];
        System.arraycopy(base, 0, result, 0, base.length);
        Arrays.fill(result, base.length, result.length, 12);
        return result;
    }

    private int getStepModifier(int step) {
        return switch (step) {
            case 1 -> -3;
            case 2 -> -2;
            case 3 -> -1;
            default -> 0;
        };
    }

    private String buildDiceExpression(int[] dice, int modifier) {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (int d : dice) {
            counts.merge(d, 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        counts.forEach((sides, count) -> {
            if (!sb.isEmpty()) sb.append("+");
            if (count > 1) sb.append(count);
            sb.append("d").append(sides);
        });
        if (modifier < 0) sb.append(modifier);
        else if (modifier > 0) sb.append("+").append(modifier);
        return sb.toString();
    }

    /** Konvertiert einen ED4-Attributwert in die zugehörige Stufe (Step). */
    public int attributeToStep(int attributeValue) {
        if (attributeValue <= 3)  return 2;
        if (attributeValue <= 6)  return 3;
        if (attributeValue <= 9)  return 4;
        if (attributeValue <= 12) return 5;
        if (attributeValue <= 15) return 6;
        if (attributeValue <= 18) return 7;
        if (attributeValue <= 21) return 8;
        if (attributeValue <= 24) return 9;
        if (attributeValue <= 27) return 10;
        if (attributeValue <= 30) return 11;
        // Extrapolation: alle 3 Punkte +1 Stufe
        return (attributeValue - 1) / 3;
    }
}
