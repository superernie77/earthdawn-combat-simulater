import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDividerModule } from '@angular/material/divider';
import { Subscription } from 'rxjs';
import { CombatService } from '../../services/combat.service';
import { CharacterService } from '../../services/character.service';
import { WebSocketService } from '../../services/websocket.service';
import {
  CombatSession, CombatantState, AttackActionRequest,
  CombatActionResult, ActiveEffect, FreeActionRequest, FreeActionResult,
  DodgeRequest, DodgeResult, StandUpResult
} from '../../models/combat.model';
import { Character } from '../../models/character.model';

@Component({
  selector: 'app-combat-tracker',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatCardModule, MatButtonModule, MatIconModule, MatSelectModule,
    MatFormFieldModule, MatInputModule, MatDialogModule,
    MatSnackBarModule, MatTooltipModule, MatDividerModule
  ],
  template: `
    <div class="loading-state" *ngIf="!session && !loadError">
      <mat-icon style="font-size:48px;height:48px;width:48px;color:#555">hourglass_empty</mat-icon>
      <p style="color:#666">Lade Session...</p>
    </div>
    <div class="loading-state" *ngIf="loadError">
      <mat-icon style="font-size:48px;height:48px;width:48px;color:#f44336">error_outline</mat-icon>
      <p style="color:#f44336">{{ loadError }}</p>
      <button mat-stroked-button (click)="router.navigate(['/combat'])">Zurück</button>
    </div>
    <div class="tracker-container" *ngIf="session">
      <!-- Header -->
      <div class="tracker-header">
        <div class="header-left">
          <button mat-icon-button (click)="router.navigate(['/combat'])"><mat-icon>arrow_back</mat-icon></button>
          <div>
            <span class="session-name">{{ session.name }}</span>
            <span class="round-badge">Runde {{ session.round }}</span>
            <span [class]="'status-badge ' + session.status.toLowerCase()">{{ statusLabel() }}</span>
          </div>
        </div>
        <div class="header-actions">
          <button mat-stroked-button *ngIf="session.status === 'SETUP'" (click)="addCharacterPanel = !addCharacterPanel">
            <mat-icon>person_add</mat-icon> Kombattant
          </button>
          <button mat-raised-button color="primary" *ngIf="session.status === 'SETUP'" (click)="rollInitiative()">
            <mat-icon>casino</mat-icon> Initiative würfeln
          </button>
          <button mat-stroked-button *ngIf="session.status === 'ACTIVE'" (click)="nextRound()">
            <mat-icon>skip_next</mat-icon> Nächste Runde
          </button>
          <button mat-stroked-button color="warn" *ngIf="session.status === 'ACTIVE'" (click)="endCombat()">
            <mat-icon>flag</mat-icon> Beenden
          </button>
          <button mat-icon-button color="warn" (click)="deleteSession()" matTooltip="Session löschen">
            <mat-icon>delete</mat-icon>
          </button>
        </div>
      </div>

      <!-- Add Character Panel -->
      <div class="add-panel" *ngIf="addCharacterPanel">
        <div class="add-panel-group-toggle">
          <button class="group-toggle-btn" [class.active]="!isNpcAdd" (click)="isNpcAdd = false">
            <mat-icon>shield</mat-icon> Held
          </button>
          <button class="group-toggle-btn enemy" [class.active]="isNpcAdd" (click)="isNpcAdd = true">
            <mat-icon>skull</mat-icon> Gegner
          </button>
        </div>
        <mat-form-field appearance="fill" style="width:300px">
          <mat-label>Charakter hinzufügen</mat-label>
          <mat-select [(ngModel)]="selectedCharId" (ngModelChange)="addCombatant()">
            <mat-option *ngFor="let c of allCharacters" [value]="c.id">
              {{ c.name }} ({{ c.discipline?.name || 'Kein' }}, Kreis {{ c.circle }})
            </mat-option>
          </mat-select>
        </mat-form-field>
      </div>

      <div class="tracker-body">
        <!-- Left: Combatants -->
        <div class="combatants-panel">
          <div class="combatants-columns">
            <!-- Heroes -->
            <div class="combatant-group">
              <div class="group-header heroes-header"><mat-icon>shield</mat-icon> Helden</div>
              <div
                class="combatant-card"
                *ngFor="let c of heroes()"
                [class.active-turn]="isActiveTurn(c) && session.status === 'ACTIVE'"
                [class.defeated]="c.defeated"
                [class.has-acted]="c.hasActedThisRound && !c.defeated && session.status === 'ACTIVE'">
                <ng-container *ngTemplateOutlet="combatantCard; context: { c: c }"></ng-container>
              </div>
            </div>
            <!-- Enemies -->
            <div class="combatant-group">
              <div class="group-header enemies-header"><mat-icon>skull</mat-icon> Gegner</div>
              <div
                class="combatant-card"
                *ngFor="let c of enemies()"
                [class.active-turn]="isActiveTurn(c) && session.status === 'ACTIVE'"
                [class.defeated]="c.defeated"
                [class.has-acted]="c.hasActedThisRound && !c.defeated && session.status === 'ACTIVE'">
                <ng-container *ngTemplateOutlet="combatantCard; context: { c: c }"></ng-container>
              </div>
            </div>
          </div>

        </div>

        <ng-template #combatantCard let-c="c">
            <!-- Combatant Header -->
            <div class="comb-header">
              <div class="comb-title">
                <span class="initiative-badge" matTooltip="Initiative">{{ c.initiative }}</span>
                <span class="combatant-name">{{ c.character.name }}</span>
                <span class="discipline-badge">{{ c.character.discipline?.name }}</span>
                <mat-icon *ngIf="c.defeated" style="color:#f44336;font-size:16px">skull</mat-icon>
                <span *ngIf="c.knockedDown && !c.defeated" class="knocked-badge" matTooltip="Niedergeschlagen: −3 auf alle Proben, −3 KVK/ZVK">↓ Nieder</span>
              </div>
              <div class="comb-actions">
                <span *ngIf="session!.status === 'ACTIVE' && c.hasActedThisRound && !c.defeated" class="acted-badge" matTooltip="Hat diese Runde bereits gehandelt">Gehandelt</span>
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE'"
                  class="attack-btn"
                  [disabled]="c.hasActedThisRound || c.defeated || !isActiveTurn(c)"
                  (click)="openAttackDialog(c)" matTooltip="Angreifen">
                  <mat-icon>sports_martial_arts</mat-icon> Angriff
                </button>
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE' && c.knockedDown && !c.defeated"
                  class="combat-option-btn standup-btn"
                  [disabled]="c.hasActedThisRound"
                  (click)="performStandUp(c)"
                  matTooltip="Aufstehen (Hauptaktion)">
                  <mat-icon>accessibility_new</mat-icon>
                </button>
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE' && c.knockedDown && !c.defeated"
                  class="combat-option-btn aufspringen-btn"
                  (click)="openAufspringenDialog(c)"
                  matTooltip="Aufspringen (GE-Probe vs 6, 2 Schaden — kann danach noch angreifen)">
                  <mat-icon>directions_run</mat-icon>
                </button>
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE'"
                  class="combat-option-btn use-action" [disabled]="c.hasActedThisRound || c.defeated"
                  (click)="useAction(c)"
                  matTooltip="Aktion benutzen (Zauber / Faden weben / Sonstiges)">
                  <mat-icon>auto_awesome</mat-icon>
                </button>
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE' && freeActionTalentsOf(c).length > 0"
                  class="combat-option-btn free-action" [disabled]="c.defeated"
                  (click)="openFreeActionDialog(c)"
                  matTooltip="Freie Kampfaktion einsetzen">
                  <mat-icon>bolt</mat-icon>
                </button>
                <button mat-icon-button *ngIf="session!.status === 'SETUP'"
                  color="warn" (click)="removeCombatant(c.id)" matTooltip="Entfernen">
                  <mat-icon>close</mat-icon>
                </button>
              </div>
            </div>
            <!-- Damage Track -->
            <div class="comb-damage-row">
              <span class="dmg-label">Schaden</span>
              <div class="damage-track" style="flex:1;margin:0 8px">
                <div class="damage-track__fill" [style.width.%]="damagePercent(c)"></div>
              </div>
              <span class="dmg-val">{{ c.currentDamage }}/{{ ur(c) }}</span>
              <div class="mini-ctrl">
                <button mat-icon-button (click)="updateValue(c, 'damage', -1)" matTooltip="Heilen">
                  <mat-icon style="font-size:16px">healing</mat-icon>
                </button>
                <button mat-icon-button color="warn" (click)="updateValue(c, 'damage', 1)" matTooltip="Schaden">
                  <mat-icon style="font-size:16px">bolt</mat-icon>
                </button>
              </div>
            </div>
            <!-- Wounds + Karma row -->
            <div class="comb-status-row">
              <div class="comb-stat">
                <span class="comb-stat-label">Wunden</span>
                <div class="mini-ctrl">
                  <button mat-icon-button (click)="updateValue(c, 'wounds', -1)"><mat-icon style="font-size:14px">remove</mat-icon></button>
                  <span class="stat-value wounds">{{ c.wounds }}</span>
                  <button mat-icon-button color="warn" (click)="updateValue(c, 'wounds', 1)"><mat-icon style="font-size:14px">add</mat-icon></button>
                </div>
              </div>
              <div class="comb-stat">
                <span class="comb-stat-label">Karma</span>
                <div class="mini-ctrl">
                  <button mat-icon-button (click)="updateValue(c, 'karma', -1)"><mat-icon style="font-size:14px">remove</mat-icon></button>
                  <span class="stat-value karma">{{ c.currentKarma }}</span>
                  <button mat-icon-button (click)="updateValue(c, 'karma', 1)"><mat-icon style="font-size:14px">add</mat-icon></button>
                </div>
              </div>
            </div>
            <!-- Defense Values -->
            <div class="comb-defense-row">
              <span class="def-stat" matTooltip="Körperliche Verteidigung">
                <mat-icon>shield</mat-icon> {{ pd(c) }}
              </span>
              <span class="def-stat mystic" matTooltip="Zauberverteidigung">
                <mat-icon>auto_awesome</mat-icon> {{ sd(c) }}
              </span>
              <span class="def-stat social" matTooltip="Soziale Verteidigung">
                <mat-icon>people</mat-icon> {{ socD(c) }}
              </span>
              <span class="def-stat armor-phys" matTooltip="Körperliche Rüstung" *ngIf="pa(c) > 0">
                <mat-icon>security</mat-icon> {{ pa(c) }}
              </span>
              <span class="def-stat armor-myst" matTooltip="Mystische Rüstung" *ngIf="ma(c) > 0">
                <mat-icon>flare</mat-icon> {{ ma(c) }}
              </span>
            </div>
            <!-- Active Effects -->
            <div class="effects-row" *ngIf="c.activeEffects.length > 0">
              <span
                *ngFor="let e of c.activeEffects"
                [class]="'effect-chip ' + (e.negative ? 'negative' : 'positive')"
                (click)="removeEffect(c, e)"
                matTooltip="Klicken zum Entfernen. {{ e.description }} {{ e.remainingRounds > 0 ? '(' + e.remainingRounds + ' Runden)' : '(permanent)' }}"
                style="cursor:pointer">
                {{ e.name }}
                <span *ngIf="e.remainingRounds > 0"> ({{ e.remainingRounds }})</span>
              </span>
            </div>
        </ng-template>

        <!-- Right: Log -->
        <div class="log-panel">

          <!-- Combat Log -->
          <div class="section-title" style="margin-top:16px">Kampfprotokoll</div>
          <div class="log-scroll">
            <div
              *ngFor="let entry of logEntries"
              [class]="'combat-log-entry ' + (entry.success ? 'success' : isSystem(entry.actionType) ? 'system' : 'failure')">
              <span class="round-badge">R{{ entry.round }}</span>
              {{ entry.description }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Result Modal -->
    <div class="result-modal" *ngIf="resultModal.open">
      <div class="dialog-backdrop" (click)="resultModal.open = false"></div>
      <div class="dialog-box result-box" *ngIf="resultModal.result as r">
        <div class="result-outcome" [class.hit]="r.hit" [class.miss]="!r.hit">
          <mat-icon>{{ r.hit ? 'gps_fixed' : 'close' }}</mat-icon>
          {{ r.hit ? 'TREFFER' : 'VERFEHLT' }}
        </div>
        <div class="result-names">
          <span class="result-actor">{{ r.actorName }}</span>
          <mat-icon style="color:#555;font-size:18px">arrow_forward</mat-icon>
          <span class="result-target">{{ r.targetName }}</span>
        </div>
        <div class="aggressive-active-badge" *ngIf="r.aggressiveAttack">
          <mat-icon>whatshot</mat-icon> Aggressiver Angriff
        </div>
        <div class="result-rolls">
          <!-- Attack roll block -->
          <div class="roll-block">
            <div class="roll-block-header">
              <span class="roll-block-label">Angriff · Step {{ r.attackStep }}</span>
              <div class="roll-block-totals">
                <span class="roll-big-total">{{ r.attackRoll.total + (r.karmaRoll?.total ?? 0) }}</span>
                <span class="roll-big-vs">vs</span>
                <span class="roll-big-target">VK {{ r.defenseValue }}</span>
              </div>
            </div>
            <div class="dice-breakdown-mini">
              <div class="die-mini" *ngFor="let d of r.attackRoll.dice" [class.exploded]="d.exploded">
                <span class="die-mini-sides">W{{ d.sides }}</span>
                <span class="die-mini-rolls">{{ d.rolls.join(' + ') }}<span *ngIf="d.rolls.length > 1" class="die-mini-sum"> = {{ d.total }}</span></span>
                <span *ngIf="d.exploded" class="explode-mini">💥</span>
              </div>
              <div class="die-mini karma-die" *ngIf="r.karmaRoll">
                <span class="die-mini-sides" style="color:#c9a84c">★ W6</span>
                <span class="die-mini-rolls">{{ r.karmaRoll.dice[0].rolls.join(' + ') }}<span *ngIf="r.karmaRoll.exploded"> 💥</span></span>
              </div>
            </div>
            <div class="roll-effect-notes" *ngIf="r.attackBonusNotes?.length">
              <span class="effect-note" *ngFor="let note of r.attackBonusNotes">✦ {{ note }}</span>
            </div>
          </div>
          <ng-container *ngIf="r.hit && r.damageRoll && !r.hitPendingDodge">
            <div class="roll-divider"></div>
            <div class="roll-row extra-success-row" *ngIf="r.extraSuccesses && r.extraSuccesses > 0">
              <span class="roll-label">Übererfolge</span>
              <span class="roll-expr">{{ r.extraSuccesses }}× → +{{ r.extraSuccesses * 2 }} Stufen</span>
              <span class="roll-value extra-success">+{{ r.extraSuccesses * 2 }}</span>
            </div>
            <div class="roll-block" style="background:rgba(239,83,80,0.06);border:1px solid #3a1e1e">
              <div class="roll-block-header">
                <span class="roll-block-label">
                  Schaden · Step {{ r.damageStep }}
                  <span class="step-calc" *ngIf="r.extraSuccesses && r.extraSuccesses > 0">
                    ({{ r.damageStep! - r.extraSuccesses * 2 }} + {{ r.extraSuccesses * 2 }} Übererfolge)
                  </span>
                </span>
                <div class="roll-block-totals">
                  <span class="roll-big-total" style="color:#ef5350">{{ r.damageRoll!.total }}</span>
                  <ng-container *ngIf="(r.armorValue ?? 0) > 0">
                    <span class="roll-big-vs">−</span>
                    <span class="roll-big-total" style="color:#888;font-size:1.4rem">{{ r.armorValue }}</span>
                    <span class="roll-big-vs">=</span>
                    <span class="roll-big-total" style="color:#ff7043">{{ r.netDamage }}</span>
                  </ng-container>
                </div>
              </div>
              <div class="dice-breakdown-mini">
                <div class="die-mini" *ngFor="let d of r.damageRoll!.dice" [class.exploded]="d.exploded">
                  <span class="die-mini-sides">W{{ d.sides }}</span>
                  <span class="die-mini-rolls">{{ d.rolls.join(' + ') }}<span *ngIf="d.rolls.length > 1" class="die-mini-sum"> = {{ d.total }}</span></span>
                  <span *ngIf="d.exploded" class="explode-mini">💥</span>
                </div>
              </div>
            </div>
            <div class="wound-banner" *ngIf="r.woundDealt">
              <mat-icon>bolt</mat-icon>
              {{ r.newWounds }} WUNDE{{ (r.newWounds ?? 0) > 1 ? 'N' : '' }} erlitten!
              Gesamt: {{ r.totalWounds }} · WS {{ r.woundThreshold }}
            </div>
            <div class="defeat-banner" *ngIf="r.targetDefeated">
              <mat-icon>skull</mat-icon> {{ r.targetName }} ist bewusstlos!
            </div>
            <ng-container *ngIf="r.knockdownResult as kd">
              <div class="knockdown-banner" [class.knocked]="kd.knockedDown" [class.stood]="!kd.knockedDown">
                <mat-icon>{{ kd.knockedDown ? 'airline_seat_flat' : 'accessibility_new' }}</mat-icon>
                STR {{ kd.roll.total }} vs {{ kd.targetNumber }} →
                {{ kd.knockedDown ? 'NIEDERGESCHLAGEN!' : 'Bleibt stehen' }}
              </div>
            </ng-container>
          </ng-container>
        </div>
        <!-- Dodge prompt -->
        <div class="dodge-prompt" *ngIf="r.hitPendingDodge">
          <mat-icon>directions_run</mat-icon>
          Ziel kann Ausweichen versuchen
        </div>
        <div style="display:flex;gap:8px;margin-top:16px" *ngIf="r.hitPendingDodge; else closeOnly">
          <button mat-stroked-button style="flex:1" (click)="skipDodge()">Schaden annehmen</button>
          <button mat-raised-button color="primary" style="flex:1" (click)="openDodgeDialog()">
            <mat-icon>directions_run</mat-icon> Ausweichen
          </button>
        </div>
        <ng-template #closeOnly>
          <button mat-raised-button style="width:100%;margin-top:16px" (click)="resultModal.open = false">
            Schließen
          </button>
        </ng-template>
      </div>
    </div>

    <!-- Dodge Dialog -->
    <div class="attack-dialog" *ngIf="dodgeDialog.open">
      <div class="dialog-backdrop" (click)="dodgeDialog.open = false"></div>
      <div class="dialog-box">
        <h3><mat-icon style="vertical-align:middle;margin-right:6px;color:#42a5f5">directions_run</mat-icon>Ausweichen</h3>
        <div style="color:#888;font-size:0.85rem;margin-bottom:12px">
          Probe: Stufe {{ dodgeStep() }} vs. Angriff <strong style="color:#fff">{{ dodgeDialog.attackTotal }}</strong> · Kostet 1 Schaden
        </div>
        <div style="display:flex;gap:8px;align-items:center">
          <label class="karma-toggle"
            [class.active]="dodgeDialog.spendKarma"
            [class.disabled]="(dodgeCombatant()?.currentKarma ?? 0) <= 0"
            (click)="(dodgeCombatant()?.currentKarma ?? 0) > 0 && (dodgeDialog.spendKarma = !dodgeDialog.spendKarma)">
            <mat-icon>auto_awesome</mat-icon>
            Karma
            <span class="karma-count-badge" [class.empty]="(dodgeCombatant()?.currentKarma ?? 0) <= 0">
              {{ dodgeCombatant()?.currentKarma ?? 0 }}
            </span>
          </label>
        </div>
        <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:8px">
          <button mat-stroked-button (click)="dodgeDialog.open = false; skipDodge()">Abbrechen</button>
          <button mat-raised-button color="primary" (click)="performDodge()">
            <mat-icon>directions_run</mat-icon> Würfeln
          </button>
        </div>
      </div>
    </div>

    <!-- Dodge Result Modal -->
    <div class="result-modal" *ngIf="dodgeModal.open">
      <div class="dialog-backdrop" (click)="dodgeModal.open = false"></div>
      <div class="dialog-box result-box" *ngIf="dodgeModal.result as r">
        <div class="result-outcome" [class.hit]="r.success" [class.miss]="!r.success">
          <mat-icon>{{ r.success ? 'directions_run' : 'close' }}</mat-icon>
          {{ r.success ? 'AUSGEWICHEN' : 'NICHT AUSGEWICHEN' }}
        </div>
        <div class="result-names">
          <span class="result-actor">{{ r.defenderName }}</span>
          <span style="color:#888;margin:0 6px;font-size:0.85rem">vs Angriff {{ r.attackTotal }}</span>
        </div>
        <div class="result-rolls">
          <!-- Dodge roll block -->
          <div class="roll-block">
            <div class="roll-block-header">
              <span class="roll-block-label">Ausweichen · Step {{ r.rollStep }}</span>
              <div class="roll-block-totals">
                <span class="roll-big-total">{{ (r.roll?.total ?? 0) + (r.karmaRoll?.total ?? 0) }}</span>
                <span class="roll-big-vs">vs</span>
                <span class="roll-big-target">{{ r.attackTotal }}</span>
              </div>
            </div>
            <div class="dice-breakdown-mini" *ngIf="r.roll">
              <div class="die-mini" *ngFor="let d of r.roll!.dice" [class.exploded]="d.exploded">
                <span class="die-mini-sides">W{{ d.sides }}</span>
                <span class="die-mini-rolls">{{ d.rolls.join(' + ') }}<span *ngIf="d.rolls.length > 1" class="die-mini-sum"> = {{ d.total }}</span></span>
                <span *ngIf="d.exploded" class="explode-mini">💥</span>
              </div>
              <div class="die-mini karma-die" *ngIf="r.karmaRoll">
                <span class="die-mini-sides" style="color:#c9a84c">★ W6</span>
                <span class="die-mini-rolls">{{ r.karmaRoll.dice[0].rolls.join(' + ') }}<span *ngIf="r.karmaRoll.exploded"> 💥</span></span>
              </div>
            </div>
          </div>
          <div class="roll-divider"></div>
          <div class="roll-row" style="background:rgba(239,83,80,0.08)">
            <span class="roll-label">Kosten</span>
            <span class="roll-expr">Ausweichen-Schaden</span>
            <span class="roll-value" style="color:#ef5350">−{{ r.damageCost }}</span>
          </div>
          <ng-container *ngIf="!r.success">
            <div class="roll-block" style="background:rgba(239,83,80,0.06);border:1px solid #3a1e1e">
              <div class="roll-block-header">
                <span class="roll-block-label">Schaden · Step {{ r.damageStep }}</span>
                <div class="roll-block-totals">
                  <span class="roll-big-total" style="color:#ef5350">{{ r.damageRoll?.total }}</span>
                  <ng-container *ngIf="(r.armorValue ?? 0) > 0">
                    <span class="roll-big-vs">−</span>
                    <span class="roll-big-total" style="color:#888;font-size:1.4rem">{{ r.armorValue }}</span>
                    <span class="roll-big-vs">=</span>
                    <span class="roll-big-total" style="color:#ff7043">{{ r.netDamageApplied }}</span>
                  </ng-container>
                </div>
              </div>
              <div class="dice-breakdown-mini" *ngIf="r.damageRoll">
                <div class="die-mini" *ngFor="let d of r.damageRoll.dice" [class.exploded]="d.exploded">
                  <span class="die-mini-sides">W{{ d.sides }}</span>
                  <span class="die-mini-rolls">{{ d.rolls.join(' + ') }}<span *ngIf="d.rolls.length > 1" class="die-mini-sum"> = {{ d.total }}</span></span>
                  <span *ngIf="d.exploded" class="explode-mini">💥</span>
                </div>
              </div>
            </div>
            <div class="wound-banner" *ngIf="r.newWounds > 0">
              <mat-icon>bolt</mat-icon>
              {{ r.newWounds }} WUNDE{{ r.newWounds > 1 ? 'N' : '' }} erlitten!
              Gesamt: {{ r.totalWounds }} · WS {{ r.woundThreshold }}
            </div>
            <div class="defeat-banner" *ngIf="r.targetDefeated">
              <mat-icon>skull</mat-icon> {{ r.defenderName }} ist bewusstlos!
            </div>
            <ng-container *ngIf="r.knockdownResult as kd">
              <div class="knockdown-banner" [class.knocked]="kd.knockedDown" [class.stood]="!kd.knockedDown">
                <mat-icon>{{ kd.knockedDown ? 'airline_seat_flat' : 'accessibility_new' }}</mat-icon>
                STR {{ kd.roll.total }} vs {{ kd.targetNumber }} →
                {{ kd.knockedDown ? 'NIEDERGESCHLAGEN!' : 'Bleibt stehen' }}
              </div>
            </ng-container>
          </ng-container>
        </div>
        <button mat-raised-button style="width:100%;margin-top:16px" (click)="dodgeModal.open = false">
          Schließen
        </button>
      </div>
    </div>

    <!-- Attack Dialog -->
    <div class="attack-dialog" *ngIf="attackDialog.open">
      <div class="dialog-backdrop" (click)="attackDialog.open = false"></div>
      <div class="dialog-box">
        <h3>Angriff: {{ attackDialog.attacker?.character?.name }}</h3>
        <div class="dialog-combat-options">
          <button class="combat-option-toggle aggressive"
            [class.active]="attackDialog.aggressiveAttack"
            (click)="toggleDialogAggressive()"
            matTooltip="+3 Stufen, 1 Schaden, -3 VK">
            <mat-icon>local_fire_department</mat-icon>
            Aggressiver Angriff
          </button>
          <div class="aggressive-info" *ngIf="attackDialog.aggressiveAttack">
            <span class="info-bonus">+3 Step</span>
            <span class="info-penalty">-3 VK</span>
            <span class="info-cost">1 Schaden</span>
          </div>
          <button class="combat-option-toggle defensive"
            [class.active]="attackDialog.defensiveStance"
            (click)="toggleDialogDefensive()"
            matTooltip="-3 Stufen, +3 Verteidigung">
            <mat-icon>shield</mat-icon>
            Defensive Haltung
          </button>
          <div class="aggressive-info" *ngIf="attackDialog.defensiveStance">
            <span class="info-penalty">-3 Step</span>
            <span class="info-bonus">+3 VK</span>
          </div>
        </div>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Ziel</mat-label>
          <mat-select [(ngModel)]="attackDialog.defenderId">
            <mat-option *ngFor="let c of possibleTargets()" [value]="c.id">
              {{ c.character.name }}
            </mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Waffe</mat-label>
          <mat-select [(ngModel)]="attackDialog.weaponId">
            <mat-option [value]="null">Keine Waffe</mat-option>
            <mat-option *ngFor="let e of weaponsOf(attackDialog.attacker)" [value]="e.id">
              {{ e.name }} (+{{ e.damageBonus }} Schaden)
            </mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Waffentalent</mat-label>
          <mat-select [(ngModel)]="attackDialog.talentId">
            <mat-option [value]="null">Kein Talent</mat-option>
            <mat-option *ngFor="let t of attackTalentsOf(attackDialog.attacker)" [value]="t.talentDefinition.id">
              ⚔ {{ t.talentDefinition.name }} (Rang {{ t.rank }})
            </mat-option>
            <mat-option *ngFor="let t of nonAttackTalentsOf(attackDialog.attacker)" [value]="t.talentDefinition.id">
              {{ t.talentDefinition.name }} (Rang {{ t.rank }})
            </mat-option>
          </mat-select>
        </mat-form-field>
        <div style="display:flex;gap:8px;align-items:center">
          <label class="karma-toggle"
            [class.active]="attackDialog.spendKarma"
            [class.disabled]="(attackDialog.attacker?.currentKarma ?? 0) <= 0"
            (click)="(attackDialog.attacker?.currentKarma ?? 0) > 0 && (attackDialog.spendKarma = !attackDialog.spendKarma)">
            <mat-icon>auto_awesome</mat-icon>
            Karma
            <span class="karma-count-badge" [class.empty]="(attackDialog.attacker?.currentKarma ?? 0) <= 0">
              {{ attackDialog.attacker?.currentKarma ?? 0 }}
            </span>
          </label>
        </div>
        <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:8px">
          <button mat-stroked-button (click)="attackDialog.open = false">Abbrechen</button>
          <button mat-raised-button color="warn" (click)="performAttack()">⚔ Angreifen</button>
        </div>
      </div>
    </div>

    <!-- Effect Dialog -->
    <div class="attack-dialog" *ngIf="effectDialog.open">
      <div class="dialog-backdrop" (click)="effectDialog.open = false"></div>
      <div class="dialog-box">
        <h3>Effekt für {{ effectDialog.target?.character?.name }}</h3>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Name</mat-label>
          <input matInput [(ngModel)]="effectDialog.name" placeholder="z.B. Behinderung, Unsichtbarkeit">
        </mat-form-field>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Beschreibung</mat-label>
          <input matInput [(ngModel)]="effectDialog.description">
        </mat-form-field>
        <div style="display:flex;gap:8px">
          <mat-form-field appearance="fill" style="flex:1">
            <mat-label>Dauer (Runden, -1 = permanent)</mat-label>
            <input matInput type="number" [(ngModel)]="effectDialog.rounds">
          </mat-form-field>
          <label style="display:flex;align-items:center;gap:4px">
            <input type="checkbox" [(ngModel)]="effectDialog.negative"> Negativ
          </label>
        </div>
        <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:8px">
          <button mat-stroked-button (click)="effectDialog.open = false">Abbrechen</button>
          <button mat-raised-button color="primary" (click)="addEffect()">Hinzufügen</button>
        </div>
      </div>
    </div>

    <!-- Free Action Dialog -->
    <div class="attack-dialog" *ngIf="freeActionDialog.open">
      <div class="dialog-backdrop" (click)="freeActionDialog.open = false"></div>
      <div class="dialog-box">
        <h3><mat-icon style="vertical-align:middle;margin-right:6px;color:#c9a84c">bolt</mat-icon>Freie Aktion: {{ freeActionDialog.actor?.character?.name }}</h3>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Talent</mat-label>
          <mat-select [(ngModel)]="freeActionDialog.talentId" (ngModelChange)="onFreeActionTalentChange()">
            <mat-option *ngFor="let t of freeActionTalentsOf(freeActionDialog.actor)" [value]="t.talentDefinition.id">
              {{ t.talentDefinition.name }} (Rang {{ t.rank }})
            </mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="fill" style="width:100%" *ngIf="freeActionNeedsTarget()">
          <mat-label>Ziel</mat-label>
          <mat-select [(ngModel)]="freeActionDialog.targetId">
            <mat-option *ngFor="let c of freeActionTargets()" [value]="c.id">
              {{ c.character.name }}
            </mat-option>
          </mat-select>
        </mat-form-field>
        <div class="fa-cost-badge" *ngIf="freeActionDamageCost() > 0">
          <mat-icon>warning</mat-icon> Kostet {{ freeActionDamageCost() }} Schaden
        </div>
        <div style="display:flex;gap:8px;align-items:center;margin-top:8px">
          <label class="karma-toggle"
            [class.active]="freeActionDialog.spendKarma"
            [class.disabled]="(freeActionDialog.actor?.currentKarma ?? 0) <= 0"
            (click)="(freeActionDialog.actor?.currentKarma ?? 0) > 0 && (freeActionDialog.spendKarma = !freeActionDialog.spendKarma)">
            <mat-icon>auto_awesome</mat-icon>
            Karma
            <span class="karma-count-badge" [class.empty]="(freeActionDialog.actor?.currentKarma ?? 0) <= 0">
              {{ freeActionDialog.actor?.currentKarma ?? 0 }}
            </span>
          </label>
        </div>
        <div style="display:flex;justify-content:flex-end;gap:8px;margin-top:8px">
          <button mat-stroked-button (click)="freeActionDialog.open = false">Abbrechen</button>
          <button mat-raised-button color="warn" (click)="performFreeAction()" [disabled]="!freeActionDialog.talentId">
            <mat-icon>bolt</mat-icon> Ausführen
          </button>
        </div>
      </div>
    </div>

    <!-- Free Action Result Modal -->
    <div class="result-modal" *ngIf="freeActionModal.open">
      <div class="dialog-backdrop" (click)="freeActionModal.open = false"></div>
      <div class="dialog-box result-box" *ngIf="freeActionModal.result as r">
        <div class="result-outcome" [class.hit]="r.success" [class.miss]="!r.success">
          <mat-icon>{{ r.success ? 'auto_fix_high' : 'close' }}</mat-icon>
          {{ r.success ? 'ERFOLG' : 'FEHLSCHLAG' }}
        </div>
        <div class="result-names">
          <span class="result-actor">{{ r.actorName }}</span>
          <span style="color:#c9a84c;font-weight:600;margin:0 6px">{{ r.talentName }}</span>
          <ng-container *ngIf="r.targetName">
            <mat-icon style="color:#555;font-size:18px">arrow_forward</mat-icon>
            <span class="result-target">{{ r.targetName }}</span>
          </ng-container>
        </div>
        <div class="result-rolls">
          <div class="roll-block">
            <div class="roll-block-header">
              <span class="roll-block-label">Probe · Step {{ r.rollStep }}</span>
              <div class="roll-block-totals">
                <span class="roll-big-total">{{ r.roll.total + (r.karmaRoll?.total ?? 0) }}</span>
                <ng-container *ngIf="r.defenseValue > 0">
                  <span class="roll-big-vs">vs</span>
                  <span class="roll-big-target">VK {{ r.defenseValue }}</span>
                </ng-container>
              </div>
            </div>
            <div class="dice-breakdown-mini">
              <div class="die-mini" *ngFor="let d of r.roll.dice" [class.exploded]="d.exploded">
                <span class="die-mini-sides">W{{ d.sides }}</span>
                <span class="die-mini-rolls">{{ d.rolls.join(' + ') }}<span *ngIf="d.rolls.length > 1" class="die-mini-sum"> = {{ d.total }}</span></span>
                <span *ngIf="d.exploded" class="explode-mini">💥</span>
              </div>
              <div class="die-mini karma-die" *ngIf="r.karmaRoll">
                <span class="die-mini-sides" style="color:#c9a84c">★ W6</span>
                <span class="die-mini-rolls">{{ r.karmaRoll.dice[0].rolls.join(' + ') }}<span *ngIf="r.karmaRoll.exploded"> 💥</span></span>
              </div>
            </div>
          </div>
          <ng-container *ngIf="r.success">
            <div class="roll-divider"></div>
            <div class="roll-row extra-success-row" *ngIf="r.extraSuccesses > 0">
              <span class="roll-label">Übererfolge</span>
              <span class="roll-expr">{{ r.extraSuccesses }}×</span>
              <span class="roll-value extra-success">{{ r.extraSuccesses }}</span>
            </div>
            <div class="wound-banner" *ngIf="r.effectApplied">
              <mat-icon>auto_fix_high</mat-icon> Effekt angewandt!
            </div>
          </ng-container>
          <div class="roll-row" *ngIf="r.damageTaken > 0" style="background:rgba(239,83,80,0.08);margin-top:8px">
            <span class="roll-label">Kosten</span>
            <span class="roll-expr">Schaden für Anwender</span>
            <span class="roll-value" style="color:#ef5350">−{{ r.damageTaken }}</span>
          </div>
        </div>
        <button mat-raised-button style="width:100%;margin-top:16px" (click)="freeActionModal.open = false">
          Schließen
        </button>
      </div>
    </div>

    <!-- Aufspringen Dialog -->
    <div class="attack-dialog" *ngIf="aufspringenDialog.open">
      <div class="dialog-backdrop" (click)="aufspringenDialog.open = false"></div>
      <div class="dialog-box">
        <h3><mat-icon style="vertical-align:middle;margin-right:6px;color:#42a5f5">directions_run</mat-icon>Aufspringen</h3>
        <div style="color:#888;font-size:0.85rem;margin-bottom:12px">
          GE-Probe vs 6 · Kostet 2 Schaden · Kann danach noch angreifen
        </div>
        <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:8px">
          <button mat-stroked-button (click)="aufspringenDialog.open = false">Abbrechen</button>
          <button mat-raised-button color="primary" (click)="performAufspringen()">
            <mat-icon>directions_run</mat-icon> Würfeln
          </button>
        </div>
      </div>
    </div>

    <!-- Stand Up Result Modal -->
    <div class="result-modal" *ngIf="standUpModal.open">
      <div class="dialog-backdrop" (click)="standUpModal.open = false"></div>
      <div class="dialog-box result-box" *ngIf="standUpModal.result as r">
        <div class="result-outcome" [class.hit]="!r.stillKnockedDown" [class.miss]="r.stillKnockedDown">
          <mat-icon>{{ r.stillKnockedDown ? 'close' : 'accessibility_new' }}</mat-icon>
          {{ r.stillKnockedDown ? 'FEHLGESCHLAGEN' : (r.simpleStandUp ? 'AUFGESTANDEN' : 'AUFGESPRUNGEN') }}
        </div>
        <div class="result-names">
          <span class="result-actor">{{ r.actorName }}</span>
        </div>
        <div class="result-rolls" *ngIf="!r.simpleStandUp && r.roll">
          <div class="roll-block">
            <div class="roll-block-header">
              <span class="roll-block-label">GE-Probe · Step {{ r.rollStep }}</span>
              <div class="roll-block-totals">
                <span class="roll-big-total">{{ (r.roll?.total ?? 0) + (r.karmaRoll?.total ?? 0) }}</span>
                <span class="roll-big-vs">vs</span>
                <span class="roll-big-target">{{ r.targetNumber }}</span>
              </div>
            </div>
            <div class="dice-breakdown-mini">
              <div class="die-mini" *ngFor="let d of r.roll!.dice" [class.exploded]="d.exploded">
                <span class="die-mini-sides">W{{ d.sides }}</span>
                <span class="die-mini-rolls">{{ d.rolls.join(' + ') }}<span *ngIf="d.rolls.length > 1" class="die-mini-sum"> = {{ d.total }}</span></span>
                <span *ngIf="d.exploded" class="explode-mini">💥</span>
              </div>
              <div class="die-mini karma-die" *ngIf="r.karmaRoll">
                <span class="die-mini-sides" style="color:#c9a84c">★ W6</span>
                <span class="die-mini-rolls">{{ r.karmaRoll.dice[0].rolls.join(' + ') }}<span *ngIf="r.karmaRoll.exploded"> 💥</span></span>
              </div>
            </div>
          </div>
          <div class="roll-row" *ngIf="r.damageTaken" style="background:rgba(239,83,80,0.08)">
            <span class="roll-label">Kosten</span>
            <span class="roll-expr">Schaden</span>
            <span class="roll-value" style="color:#ef5350">−{{ r.damageTaken }}</span>
          </div>
        </div>
        <button mat-raised-button style="width:100%;margin-top:16px" (click)="standUpModal.open = false">
          Schließen
        </button>
      </div>
    </div>
  `,
  styles: [`
    .loading-state { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 60vh; gap: 12px; }
    .tracker-container { padding: 16px; height: 100vh; display: flex; flex-direction: column; box-sizing: border-box; }
    .tracker-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
    .header-left { display: flex; align-items: center; gap: 12px; }
    .session-name { font-family: 'Cinzel', serif; font-size: 1.2rem; color: #c9a84c; }
    .round-badge { background: #3a3028; padding: 2px 8px; border-radius: 10px; font-size: 12px; margin-left: 8px; color: #c9a84c; }
    .status-badge {
      margin-left: 6px; padding: 2px 8px; border-radius: 10px; font-size: 11px; font-weight: 600;
      &.setup   { background: rgba(158,158,158,0.2); color: #9e9e9e; }
      &.active  { background: rgba(76,175,80,0.2); color: #4caf50; }
      &.finished{ background: rgba(244,67,54,0.2); color: #f44336; }
    }
    .header-actions { display: flex; gap: 8px; }
    .add-panel { padding: 12px; background: #2a2520; border-radius: 6px; margin-bottom: 12px; display: flex; gap: 12px; align-items: center; }
    .add-panel-group-toggle { display: flex; gap: 4px; }
    .group-toggle-btn {
      display: flex; align-items: center; gap: 4px; padding: 6px 12px; border-radius: 16px;
      border: 1px solid #555; color: #888; background: none; cursor: pointer; font-family: inherit; font-size: 0.82rem;
      mat-icon { font-size: 16px; height: 16px; width: 16px; }
      &.active { border-color: #4caf50; color: #4caf50; background: rgba(76,175,80,0.1); }
      &.enemy.active { border-color: #ef5350; color: #ef5350; background: rgba(239,83,80,0.1); }
    }

    .tracker-body { display: grid; grid-template-columns: 1fr 380px; gap: 16px; flex: 1; overflow: hidden; }

    .combatants-panel { overflow-y: auto; }
    .combatants-columns { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    .combatant-group { display: flex; flex-direction: column; gap: 8px; }
    .group-header {
      display: flex; align-items: center; gap: 6px;
      font-family: 'Cinzel', serif; font-size: 0.85rem; font-weight: 700;
      padding: 4px 8px; border-radius: 6px; margin-bottom: 2px;
      mat-icon { font-size: 16px; height: 16px; width: 16px; }
    }
    .heroes-header { color: #4caf50; background: rgba(76,175,80,0.08); border: 1px solid rgba(76,175,80,0.2); }
    .enemies-header { color: #ef5350; background: rgba(239,83,80,0.08); border: 1px solid rgba(239,83,80,0.2); }

    .comb-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
    .comb-title { display: flex; align-items: center; gap: 8px; }
    .initiative-badge {
      background: #3a3028; color: #c9a84c; width: 32px; height: 32px;
      border-radius: 50%; display: flex; align-items: center; justify-content: center;
      font-weight: bold; font-size: 0.85rem; flex-shrink: 0;
    }
    .comb-actions { display: flex; align-items: center; gap: 4px; }
    .attack-btn {
      height: 36px; padding: 0 12px; font-size: 13px; font-weight: 600;
      border-color: #c9a84c; color: #c9a84c;
      mat-icon { font-size: 18px; height: 18px; width: 18px; margin-right: 4px; }
      &:not([disabled]):hover { background: rgba(201,168,76,0.1); }
      &[disabled] { opacity: 0.35; border-color: #3a3028; color: #555; }
    }
    .combat-option-btn {
      height: 32px; min-width: 0; padding: 0 8px; font-size: 12px;
      border-color: #3a3028; color: #888; display: flex; align-items: center; gap: 4px;
      mat-icon { font-size: 16px; height: 16px; width: 16px; }
      &.aggressive.active { border-color: #ff7043; color: #ff7043; background: rgba(255,112,67,0.1); }
      &.defensive.active { border-color: #42a5f5; color: #42a5f5; background: rgba(66,165,245,0.1); }
      &.free-action { border-color: #3a3028; color: #c9a84c; }
      &.free-action:not([disabled]):hover { border-color: #c9a84c; background: rgba(201,168,76,0.1); }
      &.active { border-color: #ff6d00; color: #ff6d00; background: rgba(255,109,0,0.1); }
      &:hover { border-color: #ff6d00; color: #ff6d00; }
    }
    .fa-cost-badge {
      display: flex; align-items: center; gap: 6px;
      background: rgba(239,83,80,0.1); border: 1px solid #ef5350;
      color: #ef5350; border-radius: 6px; padding: 4px 10px;
      font-size: 0.82rem; font-weight: 600; margin-bottom: 4px;
      mat-icon { font-size: 16px; height: 16px; width: 16px; }
    }
    .bonus-badge {
      font-size: 11px; font-weight: bold; color: #ff6d00;
      &.defense { color: #42a5f5; }
    }
    .acted-badge {
      font-size: 10px; font-weight: 700; color: #888; background: rgba(255,255,255,0.05);
      border: 1px solid #444; border-radius: 8px; padding: 2px 8px;
      text-transform: uppercase; letter-spacing: 0.05em;
    }
    .combatant-card.has-acted { opacity: 0.55; }
    .combat-option-btn.use-action:not([disabled]) { color: #ab47bc; border-color: #6a1b9a; }
    .combat-option-btn.use-action:not([disabled]):hover { border-color: #ab47bc; background: rgba(171,71,188,0.1); }

    .comb-damage-row { display: flex; align-items: center; gap: 4px; margin-bottom: 6px; }
    .dmg-label { font-size: 10px; color: #666; min-width: 50px; }
    .dmg-val { font-size: 15px; font-weight: 600; color: #e0d5c0; min-width: 44px; text-align: right; }
    .mini-ctrl { display: flex; align-items: center; }
    .stat-value { font-size: 1.1rem; font-weight: 700; min-width: 28px; text-align: center;
      &.wounds { color: #ef5350; }
      &.karma { color: #c9a84c; }
    }

    .comb-status-row { display: flex; gap: 16px; margin-bottom: 6px; }
    .comb-stat { display: flex; align-items: center; gap: 4px; flex-wrap: wrap; }
    .comb-stat-label { font-size: 11px; color: #888; min-width: 45px; }

    .comb-defense-row {
      display: flex; gap: 12px; padding: 4px 0 2px; border-top: 1px solid #2a2520;
    }
    .def-stat {
      display: flex; align-items: center; gap: 3px;
      font-size: 0.82rem; font-weight: 600; color: #7cb8e0;
      mat-icon { font-size: 13px; height: 13px; width: 13px; }
      &.mystic { color: #b39ddb; }
      &.social { color: #80cbc4; }
      &.armor-phys { color: #a5d6a7; }
      &.armor-myst { color: #ce93d8; }
    }
    .effects-row { display: flex; flex-wrap: wrap; gap: 2px; }

    .log-panel { display: flex; flex-direction: column; overflow: hidden; }
    .log-scroll { flex: 1; overflow-y: auto; background: #1a1a1a; border: 1px solid #333; border-radius: 4px; padding: 4px; }

    .result-modal {
      position: fixed; inset: 0; z-index: 1000;
      display: flex; align-items: center; justify-content: center;
      background: rgba(0,0,0,0.6);
    }
    .result-modal .dialog-box.result-box {
      min-width: 380px; max-width: 460px;
    }
    .result-outcome {
      text-align: center; font-size: 2rem; font-weight: 900;
      letter-spacing: 0.15em; padding: 12px 0 8px;
      display: flex; align-items: center; justify-content: center; gap: 10px;
      mat-icon { font-size: 2rem; height: 2rem; width: 2rem; }
      &.hit { color: #ef5350; }
      &.miss { color: #555; }
    }
    .result-names {
      display: flex; align-items: center; justify-content: center; gap: 8px;
      margin-bottom: 16px; font-size: 0.9rem;
    }
    .result-actor { color: #c9a84c; font-weight: 600; }
    .result-target { color: #90caf9; font-weight: 600; }
    .result-rolls { display: flex; flex-direction: column; gap: 6px; }
    .roll-row {
      display: grid; grid-template-columns: 70px 1fr auto auto;
      align-items: center; gap: 8px;
      background: #1e1a16; border-radius: 5px; padding: 6px 10px; font-size: 0.85rem;
    }
    .roll-label { color: #888; font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.05em; }
    .roll-expr { color: #666; font-size: 0.8rem; }
    .roll-value { font-size: 1.4rem; font-weight: bold; color: #c9a84c; text-align: right; min-width: 40px; }
    .roll-value.damage { color: #ef5350; }
    .roll-value.net { color: #ff7043; font-size: 1.6rem; }
    .roll-value.karma { color: #7e57c2; }
    .karma-row { background: rgba(126,87,194,0.08); border: 1px solid #2a1f3a; }
    .effect-bonus-row { background: rgba(201,168,76,0.08); border: 1px solid #3a2e18; }
    .roll-value.effect-bonus { color: #c9a84c; font-size: 1rem; }
    .roll-value.extra-success { color: #ffa726; }
    .extra-success-row { background: rgba(255,167,38,0.08); border: 1px solid #3a2e18; }

    .roll-block { background: #1e1a16; border-radius: 6px; padding: 10px 12px; display: flex; flex-direction: column; gap: 8px; }
    .roll-block-header { display: flex; justify-content: space-between; align-items: center; }
    .roll-block-label { color: #666; font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.05em; }
    .step-calc { color: #888; font-size: 0.7rem; text-transform: none; letter-spacing: 0; margin-left: 4px; }
    .karma-toggle { display: flex; align-items: center; gap: 6px; padding: 6px 14px; border-radius: 20px; cursor: pointer; border: 1px solid #555; color: #888; transition: all 0.2s; user-select: none; }
    .karma-toggle.active { border-color: #c9a84c; color: #c9a84c; background: rgba(201,168,76,0.1); }
    .karma-toggle.disabled { opacity: 0.4; cursor: not-allowed; }
    .karma-toggle mat-icon { font-size: 18px; height: 18px; width: 18px; }
    .karma-count-badge { background: rgba(201,168,76,0.2); border-radius: 10px; padding: 0 6px; font-size: 0.8rem; font-weight: bold; color: #c9a84c; }
    .karma-count-badge.empty { background: rgba(244,67,54,0.2); color: #f44336; }
    .roll-block-totals { display: flex; align-items: baseline; gap: 8px; }
    .roll-big-total { font-size: 2.2rem; font-weight: 900; color: #c9a84c; line-height: 1; }
    .roll-big-vs { font-size: 0.8rem; color: #555; padding: 0 2px; }
    .roll-big-target { font-size: 2.2rem; font-weight: 900; color: #e0d5c0; line-height: 1; }
    .dice-breakdown-mini { display: flex; flex-wrap: wrap; gap: 5px; }
    .die-mini { display: flex; align-items: center; gap: 4px; background: #252118; border-radius: 4px; padding: 3px 8px; font-size: 0.78rem; border: 1px solid #2e2920; }
    .die-mini.exploded { border-color: #ff9800; }
    .die-mini-sides { color: #888; font-weight: 600; margin-right: 2px; }
    .die-mini-rolls { color: #c9a84c; }
    .die-mini.karma-die .die-mini-rolls { color: #ab47bc; }
    .die-mini-sum { color: #777; }
    .roll-effect-notes { display: flex; flex-wrap: wrap; gap: 4px; }
    .effect-note { font-size: 0.75rem; color: #c9a84c; background: rgba(201,168,76,0.08); border-radius: 10px; padding: 1px 8px; }

    .aggressive-active-badge {
      display: flex; align-items: center; gap: 6px;
      background: rgba(255,112,67,0.12); border: 1px solid #ff7043;
      color: #ff7043; border-radius: 6px; padding: 4px 10px;
      font-size: 0.82rem; font-weight: 600; margin-bottom: 8px;
      mat-icon { font-size: 16px; height: 16px; width: 16px; }
    }
    .dialog-combat-options { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
    .combat-option-toggle {
      display: flex; align-items: center; gap: 6px;
      padding: 6px 14px; border-radius: 20px; cursor: pointer;
      border: 1px solid #555; color: #888; transition: all 0.2s;
      background: none; font-family: inherit; font-size: inherit;
      user-select: none;
      mat-icon { font-size: 18px; height: 18px; width: 18px; }
      &.aggressive:hover { border-color: #ff7043; color: #ff7043; }
      &.aggressive.active { border-color: #ff7043; color: #ff7043; background: rgba(255,112,67,0.12); }
      &.defensive:hover { border-color: #42a5f5; color: #42a5f5; }
      &.defensive.active { border-color: #42a5f5; color: #42a5f5; background: rgba(66,165,245,0.12); }
    }
    .aggressive-info { display: flex; gap: 8px; flex-wrap: wrap; }
    .info-bonus { background: rgba(76,175,80,0.15); color: #4caf50; border-radius: 10px; padding: 2px 8px; font-size: 0.78rem; font-weight: 600; }
    .info-penalty { background: rgba(244,67,54,0.15); color: #f44336; border-radius: 10px; padding: 2px 8px; font-size: 0.78rem; font-weight: 600; }
    .info-cost { background: rgba(255,152,0,0.15); color: #ff9800; border-radius: 10px; padding: 2px 8px; font-size: 0.78rem; font-weight: 600; }
    .roll-vs { font-size: 0.75rem; color: #555; white-space: nowrap; }
    .roll-divider { height: 1px; background: #2a2520; margin: 2px 0; }
    .armor-row { opacity: 0.7; }
    .net-row { background: rgba(255,112,67,0.08); border: 1px solid #3a2820; }
    .wound-banner {
      display: flex; align-items: center; gap: 6px; justify-content: center;
      background: rgba(244,67,54,0.15); border: 1px solid #c62828;
      border-radius: 6px; padding: 8px; color: #ef5350;
      font-weight: 700; font-size: 1rem; letter-spacing: 0.05em;
      mat-icon { color: #f44336; }
    }
    .dodge-prompt {
      display: flex; align-items: center; gap: 6px;
      background: rgba(66,165,245,0.1); border: 1px solid #42a5f5;
      color: #42a5f5; border-radius: 6px; padding: 8px 12px;
      font-size: 0.85rem; font-weight: 600; margin-top: 12px;
      mat-icon { font-size: 18px; height: 18px; width: 18px; }
    }
    .defeat-banner {
      display: flex; align-items: center; gap: 6px; justify-content: center;
      background: rgba(0,0,0,0.4); border: 1px solid #555;
      border-radius: 6px; padding: 8px; color: #888;
      font-weight: 700; font-size: 0.9rem;
    }
    .knockdown-banner {
      display: flex; align-items: center; gap: 6px; justify-content: center;
      border-radius: 6px; padding: 8px; font-weight: 700; font-size: 0.9rem;
      mat-icon { font-size: 18px; height: 18px; width: 18px; }
      &.knocked { background: rgba(255,152,0,0.15); border: 1px solid #ff9800; color: #ff9800; }
      &.stood { background: rgba(76,175,80,0.1); border: 1px solid #4caf50; color: #4caf50; }
    }
    .knocked-badge {
      font-size: 10px; font-weight: 700; color: #ff9800; background: rgba(255,152,0,0.15);
      border: 1px solid #ff9800; border-radius: 8px; padding: 2px 7px;
      text-transform: uppercase; letter-spacing: 0.05em;
    }
    .combat-option-btn.standup-btn { color: #ff9800; border-color: #5a3800; }
    .combat-option-btn.standup-btn:not([disabled]):hover { border-color: #ff9800; background: rgba(255,152,0,0.1); }
    .combat-option-btn.aufspringen-btn { color: #42a5f5; border-color: #1a3050; }
    .combat-option-btn.aufspringen-btn:hover { border-color: #42a5f5; background: rgba(66,165,245,0.1); }

    /* Inline dialogs */
    .attack-dialog, .effect-dialog {
      position: fixed; inset: 0; z-index: 1000; display: flex; align-items: center; justify-content: center;
    }
    .dialog-backdrop { position: absolute; inset: 0; background: rgba(0,0,0,0.7); }
    .dialog-box {
      position: relative; background: #2a2520; border: 1px solid #c9a84c;
      border-radius: 8px; padding: 20px; min-width: 340px; z-index: 1;
      h3 { font-family: 'Cinzel',serif; color: #c9a84c; margin: 0 0 16px; }
    }
  `]
})
export class CombatTrackerComponent implements OnInit, OnDestroy {
  session?: CombatSession;
  loadError?: string;
  allCharacters: Character[] = [];
  logEntries: any[] = [];
  lastResult?: CombatActionResult;
  resultModal: { open: boolean; result?: CombatActionResult } = { open: false };
  selectedCharId?: number;
  addCharacterPanel = false;
  isNpcAdd = false;
  private wsSub?: Subscription;

