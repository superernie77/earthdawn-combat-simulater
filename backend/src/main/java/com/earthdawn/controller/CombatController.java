package com.earthdawn.controller;

import com.earthdawn.dto.*;
import com.earthdawn.dto.TauntRequest;
import com.earthdawn.dto.TauntResult;
import com.earthdawn.model.ActiveEffect;
import com.earthdawn.model.CombatLog;
import com.earthdawn.model.CombatSession;
import com.earthdawn.model.enums.DeclaredActionType;
import com.earthdawn.model.enums.DeclaredStance;
import com.earthdawn.service.CombatService;
import com.earthdawn.service.SpellService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/combat")
@RequiredArgsConstructor
public class CombatController {

    private final CombatService combatService;
    private final SpellService spellService;

    @GetMapping("/sessions")
    public List<CombatSession> findAll() {
        return combatService.findAll();
    }

    @GetMapping("/sessions/{id}")
    public CombatSession findById(@PathVariable Long id) {
        return combatService.findById(id);
    }

    @PostMapping("/sessions")
    public CombatSession create(@RequestBody Map<String, String> body) {
        return combatService.createSession(body.get("name"));
    }

    @PostMapping("/sessions/{id}/combatants")
    public CombatSession addCombatant(@PathVariable Long id,
                                       @RequestParam Long characterId,
                                       @RequestParam(defaultValue = "false") boolean isNpc) {
        return combatService.addCombatant(id, characterId, isNpc);
    }

