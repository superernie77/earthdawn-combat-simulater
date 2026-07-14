package com.earthdawn.service;

import com.earthdawn.dto.NeutralizeMagicRequest;
import com.earthdawn.dto.NeutralizeMagicResult;
import com.earthdawn.dto.RollResult;
import com.earthdawn.model.*;
import com.earthdawn.model.enums.*;
import com.earthdawn.repository.CharacterRepository;
import com.earthdawn.repository.CombatSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests für "Magie neutralisieren": WIL + Rang vs. (gewählte Effektstufe + 10).
 * Verbraucht die Aktion der Runde und 1 Überanstrengung; Erfolg entfernt den gewählten Effekt.
 */
@ExtendWith(MockitoExtension.class)
class CombatServiceNeutralizeMagicTest {

    @Mock CombatSessionRepository sessionRepo;
    @Mock CharacterRepository characterRepo;
    @Mock StepRollService diceService;
    @Mock ModifierAggregator modifiers;
    @Mock SimpMessagingTemplate websocket;
    @Mock ObjectMapper objectMapper;

    @InjectMocks CombatService combatService;

    private CombatantState actor;
    private CombatantState target;
    private CombatSession session;

    @BeforeEach
    void setUp() {
        actor  = combatant(10L, "Magier", 12, neutralizeTalent(4)); // WIL 12 → Step 5, Rang 4
        target = combatant(20L, "Ziel", 10, new ArrayList<>());
        target.getActiveEffects().add(effect(500L, "Verängstigt"));
        session = CombatSession.builder()
                .id(1L).name("Test").round(1).phase(CombatPhase.ACTION)
                .combatants(new ArrayList<>(List.of(actor, target)))
                .log(new ArrayList<>())
                .build();
        lenient().when(sessionRepo.findById(1L)).thenReturn(Optional.of(session));
        lenient().when(sessionRepo.save(any(CombatSession.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(diceService.attributeToStep(12)).thenReturn(5);
    }

    @Test
    void success_removesEffect_consumesActionAndStrain() {
        // rollStep = WIL-Step 5 + Rang 4 = 9; MW = Stufe 6 + 10 = 16
        when(diceService.roll(9)).thenReturn(roll(18));

        NeutralizeMagicResult r = combatService.performNeutralizeMagic(1L, req(6));

        assertThat(r.isSuccess()).isTrue();
        assertThat(r.isEffectRemoved()).isTrue();
        assertThat(r.getTargetNumber()).isEqualTo(16); // Stufe 6 + 10
        assertThat(r.getRollStep()).isEqualTo(9);
        assertThat(r.getEffectName()).isEqualTo("Verängstigt");
        assertThat(target.getActiveEffects()).isEmpty();       // Effekt entfernt
        assertThat(actor.isHasActedThisRound()).isTrue();      // verbraucht die Aktion
        assertThat(actor.getCurrentDamage()).isEqualTo(1);     // 1 Überanstrengung
    }

    @Test
    void failure_keepsEffect_butStillCostsActionAndStrain() {
        when(diceService.roll(9)).thenReturn(roll(12)); // < MW 16

        NeutralizeMagicResult r = combatService.performNeutralizeMagic(1L, req(6));

        assertThat(r.isSuccess()).isFalse();
        assertThat(r.isEffectRemoved()).isFalse();
        assertThat(target.getActiveEffects()).hasSize(1);   // Effekt bleibt
        assertThat(actor.isHasActedThisRound()).isTrue();
        assertThat(actor.getCurrentDamage()).isEqualTo(1);
    }

    @Test
    void targetNumber_isEffectLevelPlusTen() {
        when(diceService.roll(9)).thenReturn(roll(30));
        assertThat(combatService.performNeutralizeMagic(1L, req(0)).getTargetNumber()).isEqualTo(10);

        actor.setHasActedThisRound(false);
        target.getActiveEffects().add(effect(501L, "Segen"));
        NeutralizeMagicRequest r2 = req(12);
        r2.setEffectId(501L);
        assertThat(combatService.performNeutralizeMagic(1L, r2).getTargetNumber()).isEqualTo(22);
    }

    @Test
    void exactlyTargetNumber_isSuccess() {
        when(diceService.roll(9)).thenReturn(roll(16)); // genau MW 16
        assertThat(combatService.performNeutralizeMagic(1L, req(6)).isSuccess()).isTrue();
    }

    @Test
    void karma_addedToTotal() {
        when(diceService.roll(9)).thenReturn(roll(14));
        when(diceService.roll(4)).thenReturn(roll(3)); // Karma → 17 >= 16
        actor.setCurrentKarma(2);
        NeutralizeMagicRequest req = req(6);
        req.setSpendKarma(true);

        NeutralizeMagicResult r = combatService.performNeutralizeMagic(1L, req);

        assertThat(r.getKarmaRoll()).isNotNull();
        assertThat(r.isSuccess()).isTrue();
        assertThat(actor.getCurrentKarma()).isEqualTo(1);
    }

    @Test
    void alreadyActed_throws() {
        actor.setHasActedThisRound(true);
        assertThatThrownBy(() -> combatService.performNeutralizeMagic(1L, req(6)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bereits gehandelt");
    }

    @Test
    void missingTalent_throws() {
        actor.getCharacter().getTalents().clear();
        assertThatThrownBy(() -> combatService.performNeutralizeMagic(1L, req(6)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Magie neutralisieren");
    }

    @Test
    void unknownEffect_throws() {
        NeutralizeMagicRequest req = req(6);
        req.setEffectId(999L);
        assertThatThrownBy(() -> combatService.performNeutralizeMagic(1L, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Effekt nicht gefunden");
    }

    @Test
    void notInActionPhase_throws() {
        session.setPhase(CombatPhase.DECLARATION);
        assertThatThrownBy(() -> combatService.performNeutralizeMagic(1L, req(6)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Aktionsphase");
    }

    // --- Auswahldialog (Broadcast) ---

    @Test
    void openDialog_requiresTalentAndUnusedAction() {
        combatService.openNeutralizeMagicDialog(1L, 10L); // wirft nicht

        actor.setHasActedThisRound(true);
        assertThatThrownBy(() -> combatService.openNeutralizeMagicDialog(1L, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bereits gehandelt");
    }

    @Test
    void openDialog_doesNotConsumeActionOrStrain() {
        combatService.openNeutralizeMagicDialog(1L, 10L);

        assertThat(actor.isHasActedThisRound()).isFalse();
        assertThat(actor.getCurrentDamage()).isZero();
        assertThat(target.getActiveEffects()).hasSize(1);
    }

    // --- Helpers ---

    private NeutralizeMagicRequest req(int effectLevel) {
        return NeutralizeMagicRequest.builder()
                .sessionId(1L).actorCombatantId(10L).targetCombatantId(20L)
                .effectId(500L).effectLevel(effectLevel)
                .bonusSteps(0).spendKarma(false).build();
    }

    private ActiveEffect effect(long id, String name) {
        return ActiveEffect.builder()
                .id(id).name(name).sourceType(SourceType.CONDITION)
                .remainingRounds(3).negative(true)
                .modifiers(new ArrayList<>())
                .build();
    }

    private RollResult roll(int total) {
        return RollResult.builder().total(total).diceExpression("W20").build();
    }

    private List<CharacterTalent> neutralizeTalent(int rank) {
        TalentDefinition def = TalentDefinition.builder()
                .id(77L).name(TalentNames.MAGIE_NEUTRALISIEREN).attribute(AttributeType.WILLPOWER).build();
        return new ArrayList<>(List.of(CharacterTalent.builder().id(77L).talentDefinition(def).rank(rank).build()));
    }

    private CombatantState combatant(long id, String name, int willpower, List<CharacterTalent> talents) {
        GameCharacter c = GameCharacter.builder()
                .id(id).name(name).willpower(willpower).dexterity(10).strength(10)
                .equipment(new ArrayList<>()).talents(talents)
                .skills(new ArrayList<>()).spells(new ArrayList<>())
                .build();
        return CombatantState.builder()
                .id(id).character(c).activeEffects(new ArrayList<>())
                .currentDamage(0).wounds(0).currentKarma(0).pendingRiposteAttackTotal(-1)
                .build();
    }
}