  attackDialog: {
    open: boolean;
    attacker?: CombatantState;
    defenderId?: number;
    talentId?: number;
    weaponId?: number;
    bonusSteps: number;
    spendKarma: boolean;
    aggressiveAttack: boolean;
    defensiveStance: boolean;
  } = { open: false, bonusSteps: 0, spendKarma: false, aggressiveAttack: false, defensiveStance: false };

  /** Letztes Angriffsziel pro Kombattant (combatantId → defenderId) */
  private lastTargetMap = new Map<number, number>();

  effectDialog: {
    open: boolean;
    target?: CombatantState;
    name: string;
    description: string;
    rounds: number;
    negative: boolean;
  } = { open: false, name: '', description: '', rounds: -1, negative: false };

  freeActionDialog: {
    open: boolean;
    actor?: CombatantState;
    talentId?: number;
    targetId?: number;
    bonusSteps: number;
    spendKarma: boolean;
  } = { open: false, bonusSteps: 0, spendKarma: false };

  freeActionModal: { open: boolean; result?: FreeActionResult } = { open: false };

  dodgeDialog: {
    open: boolean;
    defenderId?: number;
    attackTotal: number;
    bonusSteps: number;
    spendKarma: boolean;
  } = { open: false, attackTotal: 0, bonusSteps: 0, spendKarma: false };

