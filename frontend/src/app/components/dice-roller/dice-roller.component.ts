import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSliderModule } from '@angular/material/slider';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDividerModule } from '@angular/material/divider';
import { DiceService } from '../../services/dice.service';
import { CharacterService } from '../../services/character.service';
import { ActiveCharacterService } from '../../services/active-character.service';
import { RollResult, ProbeResult } from '../../models/dice.model';
import { Character } from '../../models/character.model';

@Component({
  selector: 'app-dice-roller',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatCardModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatSliderModule,
    MatSelectModule, MatTooltipModule, MatSnackBarModule, MatDividerModule
  ],
  template: `
    <div class="page-container">
      <h1>Würfelwurf</h1>
      <p style="color:#888">Earthdawn 4 (FASA) Würfelsystem — Exploding Dice</p>

      <div class="page-layout">

      <div class="roller-card">
        <!-- Aktiver Charakter -->
        <div class="char-section">
          <div class="section-title">Aktiver Charakter</div>
          <div class="char-row">
            <mat-form-field appearance="fill" style="flex:1">
              <mat-label>Charakter wählen</mat-label>
              <mat-select [(ngModel)]="activeCharId" (ngModelChange)="onCharSelected()">
                <mat-option [value]="null">— Kein Charakter —</mat-option>
                <mat-option *ngFor="let c of allCharacters" [value]="c.id">
                  {{ c.name }} ({{ c.discipline?.name || 'Kein' }})
                </mat-option>
              </mat-select>
            </mat-form-field>
            <div class="char-info" *ngIf="activeChar">
              <div class="char-name-badge">{{ activeChar.name }}</div>
              <div class="karma-status" [class.low]="activeChar.karmaCurrent <= 2">
                <mat-icon style="font-size:14px;height:14px;width:14px">auto_awesome</mat-icon>
                {{ activeChar.karmaCurrent }} / {{ activeChar.karmaMax }} Karma
              </div>
              <div class="discipline-info">
                Karma: W6 (fix)
              </div>
            </div>
          </div>
        </div>

        <!-- Step Input -->
        <div class="step-control">
          <div class="section-title">Step-Zahl</div>
          <div class="step-row">
            <button mat-icon-button (click)="step = step - 1" [disabled]="step <= 1"><mat-icon>remove</mat-icon></button>
            <div class="step-display">
              <span class="step-number">{{ step }}</span>
              <span class="dice-expr">{{ diceExpr }}</span>
            </div>
            <button mat-icon-button (click)="step = step + 1" [disabled]="step >= 40"><mat-icon>add</mat-icon></button>
            <input type="number" class="step-input" [(ngModel)]="step" min="1" max="30"
              (ngModelChange)="onStepChange()">
          </div>

          <!-- Quick Step Buttons -->

          <div class="quick-steps">
            <button mat-stroked-button *ngFor="let s of quickSteps" (click)="step = s; onStepChange()">
              Step {{ s }}
            </button>
          </div>
        </div>

        <!-- Karma -->
        <div class="karma-section">
          <div class="section-title">Karma</div>
          <div class="karma-row">
            <label class="karma-toggle"
              [class.active]="useKarma"
              [class.disabled]="activeChar && activeChar.karmaCurrent <= 0"
              (click)="toggleKarma()"
              [matTooltip]="activeChar && activeChar.karmaCurrent <= 0 ? 'Kein Karma mehr!' : 'Karma zum Wurf hinzufügen'">
              <mat-icon>auto_awesome</mat-icon>
              Karma verwenden
              <span *ngIf="activeChar" class="karma-count-badge" [class.empty]="activeChar.karmaCurrent <= 0">
                {{ activeChar.karmaCurrent }}
              </span>
            </label>
            <div class="karma-step-ctrl" *ngIf="useKarma">
              <span class="karma-step-val">W6</span>
            </div>
          </div>
        </div>

        <!-- Kampfoptionen -->
        <div class="combat-options-section" *ngIf="activeChar">
          <div class="section-title">Kampfoptionen</div>
          <div class="combat-options-row">
            <button class="combat-option-toggle aggressive"
              [class.active]="aggressiveAttack"
              (click)="toggleAggressiveAttack()"
              matTooltip="+3 auf nächsten Angriff, 1 Schaden, -3 auf alle Verteidigungswerte">
              <mat-icon>local_fire_department</mat-icon>
              Aggressiver Angriff
            </button>
            <div class="aggressive-info" *ngIf="aggressiveAttack">
              <span class="info-bonus">+3 Step</span>
              <span class="info-penalty">-3 VK (diese Runde)</span>
              <span class="info-cost">1 Schaden</span>
            </div>
            <button class="combat-option-toggle defensive"
              [class.active]="defensiveStance"
              (click)="toggleDefensiveStance()"
              matTooltip="-3 auf nächsten Angriff, +3 auf alle Verteidigungswerte">
              <mat-icon>shield</mat-icon>
              Defensive Haltung
            </button>
            <div class="aggressive-info" *ngIf="defensiveStance">
              <span class="info-penalty">-3 Step</span>
              <span class="info-bonus">+3 VK</span>
            </div>
          </div>
        </div>

        <!-- Talente & Fertigkeiten -->
        <div class="probe-section" *ngIf="activeChar && (activeChar.talents?.length || activeChar.skills?.length)">
          <mat-divider></mat-divider>
          <div class="section-title" style="margin-top:12px">Talente & Fertigkeiten</div>

          <div *ngIf="activeChar.talents?.length" class="ability-group">
            <div class="ability-group-label">Talente</div>
            <div class="ability-chips">
              <button *ngFor="let t of activeChar.talents"
                class="ability-chip"
                [class.selected]="selectedProbe?.id === t.talentDefinition.id && selectedProbe?.type === 'talent'"
                (click)="selectProbe('talent', t.talentDefinition.id!, t.talentDefinition.name, probeStepFor(t.talentDefinition.attribute, t.rank))"
                [matTooltip]="t.talentDefinition.attribute + ' · Rang ' + t.rank">
                <span class="ability-name">{{ t.talentDefinition.name }}</span>
                <span class="ability-rank">{{ t.rank }}</span>
                <span class="ability-step">Step {{ probeStepFor(t.talentDefinition.attribute, t.rank) }}</span>
              </button>
            </div>
          </div>

          <div *ngIf="activeChar.skills?.length" class="ability-group">
            <div class="ability-group-label">Fertigkeiten</div>
            <div class="ability-chips">
              <button *ngFor="let s of activeChar.skills"
                class="ability-chip"
                [class.selected]="selectedProbe?.id === s.skillDefinition.id && selectedProbe?.type === 'skill'"
                (click)="selectProbe('skill', s.skillDefinition.id!, s.skillDefinition.name, probeStepFor(s.skillDefinition.attribute, s.rank))"
                [matTooltip]="s.skillDefinition.attribute + ' · Rang ' + s.rank">
                <span class="ability-name">{{ s.skillDefinition.name }}</span>
                <span class="ability-rank">{{ s.rank }}</span>
                <span class="ability-step">Step {{ probeStepFor(s.skillDefinition.attribute, s.rank) }}</span>
              </button>
            </div>
          </div>

          <!-- Probe TN when something selected -->
          <div class="probe-controls" *ngIf="selectedProbe">
            <span class="selected-probe-label">
              <mat-icon style="font-size:16px;height:16px;width:16px;vertical-align:middle">casino</mat-icon>
              {{ selectedProbe.name }} · Step {{ selectedProbe.step }}
            </span>
            <mat-form-field appearance="fill" style="width:100px">
              <mat-label>Zielwert (TN)</mat-label>
              <input matInput type="number" [(ngModel)]="probeTargetNumber" min="1">
            </mat-form-field>
            <button mat-icon-button color="warn" (click)="clearProbe()" matTooltip="Auswahl aufheben">
              <mat-icon>close</mat-icon>
            </button>
          </div>
        </div>

        <!-- Roll Button -->
        <button mat-raised-button class="roll-btn" (click)="selectedProbe ? rollProbe() : roll()" [class.karma-roll]="useKarma" [class.probe-roll]="!!selectedProbe">
          <mat-icon>casino</mat-icon>
          <span *ngIf="selectedProbe">Probe: {{ selectedProbe.name }}{{ useKarma ? ' + Karma' : '' }}</span>
          <span *ngIf="!selectedProbe">Würfeln!{{ useKarma ? ' + Karma' : '' }}</span>
        </button>

        <!-- Probe Result -->
        <div class="probe-result" *ngIf="lastProbeResult">
          <div class="result-header">
            <div class="roll-total" [class.exploded]="lastProbeResult.karmaRoll?.exploded ?? false"
              [class.success]="lastProbeResult.success" [class.failure]="!lastProbeResult.success">
              {{ lastProbeResult.total }}
              <mat-icon *ngIf="lastProbeResult.karmaRoll?.exploded" style="color:#ff9800;font-size:1rem">local_fire_department</mat-icon>
            </div>
            <div class="result-meta">
              <div class="probe-name">{{ lastProbeResult.probeName }}</div>
              <div>Step {{ lastProbeResult.step }} · {{ lastProbeResult.diceExpression }}
                <span *ngIf="lastProbeResult.karmaUsed" style="color:#c9a84c"> + Karma d{{ karmaStep }}</span>
              </div>
              <div>TN {{ lastProbeResult.targetNumber }}</div>
              <div class="success-degree" [ngClass]="degreeClass(lastProbeResult)">
                {{ lastProbeResult.successDegree }}
                <span *ngIf="lastProbeResult.success && lastProbeResult.extraSuccesses > 0"> (+{{ lastProbeResult.extraSuccesses }} Erfolge)</span>
              </div>
            </div>
          </div>
          <div class="dice-breakdown">
            <div class="die-result" *ngFor="let d of lastProbeResult.dice" [class.exploded]="d.exploded">
              <span class="die-sides">d{{ d.sides }}</span>
              <span class="die-rolls">{{ d.rolls.join(' + ') }}<span *ngIf="d.rolls.length > 1" class="die-total"> = {{ d.total }}</span></span>
              <span *ngIf="d.exploded" class="explode-icon">💥</span>
            </div>
            <div class="die-result karma-die" *ngIf="lastProbeResult.karmaRoll">
              <span class="die-sides" style="color:#c9a84c">★ d{{ karmaStep }}</span>
              <span class="die-rolls">{{ lastProbeResult.karmaRoll.dice[0].rolls.join(' + ') }}</span>
              <span *ngIf="lastProbeResult.karmaRoll.exploded" class="explode-icon">💥</span>
            </div>
          </div>
        </div>

        <!-- Free Roll Result (only when no probe) -->
        <div class="roll-result" *ngIf="lastRoll && !lastProbeResult">
          <div class="result-header">
            <div class="roll-total" [class.exploded]="lastRoll.exploded || (lastKarmaRoll?.exploded ?? false)">
              {{ totalWithKarma() }}
              <mat-icon *ngIf="lastRoll.exploded || lastKarmaRoll?.exploded" style="color:#ff9800;font-size:1rem" matTooltip="Explosion!">local_fire_department</mat-icon>
            </div>
            <div class="result-meta">
              <div>Step {{ lastRoll.step }} · {{ lastRoll.diceExpression }}
                <span *ngIf="lastKarmaRoll" style="color:#c9a84c"> + Karma d{{ karmaStep }}</span>
              </div>
              <div *ngIf="lastKarmaRoll" class="karma-breakdown">
                Basis: {{ lastRoll.total }} + Karma: {{ lastKarmaRoll.total }} = {{ totalWithKarma() }}
              </div>
            </div>
          </div>
          <div class="dice-breakdown">
            <div class="die-result" *ngFor="let d of lastRoll.dice" [class.exploded]="d.exploded">
              <span class="die-sides">d{{ d.sides }}</span>
              <span class="die-rolls">{{ d.rolls.join(' + ') }}<span *ngIf="d.rolls.length > 1" class="die-total"> = {{ d.total }}</span></span>
              <span *ngIf="d.exploded" class="explode-icon">💥</span>
            </div>
            <div class="die-result karma-die" *ngIf="lastKarmaRoll">
              <span class="die-sides" style="color:#c9a84c">★ d{{ karmaStep }}</span>
              <span class="die-rolls">{{ lastKarmaRoll.dice[0].rolls.join(' + ') }}<span *ngIf="lastKarmaRoll.dice[0].rolls.length > 1" class="die-total"> = {{ lastKarmaRoll.total }}</span></span>
              <span *ngIf="lastKarmaRoll.exploded" class="explode-icon">💥</span>
            </div>
          </div>
        </div>

        <!-- Step Table Reference -->
        <div class="step-table">
          <div class="section-title" style="cursor:pointer" (click)="showTable = !showTable">
            Stufen-Tabelle {{ showTable ? '▲' : '▼' }}
          </div>
          <div *ngIf="showTable" class="table-grid">
            <div class="table-row header">
              <span>Step</span><span>Würfel</span><span>Step</span><span>Würfel</span>
            </div>
            <ng-container *ngFor="let row of stepTableRows">
              <div class="table-row">
                <span class="step-num" [class.current]="row.left.step === step">{{ row.left.step }}</span>
                <span>{{ row.left.dice }}</span>
                <span class="step-num" [class.current]="row.right?.step === step">{{ row.right?.step }}</span>
                <span>{{ row.right?.dice }}</span>
              </div>
            </ng-container>
          </div>
        </div>
      </div>

      <!-- Verlauf (rechts) -->
      <div class="history-sidebar">
        <div class="section-title">Verlauf</div>
        <div class="history-empty" *ngIf="history.length === 0">Noch keine Würfe</div>
        <div class="history-list">
          <div class="history-item" *ngFor="let r of history" [class.exploded]="r.exploded" [class.success]="r.success === true" [class.failure]="r.success === false">
            <div class="history-top">
              <span class="history-name" *ngIf="r.probeName">{{ r.probeName }}</span>
              <span class="history-name frei" *ngIf="!r.probeName">Freier Wurf</span>
              <span class="history-total" [class.exploded]="r.exploded">{{ r.total }}<span *ngIf="r.exploded"> 💥</span></span>
            </div>
            <div class="history-bottom">
              <span class="history-step">Step {{ r.step }}</span>
              <span class="history-expr">{{ r.diceExpression }}</span>
              <span class="history-degree" *ngIf="r.success !== undefined" [class.ok]="r.success" [class.fail]="!r.success">
                <ng-container *ngIf="r.success">{{ r.extraSuccesses && r.extraSuccesses > 0 ? '+' + r.extraSuccesses + ' Erfolge' : '✓' }}</ng-container>
                <ng-container *ngIf="!r.success">✗</ng-container>
              </span>
            </div>
          </div>
        </div>
      </div>

      </div> <!-- /page-layout -->
    </div>
  `,
  styles: [`
    .page-container { padding: 24px; max-width: 1100px; }
    h1 { font-family: 'Cinzel', serif; color: #c9a84c; margin-bottom: 4px; }

    .page-layout { display: grid; grid-template-columns: 1fr 320px; gap: 16px; align-items: start; }

    /* ---- History Sidebar ---- */
    .history-sidebar {
      background: #2a2520; border: 1px solid #3a3028; border-radius: 8px;
      padding: 16px; position: sticky; top: 16px;
    }
    .history-empty { color: #555; font-size: 0.82rem; font-style: italic; margin-top: 8px; }
    .history-list { display: flex; flex-direction: column; gap: 4px; max-height: calc(100vh - 160px); overflow-y: auto; margin-top: 8px; }
    .history-item {
      background: #1a1a1a; border: 1px solid #2a2520; border-radius: 5px;
      padding: 6px 8px; cursor: default;
      &.exploded { border-color: #ff9800; }
      &.success { border-left: 3px solid #4caf50; }
      &.failure { border-left: 3px solid #f44336; }
    }
    .history-top { display: flex; justify-content: space-between; align-items: baseline; gap: 4px; }
    .history-name { font-size: 0.82rem; font-weight: 600; color: #c9a84c; flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .history-name.frei { color: #666; font-weight: 400; }
    .history-total { font-size: 1.1rem; font-weight: bold; color: #e0d5c0; flex-shrink: 0; &.exploded { color: #ff9800; } }
    .history-bottom { display: flex; align-items: center; gap: 6px; margin-top: 2px; }
    .history-step { font-size: 0.72rem; color: #555; white-space: nowrap; }
    .history-expr { font-size: 0.72rem; color: #666; flex: 1; }
    .history-degree { font-size: 0.72rem; font-weight: 600; padding: 1px 5px; border-radius: 4px; white-space: nowrap;
      &.ok { color: #4caf50; } &.fail { color: #f44336; }
    }

    .roller-card { background: #2a2520; border: 1px solid #3a3028; border-radius: 8px; padding: 24px; display: flex; flex-direction: column; gap: 20px; }

    .step-control {}
    .step-row { display: flex; align-items: center; gap: 12px; margin: 8px 0; }
    .step-display { display: flex; flex-direction: column; align-items: center; min-width: 80px; }
    .step-number { font-size: 2.5rem; font-weight: bold; color: #c9a84c; line-height: 1; }
    .dice-expr { font-size: 0.85rem; color: #888; }
    .step-input { width: 60px; background: #333; border: 1px solid #555; color: #fff; padding: 4px 8px; border-radius: 4px; text-align: center; font-size: 1rem; }

    .quick-steps { display: flex; flex-wrap: wrap; gap: 6px; }
    .quick-steps button { min-width: 0; padding: 0 10px; font-size: 0.8rem; height: 30px; }

    .char-section { }
    .char-row { display: flex; gap: 16px; align-items: flex-start; flex-wrap: wrap; }
    .char-info {
      background: #1e1a16; border: 1px solid #3a3028; border-radius: 6px;
      padding: 8px 12px; min-width: 180px;
    }
    .char-name-badge { font-family: 'Cinzel', serif; color: #c9a84c; font-size: 0.95rem; font-weight: 600; }
    .karma-status {
      display: flex; align-items: center; gap: 4px; font-size: 0.82rem; color: #c9a84c; margin-top: 4px;
      &.low { color: #f44336; }
    }
    .discipline-info { font-size: 0.75rem; color: #666; margin-top: 2px; }

    .karma-section { }
    .karma-row { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
    .karma-toggle {
      display: flex; align-items: center; gap: 6px;
      padding: 6px 14px; border-radius: 20px; cursor: pointer;
      border: 1px solid #555; color: #888; transition: all 0.2s;
      user-select: none;
      &.active { border-color: #c9a84c; color: #c9a84c; background: rgba(201,168,76,0.1); }
      &.disabled { opacity: 0.4; cursor: not-allowed; }
      mat-icon { font-size: 18px; height: 18px; width: 18px; }
    }
    .karma-count-badge {
      background: rgba(201,168,76,0.2); border-radius: 10px;
      padding: 0 6px; font-size: 0.8rem; font-weight: bold; color: #c9a84c;
      &.empty { background: rgba(244,67,54,0.2); color: #f44336; }
    }
    .karma-step-ctrl { display: flex; align-items: center; gap: 4px; }
    .karma-step-val { min-width: 32px; text-align: center; font-weight: bold; color: #c9a84c; font-size: 1.1rem; }
    .karma-breakdown { font-size: 0.8rem; color: #c9a84c; margin-top: 2px; }
    .karma-die { border-color: #c9a84c !important; }

    .probe-section { display: flex; flex-direction: column; gap: 10px; }
    .ability-group { }
    .ability-group-label { font-size: 11px; text-transform: uppercase; letter-spacing: 0.1em; color: #666; margin-bottom: 8px; }
    .ability-chips { display: flex; flex-wrap: wrap; gap: 8px; }
    .ability-chip {
      display: flex; flex-direction: column; align-items: center; gap: 3px;
      background: #1e1a16; border: 1px solid #3a3028; border-radius: 8px;
      padding: 10px 16px; cursor: pointer; transition: all 0.15s;
      color: #c0b090; min-width: 90px;
      &:hover { border-color: #c9a84c; background: rgba(201,168,76,0.08); }
      &.selected { border-color: #c9a84c; background: rgba(201,168,76,0.15); color: #c9a84c; }
    }
    .ability-name { font-size: 1rem; font-weight: 600; text-align: center; }
    .ability-rank {
      font-size: 0.78rem; background: rgba(201,168,76,0.2);
      border-radius: 8px; padding: 1px 8px; color: #c9a84c;
    }
    .ability-step { font-size: 0.8rem; color: #888; }

    .probe-controls {
      display: flex; align-items: center; gap: 12px; flex-wrap: wrap;
      background: rgba(201,168,76,0.07); border: 1px solid #4a3a20;
      border-radius: 6px; padding: 8px 12px;
    }
    .selected-probe-label { flex: 1; color: #c9a84c; font-size: 0.9rem; font-weight: 500; }

    .roll-btn { height: 52px; font-size: 1.1rem; letter-spacing: 0.05em; }
    .roll-btn.karma-roll { background: linear-gradient(135deg, #7b1fa2, #c9a84c) !important; }
    .roll-btn.probe-roll { background: linear-gradient(135deg, #1a4a2a, #2e7d32) !important; }
    .roll-btn.probe-roll.karma-roll { background: linear-gradient(135deg, #1a2a4a, #1565c0, #c9a84c) !important; }

    .probe-result {
      background: #1a1e1a; border: 1px solid #2e5a2e; border-radius: 8px; padding: 14px;
    }
    .probe-name { font-family: 'Cinzel', serif; font-size: 0.95rem; color: #c9a84c; }
    .roll-total.success { color: #4caf50; }
    .roll-total.failure { color: #f44336; }
    .success-degree { font-size: 1rem; font-weight: bold; margin-top: 4px; }
    .success-degree.success { color: #4caf50; }
    .success-degree.failure { color: #f44336; }
    .success-degree.great { color: #66bb6a; }
    .success-degree.extra { color: #81c784; }

    .result-header { display: flex; align-items: center; gap: 16px; margin-bottom: 12px; }
    .roll-total {
      font-size: 3rem; font-weight: bold; color: #c9a84c;
      min-width: 80px; text-align: center; display: flex; align-items: center; gap: 4px;
      &.exploded { color: #ff9800; }
    }

    .dice-breakdown { display: flex; flex-wrap: wrap; gap: 8px; }
    .die-result {
      background: #1a1a1a; border: 1px solid #444; border-radius: 6px;
      padding: 6px 10px; display: flex; align-items: center; gap: 6px;
      &.exploded { border-color: #ff9800; }
    }
    .die-sides { color: #666; font-size: 0.8rem; min-width: 24px; }
    .die-rolls { color: #e0d5c0; font-family: monospace; }
    .die-total { color: #c9a84c; font-weight: bold; }


    .step-table {}
    .table-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 2px; max-height: 300px; overflow-y: auto; }
    .table-row { display: grid; grid-template-columns: 40px 1fr 40px 1fr; gap: 8px; padding: 2px 4px; font-size: 0.8rem; border-bottom: 1px solid #222; }
    .table-row.header { color: #9c7b3c; font-weight: 600; border-bottom: 1px solid #444; }
    .step-num { color: #c9a84c; &.current { background: rgba(201,168,76,0.2); border-radius: 2px; } }

    .combat-options-section { }
    .combat-options-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
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
  `]
})
export class DiceRollerComponent implements OnInit {
  step = 5;
  lastRoll?: RollResult;
  lastKarmaRoll?: RollResult;
  lastProbeResult?: ProbeResult;
  history: Array<{ step: number; diceExpression: string; total: number; exploded: boolean; probeName?: string; success?: boolean; extraSuccesses?: number }> = [];
  showTable = false;
  useKarma = false;
  readonly karmaStep = 6; // Karma ist immer W6
  quickSteps = [3, 5, 7, 8, 10, 12, 14, 16, 19];

