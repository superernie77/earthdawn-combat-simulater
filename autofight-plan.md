# Autofight Feature — Implementation Plan

## Decisions

| Concern | Decision |
|---|---|
| Scope | Per-combatant checkbox on each card |
| Target | First non-defeated enemy in initiative order; Kampfsinn target takes priority via `lastTargetMap` |
| Karma | Always spend if `currentKarma > 0` |
| Stance | AGGRESSIVE when `currentDamage < 50% UR`, DEFENSIVE otherwise |
| Spell casters | Declare SPELL + cast first 0-thread DAMAGE/DEBUFF spell; else WEAPON + physical attack |
| Round advance | Manual — autofight stops when all acted; resumes automatically after user clicks "Nächste Runde" (WS update triggers) |
| Modals | Show for 4 seconds then auto-close — EXCEPT result modal stays open when a manual combatant needs to respond to a dodge |
| Dodge vs manual defender | Show dodge dialog for manual defender; auto-skip when defender also has autofight checked |

---

## File Changed

**Only:** `frontend/src/app/components/combat-tracker/combat-tracker.component.ts`

---

## Changes

### 1. Add `MatCheckboxModule`
Add ES import and to `@Component` `imports` array.

---

### 2. Checkbox on each combatant card
In the combatant card header area (~line 139–250), near the initiative badge:
```html
<mat-checkbox
  [checked]="isAutofight(c)"
  (change)="toggleAutofight(c, $event.checked)"
  color="warn"
  matTooltip="Automatisch kämpfen">
  Auto
</mat-checkbox>
```

---

### 3. New properties (after `lastTargetMap` ~line 2026)
```typescript
private autofightCombatants = new Set<number>();
private autofightPending = false;
```

---

### 4. Modify `ngOnInit` WebSocket handler (~line 2149)
```typescript
this.wsSub = this.wsService.subscribeToSession(id).subscribe(s => {
  this.session = s;
  this.logEntries = s.log ?? [];
  this.scheduleAutofight();  // ADD
});
```

---

### 5. Modify `ngOnDestroy` (~line 2156)
```typescript
ngOnDestroy(): void {
  this.autofightCombatants.clear();  // ADD
  ...
}
```

---

### 6. New helper methods

```typescript
isAutofight(c: CombatantState): boolean {
  return this.autofightCombatants.has(c.id);
}

toggleAutofight(c: CombatantState, checked: boolean): void {
  if (checked) { this.autofightCombatants.add(c.id); this.scheduleAutofight(); }
  else          { this.autofightCombatants.delete(c.id); }
}

private autoCloseModal(modal: { open: boolean }): void {
  setTimeout(() => { modal.open = false; }, 4000);
}

private autofightStance(actor: CombatantState): DeclaredStance {
  return actor.currentDamage < this.ur(actor) * 0.5 ? 'AGGRESSIVE' : 'DEFENSIVE';
}

private autofightDeclareType(actor: CombatantState): DeclaredActionType {
  if (!this.isMagicCombatant(actor)) return 'WEAPON';
  const hasSpell = (actor.character.spells ?? []).some(
    s => s.spellDefinition.threads === 0 &&
         (s.spellDefinition.effectType === 'DAMAGE' || s.spellDefinition.effectType === 'DEBUFF')
  );
  return hasSpell ? 'SPELL' : 'WEAPON';
}

private autofightTarget(actor: CombatantState): CombatantState | undefined {
  const lastId = this.lastTargetMap.get(actor.id);
  const preferred = lastId != null
    ? this.session?.combatants.find(c => c.id === lastId && !c.defeated && c.npc !== actor.npc)
    : undefined;
  return preferred ?? this.session?.combatants.find(c => !c.defeated && c.npc !== actor.npc);
}

private canUseCombatSense(actor: CombatantState): boolean {
  if (!(actor.character.talents ?? []).some(t => t.talentDefinition.name === 'Kampfsinn')) return false;
  return !actor.activeEffects.some(
    e => e.name === 'Kampfsinn' || e.name === 'Akrobatische Verteidigung'
  );
}

private combatSenseTarget(actor: CombatantState): CombatantState | undefined {
  return this.session?.combatants.find(
    c => !c.defeated && c.npc !== actor.npc && c.initiativeOrder > actor.initiativeOrder
  );
}

private canUseAcrobaticDefense(actor: CombatantState): boolean {
  if (!(actor.character.talents ?? []).some(t => t.talentDefinition.name === 'Akrobatische Verteidigung')) return false;
  return !actor.activeEffects.some(
    e => e.name === 'Kampfsinn' || e.name === 'Akrobatische Verteidigung'
  );
}
```

---

### 7. `scheduleAutofight()`
```typescript
private scheduleAutofight(): void {
  if (this.autofightCombatants.size === 0 || this.autofightPending) return;
  this.autofightPending = true;
  setTimeout(() => {
    this.autofightPending = false;
    this.runAutofightStep();
  }, 600);
}
```

