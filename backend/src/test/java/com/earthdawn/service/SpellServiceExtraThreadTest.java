package com.earthdawn.service;

import com.earthdawn.dto.RollResult;
import com.earthdawn.dto.SpellCastRequest;
import com.earthdawn.dto.SpellCastResult;
import com.earthdawn.dto.ThreadweaveRequest;
import com.earthdawn.dto.ThreadweaveResult;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Zusatzfäden (Stufe 1): Sind alle Pflichtfäden gewoben, kauft jeder weitere Faden genau eine
 * Option des Zaubers. Nur EFFECT_STEP wird verrechnet, alles andere ist Anzeige für den GM.
 * Obergrenze = Fadenweben-Rang.
 */
@ExtendWith(MockitoExtension.class)
class SpellServiceExtraThreadTest {

    private static final long SESSION = 1L;
    private static final long CASTER = 10L;

    @Mock CombatSessionRepository sessionRepo;
    @Mock SpellDefinitionRepository spellRepo;
    @Mock StepRollService diceService;
    @Mock ModifierAggregator modifiers;
    @Mock CombatService combatService;

    @InjectMocks SpellService spellService;

    private CombatSession session;

    /** Option 0 = Wirkungsstufe +2 (verrechnet), Option 1 = Reichweite (nur Anzeige). */
    private static final SpellThreadOption OPT_EFFECT_STEP = SpellThreadOption.builder()
            .label("Wirkung Verstärken (Wirkungsstufe +2)").type(SpellThreadOptionType.EFFECT_STEP).value(2).build();
    private static final SpellThreadOption OPT_DISPLAY = SpellThreadOption.builder()
            .label("Reichweite Erhöhen (+10 Schritt)").type(SpellThreadOptionType.DISPLAY).value(0).build();

    @BeforeEach
    void setUp() {
        session = CombatSession.builder().id(SESSION).name("Test")
                .combatants(new ArrayList<>()).log(new ArrayList<>()).build();
        lenient().when(combatService.findById(SESSION)).thenReturn(session);
        lenient().when(diceService.attributeToStep(anyInt())).thenReturn(5);
        lenient().when(diceService.roll(anyInt()))
                .thenReturn(RollResult.builder().total(10).diceExpression("W6").dice(List.of()).build());
    }

    // --- Pflichtfäden ---

    @Test
    void requiredThread_ignoresOptionAndDoesNotCountAsExtra() {
        SpellDefinition spell = spell(2, List.of(OPT_EFFECT_STEP));
        CombatantState caster = caster(spell, 4);
        stub(spell, caster);

        // Option mitgeschickt, ist aber noch ein Pflichtfaden (0/2) → wird ignoriert
        ThreadweaveResult r = spellService.weaveThread(req(0));

        assertThat(r.isExtraThread()).isFalse();
        assertThat(r.getThreadsWoven()).isEqualTo(1);
        assertThat(r.getExtraThreadCount()).isZero();
        assertThat(caster.getExtraThreadChoices()).isNull();
    }

    // --- Zusatzfäden ---

    @Test
    void extraThread_afterAllRequiredWoven_storesChoiceAndKeepsThreadsCapped() {
        SpellDefinition spell = spell(2, List.of(OPT_EFFECT_STEP, OPT_DISPLAY));
        CombatantState caster = caster(spell, 4);
        prepared(caster, spell, 2, 2); // 2/2 → bereit
        stub(spell, caster);

        ThreadweaveResult r = spellService.weaveThread(req(1));

        assertThat(r.isExtraThread()).isTrue();
        assertThat(r.getExtraThreadLabel()).isEqualTo("Reichweite Erhöhen (+10 Schritt)");
        assertThat(r.getExtraThreadCount()).isEqualTo(1);
        assertThat(r.getExtraThreadMax()).isEqualTo(4); // Fadenweben-Rang
        assertThat(r.isReadyToCast()).isTrue();
        // Pflichtfäden bleiben bei 2/2 — Zusatzfäden zählen separat
        assertThat(caster.getThreadsWoven()).isEqualTo(2);
        assertThat(caster.getExtraThreadChoices()).isEqualTo("1");
    }

