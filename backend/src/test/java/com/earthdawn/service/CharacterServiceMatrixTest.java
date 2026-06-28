package com.earthdawn.service;

import com.earthdawn.model.*;
import com.earthdawn.model.enums.AttributeType;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests für die Zauberzuweisung an Matrizen — inkl. der "Erweiterte Matrize".
 */
@ExtendWith(MockitoExtension.class)
class CharacterServiceMatrixTest {

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

    @Test
    void assignSpell_toErweiterteMatrize_works() {
        GameCharacter c = characterWithMatrix(20L, "Erweiterte Matrize", 4);
        when(characterRepo.findById(1L)).thenReturn(Optional.of(c));
        when(spellDefRepo.findById(42L)).thenReturn(Optional.of(spell(42L, "Phantomkrieger", 3)));

        GameCharacter result = characterService.assignSpellToMatrix(1L, 20L, 42L);

        CharacterTalent matrix = result.getTalents().get(0);
        assertThat(matrix.getAssignedSpell()).isNotNull();
        assertThat(matrix.getAssignedSpell().getName()).isEqualTo("Phantomkrieger");
    }

    @Test
    void assignSpell_toZaubermatritze_stillWorks() {
        GameCharacter c = characterWithMatrix(20L, "Zaubermatritze", 4);
        when(characterRepo.findById(1L)).thenReturn(Optional.of(c));
        when(spellDefRepo.findById(42L)).thenReturn(Optional.of(spell(42L, "Eisnadeln", 1)));

        GameCharacter result = characterService.assignSpellToMatrix(1L, 20L, 42L);

        assertThat(result.getTalents().get(0).getAssignedSpell()).isNotNull();
    }

    @Test
    void assignSpell_toNonMatrixTalent_throws() {
        GameCharacter c = characterWithMatrix(20L, "Nahkampfwaffen", 4);
        when(characterRepo.findById(1L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> characterService.assignSpellToMatrix(1L, 20L, 42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nur Zaubermatrizen");
    }

    @Test
    void assignSpell_circleAboveRank_throws() {
        GameCharacter c = characterWithMatrix(20L, "Erweiterte Matrize", 2);
        when(characterRepo.findById(1L)).thenReturn(Optional.of(c));
        when(spellDefRepo.findById(42L)).thenReturn(Optional.of(spell(42L, "Hoher Zauber", 5)));

        assertThatThrownBy(() -> characterService.assignSpellToMatrix(1L, 20L, 42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("übersteigt den Rang");
    }

    // --- Helpers ---

    private GameCharacter characterWithMatrix(long talentId, String talentName, int rank) {
        CharacterTalent matrix = CharacterTalent.builder()
                .id(talentId).rank(rank)
                .talentDefinition(TalentDefinition.builder().id(talentId).name(talentName).attribute(AttributeType.PERCEPTION).build())
                .build();
        return GameCharacter.builder()
                .id(1L).name("Magier")
                .equipment(new ArrayList<>()).talents(new ArrayList<>(List.of(matrix)))
                .skills(new ArrayList<>()).spells(new ArrayList<>())
                .build();
    }

    private SpellDefinition spell(long id, String name, int circle) {
        return SpellDefinition.builder().id(id).name(name).circle(circle).threads(1).build();
    }
}
