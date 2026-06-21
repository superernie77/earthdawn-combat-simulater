package com.earthdawn.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.earthdawn.dto.AmuletRechargeResult;
import com.earthdawn.dto.ArztResult;
import com.earthdawn.dto.DerivedStats;
import com.earthdawn.dto.DrinkPotionResult;
import com.earthdawn.dto.FieldUpdateRequest;
import com.earthdawn.dto.HolzhautResult;
import com.earthdawn.dto.RecoveryTestResult;
import com.earthdawn.dto.RollResult;
import com.earthdawn.model.CharacterSkill;
import com.earthdawn.model.CharacterSpell;
import com.earthdawn.model.CharacterTalent;
import com.earthdawn.model.Equipment;
import com.earthdawn.model.GameCharacter;
import com.earthdawn.model.SkillDefinition;
import com.earthdawn.model.SpellDefinition;
import com.earthdawn.model.TalentDefinition;
import com.earthdawn.model.TalentNames;
import com.earthdawn.model.enums.EquipmentType;
import com.earthdawn.model.enums.StatType;
import com.earthdawn.repository.CharacterRepository;
import com.earthdawn.repository.SkillDefinitionRepository;
import com.earthdawn.repository.SpellDefinitionRepository;
import com.earthdawn.repository.TalentDefinitionRepository;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class CharacterService {

    private final CharacterRepository characterRepo;
    private final TalentDefinitionRepository talentDefRepo;
    private final SkillDefinitionRepository skillDefRepo;
    private final SpellDefinitionRepository spellDefRepo;
    private final ModifierAggregator modifierAggregator;
    private final StepRollService stepRollService;

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
        existing.setRace(updated.getRace());
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
        existing.setSecondaryWeaponId(updated.getSecondaryWeaponId());
        existing.setKarmaModifier(updated.getKarmaModifier());
        existing.setKarmaMax(updated.getKarmaMax());
        existing.setKarmaCurrent(updated.getKarmaCurrent());
        existing.setGold(updated.getGold());
        existing.setSilver(updated.getSilver());
        existing.setCopper(updated.getCopper());
        existing.setCurrentDamage(updated.getCurrentDamage());
        existing.setWounds(updated.getWounds());
        existing.setNotes(updated.getNotes());
        existing.setGmCharacter(updated.isGmCharacter());
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
            case "circle"        -> {
                c.setCircle(Math.max(1, value));
                // Alle rankFromCircle-Talente (z.B. Zaubermatritze) auf neuen Kreis synchronisieren
                int newCircle = Math.max(1, value);
                c.getTalents().stream()
                        .filter(t -> t.getTalentDefinition().isRankFromCircle())
                        .forEach(t -> t.setRank(newCircle));
            }
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
        int holzhaut = c.getHolzhautBonus();
        int unconsciousness = modifierAggregator.getBaseValueFromCharacter(c, StatType.UNCONSCIOUSNESS_RATING) + holzhaut;
        int death = modifierAggregator.getBaseValueFromCharacter(c, StatType.DEATH_RATING) + holzhaut;
        return DerivedStats.builder()
                .physicalDefense(modifierAggregator.getBaseValueFromCharacter(c, StatType.PHYSICAL_DEFENSE))
                .spellDefense(modifierAggregator.getBaseValueFromCharacter(c, StatType.SPELL_DEFENSE))
                .socialDefense(modifierAggregator.getBaseValueFromCharacter(c, StatType.SOCIAL_DEFENSE))
                .woundThreshold(modifierAggregator.getBaseValueFromCharacter(c, StatType.WOUND_THRESHOLD))
                .unconsciousnessRating(unconsciousness)
                .deathRating(death)
                .initiativeStep(modifierAggregator.getBaseValueFromCharacter(c, StatType.INITIATIVE_STEP))
                .physicalArmor(modifierAggregator.getBaseValueFromCharacter(c, StatType.PHYSICAL_ARMOR))
                .mysticArmor(modifierAggregator.getBaseValueFromCharacter(c, StatType.MYSTIC_ARMOR))
                .karmaStep(modifierAggregator.getBaseValueFromCharacter(c, StatType.KARMA_STEP))
                .recoveryStep(modifierAggregator.getBaseValueFromCharacter(c, StatType.RECOVERY_STEP))
                .carryingCapacity(modifierAggregator.getBaseValueFromCharacter(c, StatType.CARRYING_CAPACITY))
                .holzhautBonus(holzhaut)
                .bloodMagicDamage(modifierAggregator.bloodMagicDamage(c))
                .build();
    }

    /**
     * Wirkt das Holzhaut-Talent: würfelt Talentrang + ZÄH-Step und speichert das Ergebnis als
     * aktiven Bonus. Ein bereits aktiver Bonus wird überschrieben (das Talent gilt als nur einmal
     * gleichzeitig aktiv). Die Heilung beim Beenden bezieht sich immer auf den zuletzt gewürfelten
     * Wert; das ist mechanisch eine zulässige Vereinfachung der "neue Probe ersetzt alte"-Regel.
     */
    public HolzhautResult useHolzhaut(Long characterId) {
        GameCharacter c = findById(characterId);
        CharacterTalent ct = c.getTalents().stream()
                .filter(t -> TalentNames.HOLZHAUT.equals(t.getTalentDefinition().getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Charakter besitzt das Talent 'Holzhaut' nicht."));

        int rank = ct.getRank();
        int touStep = stepRollService.attributeToStep(c.getToughness());
        int rollStep = touStep + rank;
        RollResult roll = stepRollService.roll(rollStep);

        int previous = c.getHolzhautBonus();
        c.setHolzhautBonus(roll.getTotal());
        characterRepo.save(c);

        return HolzhautResult.builder()
                .rank(rank)
                .toughnessStep(touStep)
                .rollStep(rollStep)
                .roll(roll)
                .bonus(roll.getTotal())
                .previousBonus(previous)
                .healed(0)
                .build();
    }

    /**
     * Beendet einen aktiven Holzhaut-Effekt: reduziert currentDamage um den Bonus (min 0)
     * und setzt den Bonus auf 0 zurück.
     */
    public HolzhautResult endHolzhaut(Long characterId) {
        GameCharacter c = findById(characterId);
        int bonus = c.getHolzhautBonus();
        if (bonus <= 0) {
            throw new IllegalStateException("Kein aktiver Holzhaut-Effekt zum Beenden.");
        }
        int healed = Math.min(bonus, c.getCurrentDamage());
        c.setCurrentDamage(c.getCurrentDamage() - healed);
        c.setHolzhautBonus(0);
        characterRepo.save(c);

        return HolzhautResult.builder()
                .rank(0)
                .toughnessStep(0)
                .rollStep(0)
                .roll(null)
                .bonus(0)
                .previousBonus(bonus)
                .healed(healed)
                .build();
    }

    /** Berechnet abgeleitete Werte und schreibt sie in den Charakter. */
    public GameCharacter recalculateDerived(Long id) {
        GameCharacter c = findById(id);
        c.setPhysicalDefense((c.getDexterity() + 3) / 2);
        c.setSpellDefense((c.getPerception() + 3) / 2);
        c.setSocialDefense((c.getCharisma() + 3) / 2);
        // Wundenschwelle: (ZÄ + 1) / 2 + 2  (matches ED4 FASA official table)
        c.setWoundThreshold((c.getToughness() + 1) / 2 + 2);
        int circleBonus = Math.max(0, c.getCircle());
        int bwBonus = c.getDiscipline() != null ? c.getDiscipline().getBwBonusPerCircle() : 5;
        int tdBonus = c.getDiscipline() != null ? c.getDiscipline().getTdBonusPerCircle() : 6;
        // Bewusstlosigkeitsschwelle: ZÄ × 2  (+ bwBonus per additional circle)
        c.setUnconsciousnessRating(c.getToughness() * 2 + bwBonus * circleBonus);
        // Todesschwelle: Bewusstlosigkeit + ZÄ-Stufe  (+ tdBonus per additional circle)
        int toughnessStep = stepRollService.attributeToStep(c.getToughness());
        c.setDeathRating(c.getToughness() * 2 + toughnessStep + tdBonus * circleBonus);
        c.setKarmaMax(c.getKarmaModifier() * c.getCircle());
        c.setKarmaCurrent(Math.min(c.getKarmaCurrent(), c.getKarmaMax()));
        return characterRepo.save(c);
    }

    public GameCharacter addTalent(Long characterId, Long talentDefinitionId, int rank) {
        GameCharacter c = findById(characterId);
        TalentDefinition def = talentDefRepo.findById(talentDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException("Talent nicht gefunden: " + talentDefinitionId));

        // Mehrfachinstanz-Prüfung: maxInstances begrenzt, wie oft ein Talent gelernt werden darf
        long existingCount = c.getTalents().stream()
                .filter(t -> t.getTalentDefinition().getId().equals(def.getId()))
                .count();
        if (existingCount >= def.getMaxInstances()) {
            throw new IllegalStateException(
                "Talent '" + def.getName() + "' kann maximal " + def.getMaxInstances() +
                " Mal gelernt werden (bereits " + existingCount + " vorhanden).");
        }

        // rankFromCircle: Rang wird immer aus dem Kreis des Charakters abgeleitet
        int effectiveRank = def.isRankFromCircle() ? c.getCircle() : rank;

        CharacterTalent talent = CharacterTalent.builder()
                .character(c)
                .talentDefinition(def)
                .rank(effectiveRank)
                .build();
        c.getTalents().add(talent);
        syncClawWeapon(c, def.getName(), effectiveRank);
        return characterRepo.save(c);
    }

    public void updateTalentRank(Long characterId, Long talentId, int newRank) {
        GameCharacter c = findById(characterId);
        int clamped = Math.max(1, Math.min(15, newRank));
        c.getTalents().stream()
                .filter(t -> t.getId().equals(talentId))
                .findFirst()
                .ifPresent(t -> {
                    t.setRank(clamped);
                    syncClawWeapon(c, t.getTalentDefinition().getName(), clamped);
                });
        characterRepo.save(c);
    }

    public void removeTalent(Long characterId, Long talentId) {
        GameCharacter c = findById(characterId);
        c.getTalents().stream()
                .filter(t -> t.getId().equals(talentId))
                .findFirst()
                .ifPresent(t -> {
                    if (TalentNames.KRALLENHAND.equals(t.getTalentDefinition().getName())) {
                        c.getEquipment().removeIf(Equipment::isClawWeapon);
                    }
                });
        c.getTalents().removeIf(t -> t.getId().equals(talentId));
        characterRepo.save(c);
    }

    /**
     * Weist einer Zaubermatritze-Instanz einen Zauber zu (oder leert sie bei spellId=null).
     * Validierung: Der Kreis des Zaubers darf den Rang der Matrix nicht überschreiten.
     */
    public GameCharacter assignSpellToMatrix(Long characterId, Long talentId, Long spellId) {
        GameCharacter c = findById(characterId);
        CharacterTalent matrix = c.getTalents().stream()
                .filter(t -> t.getId().equals(talentId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Talent nicht gefunden: " + talentId));

        if (!TalentNames.ZAUBERMATRITZE.equals(matrix.getTalentDefinition().getName())) {
            throw new IllegalArgumentException("Nur Zaubermatrizen können Zauber zugewiesen werden.");
        }

        if (spellId == null) {
            matrix.setAssignedSpell(null);
        } else {
            SpellDefinition spell = spellDefRepo.findById(spellId)
                    .orElseThrow(() -> new EntityNotFoundException("Zauber nicht gefunden: " + spellId));
            if (spell.getCircle() > matrix.getRank()) {
                throw new IllegalArgumentException(
                    "Zauber '" + spell.getName() + "' (Kreis " + spell.getCircle() + ") übersteigt den Rang der Matrix (" + matrix.getRank() + ").");
            }
            matrix.setAssignedSpell(spell);
        }
        return characterRepo.save(c);
    }

    /**
     * Hält die Krallenhand-Waffe synchron zum Talent-Rang. Wird beim Hinzufügen oder
     * Aktualisieren des Talents aufgerufen. Schadensbonus = Rang + 3 (entspricht der
     * Krallenhandstufe = STR + Rang + 3 in Kombination mit der Standard-Schadensformel
     * STR + weaponBonus).
     */
    private void syncClawWeapon(GameCharacter c, String talentName, int rank) {
        if (!TalentNames.KRALLENHAND.equals(talentName)) return;
        int newBonus = rank + 3;
        Equipment claw = c.getEquipment().stream()
                .filter(Equipment::isClawWeapon)
                .findFirst()
                .orElse(null);
        if (claw == null) {
            claw = Equipment.builder()
                    .character(c)
                    .name(TalentNames.CLAW_WEAPON_NAME)
                    .type(EquipmentType.WEAPON)
                    .description("Magische Klauenhände (vom Talent verwaltet, Karma auf Schaden möglich, kann nicht entwaffnet werden)")
                    .damageBonus(newBonus)
                    .clawWeapon(true)
                    .build();
            c.getEquipment().add(claw);
        } else {
            claw.setDamageBonus(newBonus);
        }
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
        // Beim Hinzufügen von Rüstung/Schild: neues Stück wird aktiv, vorhandene deaktivieren
        if (equipment.getType() == EquipmentType.ARMOR || equipment.getType() == EquipmentType.SHIELD) {
            equipment.setActive(true);
            c.getEquipment().stream()
                    .filter(e -> e.getType() == equipment.getType())
                    .forEach(e -> e.setActive(false));
        }
        // Verzweiflungsschlag-Amulett: feste Werte serverseitig erzwingen (geladen, +6, 3 Blutmagie)
        if (equipment.getType() == EquipmentType.AMULET) {
            equipment.setCharged(true);
            if (equipment.getAmuletStepBonus() <= 0) equipment.setAmuletStepBonus(6);
            if (equipment.getBloodMagicDamage() <= 0) equipment.setBloodMagicDamage(3);
        }
        c.getEquipment().add(equipment);
        return characterRepo.save(c);
    }

    /**
     * Lädt ein entladenes Amulett über eine geopferte Erholungsprobe wieder auf.
     * Verbraucht einen Erholungs-Slot und würfelt eine Erholungsprobe. Wurf ≥ 3 →
     * Amulett wird geladen, die Heilung verfällt. Wurf < 3 → Aufladen scheitert,
     * die Probe heilt stattdessen regulär.
     */
    public AmuletRechargeResult rechargeAmulet(Long characterId, Long equipmentId) {
        GameCharacter c = findById(characterId);

        Equipment amulet = c.getEquipment().stream()
                .filter(e -> e.getId().equals(equipmentId) && EquipmentType.AMULET.equals(e.getType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Amulett nicht gefunden: " + equipmentId));

        if (amulet.isCharged()) {
            throw new IllegalStateException("Amulett ist bereits geladen.");
        }

        int max = getRecoveryTestsMax(c.getToughness());
        int remaining = getRecoveryTestsRemaining(c);
        if (remaining <= 0) {
            throw new IllegalStateException("Keine Erholungsproben mehr für heute übrig.");
        }

        int touStep = stepRollService.attributeToStep(c.getToughness());
        int woundPenalty = c.getWounds();
        int rollStep = Math.max(1, touStep - woundPenalty);

        remaining--;
        c.setRecoveryTestsRemaining(remaining);

        RollResult roll = stepRollService.roll(rollStep);
        boolean recharged = roll.getTotal() >= 3;
        int healed = 0;
        if (recharged) {
            // Erholungsprobe geopfert: Amulett laden, keine Heilung
            amulet.setCharged(true);
        } else {
            // Reicht nicht zum Aufladen: heilt stattdessen regulär
            healed = Math.min(roll.getTotal(), c.getCurrentDamage());
            c.setCurrentDamage(c.getCurrentDamage() - healed);
        }
        characterRepo.save(c);

        return AmuletRechargeResult.builder()
                .amuletName(amulet.getName())
                .toughnessStep(touStep)
                .woundPenalty(woundPenalty)
                .rollStep(rollStep)
                .roll(roll)
                .recharged(recharged)
                .healed(healed)
                .remainingDamage(c.getCurrentDamage())
                .recoveryTestsRemaining(remaining)
                .recoveryTestsMax(max)
                .build();
    }

    /**
     * Setzt ein Rüstungs- oder Schildstück auf aktiv oder inaktiv.
     * Wird ein Stück aktiviert, werden alle anderen Stücke desselben Typs automatisch deaktiviert
     * (es kann immer nur ein Stück pro Typ gleichzeitig aktiv sein).
     */
    public GameCharacter setEquipmentActive(Long characterId, Long equipmentId, boolean active) {
        GameCharacter c = findById(characterId);
        Equipment target = c.getEquipment().stream()
                .filter(e -> e.getId().equals(equipmentId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Ausrüstung nicht gefunden: " + equipmentId));

        if (target.getType() != EquipmentType.ARMOR && target.getType() != EquipmentType.SHIELD) {
            throw new IllegalArgumentException("Aktiv/inaktiv gilt nur für Rüstungen und Schilde.");
        }

        if (active) {
            // Alle anderen Stücke desselben Typs deaktivieren (Exklusivität)
            c.getEquipment().stream()
                    .filter(e -> e.getType() == target.getType())
                    .forEach(e -> e.setActive(false));
            // Manuell wieder angelegtes Schild gilt nicht mehr als "automatisch abgelegt"
            target.setAutoStowed(false);
        }
        target.setActive(active);
        return characterRepo.save(c);
    }

    public GameCharacter removeEquipment(Long characterId, Long equipmentId) {
        GameCharacter c = findById(characterId);
        c.getEquipment().stream()
                .filter(e -> e.getId().equals(equipmentId))
                .findFirst()
                .ifPresent(e -> {
                    if (e.isClawWeapon()) {
                        throw new IllegalStateException(
                                "Krallenhand wird vom Talent verwaltet und kann nicht manuell entfernt werden. " +
                                "Entferne stattdessen das Krallenhand-Talent.");
                    }
                });
        c.getEquipment().removeIf(e -> e.getId().equals(equipmentId));
        return characterRepo.save(c);
    }

    public GameCharacter updateEquipmentQuantity(Long characterId, Long equipmentId, int quantity) {
        GameCharacter c = findById(characterId);
        c.getEquipment().stream()
                .filter(e -> e.getId().equals(equipmentId))
                .findFirst()
                .ifPresent(e -> e.setQuantity(Math.max(0, quantity)));
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

    // --- Erholungsproben ---

    private int getRecoveryTestsMax(int toughness) {
        if (toughness <= 6)  return 1;
        if (toughness <= 12) return 2;
        if (toughness <= 18) return 3;
        if (toughness <= 24) return 4;
        return 5;
    }

    private int getRecoveryTestsRemaining(GameCharacter c) {
        return c.getRecoveryTestsRemaining() != null
                ? c.getRecoveryTestsRemaining()
                : getRecoveryTestsMax(c.getToughness());
    }

    /** Reguläre Erholungsprobe: verbraucht einen Tages-Slot, wendet pendingRecoveryBonus an und löscht ihn. */
    public RecoveryTestResult performRecoveryTest(Long characterId) {
        GameCharacter c = findById(characterId);

        int max = getRecoveryTestsMax(c.getToughness());
        int remaining = getRecoveryTestsRemaining(c);
        if (remaining <= 0) {
            throw new IllegalStateException("Keine Erholungsproben mehr für heute übrig.");
        }

        int touStep = stepRollService.attributeToStep(c.getToughness());
        // Nach erfolgreicher Arztbehandlung entfällt der Wundabzug für diese eine Erholungsprobe
        int woundPenalty = c.isArztWoundPenaltyNegated() ? 0 : c.getWounds();
        int rollStep = Math.max(1, touStep - woundPenalty);
        int bonusSteps = c.getPendingRecoveryBonus();

        remaining--;
        c.setRecoveryTestsRemaining(remaining);
        c.setPendingRecoveryBonus(0);
        c.setArztWoundPenaltyNegated(false); // Wundpflege verbraucht

        RollResult roll = stepRollService.roll(rollStep + bonusSteps);
        int healed = Math.min(roll.getTotal(), c.getCurrentDamage());
        c.setCurrentDamage(c.getCurrentDamage() - healed);
        characterRepo.save(c);

        return RecoveryTestResult.builder()
                .toughnessStep(touStep)
                .woundPenalty(woundPenalty)
                .rollStep(rollStep)
                .bonusSteps(bonusSteps)
                .roll(roll)
                .healed(healed)
                .remainingDamage(c.getCurrentDamage())
                .recoveryTestsRemaining(remaining)
                .recoveryTestsMax(max)
                .usedExtraSlot(false)
                .potionName(null)
                .build();
    }

    /**
     * Trank trinken.
     * Erholungstrank (extraRecovery=false): setzt pendingRecoveryBonus += healStep, kein Wurf.
     * Heiltrank (extraRecovery=true): sofortige Extra-Erholungsprobe + healStep Bonus, kein Slot-Verbrauch.
     */
    public DrinkPotionResult drinkPotion(Long characterId, Long potionId) {
        GameCharacter c = findById(characterId);

        Equipment potion = c.getEquipment().stream()
                .filter(e -> e.getId().equals(potionId)
                        && EquipmentType.POTION.equals(e.getType())
                        && e.getQuantity() > 0)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Trank nicht gefunden oder aufgebraucht."));

        potion.setQuantity(potion.getQuantity() - 1);

        if (!potion.isExtraRecovery()) {
            // Erholungstrank: pending bonus akkumulieren, kein Wurf
            c.setPendingRecoveryBonus(c.getPendingRecoveryBonus() + potion.getHealStep());
            characterRepo.save(c);
            return DrinkPotionResult.builder()
                    .extraRecovery(false)
                    .potionName(potion.getName())
                    .pendingBonus(c.getPendingRecoveryBonus())
                    .recovery(null)
                    .build();
        }

        // Heiltrank: sofortige Extra-Erholungsprobe
        int touStep = stepRollService.attributeToStep(c.getToughness());
        int woundPenalty = c.getWounds();
        int rollStep = Math.max(1, touStep - woundPenalty);
        int bonusSteps = potion.getHealStep();
        int max = getRecoveryTestsMax(c.getToughness());
        int remaining = getRecoveryTestsRemaining(c);

        RollResult roll = stepRollService.roll(rollStep + bonusSteps);
        int healed = Math.min(roll.getTotal(), c.getCurrentDamage());
        c.setCurrentDamage(c.getCurrentDamage() - healed);
        characterRepo.save(c);

        RecoveryTestResult recovery = RecoveryTestResult.builder()
                .toughnessStep(touStep)
                .woundPenalty(woundPenalty)
                .rollStep(rollStep)
                .bonusSteps(bonusSteps)
                .roll(roll)
                .healed(healed)
                .remainingDamage(c.getCurrentDamage())
                .recoveryTestsRemaining(remaining)
                .recoveryTestsMax(max)
                .usedExtraSlot(true)
                .potionName(potion.getName())
                .build();

        return DrinkPotionResult.builder()
                .extraRecovery(true)
                .potionName(potion.getName())
                .pendingBonus(c.getPendingRecoveryBonus())
                .recovery(recovery)
                .build();
    }

    public GameCharacter resetRecoveryTests(Long characterId) {
        GameCharacter c = findById(characterId);
        c.setRecoveryTestsRemaining(getRecoveryTestsMax(c.getToughness()));
        return characterRepo.save(c);
    }

    /** Mindestwurf für die Behandlung von Verletzungen und Wunden (ED4-Spec). */
    private static final int ARZT_DN_WUNDEN = 5;

    /**
     * Arzt-Fertigkeit: Heiler behandelt die Wunden eines verletzten Charakters.
     * Wurf: WAH-Stufe + Rang vs. festem Mindestwurf 5 (Verletzungen und Wunden).
     * Verbraucht 1× Verbandszeug des Heilers (pro Anwendung).
     * Erfolg: +Rang Bonus auf die nächste Erholungsprobe UND der Wundabzug der nächsten
     * Erholungsprobe wird aufgehoben (Wunde bleibt, aber ohne Recovery-Malus).
     */
    public ArztResult applyArzt(Long woundedCharId, Long healerCharId) {
        GameCharacter wounded = findById(woundedCharId);
        GameCharacter healer = findById(healerCharId);

        if (wounded.getWounds() <= 0) {
            throw new IllegalStateException("Charakter hat keine Wunden — Arztbehandlung nicht möglich.");
        }

        CharacterSkill arztSkill = healer.getSkills().stream()
                .filter(s -> "Arzt".equals(s.getSkillDefinition().getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(healer.getName() + " hat keine Arzt-Fertigkeit."));

        // Verbandszeug erforderlich — 1 Anwendung pro Arztprobe
        Equipment verbandszeug = healer.getEquipment().stream()
                .filter(e -> EquipmentType.VERBANDSZEUG.equals(e.getType()) && e.getQuantity() > 0)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(healer.getName() + " hat kein Verbandszeug."));
        verbandszeug.setQuantity(verbandszeug.getQuantity() - 1);

        int wounds = wounded.getWounds();
        int targetNumber = ARZT_DN_WUNDEN;
        int perStep = stepRollService.attributeToStep(healer.getPerception());
        int skillRank = arztSkill.getRank();
        int rollStep = perStep + skillRank;

        RollResult roll = stepRollService.roll(rollStep);
        boolean success = roll.getTotal() >= targetNumber;

        int bonusGranted = 0;
        if (success) {
            bonusGranted = skillRank;
            wounded.setPendingRecoveryBonus(wounded.getPendingRecoveryBonus() + bonusGranted);
            // Wundbehandlung: nächste Erholungsprobe ohne Wundabzug
            wounded.setArztWoundPenaltyNegated(true);
            characterRepo.save(wounded);
        }
        characterRepo.save(healer); // Verbandszeug-Verbrauch persistieren

        return ArztResult.builder()
                .healerName(healer.getName())
                .woundedName(wounded.getName())
                .wounds(wounds)
                .targetNumber(targetNumber)
                .perStep(perStep)
                .skillRank(skillRank)
                .rollStep(rollStep)
                .roll(roll)
                .success(success)
                .bonusGranted(bonusGranted)
                .newPendingBonus(wounded.getPendingRecoveryBonus())
                .woundPenaltyNegated(success)
                .verbandszeugRemaining(verbandszeug.getQuantity())
                .build();
    }

    public void delete(Long id) {
        characterRepo.deleteById(id);
    }
}