---

### 8. `runAutofightStep()`

```typescript
private runAutofightStep(): void {
  if (!this.session || this.session.status !== 'ACTIVE') return;
  if (this.autofightCombatants.size === 0) return;

  const sessionId = this.session.id;

  // DECLARATION PHASE
  if (this.session.phase === 'DECLARATION') {
    const undeclared = this.session.combatants.find(
      c => !c.defeated && !c.hasDeclared && this.autofightCombatants.has(c.id)
    );
    if (undeclared) {
      const stance = this.autofightStance(undeclared);
      const actionType = this.autofightDeclareType(undeclared);
      this.combatService.declareAction(sessionId, undeclared.id, stance, actionType).subscribe({
        next: s => { this.session = s; this.logEntries = s.log ?? []; this.scheduleAutofight(); },
        error: err => this.snack.open('Autofight (Ansage): ' + (err?.error?.message ?? err.message), 'OK', { duration: 3000 })
      });
    }
    return;
  }

  // ACTION PHASE
  if (this.session.phase === 'ACTION') {
    const active = this.session.combatants.filter(c => !c.defeated);
    if (active.every(c => c.hasActedThisRound)) return; // wait for manual "Nächste Runde"

    const actor = this.session.combatants.find(c => !c.defeated && !c.hasActedThisRound);
    if (!actor) return;
    if (!this.autofightCombatants.has(actor.id)) return; // manual combatant — wait

    // Knocked down
    if (actor.knockedDown) {
      this.combatService.standUp(sessionId, actor.id).subscribe({
        next: result => {
          this.standUpModal = { open: true, result };
          this.autoCloseModal(this.standUpModal);
          this.combatService.findById(sessionId).subscribe(s => {
            this.session = s; this.logEntries = s.log ?? []; this.scheduleAutofight();
          });
        },
        error: err => this.snack.open('Autofight (Aufstehen): ' + (err?.error?.message ?? err.message), 'OK', { duration: 3000 })
      });
      return;
    }

    // No-target check
    const target = this.autofightTarget(actor);
    if (!target) {
      this.combatService.declareCombatOption(sessionId, actor.id, 'USE_ACTION').subscribe({
        next: s => { this.session = s; this.logEntries = s.log ?? []; this.scheduleAutofight(); },
        error: err => this.snack.open('Autofight (Überspringen): ' + (err?.error?.message ?? err.message), 'OK', { duration: 3000 })
      });
      return;
    }

    // Free action: Kampfsinn
    if (this.canUseCombatSense(actor)) {
      const csTarget = this.combatSenseTarget(actor);
      if (csTarget) {
        const req: CombatSenseRequest = {
          sessionId, actorCombatantId: actor.id, targetCombatantId: csTarget.id,
          bonusSteps: 0, spendKarma: actor.currentKarma > 0
        };
        this.lastTargetMap.set(actor.id, csTarget.id);
        this.combatService.performCombatSense(sessionId, req).subscribe({
          next: result => {
            this.combatSenseModal = { open: true, result };
            this.autoCloseModal(this.combatSenseModal);
            this.combatService.findById(sessionId).subscribe(s => {
              this.session = s; this.logEntries = s.log ?? []; this.scheduleAutofight();
            });
          },
          error: err => this.snack.open('Autofight (Kampfsinn): ' + (err?.error?.message ?? err.message), 'OK', { duration: 3000 })
        });
        return;
      }
    }

    // Free action: Akrobatische Verteidigung
    if (this.canUseAcrobaticDefense(actor)) {
      this.combatService.performAcrobaticDefense(sessionId, actor.id, 0, actor.currentKarma > 0).subscribe({
        next: result => {
          this.acrobaticModal = { open: true, result };
          this.autoCloseModal(this.acrobaticModal);
          this.combatService.findById(sessionId).subscribe(s => {
            this.session = s; this.logEntries = s.log ?? []; this.scheduleAutofight();
          });
        },
        error: err => this.snack.open('Autofight (Akrobatik): ' + (err?.error?.message ?? err.message), 'OK', { duration: 3000 })
      });
      return;
    }

    // Main action: 0-thread spell (magic users)
    if (this.isMagicCombatant(actor)) {
      const castableSpell = (actor.character.spells ?? []).find(
        s => s.spellDefinition.threads === 0 &&
             (s.spellDefinition.effectType === 'DAMAGE' || s.spellDefinition.effectType === 'DEBUFF')
      );
      if (castableSpell) {
        const spellReq: SpellCastRequest = {
          sessionId, casterCombatantId: actor.id, targetCombatantId: target.id,
          spellId: castableSpell.spellDefinition.id, spendKarma: actor.currentKarma > 0
        };
        this.combatService.castSpell(sessionId, spellReq).subscribe({
          next: result => {
            this.spellCastModal = { open: true, result };
            this.autoCloseModal(this.spellCastModal);
            this.combatService.findById(sessionId).subscribe(s => {
              this.session = s; this.logEntries = s.log ?? []; this.scheduleAutofight();
            });
          },
          error: err => this.snack.open('Autofight (Zauber): ' + (err?.error?.message ?? err.message), 'OK', { duration: 3000 })
        });
        return;
      }
    }

    // Main action: physical attack
    const bestTalent = (actor.character.talents ?? [])
      .filter(t => t.talentDefinition.attackTalent && t.talentDefinition.name !== 'Spruchzauberei')
      .sort((a, b) => b.rank - a.rank)[0];
    const bestWeapon = (actor.character.equipment ?? [])
      .filter(e => e.type === 'WEAPON')
      .sort((a, b) => b.damageBonus - a.damageBonus)[0];
    const talentName = bestTalent?.talentDefinition.name ?? '';
    const actionType: AttackActionRequest['actionType'] =
      (talentName === 'Projektilwaffen' || talentName === 'Wurfwaffen') ? 'RANGED_ATTACK' : 'MELEE_ATTACK';

    const req: AttackActionRequest = {
      sessionId,
      attackerCombatantId: actor.id,
      defenderCombatantId: target.id,
      actionType,
      talentId: bestTalent?.talentDefinition.id,
      weaponId: bestWeapon?.id,
      bonusSteps: 0,
      spendKarma: actor.currentKarma > 0,
      aggressiveAttack: false,
      defensiveStance: false
    };

    this.combatService.performAttack(req).subscribe({
      next: result => {
        this.lastTargetMap.set(actor.id, target.id);
        this.resultModal = { open: true, result };

        if (result.hitPendingDodge && result.dodgeDefenderId) {
          const defenderIsAuto = this.autofightCombatants.has(result.dodgeDefenderId);
          if (defenderIsAuto) {
            this.autoCloseModal(this.resultModal);
            const dodgeReq: DodgeRequest = {
              sessionId, defenderCombatantId: result.dodgeDefenderId,
              dodgeAttempted: false, bonusSteps: 0, spendKarma: false
            };
            this.combatService.resolveDodge(sessionId, dodgeReq).subscribe({
              next: dodgeResult => {
                this.dodgeModal = { open: true, result: dodgeResult };
                this.autoCloseModal(this.dodgeModal);
                this.combatService.findById(sessionId).subscribe(s => {
                  this.session = s; this.logEntries = s.log ?? []; this.scheduleAutofight();
                });
              },
              error: err => this.snack.open('Autofight (Ausweichen): ' + (err?.error?.message ?? err.message), 'OK', { duration: 3000 })
            });
          } else {
            // Manual defender: keep result modal open, wait for user to handle dodge.
            // WS update after dodge resolution will re-trigger scheduleAutofight().
            this.combatService.findById(sessionId).subscribe(s => {
              this.session = s; this.logEntries = s.log ?? [];
            });
          }
        } else {
          this.autoCloseModal(this.resultModal);
          this.combatService.findById(sessionId).subscribe(s => {
            this.session = s; this.logEntries = s.log ?? []; this.scheduleAutofight();
          });
        }
      },
      error: err => this.snack.open('Autofight (Angriff): ' + (err?.error?.message ?? err.message), 'OK', { duration: 3000 })
    });
  }
}
```