  dodgeModal: { open: boolean; result?: DodgeResult } = { open: false };

  aufspringenDialog: {
    open: boolean;
    combatant?: CombatantState;
  } = { open: false };

  standUpModal: { open: boolean; result?: StandUpResult } = { open: false };

  constructor(
    private route: ActivatedRoute,
    public router: Router,
    private combatService: CombatService,
    private characterService: CharacterService,
    private wsService: WebSocketService,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    const id = +this.route.snapshot.params['id'];
    this.combatService.findById(id).subscribe({
      next: s => {
        this.session = s;
        this.logEntries = s.log ?? [];
      },
      error: err => {
        this.loadError = `Session konnte nicht geladen werden (${err.status ?? err.message}).`;
      }
    });
    this.characterService.findAll().subscribe(c => this.allCharacters = c);

    this.wsSub = this.wsService.subscribeToSession(id).subscribe(s => {
      this.session = s;
      this.logEntries = s.log ?? [];
    });
  }

  ngOnDestroy(): void {
    this.wsSub?.unsubscribe();
  }

  rollInitiative(): void {
    if (!this.session) return;
    this.combatService.rollInitiative(this.session.id).subscribe(s => {
      this.session = s;
      this.snack.open('Initiative gewürfelt!', 'OK', { duration: 1500 });
    });
  }

