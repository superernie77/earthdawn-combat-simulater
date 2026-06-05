package com.earthdawn.service;

import com.earthdawn.model.Equipment;
import com.earthdawn.model.GameCharacter;
import com.earthdawn.model.enums.EquipmentType;
import com.earthdawn.repository.CharacterRepository;
import com.earthdawn.repository.SkillDefinitionRepository;
import com.earthdawn.repository.SpellDefinitionRepository;
import com.earthdawn.repository.TalentDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit-Tests für die Aktiv/Inaktiv-Logik bei Rüstungen und Schilden.
 * Kein Spring-Context — reine Mockito-Tests (schnell).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CharacterServiceEquipmentActiveTest {

    @Mock private CharacterRepository characterRepo;
    @Mock private TalentDefinitionRepository talentDefRepo;
    @Mock private SkillDefinitionRepository skillDefRepo;
    @Mock private SpellDefinitionRepository spellDefRepo;
    @Mock private ModifierAggregator modifierAggregator;
    @Mock private StepRollService stepRollService;

    private CharacterService service;

    @BeforeEach
    void setUp() {
        service = new CharacterService(characterRepo, talentDefRepo, skillDefRepo,
                spellDefRepo, modifierAggregator, stepRollService);
        // save() always returns whatever is passed in
        when(characterRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // --- Hilfsmethoden ---

    private GameCharacter makeCharacter(Long id) {
        GameCharacter c = GameCharacter.builder()
                .name("Test")
                .dexterity(10).strength(10).toughness(10)
                .perception(10).willpower(10).charisma(10)
                .equipment(new ArrayList<>())
                .talents(new ArrayList<>())
                .skills(new ArrayList<>())
                .spells(new ArrayList<>())
                .build();
        // Setze ID via Reflection (kein Setter vorhanden, aber wir können Optional mocken)
        when(characterRepo.findById(id)).thenReturn(Optional.of(c));
        return c;
    }

    private Equipment equip(Long id, EquipmentType type, boolean active) {
        Equipment e = Equipment.builder()
                .name("Item " + id)
                .type(type)
                .active(active)
                .build();
        // Setze ID via Reflections — Equipment.id ist private final in Lombok @Data
        // Wir nutzen stattdessen eine separate List und filtern nach Objekt-Referenz.
        // Für den Test genügt das Setzen über Reflection:
        try {
            java.lang.reflect.Field f = Equipment.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(e, id);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return e;
    }

    // --- addEquipment: neue Rüstung → deaktiviert vorhandene ---

    @Test
    void addArmor_deactivatesPreviousActiveArmor() {
        GameCharacter c = makeCharacter(1L);
        Equipment existingArmor = equip(10L, EquipmentType.ARMOR, true);
        c.getEquipment().add(existingArmor);

        Equipment newArmor = Equipment.builder().name("Neue Rüstung").type(EquipmentType.ARMOR)
                .physicalArmor(5).active(true).build();

        service.addEquipment(1L, newArmor);

        assertThat(existingArmor.isActive()).isFalse();
        assertThat(newArmor.isActive()).isTrue();
    }

    @Test
    void addShield_deactivatesPreviousActiveShield() {
        GameCharacter c = makeCharacter(1L);
        Equipment existingShield = equip(20L, EquipmentType.SHIELD, true);
        c.getEquipment().add(existingShield);

        Equipment newShield = Equipment.builder().name("Neues Schild").type(EquipmentType.SHIELD)
                .physicalDefenseBonus(2).active(true).build();

        service.addEquipment(1L, newShield);

        assertThat(existingShield.isActive()).isFalse();
        assertThat(newShield.isActive()).isTrue();
    }

    @Test
    void addArmor_doesNotAffectShields() {
        GameCharacter c = makeCharacter(1L);
        Equipment existingShield = equip(30L, EquipmentType.SHIELD, true);
        c.getEquipment().add(existingShield);

        Equipment newArmor = Equipment.builder().name("Rüstung").type(EquipmentType.ARMOR)
                .physicalArmor(4).build();

        service.addEquipment(1L, newArmor);

        // Schild bleibt aktiv, nur Rüstungen desselben Typs werden deaktiviert
        assertThat(existingShield.isActive()).isTrue();
    }

    // --- setEquipmentActive: Aktivierung erzwingt Exklusivität ---

    @Test
    void activateArmor_deactivatesOtherArmors() {
        GameCharacter c = makeCharacter(1L);
        Equipment armor1 = equip(10L, EquipmentType.ARMOR, true);
        Equipment armor2 = equip(11L, EquipmentType.ARMOR, false);
        c.getEquipment().add(armor1);
        c.getEquipment().add(armor2);

        service.setEquipmentActive(1L, 11L, true);

        assertThat(armor1.isActive()).isFalse();
        assertThat(armor2.isActive()).isTrue();
    }

    @Test
    void deactivateArmor_leavesOthersUntouched() {
        GameCharacter c = makeCharacter(1L);
        Equipment armor1 = equip(10L, EquipmentType.ARMOR, true);
        Equipment armor2 = equip(11L, EquipmentType.ARMOR, false);
        c.getEquipment().add(armor1);
        c.getEquipment().add(armor2);

        service.setEquipmentActive(1L, 10L, false);

        assertThat(armor1.isActive()).isFalse();
        assertThat(armor2.isActive()).isFalse(); // war schon false, bleibt false
    }

    @Test
    void activateShield_deactivatesOtherShields() {
        GameCharacter c = makeCharacter(1L);
        Equipment shield1 = equip(20L, EquipmentType.SHIELD, true);
        Equipment shield2 = equip(21L, EquipmentType.SHIELD, false);
        c.getEquipment().add(shield1);
        c.getEquipment().add(shield2);

        service.setEquipmentActive(1L, 21L, true);

        assertThat(shield1.isActive()).isFalse();
        assertThat(shield2.isActive()).isTrue();
    }

    @Test
    void activateArmor_doesNotAffectShields() {
        GameCharacter c = makeCharacter(1L);
        Equipment armor = equip(10L, EquipmentType.ARMOR, false);
        Equipment shield = equip(20L, EquipmentType.SHIELD, true);
        c.getEquipment().add(armor);
        c.getEquipment().add(shield);

        service.setEquipmentActive(1L, 10L, true);

        assertThat(armor.isActive()).isTrue();
        assertThat(shield.isActive()).isTrue(); // Schild bleibt aktiv
    }

    @Test
    void setEquipmentActive_throwsForWeapon() {
        GameCharacter c = makeCharacter(1L);
        Equipment weapon = equip(50L, EquipmentType.WEAPON, true);
        c.getEquipment().add(weapon);

        assertThatThrownBy(() -> service.setEquipmentActive(1L, 50L, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rüstungen und Schilde");
    }

    @Test
    void setEquipmentActive_throwsForPotion() {
        GameCharacter c = makeCharacter(1L);
        Equipment potion = equip(60L, EquipmentType.POTION, true);
        c.getEquipment().add(potion);

        assertThatThrownBy(() -> service.setEquipmentActive(1L, 60L, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rüstungen und Schilde");
    }
}
