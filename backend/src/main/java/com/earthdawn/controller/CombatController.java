package com.earthdawn.controller;

import com.earthdawn.dto.*;
import com.earthdawn.dto.TauntRequest;
import com.earthdawn.dto.TauntResult;
import com.earthdawn.model.ActiveEffect;
import com.earthdawn.model.CombatLog;
import com.earthdawn.model.CombatSession;
import com.earthdawn.model.enums.DeclaredActionType;
import com.earthdawn.model.enums.DeclaredStance;
import com.earthdawn.model.enums.ObstacleType;
import com.earthdawn.service.CombatMapService;
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
    private final CombatMapService mapService;

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

    // --- Kampfkarte (optionale Zusatzschicht — fasst keine Kampf-Logik an) ---

    @PostMapping("/sessions/{id}/map/configure")
    public CombatSession configureMap(@PathVariable Long id,
                                       @RequestParam boolean enabled,
                                       @RequestParam(required = false) Integer width,
                                       @RequestParam(required = false) Integer height) {
        return mapService.configureMap(id, enabled, width, height);
    }

    @PostMapping("/sessions/{id}/map/place")
    public CombatSession placeOnMap(@PathVariable Long id,
                                     @RequestParam Long combatantId,
                                     @RequestParam int q,
                                     @RequestParam int r) {
        return mapService.placeCombatant(id, combatantId, q, r);
    }

    @PostMapping("/sessions/{id}/map/move")
    public CombatSession moveOnMap(@PathVariable Long id,
                                    @RequestParam Long combatantId,
                                    @RequestParam int q,
                                    @RequestParam int r,
                                    @RequestParam(defaultValue = "false") boolean gmOverride) {
        return mapService.moveCombatant(id, combatantId, q, r, gmOverride);
    }

    @PostMapping("/sessions/{id}/map/obstacles")
    public CombatSession addObstacle(@PathVariable Long id,
                                      @RequestParam ObstacleType type,
                                      @RequestParam int q,
                                      @RequestParam int r) {
        return mapService.addObstacle(id, type, q, r);
    }

    @DeleteMapping("/sessions/{id}/map/obstacles/{obstacleId}")
    public CombatSession removeObstacle(@PathVariable Long id, @PathVariable Long obstacleId) {
        return mapService.removeObstacle(id, obstacleId);
    }

    @PostMapping("/sessions/{id}/map/obstacles/{obstacleId}/toggle-door")
    public CombatSession toggleDoor(@PathVariable Long id, @PathVariable Long obstacleId) {
        return mapService.toggleDoor(id, obstacleId);
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

    @PostMapping("/sessions/{id}/fear")
    public FearResult performFear(@PathVariable Long id, @RequestBody FearRequest req) {
        req.setSessionId(id);
        return combatService.performFear(id, req);
    }

    @PostMapping("/sessions/{id}/combatants/{combatantId}/resist-fear")
    public FearResistResult resistFear(@PathVariable Long id, @PathVariable Long combatantId) {
        return combatService.resistFear(id, combatantId);
    }

    /** Öffnet den für alle Clients synchronisierten Auswahldialog für Magie neutralisieren. */
    @PostMapping("/sessions/{id}/combatants/{combatantId}/neutralize-magic/open")
    public CombatSession openNeutralizeMagicDialog(@PathVariable Long id, @PathVariable Long combatantId) {
        return combatService.openNeutralizeMagicDialog(id, combatantId);
    }

    @PostMapping("/sessions/{id}/neutralize-magic")
    public NeutralizeMagicResult performNeutralizeMagic(@PathVariable Long id, @RequestBody NeutralizeMagicRequest req) {
        req.setSessionId(id);
        return combatService.performNeutralizeMagic(id, req);
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

    /**
     * Setzt oder löscht den Dialog-Status eines Kombattanten (welches Ziel/Waffe/Zauber er gerade
     * plant). Wird sofort via WebSocket an alle Zuschauer der Session gebroadcastet.
     * Body leer oder actionType=null → Dialog geschlossen.
     */
    @PostMapping("/sessions/{id}/combatants/{cId}/dialog-state")
    public ResponseEntity<Void> updateDialogState(@PathVariable Long id,
                                                   @PathVariable Long cId,
                                                   @RequestBody(required = false) DialogState state) {
        combatService.updateDialogState(id, cId, state);
        return ResponseEntity.ok().build();
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

    @PostMapping("/sessions/{id}/combatants/{combatantId}/gm-condition")
    public CombatSession applyGmCondition(@PathVariable Long id,
                                          @PathVariable Long combatantId,
                                          @RequestParam String type,
                                          @RequestParam(defaultValue = "1") int rounds) {
        return combatService.applyGmCondition(id, combatantId, type, rounds);
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

    @PostMapping("/sessions/{id}/combatants/{combatantId}/karma-initiative")
    public CombatSession setKarmaInitiative(@PathVariable Long id,
                                            @PathVariable Long combatantId,
                                            @RequestParam boolean spend) {
        return combatService.setKarmaInitiative(id, combatantId, spend);
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

    @PostMapping("/sessions/{id}/spot-armor-flaw")
    public SpotArmorFlawResult performSpotArmorFlaw(@PathVariable Long id,
                                                    @RequestBody SpotArmorFlawRequest req) {
        req.setSessionId(id);
        return combatService.performSpotArmorFlaw(req);
    }

    @PostMapping("/sessions/{id}/combatants/{combatantId}/iron-will")
    public IronWillResult performIronWill(
            @PathVariable Long id,
            @PathVariable Long combatantId,
            @RequestParam int attackTotal,
            @RequestParam(defaultValue = "false") boolean spendKarma) {
        return combatService.performIronWill(id, combatantId, attackTotal, spendKarma);
    }

    @PostMapping("/sessions/{id}/riposte")
    public RiposteResult performRiposte(@PathVariable Long id, @RequestBody RiposteRequest req) {
        req.setSessionId(id);
        return combatService.performRiposte(id, req);
    }

    @PostMapping("/sessions/{id}/manoeuver")
    public ManoeuverResult performManoeuver(@PathVariable Long id, @RequestBody ManoeuverRequest req) {
        req.setSessionId(id);
        return combatService.performManoeuver(id, req);
    }

    @PostMapping("/sessions/{id}/combatants/{combatantId}/tigersprung")
    public TigersprungResult performTigersprung(@PathVariable Long id, @PathVariable Long combatantId) {
        return combatService.performTigersprung(id, combatantId);
    }

    @PostMapping("/sessions/{id}/zweitwaffe")
    public CombatActionResult performZweitwaffe(@PathVariable Long id, @RequestBody ZweitwaffeRequest req) {
        req.setSessionId(id);
        return combatService.performZweitwaffe(id, req);
    }

    @PostMapping("/sessions/{id}/nachtreten")
    public CombatActionResult performNachtreten(@PathVariable Long id, @RequestBody NachtretenRequest req) {
        req.setSessionId(id);
        return combatService.performNachtreten(id, req);
    }

    @PostMapping("/sessions/{id}/schwanzangriff")
    public CombatActionResult performSchwanzangriff(@PathVariable Long id, @RequestBody SchwanzangriffRequest req) {
        req.setSessionId(id);
        return combatService.performSchwanzangriff(id, req);
    }

    @PostMapping("/sessions/{id}/combatants/{combatantId}/lufttanz")
    public LufttanzActivationResult performLufttanz(@PathVariable Long id, @PathVariable Long combatantId) {
        return combatService.performLufttanz(id, combatantId);
    }

    @PostMapping("/sessions/{id}/lufttanz-attack")
    public CombatActionResult performLufttanzAttack(@PathVariable Long id, @RequestBody LufttanzAttackRequest req) {
        req.setSessionId(id);
        return combatService.performLufttanzAttack(req);
    }

    @PostMapping("/sessions/{id}/combatants/{combatantId}/blattschuss-add-karma")
    public CombatActionResult performBlattschussAddKarma(@PathVariable Long id,
                                                          @PathVariable Long combatantId) {
        return combatService.performBlattschussAddKarma(id, combatantId);
    }

    /** Schließt das aktuell geöffnete Result-Modal für ALLE Zuschauer der Session (synchronisiert). */
    @PostMapping("/sessions/{id}/dismiss-modal")
    public CombatSession dismissModal(@PathVariable Long id) {
        return combatService.dismissModal(id);
    }
}