  nextRound(): void {
    if (!this.session) return;
    this.combatService.nextRound(this.session.id).subscribe(s => this.session = s);
  }

  endCombat(): void {
    if (!this.session) return;
    this.combatService.endCombat(this.session.id).subscribe(s => this.session = s);
  }

  deleteSession(): void {
    if (!this.session || !confirm(`Session "${this.session.name}" wirklich löschen?`)) return;
    this.combatService.delete(this.session.id).subscribe(() => this.router.navigate(['/combat']));
  }

  addCombatant(): void {
    if (!this.session || !this.selectedCharId) return;
    this.combatService.addCombatant(this.session.id, this.selectedCharId, this.isNpcAdd).subscribe(s => {
      this.session = s;
      this.selectedCharId = undefined;
    });
  }

  heroes(): CombatantState[] {
    return (this.session?.combatants ?? []).filter(c => !c.npc);
  }

  enemies(): CombatantState[] {
    return (this.session?.combatants ?? []).filter(c => c.npc);
  }

  isActiveTurn(c: CombatantState): boolean {
    const first = (this.session?.combatants ?? []).find(x => !x.defeated && !x.hasActedThisRound);
    return first?.id === c.id;
  }

  dodgeCombatant(): CombatantState | undefined {
    return (this.session?.combatants ?? []).find(c => c.id === this.dodgeDialog.defenderId);
  }

