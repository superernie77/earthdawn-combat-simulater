import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CombatSession, AttackActionRequest, CombatActionResult,
  CombatLog, ActiveEffect, FreeActionRequest, FreeActionResult,
  DodgeRequest, DodgeResult, StandUpResult,
  ThreadweaveRequest, ThreadweaveResult,
  SpellCastRequest, SpellCastResult
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

  removeEffect(sessionId: number, combatantId: number, effectId: number): Observable<CombatSession> {
    return this.http.delete<CombatSession>(
      `${this.base}/sessions/${sessionId}/combatants/${combatantId}/effects/${effectId}`
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
}
