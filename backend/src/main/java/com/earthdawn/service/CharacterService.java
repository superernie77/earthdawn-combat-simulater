package com.earthdawn.service;

import com.earthdawn.dto.DerivedStats;
import com.earthdawn.dto.FieldUpdateRequest;
import com.earthdawn.model.*;
import com.earthdawn.model.enums.EquipmentType;
import com.earthdawn.model.enums.StatType;
import com.earthdawn.repository.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CharacterService {

    private final CharacterRepository characterRepo;
    private final TalentDefinitionRepository talentDefRepo;
    private final SkillDefinitionRepository skillDefRepo;
    private final SpellDefinitionRepository spellDefRepo;
    private final ModifierAggregator modifierAggregator;

    public List<GameCharacter> findAll() {
        return characterRepo.findByOrderByNameAsc();
    }

    public GameCharacter findById(Long id) {
        return characterRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Charakter nicht gefunden: " + id));
    }

    public GameCharacter create(GameCharacter character) {
        return characterRepo.save(character);
    }

    public GameCharacter update(Long id, GameCharacter updated) {
        GameCharacter existing = findById(id);
        existing.setName(updated.getName());
        existing.setPlayerName(updated.getPlayerName());
        existing.setCircle(updated.getCircle());
        existing.setLegendPoints(updated.getLegendPoints());
        existing.setDiscipline(updated.getDiscipline());
        existing.setDexterity(updated.getDexterity());
        existing.setStrength(updated.getStrength());
        existing.setToughness(updated.getToughness());
        existing.setPerception(updated.getPerception());
        existing.setWillpower(updated.getWillpower());
        existing.setCharisma(updated.getCharisma());
        existing.setPhysicalDefense(updated.getPhysicalDefense());
        existing.setSpellDefense(updated.getSpellDefense());
        existing.setSocialDefense(updated.getSocialDefense());
        existing.setPhysicalDefenseBonus(updated.getPhysicalDefenseBonus());
        existing.setSpellDefenseBonus(updated.getSpellDefenseBonus());
        existing.setSocialDefenseBonus(updated.getSocialDefenseBonus());
        existing.setWoundThreshold(updated.getWoundThreshold());
        existing.setUnconsciousnessRating(updated.getUnconsciousnessRating());
        existing.setDeathRating(updated.getDeathRating());
        existing.setPhysicalArmor(updated.getPhysicalArmor());
        existing.setMysticArmor(updated.getMysticArmor());
        existing.setWeaponName(updated.getWeaponName());
        existing.setWeaponDamageStep(updated.getWeaponDamageStep());
        existing.setKarmaModifier(updated.getKarmaModifier());
        existing.setKarmaMax(updated.getKarmaMax());
        existing.setKarmaCurrent(updated.getKarmaCurrent());
        existing.setGold(updated.getGold());
        existing.setSilver(updated.getSilver());
        existing.setCopper(updated.getCopper());
        existing.setCurrentDamage(updated.getCurrentDamage());
        existing.setWounds(updated.getWounds());
        existing.setNotes(updated.getNotes());
        return characterRepo.save(existing);
    }

    /** Aktualisiert einzelne Felder mit +/- Controls (z.B. Silber hinzufügen, Karma abziehen). */
    public GameCharacter updateField(Long id, FieldUpdateRequest req) {
        GameCharacter c = findById(id);
        int newValue;

        if (req.getAbsoluteValue() != null) {
            newValue = req.getAbsoluteValue();
        } else {
            int delta = req.getDelta() != null ? req.getDelta() : 0;
            newValue = getCurrentFieldValue(c, req.getField()) + delta;
        }

        applyFieldValue(c, req.getField(), newValue);
        return characterRepo.save(c);
    }

    private int getCurrentFieldValue(GameCharacter c, String field) {
        return switch (field) {
            case "gold"          -> c.getGold();
            case "silver"        -> c.getSilver();
            case "copper"        -> c.getCopper();
            case "karma"         -> c.getKarmaCurrent();
            case "karmaMax"      -> c.getKarmaMax();
            case "damage"        -> c.getCurrentDamage();
            case "wounds"        -> c.getWounds();
            case "circle"                -> c.getCircle();
            case "dexterity"             -> c.getDexterity();
            case "strength"              -> c.getStrength();
            case "toughness"             -> c.getToughness();
            case "perception"            -> c.getPerception();
            case "willpower"             -> c.getWillpower();
            case "charisma"              -> c.getCharisma();
            case "physicalDefenseBonus"  -> c.getPhysicalDefenseBonus();
            case "spellDefenseBonus"     -> c.getSpellDefenseBonus();
            case "socialDefenseBonus"    -> c.getSocialDefenseBonus();
            default                      -> 0;
        };
    }

    private void applyFieldValue(GameCharacter c, String field, int value) {
        switch (field) {
            case "gold"          -> c.setGold(Math.max(0, value));
            case "silver"        -> c.setSilver(Math.max(0, value));
            case "copper"        -> c.setCopper(Math.max(0, value));
            case "karma"         -> c.setKarmaCurrent(Math.max(0, Math.min(value, c.getKarmaMax())));
            case "karmaMax"      -> c.setKarmaMax(Math.max(0, value));
            case "damage"        -> c.setCurrentDamage(Math.max(0, value));
            case "wounds"        -> c.setWounds(Math.max(0, value));
            case "circle"        -> c.setCircle(Math.max(1, value));
            case "dexterity"             -> c.setDexterity(Math.max(1, value));
            case "strength"              -> c.setStrength(Math.max(1, value));
            case "toughness"             -> c.setToughness(Math.max(1, value));
            case "perception"            -> c.setPerception(Math.max(1, value));
            case "willpower"             -> c.setWillpower(Math.max(1, value));
            case "charisma"              -> c.setCharisma(Math.max(1, value));
            case "physicalDefenseBonus"  -> c.setPhysicalDefenseBonus(value);
            case "spellDefenseBonus"     -> c.setSpellDefenseBonus(value);
            case "socialDefenseBonus"    -> c.setSocialDefenseBonus(value);
            case "notes"                 -> {} // notes werden als String separat behandelt
        }
    }

    public GameCharacter updateNotes(Long id, String notes) {
        GameCharacter c = findById(id);
        c.setNotes(notes);
        return characterRepo.save(c);
    }

    /** Berechnet alle abgeleiteten Werte neu vom Charakter. */
    public DerivedStats getDerivedStats(Long id) {
        GameCharacter c = findById(id);
        return DerivedStats.builder()
                .physicalDefense(modifierAggregator.getBaseValueFromCharacter(c, StatType.PHYSICAL_DEFENSE))
                .spellDefense(modifierAggregator.getBaseValueFromCharacter(c, StatType.SPELL_DEFENSE))
                .socialDefense(modifierAggregator.getBaseValueFromCharacter(c, StatType.SOCIAL_DEFENSE))
                .woundThreshold(modifierAggregator.getBaseValueFromCharacter(c, StatType.WOUND_THRESHOLD))
                .unconsciousnessRating(modifierAggregator.getBaseValueFromCharacter(c, StatType.UNCONSCIOUSNESS_RATING))
                .deathRating(modifierAggregator.getBaseValueFromCharacter(c, StatType.DEATH_RATING))
                .initiativeStep(modifierAggregator.getBaseValueFromCharacter(c, StatType.INITIATIVE_STEP))
                .physicalArmor(modifierAggregator.getBaseValueFromCharacter(c, StatType.PHYSICAL_ARMOR))
                .mysticArmor(modifierAggregator.getBaseValueFromCharacter(c, StatType.MYSTIC_ARMOR))
                .karmaStep(modifierAggregator.getBaseValueFromCharacter(c, StatType.KARMA_STEP))
                .recoveryStep(modifierAggregator.getBaseValueFromCharacter(c, StatType.RECOVERY_STEP))
                .carryingCapacity(modifierAggregator.getBaseValueFromCharacter(c, StatType.CARRYING_CAPACITY))
                .build();
    }

    /** Berechnet abgeleitete Werte und schreibt sie in den Charakter. */
    public GameCharacter recalculateDerived(Long id) {
        GameCharacter c = findById(id);
        c.setPhysicalDefense((c.getDexterity() + 3) / 2);
        c.setSpellDefense((c.getPerception() + 3) / 2);
        c.setSocialDefense((c.getCharisma() + 3) / 2);
        c.setWoundThreshold((c.getToughness() / 2) + 4);
        int circleBonus = Math.max(0, c.getCircle() - 1);
        int bwBonus = c.getDiscipline() != null ? c.getDiscipline().getBwBonusPerCircle() : 5;
        int tdBonus = c.getDiscipline() != null ? c.getDiscipline().getTdBonusPerCircle() : 6;
        c.setUnconsciousnessRating(c.getToughness() * 2 + bwBonus * circleBonus);
        c.setDeathRating(c.getToughness() * 2 + 10 + tdBonus * circleBonus);
        c.setKarmaMax(c.getKarmaModifier() * c.getCircle());
        c.setKarmaCurrent(Math.min(c.getKarmaCurrent(), c.getKarmaMax()));
        return characterRepo.save(c);
    }

    public GameCharacter addTalent(Long characterId, Long talentDefinitionId, int rank) {
        GameCharacter c = findById(characterId);
        TalentDefinition def = talentDefRepo.findById(talentDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException("Talent nicht gefunden: " + talentDefinitionId));

        CharacterTalent talent = CharacterTalent.builder()
                .character(c)
                .talentDefinition(def)
                .rank(rank)
                .build();
        c.getTalents().add(talent);
        return characterRepo.save(c);
    }

    public void updateTalentRank(Long characterId, Long talentId, int newRank) {
        GameCharacter c = findById(characterId);
        c.getTalents().stream()
                .filter(t -> t.getId().equals(talentId))
                .findFirst()
                .ifPresent(t -> t.setRank(Math.max(1, Math.min(15, newRank))));
        characterRepo.save(c);
    }

    public void removeTalent(Long characterId, Long talentId) {
        GameCharacter c = findById(characterId);
        c.getTalents().removeIf(t -> t.getId().equals(talentId));
        characterRepo.save(c);
    }

    public GameCharacter addSkill(Long characterId, Long skillDefinitionId, int rank) {
        GameCharacter c = findById(characterId);
        SkillDefinition def = skillDefRepo.findById(skillDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException("Fertigkeit nicht gefunden: " + skillDefinitionId));

        CharacterSkill skill = CharacterSkill.builder()
                .character(c)
                .skillDefinition(def)
                .rank(rank)
                .build();
        c.getSkills().add(skill);
        return characterRepo.save(c);
    }

    public void updateSkillRank(Long characterId, Long skillId, int newRank) {
        GameCharacter c = findById(characterId);
        c.getSkills().stream()
                .filter(s -> s.getId().equals(skillId))
                .findFirst()
                .ifPresent(s -> s.setRank(Math.max(1, Math.min(10, newRank))));
        characterRepo.save(c);
    }

    public void removeSkill(Long characterId, Long skillId) {
        GameCharacter c = findById(characterId);
        c.getSkills().removeIf(s -> s.getId().equals(skillId));
        characterRepo.save(c);
    }

    public GameCharacter addEquipment(Long characterId, Equipment equipment) {
        GameCharacter c = findById(characterId);
        equipment.setCharacter(c);
        c.getEquipment().add(equipment);
        return characterRepo.save(c);
    }

    public GameCharacter removeEquipment(Long characterId, Long equipmentId) {
        GameCharacter c = findById(characterId);
        c.getEquipment().removeIf(e -> e.getId().equals(equipmentId));
        return characterRepo.save(c);
    }

    // --- Zauber ---

    public GameCharacter addSpell(Long characterId, Long spellDefinitionId) {
        GameCharacter c = findById(characterId);
        SpellDefinition def = spellDefRepo.findById(spellDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException("Zauber nicht gefunden: " + spellDefinitionId));

        // Duplikat-Check
        boolean alreadyHas = c.getSpells().stream()
                .anyMatch(s -> s.getSpellDefinition().getId().equals(spellDefinitionId));
        if (alreadyHas) throw new IllegalStateException("Charakter hat diesen Zauber bereits.");

        CharacterSpell spell = CharacterSpell.builder()
                .character(c)
                .spellDefinition(def)
                .build();
        c.getSpells().add(spell);
        return characterRepo.save(c);
    }

    public void removeSpell(Long characterId, Long spellId) {
        GameCharacter c = findById(characterId);
        c.getSpells().removeIf(s -> s.getId().equals(spellId));
        characterRepo.save(c);
    }

    public List<SpellDefinition> getAllSpells() {
        return spellDefRepo.findAllByOrderByDisciplineAscCircleAscNameAsc();
    }

    public List<SpellDefinition> getSpellsByDiscipline(String discipline) {
        return spellDefRepo.findByDisciplineOrderByCircleAscNameAsc(discipline);
    }

    public void delete(Long id) {
        characterRepo.deleteById(id);
    }
}