  dodgeStep(): number {
    const c = this.dodgeCombatant();
    if (!c) return 0;
    const dex = c.character.dexterity;
    const attrStep = dex <= 3 ? 2 : dex <= 6 ? 3 : dex <= 9 ? 4 : dex <= 12 ? 5 :
                     dex <= 15 ? 6 : dex <= 18 ? 7 : dex <= 21 ? 8 : dex <= 24 ? 9 :
                     dex <= 27 ? 10 : dex <= 30 ? 11 : Math.floor((dex - 1) / 3);
    const dodgeTalent = c.character.talents.find(t => t.talentDefinition.name === 'Ausweichen');
    return attrStep + (dodgeTalent?.rank ?? 0);
  }

  removeCombatant(combatantId: number): void {
    if (!this.session) return;
    this.combatService.removeCombatant(this.session.id, combatantId).subscribe(s => this.session = s);
  }

  updateValue(c: CombatantState, field: string, delta: number): void {
    if (!this.session) return;
    this.combatService.updateValue(this.session.id, c.id, field, delta).subscribe(s => this.session = s);
  }

  useAction(c: CombatantState): void {
    if (!this.session) return;
    this.combatService.declareCombatOption(this.session.id, c.id, 'USE_ACTION')
      .subscribe(s => this.session = s);
  }