    @Test
    void extraThread_sameOptionTwice_isAllowedAndAccumulates() {
        SpellDefinition spell = spell(2, List.of(OPT_EFFECT_STEP));
        CombatantState caster = caster(spell, 4);
        prepared(caster, spell, 2, 2);
        caster.setExtraThreadChoices("0");
        stub(spell, caster);

        ThreadweaveResult r = spellService.weaveThread(req(0));

        assertThat(r.getExtraThreadCount()).isEqualTo(2);
        assertThat(caster.getExtraThreadChoices()).isEqualTo("0,0");
    }

    @Test
    void extraThread_failedRoll_doesNotStoreChoice() {
        SpellDefinition spell = spell(2, List.of(OPT_EFFECT_STEP));
        CombatantState caster = caster(spell, 4);
        prepared(caster, spell, 2, 2);
        stub(spell, caster);
        when(diceService.roll(anyInt()))
                .thenReturn(RollResult.builder().total(1).diceExpression("W6").dice(List.of()).build()); // < MW 5

        ThreadweaveResult r = spellService.weaveThread(req(0));

        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getExtraThreadLabel()).isNull();
        assertThat(r.getExtraThreadCount()).isZero();
        assertThat(caster.getExtraThreadChoices()).isNull();
    }

    @Test
    void extraThread_withoutOption_isRejected() {
        SpellDefinition spell = spell(2, List.of(OPT_EFFECT_STEP));
        CombatantState caster = caster(spell, 4);
        prepared(caster, spell, 2, 2);
        stub(spell, caster);

        assertThatThrownBy(() -> spellService.weaveThread(req(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gültige Option");
    }

    @Test
    void extraThread_withOutOfRangeOption_isRejected() {
        SpellDefinition spell = spell(2, List.of(OPT_EFFECT_STEP));
        CombatantState caster = caster(spell, 4);
        prepared(caster, spell, 2, 2);
        stub(spell, caster);

        assertThatThrownBy(() -> spellService.weaveThread(req(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gültige Option");
    }

    @Test
    void extraThread_beyondWeavingRank_isRejected() {
        SpellDefinition spell = spell(2, List.of(OPT_EFFECT_STEP));
        CombatantState caster = caster(spell, 2); // Fadenweben-Rang 2
        prepared(caster, spell, 2, 2);
        caster.setExtraThreadChoices("0,0"); // bereits 2 Zusatzfäden
        stub(spell, caster);

        assertThatThrownBy(() -> spellService.weaveThread(req(0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Maximal 2 Zusatzfäden");
    }

    @Test
    void extraThread_onSpellWithoutOptions_isRejected() {
        SpellDefinition spell = spell(2, List.of()); // keine Optionen
        CombatantState caster = caster(spell, 4);
        prepared(caster, spell, 2, 2);
        stub(spell, caster);

        assertThatThrownBy(() -> spellService.weaveThread(req(0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bietet keine Zusatzfäden");
    }

    // --- Sofortzauber (0 Fäden) ---

    @Test
    void zeroThreadSpellWithOptions_allowsExtraThread() {
        SpellDefinition spell = spell(0, List.of(OPT_EFFECT_STEP)); // z.B. Blitz
        CombatantState caster = caster(spell, 4);
        stub(spell, caster);

        // Erster Wurf ist bereits ein Zusatzfaden, da 0 Pflichtfäden nötig sind
        ThreadweaveResult r = spellService.weaveThread(req(0));

        assertThat(r.isExtraThread()).isTrue();
        assertThat(r.getExtraThreadCount()).isEqualTo(1);
        assertThat(caster.getExtraThreadChoices()).isEqualTo("0");
    }

    @Test
    void zeroThreadSpellWithoutOptions_isRejected() {
        SpellDefinition spell = spell(0, List.of());
        CombatantState caster = caster(spell, 4);
        stub(spell, caster);

        assertThatThrownBy(() -> spellService.weaveThread(req(0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("benötigt keine Fäden");
    }

    // --- Wirken ---

    @Test
    void cast_effectStepOptions_raiseTheEffectStepAndAreReported() {
        SpellDefinition heal = healSpell(List.of(OPT_EFFECT_STEP, OPT_DISPLAY));
        CombatantState caster = caster(heal, 4);
        prepared(caster, heal, 0, 0);
        caster.setExtraThreadChoices("0,0"); // 2× Wirkungsstufe +2 = +4
        stub(heal, caster);

        SpellCastResult r = spellService.castSpell(castReq());

        // Heilstufe 6 + 4 aus Zusatzfäden = 10 → aber Zauberwurf ist ebenfalls Stufe 10.
        assertThat(r.getExtraThreadEffectStep()).isEqualTo(4);
        assertThat(r.getExtraThreadLabels())
                .containsExactly("Wirkung Verstärken (Wirkungsstufe +2)", "Wirkung Verstärken (Wirkungsstufe +2)");
        assertThat(r.getDescription()).contains("Zusatzfäden: Wirkung Verstärken");
    }

    @Test
    void cast_displayOnlyOptions_doNotChangeTheEffectStep() {
        SpellDefinition heal = healSpell(List.of(OPT_EFFECT_STEP, OPT_DISPLAY));
        CombatantState caster = caster(heal, 4);
        prepared(caster, heal, 0, 0);
        caster.setExtraThreadChoices("1"); // nur Anzeige-Option
        stub(heal, caster);

        SpellCastResult r = spellService.castSpell(castReq());

        assertThat(r.getExtraThreadEffectStep()).isZero();
        assertThat(r.getExtraThreadLabels()).containsExactly("Reichweite Erhöhen (+10 Schritt)");
        verify(diceService).roll(6); // Heilstufe unverändert
    }

    @Test
    void cast_clearsExtraThreadChoices() {
        SpellDefinition heal = healSpell(List.of(OPT_EFFECT_STEP));
        CombatantState caster = caster(heal, 4);
        prepared(caster, heal, 0, 0);
        caster.setExtraThreadChoices("0");
        stub(heal, caster);

        spellService.castSpell(castReq());

        assertThat(caster.getExtraThreadChoices()).isNull();
        assertThat(caster.getPreparingSpellId()).isNull();
    }

    @Test
    void cast_ignoresChoicesBelongingToADifferentSpell() {
        SpellDefinition heal = healSpell(List.of(OPT_EFFECT_STEP));
        CombatantState caster = caster(heal, 4);
        caster.setPreparingSpellId(999L); // anderer Zauber in Vorbereitung
        caster.setExtraThreadChoices("0");
        stub(heal, caster);

        SpellCastResult r = spellService.castSpell(castReq());

        assertThat(r.getExtraThreadEffectStep()).isZero();
        assertThat(r.getExtraThreadLabels()).isEmpty();
    }

    @Test
    void switchingSpell_dropsPreviousExtraThreadChoices() {
        SpellDefinition spell = spell(2, List.of(OPT_EFFECT_STEP));
        CombatantState caster = caster(spell, 4);
        caster.setPreparingSpellId(999L); // anderer Zauber vorbereitet
        caster.setThreadsWoven(1);
        caster.setThreadsRequired(1);
        caster.setExtraThreadChoices("0,0");
        stub(spell, caster);

        spellService.weaveThread(req(0));

        // Vorbereitung wurde auf den neuen Zauber umgestellt → alte Zusatzfäden weg
        assertThat(caster.getPreparingSpellId()).isEqualTo(50L);
        assertThat(caster.getExtraThreadChoices()).isNull();
        assertThat(caster.getThreadsWoven()).isEqualTo(1); // Pflichtfaden dieses Zaubers
    }

    // --- Schadenszauber: Aufschlüsselung der Stufe ---

    @Test
    void castDamage_addsExtraThreadEffectStepOnTopOfEffectStepAndExtraSuccesses() {
        SpellDefinition blitz = damageSpell(List.of(OPT_EFFECT_STEP));
        CombatantState caster = caster(blitz, 4);
        CombatantState target = target();
        prepared(caster, blitz, 0, 0);
        caster.setExtraThreadChoices("0"); // 1x Wirkungsstufe +2
        stub(blitz, caster);
        when(combatService.findCombatant(eq(session), eq(20L))).thenReturn(target);
        lenient().when(modifiers.getEffectiveValue(eq(target), eq(StatType.MYSTIC_ARMOR), any())).thenReturn(2);
        lenient().when(modifiers.getEffectiveValue(eq(target), eq(StatType.WOUND_THRESHOLD), any())).thenReturn(9);

        SpellCastResult r = spellService.castSpell(castReqOn(20L));

        // Wirkungsstufe 4 + WIL-Stufe 5 = 9 Basis, +2 Übererfolge (1 Übererfolg x2), +2 Zusatzfaden
        assertThat(r.getExtraSuccesses()).isEqualTo(1);
        assertThat(r.getDamageStepBonus()).isEqualTo(2);
        assertThat(r.getExtraThreadEffectStep()).isEqualTo(2);
        assertThat(r.getDamageStep()).isEqualTo(13);
        // Die Basis muss sich exakt zurückrechnen lassen — genau das zeigt das UI in der Klammer an
        assertThat(r.getDamageStep() - r.getDamageStepBonus() - r.getExtraThreadEffectStep()).isEqualTo(9);
        verify(diceService).roll(13);
    }

    @Test
    void castDamage_withoutExtraThreads_isUnchanged() {
        SpellDefinition blitz = damageSpell(List.of(OPT_EFFECT_STEP));
        CombatantState caster = caster(blitz, 4);
        CombatantState target = target();
        stub(blitz, caster);
        when(combatService.findCombatant(eq(session), eq(20L))).thenReturn(target);
        lenient().when(modifiers.getEffectiveValue(eq(target), eq(StatType.MYSTIC_ARMOR), any())).thenReturn(2);
        lenient().when(modifiers.getEffectiveValue(eq(target), eq(StatType.WOUND_THRESHOLD), any())).thenReturn(9);

        SpellCastResult r = spellService.castSpell(castReqOn(20L));

        assertThat(r.getExtraThreadEffectStep()).isZero();
        assertThat(r.getDamageStep()).isEqualTo(11); // 4 Wirkungsstufe + 5 WIL + 2 Übererfolge
        verify(diceService).roll(11);
    }

    // --- Erweiterte Matrize: freier Zusatzfaden für Sofortzauber ---

    @Test
    void zeroThreadSpellInErweiterteMatrize_getsFreeEffectStepThreadWithoutPreparation() {
        SpellDefinition blitz = damageSpell(List.of(OPT_EFFECT_STEP, OPT_DISPLAY));
        CombatantState caster = casterWithEnhancedMatrix(blitz, 4);
        CombatantState target = target();
        stub(blitz, caster);
        when(combatService.findCombatant(eq(session), eq(20L))).thenReturn(target);
        lenient().when(modifiers.getEffectiveValue(eq(target), eq(StatType.MYSTIC_ARMOR), any())).thenReturn(2);
        lenient().when(modifiers.getEffectiveValue(eq(target), eq(StatType.WOUND_THRESHOLD), any())).thenReturn(9);

        // Kein Fadenweben, kein preparingSpellId — der Faden kommt allein aus der Matrize
        SpellCastResult r = spellService.castSpell(castReqOn(20L));

        assertThat(r.getExtraThreadEffectStep()).isEqualTo(2);
        assertThat(r.getExtraThreadLabels()).containsExactly(
                "Wirkung Verstärken (Wirkungsstufe +2) — frei (Erweiterte Matrize)");
        assertThat(r.getDamageStep()).isEqualTo(13); // 4 + 5 + 2 Übererfolge + 2 frei
    }

    @Test
    void zeroThreadSpell_withoutErweiterteMatrize_getsNoFreeThread() {
        SpellDefinition blitz = damageSpell(List.of(OPT_EFFECT_STEP));
        CombatantState caster = caster(blitz, 4); // nur normale Matrize
        CombatantState target = target();
        stub(blitz, caster);
        when(combatService.findCombatant(eq(session), eq(20L))).thenReturn(target);
        lenient().when(modifiers.getEffectiveValue(eq(target), eq(StatType.MYSTIC_ARMOR), any())).thenReturn(2);
        lenient().when(modifiers.getEffectiveValue(eq(target), eq(StatType.WOUND_THRESHOLD), any())).thenReturn(9);

        SpellCastResult r = spellService.castSpell(castReqOn(20L));

        assertThat(r.getExtraThreadEffectStep()).isZero();
        assertThat(r.getExtraThreadLabels()).isEmpty();
    }

    @Test
    void spellWithRequiredThreads_inErweiterteMatrize_getsNoFreeThread() {
        // 2 Fäden − 1 vorgewoben = 1 Pflichtfaden: der Matrizenfaden ist verbraucht, nichts ist frei
        SpellDefinition heal = healSpell(List.of(OPT_EFFECT_STEP));
        heal.setThreads(2);
        CombatantState caster = casterWithEnhancedMatrix(heal, 4);
        prepared(caster, heal, 1, 1);
        stub(heal, caster);

        SpellCastResult r = spellService.castSpell(castReq());

        assertThat(r.getExtraThreadEffectStep()).isZero();
        assertThat(r.getExtraThreadLabels()).isEmpty();
    }

    @Test
    void freeThread_stacksOnTopOfWovenExtraThreadsAndIgnoresTheRankCap() {
        SpellDefinition blitz = damageSpell(List.of(OPT_EFFECT_STEP));
        CombatantState caster = casterWithEnhancedMatrix(blitz, 1); // Rang 1 → max. 1 gewobener Zusatzfaden
        CombatantState target = target();
        prepared(caster, blitz, 0, 0);
        caster.setExtraThreadChoices("0"); // dieser eine gewobene Zusatzfaden schöpft den Rang aus
        stub(blitz, caster);
        when(combatService.findCombatant(eq(session), eq(20L))).thenReturn(target);
        lenient().when(modifiers.getEffectiveValue(eq(target), eq(StatType.MYSTIC_ARMOR), any())).thenReturn(2);
        lenient().when(modifiers.getEffectiveValue(eq(target), eq(StatType.WOUND_THRESHOLD), any())).thenReturn(9);

        SpellCastResult r = spellService.castSpell(castReqOn(20L));

        // gewoben (+2) UND frei (+2) — der freie Faden kommt oben drauf
        assertThat(r.getExtraThreadEffectStep()).isEqualTo(4);
        assertThat(r.getExtraThreadLabels()).hasSize(2);
        assertThat(r.getExtraThreadLabels().get(1)).contains("frei (Erweiterte Matrize)");
        assertThat(r.getDamageStep()).isEqualTo(15); // 4 + 5 + 2 Übererfolge + 4
    }

    @Test
    void zeroThreadSpellWithoutEffectStepOption_getsNoFreeThread() {
        // z.B. Katastrophe: BUFF ohne Wirkungsstufe — es gibt nichts zu erhöhen
        SpellDefinition buff = SpellDefinition.builder()
                .id(50L).name("Katastrophe").discipline("Illusionist").circle(1)
                .threads(0).weavingDifficulty(5).castingDifficulty(1)
                .effectType(SpellEffectType.BUFF).effectStep(0)
                .modifyStat(StatType.PHYSICAL_DEFENSE).modifyOperation(ModifierOperation.ADD)
                .modifyValue(1).modifyTrigger(TriggerContext.ALWAYS).duration(3)
                .threadOptions(new ArrayList<>(List.of(OPT_DISPLAY)))
                .build();
        CombatantState caster = casterWithEnhancedMatrix(buff, 4);
        stub(buff, caster);

        SpellCastResult r = spellService.castSpell(castReq());

        assertThat(r.getExtraThreadEffectStep()).isZero();
        assertThat(r.getExtraThreadLabels()).isEmpty();
    }

    // --- CSV-Helfer ---

    @Test
    void parseChoices_handlesNullBlankAndGarbage() {
        assertThat(SpellService.parseChoices(null)).isEmpty();
        assertThat(SpellService.parseChoices("  ")).isEmpty();
        assertThat(SpellService.parseChoices("0,1,0")).containsExactly(0, 1, 0);
        assertThat(SpellService.parseChoices("0, ,x,2")).containsExactly(0, 2);
    }

    @Test
    void formatChoices_roundTrips() {
        assertThat(SpellService.formatChoices(List.of())).isNull();
        assertThat(SpellService.formatChoices(null)).isNull();
        assertThat(SpellService.formatChoices(List.of(0, 0, 3))).isEqualTo("0,0,3");
        assertThat(SpellService.parseChoices(SpellService.formatChoices(List.of(1, 2)))).containsExactly(1, 2);
    }

    // --- Helpers ---

    private void stub(SpellDefinition spell, CombatantState caster) {
        lenient().when(spellRepo.findById(spell.getId())).thenReturn(Optional.of(spell));
        lenient().when(combatService.findCombatant(eq(session), eq(CASTER))).thenReturn(caster);
    }

    private void prepared(CombatantState caster, SpellDefinition spell, int woven, int required) {
        caster.setPreparingSpellId(spell.getId());
        caster.setThreadsWoven(woven);
        caster.setThreadsRequired(required);
    }

    private ThreadweaveRequest req(Integer optionIndex) {
        ThreadweaveRequest r = new ThreadweaveRequest();
        r.setSessionId(SESSION);
        r.setCasterCombatantId(CASTER);
        r.setSpellId(50L);
        r.setSpendKarma(false);
        r.setExtraThreadOptionIndex(optionIndex);
        return r;
    }

    /** Schadenszauber mit Ziel: ZV 5, Wurf 10 -> (10-5)/5 = 1 Übererfolg -> +2 Schadensstufen. */
    private SpellDefinition damageSpell(List<SpellThreadOption> options) {
        return SpellDefinition.builder()
                .id(50L).name("Blitz").discipline("Illusionist").circle(1)
                .threads(0).weavingDifficulty(5).castingDifficulty(5)
                .effectType(SpellEffectType.DAMAGE).effectStep(4)
                .useMysticArmor(true).extraSuccessEffect("DAMAGE")
                .threadOptions(new ArrayList<>(options))
                .build();
    }

    private CombatantState target() {
        GameCharacter c = GameCharacter.builder()
                .id(2L).name("Orkbrenner").perception(10).willpower(10)
                .equipment(new ArrayList<>()).talents(new ArrayList<>())
                .skills(new ArrayList<>()).spells(new ArrayList<>())
                .build();
        return CombatantState.builder()
                .id(20L).character(c).activeEffects(new ArrayList<>())
                .currentKarma(0).wounds(0).currentDamage(0)
                .build();
    }

    private SpellCastRequest castReqOn(long targetId) {
        SpellCastRequest r = castReq();
        r.setTargetCombatantId(targetId);
        return r;
    }

    private SpellCastRequest castReq() {
        SpellCastRequest r = new SpellCastRequest();
        r.setSessionId(SESSION);
        r.setCasterCombatantId(CASTER);
        r.setSpellId(50L);
        r.setSpendKarma(false);
        return r;
    }

    private SpellDefinition spell(int threads, List<SpellThreadOption> options) {
        return SpellDefinition.builder()
                .id(50L).name("Blitz").discipline("Illusionist").circle(1)
                .threads(threads).weavingDifficulty(5)
                .threadOptions(new ArrayList<>(options))
                .build();
    }

    /** HEAL auf sich selbst: feste Wirkschwierigkeit 1 → Auto-Erfolg, Heilstufe 6. */
    private SpellDefinition healSpell(List<SpellThreadOption> options) {
        return SpellDefinition.builder()
                .id(50L).name("Heilung").discipline("Illusionist").circle(1)
                .threads(0).weavingDifficulty(5).castingDifficulty(1)
                .effectType(SpellEffectType.HEAL).effectStep(6)
                .threadOptions(new ArrayList<>(options))
                .build();
    }

    /** Wie caster(), aber der Zauber liegt zusätzlich in einer Erweiterten Matrize. */
    private CombatantState casterWithEnhancedMatrix(SpellDefinition spell, int weavingRank) {
        CombatantState c = caster(spell, weavingRank);
        c.getCharacter().getTalents().add(CharacterTalent.builder().id(4L).rank(1)
                .talentDefinition(TalentDefinition.builder().id(4L).name(TalentNames.ERWEITERTE_MATRIZE)
                        .attribute(AttributeType.PERCEPTION).build())
                .assignedSpell(spell)
                .build());
        return c;
    }

    /** Illusionist mit Illusionismus (Fadenweben) und Spruchzauberei. */
    private CombatantState caster(SpellDefinition spell, int weavingRank) {
        List<CharacterTalent> talents = new ArrayList<>(List.of(
                CharacterTalent.builder().id(1L).rank(weavingRank)
                        .talentDefinition(TalentDefinition.builder().id(1L).name("Illusionismus")
                                .attribute(AttributeType.PERCEPTION).build())
                        .build(),
                CharacterTalent.builder().id(3L).rank(5)
                        .talentDefinition(TalentDefinition.builder().id(3L).name("Spruchzauberei")
                                .attribute(AttributeType.PERCEPTION).build())
                        .build()));

        GameCharacter c = GameCharacter.builder()
                .id(1L).name("Illusionist").perception(10).willpower(10)
                .discipline(DisciplineDefinition.builder().name("Illusionist").build())
                .equipment(new ArrayList<>()).talents(talents)
                .skills(new ArrayList<>()).spells(new ArrayList<>())
                .build();

        return CombatantState.builder()
                .id(CASTER).character(c).activeEffects(new ArrayList<>())
                .currentKarma(0).wounds(0).currentDamage(0)
                .build();
    }
}
