package com.earthdawn.service;

import com.earthdawn.model.CombatantState;
import com.earthdawn.model.DisciplineDefinition;
import com.earthdawn.model.GameCharacter;
import com.earthdawn.repository.CharacterRepository;
import com.earthdawn.repository.CombatSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regelmatrix für Karma auf den Schadenswurf (disziplinabhängig).
 * Krallenhand-Waffen erlauben es generell; sonst nach Disziplin + Waffenart (+ Kreis).
 */
@ExtendWith(MockitoExtension.class)
class CombatServiceKarmaDamageTest {

    @Mock CombatSessionRepository sessionRepo;
    @Mock CharacterRepository characterRepo;
    @Mock StepRollService diceService;
    @Mock ModifierAggregator modifiers;
    @Mock SimpMessagingTemplate websocket;
    @Mock ObjectMapper objectMapper;

    @InjectMocks CombatService combatService;

    private static final String NAH = "Nahkampfwaffen";
    private static final String PRO = "Projektilwaffen";
    private static final String WURF = "Wurfwaffen";
    private static final String WAFFENLOS = "Waffenloser Kampf";

    @Test
    void clawWeapon_alwaysAllowed_regardlessOfDiscipline() {
        CombatantState c = combatant("Dieb", 1);
        assertThat(combatService.karmaOnDamageAllowed(c, true, NAH)).isTrue();
        assertThat(combatService.karmaOnDamageAllowed(c, true, null)).isTrue();
    }

    @Test
    void krieger_meleeOnlyFromCircleFive() {
        assertThat(combatService.karmaOnDamageAllowed(combatant("Krieger", 5), false, NAH)).isTrue();
        assertThat(combatService.karmaOnDamageAllowed(combatant("Krieger", 5), false, WAFFENLOS)).isTrue();
        assertThat(combatService.karmaOnDamageAllowed(combatant("Krieger", 4), false, NAH)).isFalse(); // zu niedriger Kreis
        assertThat(combatService.karmaOnDamageAllowed(combatant("Krieger", 8), false, PRO)).isFalse();  // Fernkampf
    }

    @Test
    void schuetze_rangedWeaponsOnly() {
        CombatantState c = combatant("Schütze", 3);
        assertThat(combatService.karmaOnDamageAllowed(c, false, PRO)).isTrue();
        assertThat(combatService.karmaOnDamageAllowed(c, false, WURF)).isTrue();
        assertThat(combatService.karmaOnDamageAllowed(c, false, NAH)).isFalse();
        assertThat(combatService.karmaOnDamageAllowed(c, false, WAFFENLOS)).isFalse();
    }

    @Test
    void schwertmeister_meleeWeaponOnly_notUnarmed() {
        CombatantState c = combatant("Schwertmeister", 3);
        assertThat(combatService.karmaOnDamageAllowed(c, false, NAH)).isTrue();
        assertThat(combatService.karmaOnDamageAllowed(c, false, WAFFENLOS)).isFalse();
        assertThat(combatService.karmaOnDamageAllowed(c, false, PRO)).isFalse();
    }

    @Test
    void luftpirat_meleeOrThrown() {
        CombatantState c = combatant("Luftpirat", 3);
        assertThat(combatService.karmaOnDamageAllowed(c, false, NAH)).isTrue();
        assertThat(combatService.karmaOnDamageAllowed(c, false, WURF)).isTrue();
        assertThat(combatService.karmaOnDamageAllowed(c, false, PRO)).isFalse();     // Projektil ist kein Wurf
        assertThat(combatService.karmaOnDamageAllowed(c, false, WAFFENLOS)).isFalse();
    }

    @Test
    void tiermeister_unarmedOnly() {
        CombatantState c = combatant("Tiermeister", 3);
        assertThat(combatService.karmaOnDamageAllowed(c, false, WAFFENLOS)).isTrue();
        assertThat(combatService.karmaOnDamageAllowed(c, false, NAH)).isFalse();
    }

    @Test
    void otherDiscipline_neverAllowed_withoutClaw() {
        assertThat(combatService.karmaOnDamageAllowed(combatant("Dieb", 8), false, NAH)).isFalse();
        assertThat(combatService.karmaOnDamageAllowed(combatant("Illusionist", 8), false, PRO)).isFalse();
    }

    private CombatantState combatant(String discipline, int circle) {
        GameCharacter c = GameCharacter.builder()
                .id(1L).name("Held").circle(circle)
                .discipline(DisciplineDefinition.builder().name(discipline).build())
                .equipment(new ArrayList<>()).talents(new ArrayList<>())
                .skills(new ArrayList<>()).spells(new ArrayList<>())
                .build();
        return CombatantState.builder().id(1L).character(c).activeEffects(new ArrayList<>()).build();
    }
}
