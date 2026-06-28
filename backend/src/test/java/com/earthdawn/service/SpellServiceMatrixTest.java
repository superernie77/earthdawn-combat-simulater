package com.earthdawn.service;

import com.earthdawn.dto.ThreadweaveRequest;
import com.earthdawn.dto.ThreadweaveResult;
import com.earthdawn.dto.SpellCastRequest;
import com.earthdawn.dto.SpellCastResult;
import com.earthdawn.dto.RollResult;
import com.earthdawn.model.*;
import com.earthdawn.model.enums.*;
import com.earthdawn.repository.CombatSessionRepository;
import com.earthdawn.repository.SpellDefinitionRepository;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests für die "Erweiterte Matrize": ein in der Matrize liegender Zauber gilt als
 * 1 Faden vorgewoben → benötigter Fadenweben-Aufwand −1.
 */
@ExtendWith(MockitoExtension.class)
class SpellServiceMatrixTest {

    @Mock CombatSessionRepository sessionRepo;
    @Mock SpellDefinitionRepository spellRepo;
    @Mock StepRollService diceService;
    @Mock ModifierAggregator modifiers;
    @Mock CombatService combatService;

    @InjectMocks SpellService spellService;

    private SpellDefinition spell;     // 2 Fäden
    private CombatSession session;

    @BeforeEach
    void setUp() {
        spell = SpellDefinition.builder()
                .id(50L).name("Feuerball").discipline("Elementarist").circle(2)
                .threads(2).weavingDifficulty(5).build();
        session = CombatSession.builder().id(1L).name("Test").combatants(new ArrayList<>()).log(new ArrayList<>()).build();

        lenient().when(spellRepo.findById(50L)).thenReturn(Optional.of(spell));
        lenient().when(combatService.findById(1L)).thenReturn(session);
        lenient().when(diceService.attributeToStep(anyInt())).thenReturn(5);
        lenient().when(diceService.roll(anyInt()))
                .thenReturn(RollResult.builder().total(10).diceExpression("W6").dice(List.of()).build());
    }

    @Test
    void erweiterteMatrize_reducesThreadsRequiredByOne() {
        CombatantState caster = caster(true); // Zauber liegt in erweiterter Matrize
        when(combatService.findCombatant(eq(session), eq(10L))).thenReturn(caster);

        ThreadweaveResult r = spellService.weaveThread(req());

        // 2 Fäden − 1 (vorgewoben) = 1 benötigt; nach einem Wurf 1/1 → bereit
        assertThat(r.getThreadsRequired()).isEqualTo(1);
        assertThat(r.getThreadsWoven()).isEqualTo(1);
        assertThat(r.isReadyToCast()).isTrue();
    }

    @Test
    void normalMatrize_requiresFullThreads() {
        CombatantState caster = caster(false); // normale Matrize / keine
        when(combatService.findCombatant(eq(session), eq(10L))).thenReturn(caster);

        ThreadweaveResult r = spellService.weaveThread(req());

        assertThat(r.getThreadsRequired()).isEqualTo(2);
        assertThat(r.getThreadsWoven()).isEqualTo(1);
        assertThat(r.isReadyToCast()).isFalse(); // 1 < 2
    }

    // --- Im Kampf: 1-Faden-Zauber in erweiterter Matrize ist direkt wirkbar ---

    @Test
    void castOneThreadSpell_inErweiterteMatrize_castsDirectlyWithoutPreparation() {
        SpellDefinition oneThread = oneThreadBuff();
        when(spellRepo.findById(51L)).thenReturn(Optional.of(oneThread));
        CombatantState caster = casterForCast(true, oneThread); // 1-Faden in erw. Matrize, KEINE Vorbereitung
        when(combatService.findCombatant(eq(session), eq(10L))).thenReturn(caster);

        SpellCastResult r = spellService.castSpell(castReq(51L));

        // 1 Faden − 1 (vorgewoben) = 0 → direkt wirkbar, kein "nicht vorbereitet"
        assertThat(r.isSuccess()).isTrue();
        assertThat(caster.getActiveEffects()).isNotEmpty(); // Buff-Effekt angelegt
    }

