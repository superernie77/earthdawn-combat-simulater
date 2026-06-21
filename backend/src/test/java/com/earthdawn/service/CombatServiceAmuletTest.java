package com.earthdawn.service;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests für die Verzweiflungsschlag-Amulett-Anwendung (CombatService.applyAmulets).
 * Direkter Unit-Test der Hilfsmethode — keine Session-/Würfel-Mocks nötig.
 */
@ExtendWith(MockitoExtension.class)
class CombatServiceAmuletTest {

    @Mock CombatSessionRepository sessionRepo;
    @Mock CharacterRepository characterRepo;
    @Mock StepRollService diceService;
    @Mock ModifierAggregator modifiers;
    @Mock SimpMessagingTemplate websocket;
    @Mock ObjectMapper objectMapper;

    @InjectMocks CombatService combatService;

    @Test
    void physicalAmulet_appliesBonus_andDischarges() {
        Equipment amulet = amulet(1L, false, true, 6);
        CombatantState cs = combatantWith(amulet);
        List<String> notes = new ArrayList<>();

        int bonus = combatService.applyAmulets(cs, List.of(1L), false, "Angriff", notes);

        assertThat(bonus).isEqualTo(6);
        assertThat(amulet.isCharged()).isFalse();
        assertThat(notes).hasSize(1);
        assertThat(notes.get(0)).contains("+6");
    }

    @Test
    void multipleAmulets_sumBonus_andAllDischarged() {
        Equipment a1 = amulet(1L, false, true, 6);
        Equipment a2 = amulet(2L, false, true, 6);
        CombatantState cs = combatantWith(a1, a2);
        List<String> notes = new ArrayList<>();

        int bonus = combatService.applyAmulets(cs, List.of(1L, 2L), false, "Schaden", notes);

        assertThat(bonus).isEqualTo(12);
        assertThat(a1.isCharged()).isFalse();
        assertThat(a2.isCharged()).isFalse();
    }

    @Test
    void notCharged_throws() {
        CombatantState cs = combatantWith(amulet(1L, false, false, 6)); // entladen
        assertThatThrownBy(() -> combatService.applyAmulets(cs, List.of(1L), false, "Angriff", new ArrayList<>()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nicht geladen");
    }

    @Test
    void wrongKind_throws() {
        // Zauber-Amulett, aber für physischen Angriff angefordert (forSpell=false)
        CombatantState cs = combatantWith(amulet(1L, true, true, 6));
        assertThatThrownBy(() -> combatService.applyAmulets(cs, List.of(1L), false, "Angriff", new ArrayList<>()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("passt nicht");
    }

    @Test
    void notAnAmulet_throws() {
        Equipment weapon = Equipment.builder().id(2L).name("Schwert").type(EquipmentType.WEAPON).build();
        CombatantState cs = combatantWith(weapon);
        assertThatThrownBy(() -> combatService.applyAmulets(cs, List.of(2L), false, "Angriff", new ArrayList<>()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kein Amulett");
    }

    @Test
    void emptyOrNullList_returnsZero() {
        CombatantState cs = combatantWith(amulet(1L, false, true, 6));
        assertThat(combatService.applyAmulets(cs, null, false, "Angriff", new ArrayList<>())).isZero();
        assertThat(combatService.applyAmulets(cs, List.of(), false, "Angriff", new ArrayList<>())).isZero();
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

    private Equipment amulet(long id, boolean forSpell, boolean charged, int stepBonus) {
        return Equipment.builder()
                .id(id).name("Verzweiflungsschlag-Amulett").type(EquipmentType.AMULET)
                .amuletForSpell(forSpell).charged(charged).amuletStepBonus(stepBonus)
                .bloodMagicDamage(3)
                .build();
    }
}
