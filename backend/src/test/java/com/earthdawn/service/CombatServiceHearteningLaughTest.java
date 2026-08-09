package com.earthdawn.service;

import com.earthdawn.dto.HearteningLaughRequest;
import com.earthdawn.dto.HearteningLaughResult;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Herzliches Lachen: Einfache Aktion (verbraucht keine Hauptaktion), 1 Überanstrengung,
 * CHA + Rang vs. höchste Soziale VK der Gegner. Buff auf Soziale VK + Furcht-Widerstand aller
 * Verbündeten für Rang Runden.
 */
@ExtendWith(MockitoExtension.class)
class CombatServiceHearteningLaughTest {

    @Mock CombatSessionRepository sessionRepo;
    @Mock CharacterRepository characterRepo;
    @Mock StepRollService diceService;
    @Mock ModifierAggregator modifiers;
    @Mock SimpMessagingTemplate websocket;
    @Mock ObjectMapper objectMapper;

    @InjectMocks CombatService combatService;

    private CombatSession session;
    private CombatantState hero;    // Anwender (Held), Rang 3, CHA-Stufe 6
    private CombatantState ally;    // Verbündeter (Held)
    private CombatantState enemyA;  // Gegner, Soziale VK 7
    private CombatantState enemyB;  // Gegner, Soziale VK 10 (höchste)