  allCharacters: Character[] = [];
  activeCharId: number | null = null;
  activeChar?: Character;

  selectedProbe: { type: 'talent' | 'skill'; id: number; name: string; step: number } | null = null;
  probeTargetNumber = 10;
  aggressiveAttack = false;
  defensiveStance = false;

  // ED4 attribute → base step lookup
  private readonly attrStepMap: Record<string, number> = {
    DEXTERITY: 0, STRENGTH: 0, TOUGHNESS: 0, PERCEPTION: 0, WILLPOWER: 0, CHARISMA: 0
  };

  readonly stepTableData = [
    { step:  1, dice: 'W6-3'         }, { step:  2, dice: 'W6-2'         },
    { step:  3, dice: 'W6-1'         }, { step:  4, dice: 'W6'           },
    { step:  5, dice: 'W8'           }, { step:  6, dice: 'W10'          },
    { step:  7, dice: 'W12'          }, { step:  8, dice: '2W6'          },
    { step:  9, dice: 'W8+W6'        }, { step: 10, dice: '2W8'          },
    { step: 11, dice: 'W10+W8'       }, { step: 12, dice: '2W10'         },
    { step: 13, dice: 'W12+W10'      }, { step: 14, dice: '2W12'         },
    { step: 15, dice: 'W12+2W6'      }, { step: 16, dice: 'W12+W8+W6'   },
    { step: 17, dice: 'W12+2W8'      }, { step: 18, dice: 'W12+W10+W8'  },
    { step: 19, dice: 'W12+2W10'     }, { step: 20, dice: '2W12+W10'    },
    { step: 21, dice: '3W12'         }, { step: 22, dice: '2W12+2W6'    },
    { step: 23, dice: '2W12+W8+W6'   }, { step: 24, dice: '2W12+2W8'    },
    { step: 25, dice: '2W12+W10+W8'  }, { step: 26, dice: '2W12+2W10'   },
    { step: 27, dice: '3W12+W10'     }, { step: 28, dice: '4W12'         },
    { step: 29, dice: '3W12+2W6'     }, { step: 30, dice: '3W12+W8+W6'  },
    { step: 31, dice: '3W12+2W8'     }, { step: 32, dice: '3W12+W10+W8' },
    { step: 33, dice: '3W12+2W10'    }, { step: 34, dice: '4W12+W10'    },
    { step: 35, dice: '5W12'         }, { step: 36, dice: '4W12+2W6'    },
    { step: 37, dice: '4W12+W8+W6'   }, { step: 38, dice: '4W12+2W8'    },
    { step: 39, dice: '4W12+W10+W8'  }, { step: 40, dice: '4W12+2W10'   },
  ];