  aggressiveAttack(c: CombatantState): void {
    if (!this.session) return;
    this.combatService.declareCombatOption(this.session.id, c.id, 'AGGRESSIVE_ATTACK')
      .subscribe(s => this.session = s);
  }

  defensiveStance(c: CombatantState): void {
    if (!this.session) return;
    this.combatService.declareCombatOption(this.session.id, c.id, 'DEFENSIVE_STANCE')
      .subscribe(s => this.session = s);
  }

  openAttackDialog(attacker: CombatantState): void {
    // Höchstes Angriffstalent als Default
    const bestAttackTalent = (attacker.character.talents ?? [])
      .filter(t => t.talentDefinition.attackTalent)
      .sort((a, b) => b.rank - a.rank)[0];

    // Letztes Ziel als Default (falls noch vorhanden und nicht besiegt)
    const lastDefenderId = this.lastTargetMap.get(attacker.id!);
    const lastDefender = lastDefenderId != null
      ? this.session?.combatants.find(c => c.id === lastDefenderId && !c.defeated)
      : undefined;

    // Waffe mit höchstem Schadensbonus als Default
    const bestWeapon = (attacker.character.equipment ?? [])
      .filter(e => e.type === 'WEAPON')
      .sort((a, b) => b.damageBonus - a.damageBonus)[0];

    this.attackDialog = {
      open: true,
      attacker,
      defenderId: lastDefender?.id,
      talentId: bestAttackTalent?.talentDefinition.id,
      weaponId: bestWeapon?.id,
      bonusSteps: 0,
      spendKarma: false,
      aggressiveAttack: false,
      defensiveStance: false
    };
  }