    @Test
    void castOneThreadSpell_withoutErweiterteMatrize_requiresPreparation() {
        SpellDefinition oneThread = oneThreadBuff();
        when(spellRepo.findById(51L)).thenReturn(Optional.of(oneThread));
        CombatantState caster = casterForCast(false, oneThread); // keine erw. Matrize, keine Vorbereitung
        when(combatService.findCombatant(eq(session), eq(10L))).thenReturn(caster);

        assertThatThrownBy(() -> spellService.castSpell(castReq(51L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nicht vorbereitet");
    }

    // --- Helpers ---

    private ThreadweaveRequest req() {
        ThreadweaveRequest r = new ThreadweaveRequest();
        r.setSessionId(1L);
        r.setCasterCombatantId(10L);
        r.setSpellId(50L);
        r.setSpendKarma(false);
        return r;
    }

    private SpellDefinition oneThreadBuff() {
        return SpellDefinition.builder()
                .id(51L).name("Tarnung").discipline("Illusionist").circle(1)
                .threads(1).weavingDifficulty(5).castingDifficulty(1) // feste Wirkschwierigkeit 1 → Auto-Erfolg
                .effectType(SpellEffectType.BUFF)
                .modifyStat(StatType.PHYSICAL_DEFENSE).modifyOperation(ModifierOperation.ADD)
                .modifyValue(3).modifyTrigger(TriggerContext.ALWAYS).duration(3)
                .build();
    }

    private SpellCastRequest castReq(long spellId) {
        SpellCastRequest r = new SpellCastRequest();
        r.setSessionId(1L);
        r.setCasterCombatantId(10L);
        r.setSpellId(spellId);
        r.setSpendKarma(false);
        return r;
    }

    /** Zauberer mit Spruchzauberei, optional mit erweiterter Matrize, die den Zauber hält. */
    private CombatantState casterForCast(boolean enhancedMatrix, SpellDefinition matrixSpell) {
        List<CharacterTalent> talents = new ArrayList<>(List.of(
                CharacterTalent.builder().id(3L).rank(5)
                        .talentDefinition(TalentDefinition.builder().id(3L).name("Spruchzauberei").attribute(AttributeType.PERCEPTION).build())
                        .build()));
        if (enhancedMatrix) {
            talents.add(CharacterTalent.builder().id(2L).rank(1)
                    .talentDefinition(TalentDefinition.builder().id(2L).name(TalentNames.ERWEITERTE_MATRIZE).attribute(AttributeType.PERCEPTION).build())
                    .assignedSpell(matrixSpell)
                    .build());
        }
        GameCharacter c = GameCharacter.builder()
                .id(1L).name("Illusionist").perception(10)
                .discipline(DisciplineDefinition.builder().name("Illusionist").build())
                .equipment(new ArrayList<>()).talents(talents)
                .skills(new ArrayList<>()).spells(new ArrayList<>())
                .build();
        return CombatantState.builder()
                .id(10L).character(c).activeEffects(new ArrayList<>())
                .currentKarma(0).wounds(0)
                .build();
    }

    private CombatantState caster(boolean enhancedMatrix) {
        DisciplineDefinition disc = DisciplineDefinition.builder().name("Elementarist").build();

        // Fadenweben-Talent der Disziplin (Elementarist → Elementarismus)
        CharacterTalent weaving = CharacterTalent.builder().id(1L).rank(4)
                .talentDefinition(TalentDefinition.builder().id(1L).name("Elementarismus").attribute(AttributeType.PERCEPTION).build())
                .build();

        List<CharacterTalent> talents = new ArrayList<>(List.of(weaving));
        if (enhancedMatrix) {
            talents.add(CharacterTalent.builder().id(2L).rank(2)
                    .talentDefinition(TalentDefinition.builder().id(2L).name(TalentNames.ERWEITERTE_MATRIZE).attribute(AttributeType.PERCEPTION).build())
                    .assignedSpell(spell)
                    .build());
        }

        GameCharacter c = GameCharacter.builder()
                .id(1L).name("Magier").perception(10).discipline(disc)
                .equipment(new ArrayList<>()).talents(talents)
                .skills(new ArrayList<>()).spells(new ArrayList<>())
                .build();

        return CombatantState.builder()
                .id(10L).character(c).activeEffects(new ArrayList<>())
                .currentKarma(0).wounds(0)
                .build();
    }
}