---

## Per-Turn Decision Tree

```
knockedDown?                   → standUp(); autoClose 4s
no target?                     → USE_ACTION
canUseCombatSense?
  valid csTarget (lower ini)?  → performCombatSense(karma); lastTargetMap=csTarget; return [free action]
canUseAcrobaticDefense?        → performAcrobaticDefense(karma); return [free action]
isMagicCombatant?
  0-thread DAMAGE/DEBUFF?      → castSpell(target, karma); autoClose 4s
physical attack:
  target = lastTargetMap ?? firstEnemy
  performAttack(bestTalent, bestWeapon, karma)
    hitPendingDodge + defenderAuto  → resolveDodge(skip); autoClose 4s
    hitPendingDodge + manual        → keep modal open; wait for user
    no dodge                        → autoClose 4s; scheduleAutofight
```

---

## Verification

1. Check Auto on one hero → only that hero auto-acts; others wait for manual input.
2. Bring a combatant to ≥50% UR → verify DEFENSIVE stance next declaration.
3. Combatant with `currentKarma > 0` → verify `spendKarma: true` in all requests.
4. Hero with Kampfsinn vs lower-initiative enemy → `/combat-sense` fires, then `/attack` on same target.
5. Hero with Akrobatik, no valid Kampfsinn target → `/acrobatic-defense` fires before `/attack`.
6. Mage with 0-thread DAMAGE spell → SPELL declaration + `/cast-spell` instead of `/attack`.
7. Result modals disappear after ~4 seconds without user interaction.
8. Auto-attacker hits manual defender with Ausweichen → result modal stays open, dodge dialog appears.
9. After all acted, "Nächste Runde" button still works; clicking it resumes autofight for next round.
10. All enemies defeated → heroes call `USE_ACTION`, no crash.
