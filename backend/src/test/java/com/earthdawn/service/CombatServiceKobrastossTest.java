package com.earthdawn.service;

import com.earthdawn.dto.KobrastossResult;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Kobrastoß: Ansage in der Ansagephase (2 Überanstrengung, 1×/Runde, +Rang auf die
 * Initiativeprobe) und Auswertung gegen die Initiative des angesagten Gegners
 * (+2 je Erfolg auf die erste Angriffsprobe gegen genau diesen Gegner).
 */
@ExtendWith(MockitoExtension.class)
class CombatServiceKobrastossTest {

    @Mock CombatSessionRepository sessionRepo;
    @Mock CharacterRepository characterRepo;
    @Mock StepRollService diceService;
    @Mock ModifierAggregator modifiers;
    @Mock SimpMessagingTemplate websocket;
    @Mock ObjectMapper objectMapper;

    @InjectMocks CombatService combatService;

    private CombatSession session;
    private CombatantState actor;
    private CombatantState target;

    @BeforeEach
    void setUp() {
        actor  = combatant(10L, "Sarin", 5);
        target = combatant(20L, "Ork", 0);

        session = CombatSession.builder()
                .id(1L)
                .round(1)
                .phase(CombatPhase.DECLARATION)
                .status(CombatStatus.ACTIVE)
                .combatants(new ArrayList<>(List.of(actor, target)))
                .log(new ArrayList<>())
                .build();

        lenient().when(sessionRepo.findById(anyLong())).thenReturn(Optional.of(session));
        lenient().when(sessionRepo.save(any(CombatSession.class))).thenAnswer(i -> i.getArgument(0));
    }

    // --- Ansage ---

    @Test
    void ansage_kostet2Ueberanstrengung_undGibtInitiativeStufenBonus() {
        KobrastossResult r = combatService.performKobrastoss(1L, 10L, 20L);

        assertThat(r.getRank()).isEqualTo(5);
        assertThat(r.getInitiativeBonus()).isEqualTo(5);
        assertThat(r.getDamageTaken()).isEqualTo(2);
        assertThat(r.getTargetName()).isEqualTo("Ork");
        assertThat(actor.getCurrentDamage()).isEqualTo(2);
        assertThat(actor.isKobrastossUsedThisRound()).isTrue();
        assertThat(actor.getKobrastossTargetId()).isEqualTo(20L);

        ActiveEffect effect = actor.getActiveEffects().stream()
                .filter(e -> TalentNames.KOBRASTOSS.equals(e.getName()))
                .findFirst().orElseThrow();
        ModifierEntry mod = effect.getModifiers().get(0);
        assertThat(mod.getTargetStat()).isEqualTo(StatType.INITIATIVE_STEP);
        assertThat(mod.getOperation()).isEqualTo(ModifierOperation.ADD);
        assertThat(mod.getValue()).isEqualTo(5);
        assertThat(mod.getTriggerContext()).isEqualTo(TriggerContext.ON_INITIATIVE);
        assertThat(effect.getRemainingRounds()).isEqualTo(1);
    }