  toggleDialogAggressive(): void {
    this.attackDialog.aggressiveAttack = !this.attackDialog.aggressiveAttack;
    if (this.attackDialog.aggressiveAttack) this.attackDialog.defensiveStance = false;
  }

  toggleDialogDefensive(): void {
    this.attackDialog.defensiveStance = !this.attackDialog.defensiveStance;
    if (this.attackDialog.defensiveStance) this.attackDialog.aggressiveAttack = false;
  }

  possibleTargets(): CombatantState[] {
    return (this.session?.combatants ?? []).filter(c => c.id !== this.attackDialog.attacker?.id && !c.defeated);
  }

  freeActionTargets(): CombatantState[] {
    return (this.session?.combatants ?? []).filter(c => c.id !== this.freeActionDialog.actor?.id && !c.defeated);
  }

  attackTalentsOf(c?: CombatantState) {
    return (c?.character.talents ?? []).filter(t => t.talentDefinition.attackTalent).sort((a, b) => b.rank - a.rank);
  }

  nonAttackTalentsOf(c?: CombatantState) {
    return (c?.character.talents ?? []).filter(t => !t.talentDefinition.attackTalent);
  }

  weaponsOf(c?: CombatantState) {
    return (c?.character.equipment ?? []).filter(e => e.type === 'WEAPON').sort((a, b) => b.damageBonus - a.damageBonus);
  }