  get stepTableRows(): Array<{ left: { step: number; dice: string }; right: { step: number; dice: string } | undefined }> {
    const rows = [];
    for (let i = 0; i < this.stepTableData.length; i += 2) {
      rows.push({ left: this.stepTableData[i], right: this.stepTableData[i + 1] as { step: number; dice: string } | undefined });
    }
    return rows;
  }

  get diceExpr(): string {
    return this.stepTableData.find(s => s.step === this.step)?.dice ?? `Step ${this.step}`;
  }

  constructor(
    private diceService: DiceService,
    private characterService: CharacterService,
    private activeCharService: ActiveCharacterService,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.characterService.findAll().subscribe(c => this.allCharacters = c);

    // Aktiven Charakter aus globalem Service übernehmen — immer frisch vom Backend laden.
    // KEIN activeCharService.update() hier aufrufen, sonst entsteht ein Subscription-Loop.
    let lastLoadedId: number | null = null;
    this.activeCharService.activeChar$.subscribe(c => {
      if (c?.id && c.id !== lastLoadedId) {
        lastLoadedId = c.id;
        this.activeCharId = c.id;
        this.characterService.findById(c.id).subscribe(fresh => {
          this.activeChar = fresh;
          this.selectedProbe = null;
          this.lastProbeResult = undefined;
        });
      } else if (!c) {
        lastLoadedId = null;
        this.activeChar = undefined;
        this.activeCharId = null;
        this.selectedProbe = null;
      }
    });
  }

