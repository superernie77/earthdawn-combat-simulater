package com.earthdawn.service;

import com.earthdawn.dto.ProbeRequest;
import com.earthdawn.dto.ProbeResult;
import com.earthdawn.dto.RollResult;
import com.earthdawn.model.*;
import com.earthdawn.model.enums.AttributeType;
import com.earthdawn.model.enums.EquipmentType;
import com.earthdawn.repository.CharacterRepository;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests für den Ausrüstungs-Probenbonus (GEAR, z.B. Leichte Stiefel) in ProbeService.
 */
@ExtendWith(MockitoExtension.class)
class ProbeServiceTest {

    @Mock StepRollService diceService;
    @Mock CharacterRepository characterRepo;

    @InjectMocks ProbeService probeService;

    @BeforeEach
    void setUp() {
        lenient().when(diceService.attributeToStep(10)).thenReturn(5);
        lenient().when(diceService.roll(anyInt()))
                .thenReturn(RollResult.builder().total(12).diceExpression("W6").dice(List.of()).build());
    }

    @Test
    void gearBonus_addedToMatchingTalentProbe() {
        GameCharacter c = character();
        c.getTalents().add(talent(5L, "Heimlicher Schritt", 3));
        c.getEquipment().add(gear("Leichte Stiefel", "Heimlicher Schritt", 2));
        when(characterRepo.findById(1L)).thenReturn(Optional.of(c));

        ProbeResult r = probeService.rollProbe(probeFor(5L, null));

        // step = attr 5 + Rang 3 + Bonus 2 (Stiefel) = 10
        assertThat(r.getStep()).isEqualTo(10);
        assertThat(r.getEquipmentBonus()).isEqualTo(2);
    }

    @Test
    void gearBonus_notAppliedToOtherTalent() {
        GameCharacter c = character();
        c.getTalents().add(talent(6L, "Klettern", 3));
        c.getEquipment().add(gear("Leichte Stiefel", "Heimlicher Schritt", 2)); // anderes Ziel
        when(characterRepo.findById(1L)).thenReturn(Optional.of(c));

        ProbeResult r = probeService.rollProbe(probeFor(6L, null));

        assertThat(r.getEquipmentBonus()).isZero();
        assertThat(r.getStep()).isEqualTo(8); // 5 + 3, kein Bonus
    }

    @Test
    void gearBonus_appliedToMatchingSkillProbe() {
        GameCharacter c = character();
        SkillDefinition def = SkillDefinition.builder().id(7L).name("Schleichen").attribute(AttributeType.DEXTERITY).build();
        c.getSkills().add(CharacterSkill.builder().id(1L).skillDefinition(def).rank(2).build());
        c.getEquipment().add(gear("Leise Sohlen", "Schleichen", 3));
        when(characterRepo.findById(1L)).thenReturn(Optional.of(c));

        ProbeResult r = probeService.rollProbe(probeFor(null, 7L));

        assertThat(r.getEquipmentBonus()).isEqualTo(3);
        assertThat(r.getStep()).isEqualTo(10); // 5 + 2 + 3
    }

    @Test
    void multipleGear_sumBonuses() {
        GameCharacter c = character();
        c.getTalents().add(talent(5L, "Heimlicher Schritt", 1));
        c.getEquipment().add(gear("Leichte Stiefel", "Heimlicher Schritt", 2));
        c.getEquipment().add(gear("Schattenumhang", "Heimlicher Schritt", 1));
        when(characterRepo.findById(1L)).thenReturn(Optional.of(c));

        ProbeResult r = probeService.rollProbe(probeFor(5L, null));

        assertThat(r.getEquipmentBonus()).isEqualTo(3); // 2 + 1
    }

    @Test
    void schwimmkristall_addsThreeToStrengthBasedSchwimmenProbe() {
        GameCharacter c = character();
        c.setStrength(10); // STÄ-Step 5
        TalentDefinition def = TalentDefinition.builder().id(8L).name("Schwimmen").attribute(AttributeType.STRENGTH).build();
        c.getTalents().add(CharacterTalent.builder().id(8L).talentDefinition(def).rank(4).build());
        c.getEquipment().add(gear("Schwimmkristall", "Schwimmen", 3));
        when(characterRepo.findById(1L)).thenReturn(Optional.of(c));

        ProbeResult r = probeService.rollProbe(probeFor(8L, null));

        assertThat(r.getEquipmentBonus()).isEqualTo(3);
        assertThat(r.getStep()).isEqualTo(12); // STÄ-Step 5 + Rang 4 + 3 (Kristall)
    }

    @Test
    void schwimmkristall_appliesToSchwimmenSkillToo() {
        GameCharacter c = character();
        c.setStrength(10);
        SkillDefinition def = SkillDefinition.builder().id(9L).name("Schwimmen").attribute(AttributeType.STRENGTH).build();
        c.getSkills().add(CharacterSkill.builder().id(2L).skillDefinition(def).rank(2).build());
        c.getEquipment().add(gear("Schwimmkristall", "Schwimmen", 3));
        when(characterRepo.findById(1L)).thenReturn(Optional.of(c));

        ProbeResult r = probeService.rollProbe(probeFor(null, 9L));

        assertThat(r.getEquipmentBonus()).isEqualTo(3);
        assertThat(r.getStep()).isEqualTo(10); // 5 + Rang 2 + 3
    }

    // --- Helpers ---

    private GameCharacter character() {
        return GameCharacter.builder()
                .id(1L).name("Tester").dexterity(10).wounds(0).karmaCurrent(0)
                .equipment(new ArrayList<>()).talents(new ArrayList<>())
                .skills(new ArrayList<>()).spells(new ArrayList<>())
                .build();
    }

    private CharacterTalent talent(long defId, String name, int rank) {
        TalentDefinition def = TalentDefinition.builder()
                .id(defId).name(name).attribute(AttributeType.DEXTERITY).build();
        return CharacterTalent.builder().id(defId).talentDefinition(def).rank(rank).build();
    }

    private Equipment gear(String name, String targetTalent, int bonus) {
        return Equipment.builder()
                .id((long) name.hashCode()).name(name).type(EquipmentType.GEAR)
                .probeBonusTalentName(targetTalent).probeBonusValue(bonus)
                .build();
    }

    private ProbeRequest probeFor(Long talentId, Long skillId) {
        ProbeRequest req = new ProbeRequest();
        req.setCharacterId(1L);
        req.setTalentId(talentId);
        req.setSkillId(skillId);
        req.setTargetNumber(8);
        req.setBonusSteps(0);
        req.setSpendKarma(false);
        return req;
    }
}