    @Test
    void ansage_nurEinmalProRunde() {
        combatService.performKobrastoss(1L, 10L, 20L);
        assertThatThrownBy(() -> combatService.performKobrastoss(1L, 10L, 20L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bereits in dieser Runde");
    }

    @Test
    void ansage_nurInDerAnsagephase() {
        session.setPhase(CombatPhase.ACTION);
        assertThatThrownBy(() -> combatService.performKobrastoss(1L, 10L, 20L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ansagephase");
    }

    @Test
    void ansage_ohneTalent_schlaegtFehl() {
        actor.getCharacter().getTalents().clear();
        assertThatThrownBy(() -> combatService.performKobrastoss(1L, 10L, 20L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Kobrastoß");
    }

    @Test
    void ansage_nichtGegenSichSelbst() {
        assertThatThrownBy(() -> combatService.performKobrastoss(1L, 10L, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gegnerischen");
    }

    // --- Auswertung nach dem Initiativewurf ---

    @Test
    void auswertung_einErfolgBeiGleichstand() {
        ansagen();
        actor.setInitiative(18);
        target.setInitiative(18);

        combatService.resolveKobrastoss(session);

        assertThat(actor.getPendingKobrastossBonus()).isEqualTo(2);   // 1 Erfolg × 2
    }

    @Test
    void auswertung_jeFuenfUeberGegnerEinWeitererErfolg() {
        ansagen();
        actor.setInitiative(24);
        target.setInitiative(13);   // +11 → 1 + 11/5 = 3 Erfolge

        combatService.resolveKobrastoss(session);

        assertThat(actor.getPendingKobrastossBonus()).isEqualTo(6);
    }

    @Test
    void auswertung_keinBonusWennGegnerHoeherWuerfelt() {
        ansagen();
        actor.setInitiative(12);
        target.setInitiative(13);

        combatService.resolveKobrastoss(session);

        assertThat(actor.getPendingKobrastossBonus()).isZero();
    }

    @Test
    void auswertung_ohneAnsage_passiertNichts() {
        actor.setInitiative(30);
        target.setInitiative(5);

        combatService.resolveKobrastoss(session);

        assertThat(actor.getPendingKobrastossBonus()).isZero();
    }

    @Test
    void auswertung_entferntesZiel_laesstAnsageVerfallen() {
        ansagen();
        session.getCombatants().remove(target);

        combatService.resolveKobrastoss(session);

        assertThat(actor.getKobrastossTargetId()).isEqualTo(-1L);
        assertThat(actor.getPendingKobrastossBonus()).isZero();
    }

    // --- Verbrauch beim Angriff ---

    @Test
    void bonus_giltNurGegenDasAngesagteZiel() {
        ansagen();
        actor.setPendingKobrastossBonus(4);
        CombatantState anderer = combatant(30L, "Goblin", 0);

        assertThat(combatService.consumeKobrastossBonus(actor, anderer)).isZero();
        assertThat(actor.getPendingKobrastossBonus()).isEqualTo(4);   // bleibt erhalten
        assertThat(combatService.consumeKobrastossBonus(actor, target)).isEqualTo(4);
    }

    @Test
    void bonus_giltNurFuerDenErstenAngriff() {
        ansagen();
        actor.setPendingKobrastossBonus(4);

        assertThat(combatService.consumeKobrastossBonus(actor, target)).isEqualTo(4);
        assertThat(combatService.consumeKobrastossBonus(actor, target)).isZero();
        assertThat(actor.getKobrastossTargetId()).isEqualTo(-1L);
    }

    // --- Rundenwechsel ---

    @Test
    void nextRound_setztKobrastossZurueck() {
        ansagen();
        actor.setPendingKobrastossBonus(6);

        combatService.nextRound(1L);

        assertThat(actor.isKobrastossUsedThisRound()).isFalse();
        assertThat(actor.getKobrastossTargetId()).isEqualTo(-1L);
        assertThat(actor.getPendingKobrastossBonus()).isZero();
    }

    // --- Helfer ---

    private void ansagen() {
        combatService.performKobrastoss(1L, 10L, 20L);
    }

    private CombatantState combatant(Long id, String name, int kobrastossRank) {
        List<CharacterTalent> talents = new ArrayList<>();
        if (kobrastossRank > 0) {
            talents.add(CharacterTalent.builder()
                    .id(id)
                    .rank(kobrastossRank)
                    .talentDefinition(TalentDefinition.builder()
                            .id(id).name(TalentNames.KOBRASTOSS)
                            .attribute(AttributeType.DEXTERITY).build())
                    .build());
        }
        GameCharacter c = GameCharacter.builder()
                .id(id).name(name).dexterity(15)
                .talents(talents)
                .equipment(new ArrayList<>())
                .skills(new ArrayList<>())
                .spells(new ArrayList<>())
                .build();
        return CombatantState.builder()
                .id(id).character(c)
                .activeEffects(new ArrayList<>())
                .kobrastossTargetId(-1L)
                .build();
    }
}
