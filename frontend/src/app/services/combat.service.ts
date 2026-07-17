import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CombatSession, DialogState, AttackActionRequest, CombatActionResult,
  CombatLog, ActiveEffect, FreeActionRequest, FreeActionResult,
  TauntRequest, TauntResult,
  FearRequest, FearResult, FearResistResult,
  NeutralizeMagicRequest, NeutralizeMagicResult,
  AcrobaticDefenseResult, CombatSenseRequest, CombatSenseResult,
  DistractRequest, DistractResult, IronWillResult,
  DodgeRequest, DodgeResult, StandUpResult,
  ThreadweaveRequest, ThreadweaveResult,
  SpellCastRequest, SpellCastResult,
  DeclaredStance, DeclaredActionType,
  RiposteRequest, RiposteResult,
  ManoeuverRequest, ManoeuverResult,
  TigersprungResult,
  ZweitwaffeRequest,
  NachtretenRequest,
  SchwanzangriffRequest,
  SpotArmorFlawRequest, SpotArmorFlawResult,
  LufttanzActivationResult, LufttanzAttackRequest
} from '../models/combat.model';

@Injectable({ providedIn: 'root' })
export class CombatService {
  private readonly base = '/api/combat';

  constructor(private http: HttpClient) {}

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/sessions/${id}`);
  }

  findAll(): Observable<CombatSession[]> {
    return this.http.get<CombatSession[]>(`${this.base}/sessions`);
  }

  findById(id: number): Observable<CombatSession> {
    return this.http.get<CombatSession>(`${this.base}/sessions/${id}`);
  }

  create(name: string): Observable<CombatSession> {
    return this.http.post<CombatSession>(`${this.base}/sessions`, { name });
  }

  addCombatant(sessionId: number, characterId: number, isNpc: boolean): Observable<CombatSession> {
    return this.http.post<CombatSession>(
      `${this.base}/sessions/${sessionId}/combatants`,
      null,
      { params: { characterId, isNpc } }
    );
  }

  removeCombatant(sessionId: number, combatantId: number): Observable<CombatSession> {
    return this.http.delete<CombatSession>(
      `${this.base}/sessions/${sessionId}/combatants/${combatantId}`
    );
  }

  rollInitiative(sessionId: number): Observable<CombatSession> {
    return this.http.post<CombatSession>(`${this.base}/sessions/${sessionId}/initiative`, {});
  }

  performAttack(req: AttackActionRequest): Observable<CombatActionResult> {
    return this.http.post<CombatActionResult>(
      `${this.base}/sessions/${req.sessionId}/attack`,
      req
    );
  }

  nextRound(sessionId: number): Observable<CombatSession> {
    return this.http.post<CombatSession>(`${this.base}/sessions/${sessionId}/next-round`, {});
  }

  endCombat(sessionId: number): Observable<CombatSession> {
    return this.http.post<CombatSession>(`${this.base}/sessions/${sessionId}/end`, {});
  }

  updateValue(sessionId: number, combatantId: number, field: string, delta: number): Observable<CombatSession> {
    return this.http.patch<CombatSession>(
      `${this.base}/sessions/${sessionId}/combatants/${combatantId}/value`,
      null,
      { params: { field, delta } }
    );
  }

  addEffect(sessionId: number, combatantId: number, effect: ActiveEffect): Observable<CombatSession> {
    return this.http.post<CombatSession>(
      `${this.base}/sessions/${sessionId}/combatants/${combatantId}/effects`,
      effect
    );
  }

  applyGmCondition(sessionId: number, combatantId: number, type: 'TOTER_WINKEL' | 'BEDRAENGT', rounds: number): Observable<CombatSession> {
    return this.http.post<CombatSession>(
      `${this.base}/sessions/${sessionId}/combatants/${combatantId}/gm-condition?type=${type}&rounds=${rounds}`,
      {}
    );
  }

  removeEffect(sessionId: number, combatantId: number, effectId: number): Observable<CombatSession> {
    return this.http.delete<CombatSession>(
      `${this.base}/sessions/${sessionId}/combatants/${combatantId}/effects/${effectId}`
    );
  }

  declareAction(sessionId: number, combatantId: number,
                stance: DeclaredStance, actionType: DeclaredActionType): Observable<CombatSession> {
    return this.http.post<CombatSession>(
      `${this.base}/sessions/${sessionId}/combatants/${combatantId}/declare`,
      null,
      { params: { stance, actionType } }
    );
  }

  undeclareAction(sessionId: number, combatantId: number): Observable<CombatSession> {
    return this.http.post<CombatSession>(
      `${this.base}/sessions/${sessionId}/combatants/${combatantId}/undeclare`,
      {}
    );
  }

  setKarmaInitiative(sessionId: number, combatantId: number, spend: boolean): Observable<CombatSession> {
    return this.http.post<CombatSession>(
      `${this.base}/sessions/${sessionId}/combatants/${combatantId}/karma-initiative?spend=${spend}`,
      {}
    );
  }

  declareCombatOption(sessionId: number, combatantId: number, option: string): Observable<CombatSession> {
    return this.http.post<CombatSession>(
      `${this.base}/sessions/${sessionId}/combatants/${combatantId}/combat-option`,
      null,
      { params: { option } }
    );
  }

  performFreeAction(sessionId: number, req: FreeActionRequest): Observable<FreeActionResult> {
    return this.http.post<FreeActionResult>(`${this.base}/sessions/${sessionId}/free-action`, req);
  }

  performTaunt(sessionId: number, req: TauntRequest): Observable<TauntResult> {
    return this.http.post<TauntResult>(`${this.base}/sessions/${sessionId}/taunt`, req);
  }

  performFear(sessionId: number, req: FearRequest): Observable<FearResult> {
    return this.http.post<FearResult>(`${this.base}/sessions/${sessionId}/fear`, req);
  }

  resistFear(sessionId: number, combatantId: number): Observable<FearResistResult> {
    return this.http.post<FearResistResult>(
      `${this.base}/sessions/${sessionId}/combatants/${combatantId}/resist-fear`, {}
    );
  }

  /** Öffnet den für alle Clients sichtbaren Auswahldialog (Magie neutralisieren). */
  openNeutralizeMagicDialog(sessionId: number, combatantId: number): Observable<CombatSession> {
    return this.http.post<CombatSession>(
      `${this.base}/sessions/${sessionId}/combatants/${combatantId}/neutralize-magic/open`, {}
    );
  }

  performNeutralizeMagic(sessionId: number, req: NeutralizeMagicRequest): Observable<NeutralizeMagicResult> {
    return this.http.post<NeutralizeMagicResult>(`${this.base}/sessions/${sessionId}/neutralize-magic`, req);
  }

  performAcrobaticDefense(sessionId: number, combatantId: number,
                          bonusSteps: number, spendKarma: boolean): Observable<AcrobaticDefenseResult> {
    return this.http.post<AcrobaticDefenseResult>(
      `${this.base}/sessions/${sessionId}/combatants/${combatantId}/acrobatic-defense`,
      null,
      { params: { bonusSteps, spendKarma } }
    );
  }

  performCombatSense(sessionId: number, req: CombatSenseRequest): Observable<CombatSenseResult> {
    return this.http.post<CombatSenseResult>(`${this.base}/sessions/${sessionId}/combat-sense`, req);
  }

  performDistract(sessionId: number, req: DistractRequest): Observable<DistractResult> {
    return this.http.post<DistractResult>(`${this.base}/sessions/${sessionId}/distract`, req);
  }

  performSpotArmorFlaw(sessionId: number, req: SpotArmorFlawRequest): Observable<SpotArmorFlawResult> {
    return this.http.post<SpotArmorFlawResult>(`${this.base}/sessions/${sessionId}/spot-armor-flaw`, req);
  }

  performIronWill(sessionId: number, combatantId: number,
                  attackTotal: number, spendKarma: boolean): Observable<IronWillResult> {
    return this.http.post<IronWillResult>(
      `${this.base}/sessions/${sessionId}/combatants/${combatantId}/iron-will`,
      null,
      { params: { attackTotal, spendKarma } }
    );
  }

  resolveDodge(sessionId: number, req: DodgeRequest): Observable<DodgeResult> {
    return this.http.post<DodgeResult>(`${this.base}/sessions/${sessionId}/dodge`, req);
  }

  standUp(sessionId: number, combatantId: number): Observable<StandUpResult> {
    return this.http.post<StandUpResult>(
      `${this.base}/sessions/${sessionId}/combatants/${combatantId}/stand-up`,
      {}
    );
  }

  aufspringen(sessionId: number, combatantId: number, spendKarma: boolean): Observable<StandUpResult> {
    return this.http.post<StandUpResult>(
      `${this.base}/sessions/${sessionId}/combatants/${combatantId}/aufspringen`,
      null,
      { params: { spendKarma } }
    );
  }

  getLog(sessionId: number): Observable<CombatLog[]> {
    return this.http.get<CombatLog[]>(`${this.base}/sessions/${sessionId}/log`);
  }

  // --- Zauber ---

  // --- Kampfkarte ---

  configureMap(sessionId: number, enabled: boolean, width?: number, height?: number): Observable<CombatSession> {
    let params = `enabled=${enabled}`;
    if (width != null) params += `&width=${width}`;
    if (height != null) params += `&height=${height}`;
    return this.http.post<CombatSession>(`${this.base}/sessions/${sessionId}/map/configure?${params}`, {});
  }

  placeOnMap(sessionId: number, combatantId: number, q: number, r: number): Observable<CombatSession> {
    return this.http.post<CombatSession>(
      `${this.base}/sessions/${sessionId}/map/place?combatantId=${combatantId}&q=${q}&r=${r}`, {});
  }

  moveOnMap(sessionId: number, combatantId: number, q: number, r: number, gmOverride = false): Observable<CombatSession> {
    return this.http.post<CombatSession>(
      `${this.base}/sessions/${sessionId}/map/move?combatantId=${combatantId}&q=${q}&r=${r}&gmOverride=${gmOverride}`, {});
  }

  addObstacle(sessionId: number, type: string, q: number, r: number): Observable<CombatSession> {
    return this.http.post<CombatSession>(
      `${this.base}/sessions/${sessionId}/map/obstacles?type=${type}&q=${q}&r=${r}`, {});
  }

  removeObstacle(sessionId: number, obstacleId: number): Observable<CombatSession> {
    return this.http.delete<CombatSession>(`${this.base}/sessions/${sessionId}/map/obstacles/${obstacleId}`);
  }

  toggleDoor(sessionId: number, obstacleId: number): Observable<CombatSession> {
    return this.http.post<CombatSession>(
      `${this.base}/sessions/${sessionId}/map/obstacles/${obstacleId}/toggle-door`, {});
  }

  weaveThread(sessionId: number, req: ThreadweaveRequest): Observable<ThreadweaveResult> {
    return this.http.post<ThreadweaveResult>(`${this.base}/sessions/${sessionId}/weave-thread`, req);
  }

  castSpell(sessionId: number, req: SpellCastRequest): Observable<SpellCastResult> {
    return this.http.post<SpellCastResult>(`${this.base}/sessions/${sessionId}/cast-spell`, req);
  }

  cancelSpellPreparation(sessionId: number, combatantId: number): Observable<void> {
    return this.http.post<void>(
      `${this.base}/sessions/${sessionId}/combatants/${combatantId}/cancel-spell`,
      {}
    );
  }

  // --- Neue Talente ---

  performRiposte(sessionId: number, req: RiposteRequest): Observable<RiposteResult> {
    return this.http.post<RiposteResult>(`${this.base}/sessions/${sessionId}/riposte`, req);
  }

  performManoeuver(sessionId: number, req: ManoeuverRequest): Observable<ManoeuverResult> {
    return this.http.post<ManoeuverResult>(`${this.base}/sessions/${sessionId}/manoeuver`, req);
  }

  performTigersprung(sessionId: number, combatantId: number): Observable<TigersprungResult> {
    return this.http.post<TigersprungResult>(
      `${this.base}/sessions/${sessionId}/combatants/${combatantId}/tigersprung`, {}
    );
  }

  performZweitwaffe(sessionId: number, req: ZweitwaffeRequest): Observable<CombatActionResult> {
    return this.http.post<CombatActionResult>(`${this.base}/sessions/${sessionId}/zweitwaffe`, req);
  }

  performNachtreten(sessionId: number, req: NachtretenRequest): Observable<CombatActionResult> {
    return this.http.post<CombatActionResult>(`${this.base}/sessions/${sessionId}/nachtreten`, req);
  }

  performSchwanzangriff(sessionId: number, req: SchwanzangriffRequest): Observable<CombatActionResult> {
    return this.http.post<CombatActionResult>(`${this.base}/sessions/${sessionId}/schwanzangriff`, req);
  }

  performLufttanz(sessionId: number, combatantId: number): Observable<LufttanzActivationResult> {
    return this.http.post<LufttanzActivationResult>(
      `${this.base}/sessions/${sessionId}/combatants/${combatantId}/lufttanz`, {}
    );
  }

  performLufttanzAttack(sessionId: number, req: LufttanzAttackRequest): Observable<CombatActionResult> {
    return this.http.post<CombatActionResult>(`${this.base}/sessions/${sessionId}/lufttanz-attack`, req);
  }

  performBlattschussAddKarma(sessionId: number, combatantId: number): Observable<CombatActionResult> {
    return this.http.post<CombatActionResult>(
      `${this.base}/sessions/${sessionId}/combatants/${combatantId}/blattschuss-add-karma`, {}
    );
  }

  /** Schließt das synchronisierte Result-Modal für ALLE Zuschauer der Session. */
  dismissModal(sessionId: number): Observable<CombatSession> {
    return this.http.post<CombatSession>(`${this.base}/sessions/${sessionId}/dismiss-modal`, {});
  }

  /**
   * Setzt den Dialog-Status eines Kombattanten (welches Ziel/Waffe/Zauber er plant) und
   * broadcastet ihn via WebSocket an alle Zuschauer. state=null → Dialog geschlossen.
   */
  updateDialogState(sessionId: number, combatantId: number, state: DialogState | null): Observable<void> {
    return this.http.post<void>(
      `${this.base}/sessions/${sessionId}/combatants/${combatantId}/dialog-state`,
      state ?? { actionType: null }
    );
  }
}