  onCharSelected(): void {
    if (!this.activeCharId) {
      this.activeChar = undefined;
      this.activeCharService.clear();
      return;
    }
    this.characterService.findById(this.activeCharId).subscribe(c => {
      this.activeChar = c;
      this.activeCharService.set(c);
    });
  }

  toggleKarma(): void {
    if (this.activeChar && this.activeChar.karmaCurrent <= 0) return;
    this.useKarma = !this.useKarma;
  }

  toggleAggressiveAttack(): void {
    this.aggressiveAttack = !this.aggressiveAttack;
    if (this.aggressiveAttack) this.defensiveStance = false;
  }

  toggleDefensiveStance(): void {
    this.defensiveStance = !this.defensiveStance;
    if (this.defensiveStance) this.aggressiveAttack = false;
  }

  onStepChange(): void {
    this.step = Math.max(1, Math.min(40, this.step));
  }

  roll(): void {
    const stepBonus = this.aggressiveAttack ? 3 : this.defensiveStance ? -3 : 0;
    const rollStep = Math.max(1, this.step + stepBonus);
    const wasAggressive = this.aggressiveAttack;
    const wasDefensive = this.defensiveStance;
    this.aggressiveAttack = false;
    this.defensiveStance = false;

    this.diceService.roll(rollStep).subscribe(r => {
      this.lastRoll = r;
      this.lastKarmaRoll = undefined;
      this.lastProbeResult = undefined;

      if (wasAggressive && this.activeChar?.id) {
        this.characterService.updateField(this.activeChar.id, 'damage', 1).subscribe(updated => {
          this.activeChar = updated;
          this.snack.open(`Aggressiver Angriff: 1 Schaden, -3 Verteidigung!`, 'OK', { duration: 3000 });
        });
      }
      if (wasDefensive) {
        this.snack.open(`Defensive Haltung: +3 Verteidigung diese Runde!`, 'OK', { duration: 3000 });
      }

      if (this.useKarma) {
        this.diceService.roll(this.karmaStep).subscribe(kr => {
          this.lastKarmaRoll = kr;
          this.history.unshift({ step: r.step, diceExpression: r.diceExpression + `+d${this.karmaStep}★`, total: r.total + kr.total, exploded: r.exploded || kr.exploded });
          if (this.history.length > 30) this.history.pop();

          // Karma vom Charakter abziehen
          if (this.activeChar?.id) {
            this.characterService.updateField(this.activeChar.id, 'karma', -1).subscribe(updated => {
              this.activeChar = updated;
              if (updated.karmaCurrent === 0) {
                this.snack.open(`${updated.name} hat kein Karma mehr!`, 'OK', { duration: 3000 });
                this.useKarma = false;
              }
            });
          }
        });
      } else {
        this.history.unshift({ step: r.step, diceExpression: r.diceExpression, total: r.total, exploded: r.exploded });
        if (this.history.length > 30) this.history.pop();
      }
    });
  }