  private resolveActionType(): AttackActionRequest['actionType'] {
    const talent = this.attackDialog.attacker?.character.talents
      .find(t => t.talentDefinition.id === this.attackDialog.talentId)
      ?.talentDefinition.name ?? '';
    if (talent === 'Projektilwaffen' || talent === 'Wurfwaffen') return 'RANGED_ATTACK';
    if (talent === 'Spruchzauberei') return 'SPELL_ATTACK';
    return 'MELEE_ATTACK';
  }

  performAttack(): void {
    if (!this.session || !this.attackDialog.attacker || !this.attackDialog.defenderId) return;
    const req: AttackActionRequest = {
      sessionId: this.session.id,
      attackerCombatantId: this.attackDialog.attacker.id,
      defenderCombatantId: this.attackDialog.defenderId,
      actionType: this.resolveActionType(),
      talentId: this.attackDialog.talentId ?? undefined,
      weaponId: this.attackDialog.weaponId ?? undefined,
      bonusSteps: this.attackDialog.bonusSteps,
      spendKarma: this.attackDialog.spendKarma,
      aggressiveAttack: this.attackDialog.aggressiveAttack,
      defensiveStance: this.attackDialog.defensiveStance
    };
    this.combatService.performAttack(req).subscribe(result => {
      this.lastResult = result;
      this.lastTargetMap.set(req.attackerCombatantId, req.defenderCombatantId);
      this.attackDialog.open = false;
      this.resultModal = { open: true, result };
      this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
    });
  }

  openEffectDialog(target: CombatantState): void {
    this.effectDialog = { open: true, target, name: '', description: '', rounds: -1, negative: false };
  }

  addEffect(): void {
    if (!this.session || !this.effectDialog.target || !this.effectDialog.name) return;
    const effect: ActiveEffect = {
      name: this.effectDialog.name,
      description: this.effectDialog.description,
      modifiers: [],
      remainingRounds: this.effectDialog.rounds,
      negative: this.effectDialog.negative
    };
    this.combatService.addEffect(this.session.id, this.effectDialog.target.id, effect).subscribe(s => {
      this.session = s;
      this.effectDialog.open = false;
    });
  }

  removeEffect(c: CombatantState, e: ActiveEffect): void {
    if (!this.session || !e.id) return;
    this.combatService.removeEffect(this.session.id, c.id, e.id).subscribe(s => this.session = s);
  }

  ur(c: CombatantState): number {
    return c.character.unconsciousnessRating ?? c.character.toughness * 2;
  }

  pd(c: CombatantState): number {
    return c.character.physicalDefense ?? Math.floor((c.character.dexterity + 3) / 2);
  }

  sd(c: CombatantState): number {
    return c.character.spellDefense ?? Math.floor((c.character.perception + 3) / 2);
  }

  socD(c: CombatantState): number {
    return c.character.socialDefense ?? Math.floor((c.character.charisma + 3) / 2);
  }

  pa(c: CombatantState): number {
    const equipBonus = (c.character.equipment ?? []).filter(e => e.type === 'ARMOR').reduce((s, e) => s + (e.physicalArmor ?? 0), 0);
    return (c.character.physicalArmor ?? 0) + equipBonus;
  }

  ma(c: CombatantState): number {
    const equipBonus = (c.character.equipment ?? []).filter(e => e.type === 'ARMOR').reduce((s, e) => s + (e.mysticalArmor ?? 0), 0);
    return (c.character.mysticArmor ?? 0) + equipBonus;
  }

  damagePercent(c: CombatantState): number {
    return Math.min(100, (c.currentDamage / this.ur(c)) * 100);
  }

  woundDots(c: CombatantState): boolean[] {
    return Array.from({ length: 5 }, (_, i) => i < c.wounds);
  }

  karmaDots(c: CombatantState): boolean[] {
    const max = Math.min(c.character.karmaMax, 15);
    return Array.from({ length: max }, (_, i) => i < c.currentKarma);
  }

  statusLabel(): string {
    const labels: Record<string, string> = { SETUP: 'Vorbereitung', ACTIVE: 'Aktiv', FINISHED: 'Beendet' };
    return labels[this.session?.status ?? ''] ?? '';
  }

  isSystem(actionType: string): boolean {
    return ['INITIATIVE', 'ROUND_CHANGE', 'EFFECT_ADDED', 'EFFECT_REMOVED', 'VALUE_CHANGED'].includes(actionType);
  }

  openDodgeDialog(): void {
    const r = this.resultModal.result;
    if (!r?.hitPendingDodge || !r.dodgeDefenderId) return;
    this.resultModal.open = false;
    this.dodgeDialog = {
      open: true,
      defenderId: r.dodgeDefenderId,
      attackTotal: r.attackRoll.total + (r.karmaRoll?.total ?? 0),
      bonusSteps: 0,
      spendKarma: false
    };
  }

  skipDodge(): void {
    const r = this.resultModal.result;
    if (!r?.dodgeDefenderId || !this.session) return;
    this.resultModal.open = false;
    const req: DodgeRequest = {
      sessionId: this.session.id,
      defenderCombatantId: r.dodgeDefenderId,
      dodgeAttempted: false,
      bonusSteps: 0,
      spendKarma: false
    };
    this.combatService.resolveDodge(this.session.id, req).subscribe({
      next: result => {
        this.dodgeModal = { open: true, result };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => this.snack.open('Fehler: ' + (err?.error?.message ?? err.message), 'OK', { duration: 4000 })
    });
  }

  performDodge(): void {
    if (!this.session || !this.dodgeDialog.defenderId) return;
    const req: DodgeRequest = {
      sessionId: this.session.id,
      defenderCombatantId: this.dodgeDialog.defenderId,
      dodgeAttempted: true,
      bonusSteps: this.dodgeDialog.bonusSteps,
      spendKarma: this.dodgeDialog.spendKarma
    };
    this.combatService.resolveDodge(this.session.id, req).subscribe({
      next: result => {
        this.dodgeDialog.open = false;
        this.dodgeModal = { open: true, result };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => this.snack.open('Fehler: ' + (err?.error?.message ?? err.message), 'OK', { duration: 4000 })
    });
  }

  freeActionTalentsOf(c?: CombatantState) {
    return (c?.character.talents ?? []).filter(t => t.talentDefinition.freeAction);
  }

  openFreeActionDialog(actor: CombatantState): void {
    const firstTalent = this.freeActionTalentsOf(actor)[0];
    this.freeActionDialog = {
      open: true,
      actor,
      talentId: firstTalent?.talentDefinition.id,
      targetId: undefined,
      bonusSteps: 0,
      spendKarma: false
    };
  }

  onFreeActionTalentChange(): void {
    // Reset target when talent changes, since new talent may have different target type
    this.freeActionDialog.targetId = undefined;
  }

  private selectedFreeActionTalent() {
    if (!this.freeActionDialog.actor || !this.freeActionDialog.talentId) return undefined;
    return this.freeActionTalentsOf(this.freeActionDialog.actor)
      .find(t => t.talentDefinition.id === this.freeActionDialog.talentId)
      ?.talentDefinition;
  }

  freeActionNeedsTarget(): boolean {
    const td = this.selectedFreeActionTalent();
    return td != null && !!td.freeActionTestStat;
  }

  freeActionDamageCost(): number {
    return this.selectedFreeActionTalent()?.freeActionDamageCost ?? 0;
  }

  performFreeAction(): void {
    if (!this.session || !this.freeActionDialog.actor || !this.freeActionDialog.talentId) return;
    const req: FreeActionRequest = {
      sessionId: this.session.id,
      actorCombatantId: this.freeActionDialog.actor.id,
      targetCombatantId: this.freeActionDialog.targetId,
      talentId: this.freeActionDialog.talentId,
      bonusSteps: this.freeActionDialog.bonusSteps,
      spendKarma: this.freeActionDialog.spendKarma
    };
    this.combatService.performFreeAction(this.session.id, req).subscribe({
      next: result => {
        this.freeActionDialog.open = false;
        this.freeActionModal = { open: true, result };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => {
        const msg = err?.error?.message ?? err?.message ?? JSON.stringify(err);
        this.snack.open('Fehler: ' + msg, 'OK', { duration: 5000 });
      }
    });
  }

  performStandUp(c: CombatantState): void {
    if (!this.session) return;
    this.combatService.standUp(this.session.id, c.id).subscribe({
      next: result => {
        this.standUpModal = { open: true, result };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => this.snack.open('Fehler: ' + (err?.error?.message ?? err.message), 'OK', { duration: 4000 })
    });
  }

  openAufspringenDialog(c: CombatantState): void {
    this.aufspringenDialog = { open: true, combatant: c };
  }

  performAufspringen(): void {
    if (!this.session || !this.aufspringenDialog.combatant) return;
    this.combatService.aufspringen(this.session.id, this.aufspringenDialog.combatant.id, false).subscribe({
      next: result => {
        this.aufspringenDialog.open = false;
        this.standUpModal = { open: true, result };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => this.snack.open('Fehler: ' + (err?.error?.message ?? err.message), 'OK', { duration: 4000 })
    });
  }
}
