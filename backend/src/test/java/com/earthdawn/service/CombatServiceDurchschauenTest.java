package com.earthdawn.service;

import com.earthdawn.model.ActiveEffect;
import com.earthdawn.model.CombatSession;
import com.earthdawn.model.CombatantState;
import com.earthdawn.model.GameCharacter;
import com.earthdawn.model.TalentNames;
import com.earthdawn.model.enums.ActionType;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Blindheit durchschauen: Eine Aktionsprobe des Geblendeten mit Ergebnis über 17 beendet den
 * Blindheit-Effekt (trotz des −4-Malus, der bereits im Wurf steckt).
 */
@ExtendWith(MockitoExtension.class)
class CombatServiceDurchschauenTest {

    @Mock CombatSessionRepository sessionRepo;
    @Mock CharacterRepository characterRepo;
    @Mock StepRollService diceService;
    @Mock ModifierAggregator modifiers;
    @Mock SimpMessagingTemplate websocket;
    @Mock ObjectMapper objectMapper;

    @InjectMocks
    CombatService combatService;

    private CombatSession session;
    private CombatantState blinded;

    @BeforeEach
    void setUp() {
        session = CombatSession.builder().id(1L).name("Test")
                .combatants(new ArrayList<>()).log(new ArrayList<>()).build();
        GameCharacter c = GameCharacter.builder().id(1L).name("Kaelen")
                .equipment(new ArrayList<>()).talents(new ArrayList<>())
                .skills(new ArrayList<>()).spells(new ArrayList<>())
                .build();
        blinded = CombatantState.builder().id(10L).character(c)
                .activeEffects(new ArrayList<>()).build();
        blinded.getActiveEffects().add(blindheitEffekt());
        session.getCombatants().add(blinded);
    }

    @Test
    void rollOver17_removesBlindheitAndLogs() {
        combatService.durchschauenCheck(session, blinded, 18);

        assertThat(blinded.getActiveEffects()).isEmpty();
        assertThat(session.getLog()).hasSize(1);
        assertThat(session.getLog().get(0).getActionType()).isEqualTo(ActionType.EFFECT_REMOVED);
        assertThat(session.getLog().get(0).getDescription()).contains("durchschaut die Blindheit");
    }

    @Test
    void rollOfExactly17_keepsBlindheit() {
        combatService.durchschauenCheck(session, blinded, 17);

        assertThat(blinded.getActiveEffects()).hasSize(1);
        assertThat(session.getLog()).isEmpty();
    }

    @Test
    void withoutBlindheit_highRollLogsNothing() {
        blinded.getActiveEffects().clear();
        blinded.getActiveEffects().add(ActiveEffect.builder()
                .name(TalentNames.EFFECT_VERAENGSTIGT).negative(true)
                .modifiers(new ArrayList<>()).build());

        combatService.durchschauenCheck(session, blinded, 25);

        // Andere Effekte bleiben unangetastet, kein Log-Eintrag
        assertThat(blinded.getActiveEffects()).hasSize(1);
        assertThat(session.getLog()).isEmpty();
    }

    @Test
    void removesOnlyTheBlindheitEffect() {
        blinded.getActiveEffects().add(ActiveEffect.builder()
                .name(TalentNames.EFFECT_VERAENGSTIGT).negative(true)
                .modifiers(new ArrayList<>()).build());

        combatService.durchschauenCheck(session, blinded, 20);

        assertThat(blinded.getActiveEffects()).hasSize(1);
        assertThat(blinded.getActiveEffects().get(0).getName())
                .isEqualTo(TalentNames.EFFECT_VERAENGSTIGT);
    }

    private ActiveEffect blindheitEffekt() {
        return ActiveEffect.builder()
                .name(TalentNames.EFFECT_BLINDHEIT)
                .negative(true)
                .remainingRounds(23)
                .modifiers(new ArrayList<>())
                .build();
    }
}
