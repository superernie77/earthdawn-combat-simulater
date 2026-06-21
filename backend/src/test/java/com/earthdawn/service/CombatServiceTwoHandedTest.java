package com.earthdawn.service;

import com.earthdawn.dto.CombatActionResult;
import com.earthdawn.model.*;
import com.earthdawn.model.enums.EquipmentType;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests für die Ein-/Zweihand-Schild-Automatik (CombatService.applyTwoHandedShieldRule).
 * Direkter Unit-Test der Hilfsmethode.
 */
@ExtendWith(MockitoExtension.class)
class CombatServiceTwoHandedTest {

    @Mock CombatSessionRepository sessionRepo;
    @Mock CharacterRepository characterRepo;
    @Mock StepRollService diceService;
    @Mock ModifierAggregator modifiers;
    @Mock SimpMessagingTemplate websocket;
    @Mock ObjectMapper objectMapper;

    @InjectMocks CombatService combatService;

    @Test
    void twoHandedWeapon_stowsActiveNonBucklerShield() {
        Equipment shield = shield(1L, "Rundschild", true, false, false);
        CombatantState attacker = combatantWith(shield);
        CombatActionResult.CombatActionResultBuilder result = CombatActionResult.builder();

        boolean changed = combatService.applyTwoHandedShieldRule(attacker, weapon(true), result);

        assertThat(changed).isTrue();
        assertThat(shield.isActive()).isFalse();
        assertThat(shield.isAutoStowed()).isTrue();
        assertThat(result.build().getShieldStowedName()).isEqualTo("Rundschild");
    }

    @Test
    void twoHandedWeapon_keepsBucklerActive() {
        Equipment buckler = shield(1L, "Buckler", true, true, false);
        CombatantState attacker = combatantWith(buckler);
        CombatActionResult.CombatActionResultBuilder result = CombatActionResult.builder();

        boolean changed = combatService.applyTwoHandedShieldRule(attacker, weapon(true), result);

        assertThat(changed).isFalse();
        assertThat(buckler.isActive()).isTrue();
        assertThat(buckler.isAutoStowed()).isFalse();
        assertThat(result.build().getShieldStowedName()).isNull();
    }

    @Test
    void oneHandedWeapon_restoresAutoStowedShield() {
        Equipment shield = shield(1L, "Rundschild", false, false, true); // abgelegt, autoStowed
        CombatantState attacker = combatantWith(shield);
        CombatActionResult.CombatActionResultBuilder result = CombatActionResult.builder();

        boolean changed = combatService.applyTwoHandedShieldRule(attacker, weapon(false), result);

        assertThat(changed).isTrue();
        assertThat(shield.isActive()).isTrue();
        assertThat(shield.isAutoStowed()).isFalse();
        assertThat(result.build().getShieldRestoredName()).isEqualTo("Rundschild");
    }

    @Test
    void oneHandedWeapon_doesNotRestoreManuallyStowedShield() {
        Equipment shield = shield(1L, "Rundschild", false, false, false); // manuell abgelegt
        CombatantState attacker = combatantWith(shield);
        CombatActionResult.CombatActionResultBuilder result = CombatActionResult.builder();

        boolean changed = combatService.applyTwoHandedShieldRule(attacker, weapon(false), result);

        assertThat(changed).isFalse();
        assertThat(shield.isActive()).isFalse();
        assertThat(result.build().getShieldRestoredName()).isNull();
    }

    @Test
    void noWeapon_changesNothing() {
        Equipment shield = shield(1L, "Rundschild", true, false, false);
        CombatantState attacker = combatantWith(shield);

        boolean changed = combatService.applyTwoHandedShieldRule(attacker, null, CombatActionResult.builder());

        assertThat(changed).isFalse();
        assertThat(shield.isActive()).isTrue();
    }

    // --- Helpers ---

    private CombatantState combatantWith(Equipment... equipment) {
        GameCharacter c = GameCharacter.builder()
                .id(1L).name("Held")
                .equipment(new ArrayList<>(List.of(equipment)))
                .talents(new ArrayList<>()).skills(new ArrayList<>())
                .build();
        return CombatantState.builder()
                .id(10L).character(c).activeEffects(new ArrayList<>())
                .build();
    }

    private Equipment shield(long id, String name, boolean active, boolean buckler, boolean autoStowed) {
        return Equipment.builder()
                .id(id).name(name).type(EquipmentType.SHIELD)
                .active(active).buckler(buckler).autoStowed(autoStowed)
                .physicalDefenseBonus(3)
                .build();
    }

    private Equipment weapon(boolean twoHanded) {
        return Equipment.builder()
                .id(99L).name(twoHanded ? "Zweihänder" : "Schwert").type(EquipmentType.WEAPON)
                .twoHanded(twoHanded)
                .build();
    }
}
