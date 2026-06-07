package com.earthdawn.controller;

import com.earthdawn.dto.ArztResult;
import com.earthdawn.dto.DerivedStats;
import com.earthdawn.dto.DrinkPotionResult;
import com.earthdawn.dto.FieldUpdateRequest;
import com.earthdawn.dto.HolzhautResult;
import com.earthdawn.dto.RecoveryTestResult;
import com.earthdawn.model.Equipment;
import com.earthdawn.model.GameCharacter;
import com.earthdawn.model.SpellDefinition;
import com.earthdawn.service.CharacterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/characters")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService characterService;

    @GetMapping
    public List<GameCharacter> findAll() {
        return characterService.findAll();
    }

    @GetMapping("/{id}")
    public GameCharacter findById(@PathVariable Long id) {
        return characterService.findById(id);
    }

    @PostMapping
    public GameCharacter create(@RequestBody GameCharacter character) {
        return characterService.create(character);
    }

    @PutMapping("/{id}")
    public GameCharacter update(@PathVariable Long id, @RequestBody GameCharacter character) {
        return characterService.update(id, character);
    }

    @PatchMapping("/{id}/field")
    public GameCharacter updateField(@PathVariable Long id, @RequestBody FieldUpdateRequest req) {
        return characterService.updateField(id, req);
    }

    @PatchMapping("/{id}/notes")
    public GameCharacter updateNotes(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return characterService.updateNotes(id, body.get("notes"));
    }

    @GetMapping("/{id}/derived")
    public DerivedStats getDerived(@PathVariable Long id) {
        return characterService.getDerivedStats(id);
    }

    @PostMapping("/{id}/recalculate")
    public GameCharacter recalculate(@PathVariable Long id) {
        return characterService.recalculateDerived(id);
    }

    @PostMapping("/{id}/talents")
    public GameCharacter addTalent(@PathVariable Long id,
                                    @RequestParam Long talentDefinitionId,
                                    @RequestParam(defaultValue = "1") int rank) {
        return characterService.addTalent(id, talentDefinitionId, rank);
    }

    @PatchMapping("/{id}/talents/{talentId}")
    public ResponseEntity<Void> updateTalentRank(@PathVariable Long id,
                                                  @PathVariable Long talentId,
                                                  @RequestParam int rank) {
        characterService.updateTalentRank(id, talentId, rank);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/talents/{talentId}")
    public ResponseEntity<Void> removeTalent(@PathVariable Long id, @PathVariable Long talentId) {
        characterService.removeTalent(id, talentId);
        return ResponseEntity.ok().build();
    }

    /** Weist einer Zaubermatritze einen Zauber zu (spellId=null → leert die Matrix). */
    @PatchMapping("/{id}/talents/{talentId}/assign-spell")
    public GameCharacter assignSpellToMatrix(@PathVariable Long id,
                                              @PathVariable Long talentId,
                                              @RequestParam(required = false) Long spellId) {
        return characterService.assignSpellToMatrix(id, talentId, spellId);
    }

    @PostMapping("/{id}/skills")
    public GameCharacter addSkill(@PathVariable Long id,
                                   @RequestParam Long skillDefinitionId,
                                   @RequestParam(defaultValue = "1") int rank) {
        return characterService.addSkill(id, skillDefinitionId, rank);
    }

    @PatchMapping("/{id}/skills/{skillId}")
    public ResponseEntity<Void> updateSkillRank(@PathVariable Long id,
                                                 @PathVariable Long skillId,
                                                 @RequestParam int rank) {
        characterService.updateSkillRank(id, skillId, rank);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/skills/{skillId}")
    public ResponseEntity<Void> removeSkill(@PathVariable Long id, @PathVariable Long skillId) {
        characterService.removeSkill(id, skillId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/equipment")
    public GameCharacter addEquipment(@PathVariable Long id, @RequestBody Equipment equipment) {
        return characterService.addEquipment(id, equipment);
    }

    @PatchMapping("/{id}/equipment/{equipmentId}")
    public GameCharacter updateEquipmentQuantity(@PathVariable Long id,
                                                  @PathVariable Long equipmentId,
                                                  @RequestParam int quantity) {
        return characterService.updateEquipmentQuantity(id, equipmentId, quantity);
    }

    @PatchMapping("/{id}/equipment/{equipmentId}/active")
    public GameCharacter setEquipmentActive(@PathVariable Long id,
                                             @PathVariable Long equipmentId,
                                             @RequestParam boolean active) {
        return characterService.setEquipmentActive(id, equipmentId, active);
    }

    @DeleteMapping("/{id}/equipment/{equipmentId}")
    public GameCharacter removeEquipment(@PathVariable Long id, @PathVariable Long equipmentId) {
        return characterService.removeEquipment(id, equipmentId);
    }

    // --- Zauber ---

    @GetMapping("/spells")
    public List<SpellDefinition> getSpells(@RequestParam(required = false) String discipline) {
        if (discipline != null && !discipline.isBlank()) {
            return characterService.getSpellsByDiscipline(discipline);
        }
        return characterService.getAllSpells();
    }

    @PostMapping("/{id}/spells")
    public GameCharacter addSpell(@PathVariable Long id, @RequestParam Long spellDefinitionId) {
        return characterService.addSpell(id, spellDefinitionId);
    }

    @DeleteMapping("/{id}/spells/{spellId}")
    public ResponseEntity<Void> removeSpell(@PathVariable Long id, @PathVariable Long spellId) {
        characterService.removeSpell(id, spellId);
        return ResponseEntity.ok().build();
    }

    // --- Holzhaut ---

    @PostMapping("/{id}/holzhaut")
    public HolzhautResult useHolzhaut(@PathVariable Long id) {
        return characterService.useHolzhaut(id);
    }

    @PostMapping("/{id}/holzhaut/end")
    public HolzhautResult endHolzhaut(@PathVariable Long id) {
        return characterService.endHolzhaut(id);
    }

    // --- Erholungsproben ---

    /** Reguläre Erholungsprobe: verbraucht einen Tages-Slot, wendet ausstehenden Bonus an. */
    @PostMapping("/{id}/recovery-test")
    public RecoveryTestResult performRecoveryTest(@PathVariable Long id) {
        return characterService.performRecoveryTest(id);
    }

    /** Trank trinken: Erholungstrank setzt Pending-Bonus, Heiltrank gibt sofortige Extra-Probe. */
    @PostMapping("/{id}/drink-potion")
    public DrinkPotionResult drinkPotion(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        return characterService.drinkPotion(id, body.get("potionId"));
    }

    @PostMapping("/{id}/recovery-test/reset")
    public GameCharacter resetRecoveryTests(@PathVariable Long id) {
        return characterService.resetRecoveryTests(id);
    }

    /** Arzt-Fertigkeit: Heiler behandelt den Verwundeten. Body: { healerCharacterId } */
    @PostMapping("/{id}/arzt")
    public ArztResult applyArzt(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        return characterService.applyArzt(id, body.get("healerCharacterId"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        characterService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