  /** Returns the attribute value for a given attribute key from the active character */
  private attrValue(attrKey: string): number {
    if (!this.activeChar) return 10;
    const map: Record<string, number> = {
      DEXTERITY: this.activeChar.dexterity,
      STRENGTH: this.activeChar.strength,
      TOUGHNESS: this.activeChar.toughness,
      PERCEPTION: this.activeChar.perception,
      WILLPOWER: this.activeChar.willpower,
      CHARISMA: this.activeChar.charisma,
    };
    return map[attrKey] ?? 10;
  }

  attrToStep(value: number): number {
    if (value <= 3)  return 2;
    if (value <= 6)  return 3;
    if (value <= 9)  return 4;
    if (value <= 12) return 5;
    if (value <= 15) return 6;
    if (value <= 18) return 7;
    if (value <= 21) return 8;
    if (value <= 24) return 9;
    if (value <= 27) return 10;
    if (value <= 30) return 11;
    return Math.floor((value - 1) / 3);
  }

  probeStepFor(attribute: string, rank: number): number {
    return this.attrToStep(this.attrValue(attribute)) + rank;
  }

  selectProbe(type: 'talent' | 'skill', id: number, name: string, step: number): void {
    if (this.selectedProbe?.id === id && this.selectedProbe?.type === type) {
      this.selectedProbe = null; // toggle off
    } else {
      this.selectedProbe = { type, id, name, step };
      this.step = step;
    }
    this.lastProbeResult = undefined;
    this.lastRoll = undefined;
  }