    @DeleteMapping("/sessions/{id}/combatants/{combatantId}")
    public CombatSession removeCombatant(@PathVariable Long id,
                                          @PathVariable Long combatantId) {
        return combatService.removeCombatant(id, combatantId);
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable Long id) {
        combatService.deleteSession(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/{id}/initiative")
    public CombatSession rollInitiative(@PathVariable Long id) {
        return combatService.rollInitiative(id);
    }

    @PostMapping("/sessions/{id}/attack")
    public CombatActionResult performAttack(@RequestBody AttackActionRequest req) {
        req.setSessionId(req.getSessionId() != null ? req.getSessionId() : Long.parseLong(req.getSessionId().toString()));
        return combatService.performAttack(req);
    }

    @PostMapping("/sessions/{id}/free-action")
    public FreeActionResult performFreeAction(@PathVariable Long id, @RequestBody FreeActionRequest req) {
        return combatService.performFreeAction(id, req);
    }

    @PostMapping("/sessions/{id}/taunt")
    public TauntResult performTaunt(@PathVariable Long id, @RequestBody TauntRequest req) {
        return combatService.performTaunt(id, req);
    }

    @PostMapping("/sessions/{id}/dodge")
    public DodgeResult resolveDodge(@PathVariable Long id, @RequestBody DodgeRequest req) {
        return combatService.resolveDodge(id, req);
    }

    @PostMapping("/sessions/{id}/combatants/{combatantId}/stand-up")
    public StandUpResult standUp(@PathVariable Long id, @PathVariable Long combatantId) {
        return combatService.standUp(id, combatantId);
    }

    @PostMapping("/sessions/{id}/combatants/{combatantId}/aufspringen")
    public StandUpResult aufspringen(@PathVariable Long id,
                                      @PathVariable Long combatantId,
                                      @RequestParam(defaultValue = "false") boolean spendKarma) {
        return combatService.aufspringen(id, combatantId, spendKarma);
    }

    @PostMapping("/sessions/{id}/next-round")
    public CombatSession nextRound(@PathVariable Long id) {
        return combatService.nextRound(id);
    }

    @PostMapping("/sessions/{id}/end")
    public CombatSession endCombat(@PathVariable Long id) {
        return combatService.endCombat(id);
    }

    @PatchMapping("/sessions/{id}/combatants/{combatantId}/value")
    public CombatSession updateValue(@PathVariable Long id,
                                      @PathVariable Long combatantId,
                                      @RequestParam String field,
                                      @RequestParam int delta) {
        return combatService.updateCombatantValue(id, combatantId, field, delta);
    }

    @PostMapping("/sessions/{id}/combatants/{combatantId}/effects")
    public CombatSession addEffect(@PathVariable Long id,
                                    @PathVariable Long combatantId,
                                    @RequestBody ActiveEffect effect) {
        return combatService.addEffect(id, combatantId, effect);
    }

    @DeleteMapping("/sessions/{id}/combatants/{combatantId}/effects/{effectId}")
    public CombatSession removeEffect(@PathVariable Long id,
                                       @PathVariable Long combatantId,
                                       @PathVariable Long effectId) {
        return combatService.removeEffect(id, combatantId, effectId);
    }

    @PostMapping("/sessions/{id}/combatants/{combatantId}/declare")
    public CombatSession declareAction(@PathVariable Long id,
                                        @PathVariable Long combatantId,
                                        @RequestParam DeclaredStance stance,
                                        @RequestParam DeclaredActionType actionType) {
        return combatService.declareAction(id, combatantId, stance, actionType);
    }

    @PostMapping("/sessions/{id}/combatants/{combatantId}/undeclare")
    public CombatSession undeclareAction(@PathVariable Long id,
                                          @PathVariable Long combatantId) {
        return combatService.undeclareAction(id, combatantId);
    }

    @PostMapping("/sessions/{id}/combatants/{combatantId}/combat-option")
    public CombatSession declareCombatOption(@PathVariable Long id,
                                              @PathVariable Long combatantId,
                                              @RequestParam String option) {
        return combatService.declareCombatOption(id, combatantId, option);
    }

    @GetMapping("/sessions/{id}/log")
    public List<CombatLog> getLog(@PathVariable Long id) {
        return combatService.getLog(id);
    }

    // --- Zauber ---

    @PostMapping("/sessions/{id}/weave-thread")
    public ThreadweaveResult weaveThread(@PathVariable Long id, @RequestBody ThreadweaveRequest req) {
        req.setSessionId(id);
        return spellService.weaveThread(req);
    }

    @PostMapping("/sessions/{id}/cast-spell")
    public SpellCastResult castSpell(@PathVariable Long id, @RequestBody SpellCastRequest req) {
        req.setSessionId(id);
        return spellService.castSpell(req);
    }

    @PostMapping("/sessions/{id}/combatants/{combatantId}/cancel-spell")
    public ResponseEntity<Void> cancelSpellPreparation(@PathVariable Long id,
                                                        @PathVariable Long combatantId) {
        spellService.cancelSpellPreparation(id, combatantId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/sessions/{id}/combatants/{combatantId}/acrobatic-defense")
    public AcrobaticDefenseResult performAcrobaticDefense(
            @PathVariable Long id,
            @PathVariable Long combatantId,
            @RequestParam(defaultValue = "0") int bonusSteps,
            @RequestParam(defaultValue = "false") boolean spendKarma) {
        return combatService.performAcrobaticDefense(id, combatantId, bonusSteps, spendKarma);
    }

    @PostMapping("/sessions/{id}/combat-sense")
    public CombatSenseResult performCombatSense(@PathVariable Long id,
                                                @RequestBody CombatSenseRequest req) {
        req.setSessionId(id);
        return combatService.performCombatSense(id, req);
    }

    @PostMapping("/sessions/{id}/distract")
    public DistractResult performDistract(@PathVariable Long id,
                                          @RequestBody DistractRequest req) {
        req.setSessionId(id);
        return combatService.performDistract(id, req);
    }

    @PostMapping("/sessions/{id}/combatants/{combatantId}/iron-will")
    public IronWillResult performIronWill(
            @PathVariable Long id,
            @PathVariable Long combatantId,
            @RequestParam int attackTotal,
            @RequestParam(defaultValue = "false") boolean spendKarma) {
        return combatService.performIronWill(id, combatantId, attackTotal, spendKarma);
    }
}