    @BeforeEach
    void setUp() {
        hero   = combatant(10L, "Held", false, herzlichesLachen(3));
        ally   = combatant(11L, "Gefährte", false, new ArrayList<>());
        enemyA = combatant(20L, "Ork A", true, new ArrayList<>());
        enemyB = combatant(21L, "Ork B", true, new ArrayList<>());
        session = CombatSession.builder()
                .id(1L).name("Test").phase(CombatPhase.ACTION)
                .combatants(new ArrayList<>(List.of(hero, ally, enemyA, enemyB)))
                .log(new ArrayList<>())
                .build();

        lenient().when(sessionRepo.findById(1L)).thenReturn(Optional.of(session));
        lenient().when(sessionRepo.save(any(CombatSession.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(diceService.attributeToStep(14)).thenReturn(6); // CHA 14 → Stufe 6
        lenient().when(modifiers.getEffectiveValue(eq(enemyA), eq(StatType.SOCIAL_DEFENSE), any())).thenReturn(7);
        lenient().when(modifiers.getEffectiveValue(eq(enemyB), eq(StatType.SOCIAL_DEFENSE), any())).thenReturn(10);
    }

    @Test
    void success_buffsAllAllies_doesNotConsumeMainAction_costsStrain() {
        // TN = höchste SV = 10; Wurf 20 → 1 + (20−10)/5 = 3 Erfolge → Bonus 6
        when(diceService.roll(9)).thenReturn(roll(20)); // rollStep = CHA 6 + Rang 3

        HearteningLaughResult r = combatService.performHearteningLaugh(1L, req());

        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getTargetNumber()).isEqualTo(10);
        assertThat(r.getSuccesses()).isEqualTo(3);
        assertThat(r.getBonus()).isEqualTo(6);
        assertThat(r.getDuration()).isEqualTo(3); // Rang
        assertThat(r.getAffectedAllies()).containsExactlyInAnyOrder("Held", "Gefährte");

        // Buff auf Held und Verbündetem, nicht auf Gegnern
        assertThat(hasLaughBuff(hero, 6)).isTrue();
        assertThat(hasLaughBuff(ally, 6)).isTrue();
        assertThat(hasLaughBuff(enemyA, 6)).isFalse();

        // Einfache Aktion: Hauptaktion bleibt unangetastet; 1 Überanstrengung
        assertThat(hero.isHasActedThisRound()).isFalse();
        assertThat(hero.getCurrentDamage()).isEqualTo(1);
    }

    @Test
    void failure_buffsNobody_butStillCostsStrain() {
        when(diceService.roll(9)).thenReturn(roll(8)); // 8 < SV 10

        HearteningLaughResult r = combatService.performHearteningLaugh(1L, req());

        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getAffectedAllies()).isEmpty();
        assertThat(hasLaughBuff(hero, 6)).isFalse();
        assertThat(hero.getCurrentDamage()).isEqualTo(1);
    }

    @Test
    void noEnemies_autoSucceedsAgainstTnZero() {
        session.getCombatants().removeIf(CombatantState::isNpc);
        when(diceService.roll(9)).thenReturn(roll(3));

        HearteningLaughResult r = combatService.performHearteningLaugh(1L, req());

        assertThat(r.getTargetNumber()).isZero();
        assertThat(r.isSuccess()).isTrue();
        assertThat(hasLaughBuff(hero, 2)).isTrue(); // 1 Erfolg → Bonus 2
    }

    @Test
    void reapply_replacesExistingBuff() {
        when(diceService.roll(9)).thenReturn(roll(20));
        combatService.performHearteningLaugh(1L, req());
        // zweiter Einsatz (andere Runde) mit schwächerem Ergebnis
        hero.setCurrentDamage(0);
        when(diceService.roll(9)).thenReturn(roll(12)); // 1 Erfolg → Bonus 2
        combatService.performHearteningLaugh(1L, req());

        long count = hero.getActiveEffects().stream()
                .filter(e -> TalentNames.EFFECT_HERZLICHES_LACHEN.equals(e.getName())).count();
        assertThat(count).isEqualTo(1);
        assertThat(hasLaughBuff(hero, 2)).isTrue();
    }

    @Test
    void withoutTalent_throws() {
        hero.getCharacter().setTalents(new ArrayList<>());
        assertThatThrownBy(() -> combatService.performHearteningLaugh(1L, req()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Herzliches Lachen");
    }

    // --- Zusammenspiel mit Furcht-Widerstand ---

    @Test
    void resistFear_addsLaughBonusToWillpowerTest() {
        // Verängstigt-Effekt (Widerstand-TN 12) + Herzliches-Lachen-Buff (+4) auf demselben Kombattanten
        ally.getActiveEffects().add(ActiveEffect.builder()
                .name(TalentNames.EFFECT_VERAENGSTIGT).negative(true).remainingRounds(3)
                .resistTargetNumber(12)
                .modifiers(new ArrayList<>()).build());
        ally.getActiveEffects().add(laughBuff(4));
        when(diceService.attributeToStep(10)).thenReturn(5); // WIL 10 → Stufe 5
        when(diceService.roll(9)).thenReturn(roll(14)); // resistStep = WIL 5 + Laugh 4 = 9

        combatService.resistFear(1L, 11L);

        // Wurfstufe muss den Herzliches-Lachen-Bonus enthalten
        org.mockito.Mockito.verify(diceService).roll(9);
    }

    // --- Helpers ---

    private boolean hasLaughBuff(CombatantState c, int expectedValue) {
        return c.getActiveEffects().stream()
                .filter(e -> TalentNames.EFFECT_HERZLICHES_LACHEN.equals(e.getName()))
                .flatMap(e -> e.getModifiers().stream())
                .anyMatch(m -> m.getTargetStat() == StatType.SOCIAL_DEFENSE
                        && (int) m.getValue() == expectedValue
                        && m.getTriggerContext() == TriggerContext.ON_SOCIAL_ACTION);
    }

    private ActiveEffect laughBuff(int value) {
        return ActiveEffect.builder()
                .name(TalentNames.EFFECT_HERZLICHES_LACHEN).negative(false).remainingRounds(3)
                .modifiers(new ArrayList<>(List.of(ModifierEntry.builder()
                        .targetStat(StatType.SOCIAL_DEFENSE).operation(ModifierOperation.ADD)
                        .value(value).triggerContext(TriggerContext.ON_SOCIAL_ACTION).build())))
                .build();
    }

    private HearteningLaughRequest req() {
        HearteningLaughRequest r = new HearteningLaughRequest();
        r.setSessionId(1L);
        r.setActorCombatantId(10L);
        r.setBonusSteps(0);
        r.setSpendKarma(false);
        return r;
    }

    private RollResult roll(int total) {
        return RollResult.builder().total(total).diceExpression("W6").build();
    }

    private List<CharacterTalent> herzlichesLachen(int rank) {
        TalentDefinition def = TalentDefinition.builder()
                .id(99L).name(TalentNames.HERZLICHES_LACHEN).attribute(AttributeType.CHARISMA).build();
        return new ArrayList<>(List.of(CharacterTalent.builder().id(99L).talentDefinition(def).rank(rank).build()));
    }

    private CombatantState combatant(long id, String name, boolean npc, List<CharacterTalent> talents) {
        GameCharacter c = GameCharacter.builder()
                .id(id).name(name).charisma(14).willpower(10).dexterity(10).strength(10)
                .equipment(new ArrayList<>()).talents(talents)
                .skills(new ArrayList<>()).spells(new ArrayList<>())
                .build();
        return CombatantState.builder()
                .id(id).character(c).isNpc(npc).activeEffects(new ArrayList<>())
                .currentDamage(0).wounds(0).pendingRiposteAttackTotal(-1)
                .build();
    }
}