  clearProbe(): void {
    this.selectedProbe = null;
    this.lastProbeResult = undefined;
  }

  rollProbe(): void {
    if (!this.selectedProbe || !this.activeChar?.id) return;
    const wasAggressive = this.aggressiveAttack;
    const wasDefensive = this.defensiveStance;
    this.aggressiveAttack = false;
    this.defensiveStance = false;

    const req = {
      characterId: this.activeChar.id,
      talentId: this.selectedProbe.type === 'talent' ? this.selectedProbe.id : undefined,
      skillId: this.selectedProbe.type === 'skill' ? this.selectedProbe.id : undefined,
      bonusSteps: wasAggressive ? 3 : wasDefensive ? -3 : 0,
      targetNumber: this.probeTargetNumber,
      spendKarma: this.useKarma,
    };
    this.diceService.probe(req).subscribe(r => {
      this.lastProbeResult = r;
      this.lastRoll = undefined;
      this.lastKarmaRoll = undefined;
      const karmaExpr = r.karmaUsed && r.karmaRoll ? `+d${r.karmaRoll.dice[0]?.sides ?? this.karmaStep}★` : '';
      this.history.unshift({ step: r.step, diceExpression: r.diceExpression + karmaExpr, total: r.total, exploded: r.dice.some(d => d.exploded) || (r.karmaRoll?.exploded ?? false), probeName: r.probeName, success: r.success, extraSuccesses: r.extraSuccesses });
      if (this.history.length > 30) this.history.pop();

      if (wasAggressive && this.activeChar?.id) {
        this.characterService.updateField(this.activeChar.id, 'damage', 1).subscribe(updated => {
          this.activeChar = updated;
          this.snack.open(`Aggressiver Angriff: 1 Schaden, -3 Verteidigung!`, 'OK', { duration: 3000 });
        });
      }
      if (wasDefensive) {
        this.snack.open(`Defensive Haltung: +3 Verteidigung diese Runde!`, 'OK', { duration: 3000 });
      }

      if (r.karmaUsed && this.activeChar?.id) {
        this.characterService.updateField(this.activeChar.id, 'karma', -1).subscribe(updated => {
          this.activeChar = updated;
          this.activeCharService.update(updated);
          if (updated.karmaCurrent === 0) {
            this.snack.open(`${updated.name} hat kein Karma mehr!`, 'OK', { duration: 3000 });
            this.useKarma = false;
          }
        });
      }
    });
  }

  degreeClass(p: ProbeResult): string {
    if (!p.success) return 'failure';
    if (p.extraSuccesses >= 3) return 'great';
    if (p.extraSuccesses >= 1) return 'extra';
    return 'success';
  }

  totalWithKarma(): number {
    return (this.lastRoll?.total ?? 0) + (this.lastKarmaRoll?.total ?? 0);
  }
}
