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
import { MatCheckboxModule } from '@angular/material/checkbox';
import { Subscription } from 'rxjs';
import { CombatService } from '../../services/combat.service';
import { CharacterService } from '../../services/character.service';
import { WebSocketService } from '../../services/websocket.service';
import {
  CombatSession, CombatantState, AttackActionRequest,
  CombatActionResult, ActiveEffect, FreeActionRequest, FreeActionResult,
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
  LufttanzActivationResult, LufttanzAttackRequest,
  InitiativeRollDetail, DialogState
} from '../../models/combat.model';
import { Character, SpellDefinition, CharacterSpell, SpellThreadOption } from '../../models/character.model';
import { hexDistance } from '../../services/hex-util';

/** Ein auswählbarer Effekt im "Magie neutralisieren"-Dialog. */
export interface EffectChoice {
  /** 'combatantId:effectId' */
  key: string;
  combatantId: number;
  combatantName: string;
  effectId: number;
  name: string;
  remainingRounds: number;
}

@Component({
  selector: 'app-combat-tracker',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatCardModule, MatButtonModule, MatIconModule, MatSelectModule,
    MatFormFieldModule, MatInputModule, MatDialogModule,
    MatSnackBarModule, MatTooltipModule, MatDividerModule, MatCheckboxModule
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
          <button mat-stroked-button *ngIf="session.status === 'SETUP' || session.status === 'ACTIVE'" (click)="addCharacterPanel = !addCharacterPanel">
            <mat-icon>person_add</mat-icon> Kombattant
          </button>
          <button mat-raised-button color="primary" *ngIf="session.status === 'SETUP'" (click)="rollInitiative()">
            <mat-icon>casino</mat-icon> Initiative würfeln
          </button>
          <span *ngIf="session.status === 'ACTIVE'" class="phase-badge"
                [class.phase-declaration]="session.phase === 'DECLARATION'"
                [class.phase-action]="session.phase === 'ACTION'">
            {{ session.phase === 'DECLARATION' ? '📢 Ansagephase' : '⚔ Aktionsphase' }}
            <span *ngIf="session.phase === 'DECLARATION'"> ({{ declarationProgress() }})</span>
          </span>
          <span *ngIf="session.status === 'FINISHED'" class="phase-badge"
                style="background:rgba(239,154,154,0.15);border:1px solid #ef9a9a;color:#ef9a9a">
            🏁 Kampf beendet
          </span>
          <button mat-stroked-button *ngIf="session.mapEnabled" (click)="openMapWindow()"
                  matTooltip="Kampfkarte in eigenem Fenster öffnen">
            <mat-icon>map</mat-icon> Karte
          </button>
          <button mat-stroked-button *ngIf="!session.mapEnabled && session.status === 'SETUP'" (click)="enableMap()"
                  matTooltip="Hexfeld-Kampfkarte für diese Session aktivieren">
            <mat-icon>grid_on</mat-icon> Karte aktivieren
          </button>
          <button mat-stroked-button *ngIf="session.status === 'ACTIVE'" (click)="openGmEffectDialog()"
                  matTooltip="Beliebigen Bonus/Malus-Effekt auf einen Kombattanten anwenden">
            <mat-icon>auto_fix_normal</mat-icon> GM-Effekt
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
            <mat-icon>dangerous</mat-icon> Gegner
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
              <div class="group-header enemies-header"><mat-icon>dangerous</mat-icon> Gegner</div>
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
                <span class="initiative-badge"
                      [matTooltip]="session.phase === 'DECLARATION' ? 'Initiative-Stufe (wird gewürfelt sobald alle angesagt haben)' : 'Initiative-Wurf'">
                  {{ session.phase === 'DECLARATION' ? initiativeStepLabel(c) : c.initiative }}
                </span>
                <span class="combatant-name">{{ cn(c) }}</span>
                <span class="discipline-badge">{{ c.character.discipline?.name }}</span>
                <mat-icon *ngIf="c.defeated" style="color:#f44336;font-size:16px">dangerous</mat-icon>
                <span *ngIf="c.knockedDown && !c.defeated" class="knocked-badge" matTooltip="Niedergeschlagen: −3 auf alle Proben, −3 KV/MV/SV">↓ Nieder</span>
                <span *ngIf="c.preparingSpellId && !c.defeated" class="spell-prep-badge"
                  matTooltip="Zauber vorbereitet: {{ spellNameOf(c) }} ({{ c.threadsWoven }}/{{ c.threadsRequired }} Fäden){{ extraThreadCountOf(c) ? ', ' + extraThreadCountOf(c) + ' Zusatzfäden' : '' }}">
                  ⟡ {{ spellNameOf(c) }} ({{ c.threadsWoven }}/{{ c.threadsRequired }})<span
                    *ngIf="extraThreadCountOf(c)"> +{{ extraThreadCountOf(c) }}</span>
                </span>
                <span *ngIf="dialogStateBadge(c) as badge" class="dialog-state-badge"
                  [matTooltip]="'Plant: ' + badge">{{ badge }}</span>
              </div>
              <div class="comb-meta">
                <mat-checkbox
                  [checked]="isAutofight(c)"
                  (change)="toggleAutofight(c, $event.checked)"
                  color="warn"
                  matTooltip="Automatisch kämpfen">Auto</mat-checkbox>
                <span *ngIf="session!.status === 'ACTIVE' && c.hasActedThisRound && !c.defeated" class="acted-badge" matTooltip="Hat diese Runde bereits gehandelt">Gehandelt</span>
                <span *ngIf="session!.status === 'ACTIVE' && session!.phase === 'ACTION' && c.declaredStance === 'AGGRESSIVE' && !c.defeated"
                      class="stance-badge aggressive" matTooltip="Aggressive Haltung: +3 Angriff, -3 Verteidigung">⚔ Aggressiv</span>
                <span *ngIf="session!.status === 'ACTIVE' && session!.phase === 'ACTION' && c.declaredStance === 'DEFENSIVE' && !c.defeated"
                      class="stance-badge defensive" matTooltip="Defensive Haltung: -3 Angriff, +3 Verteidigung">🛡 Defensiv</span>
              </div>
            </div>
            <!-- Declaration Phase UI -->
            <div class="declaration-row"
                 *ngIf="session!.status === 'ACTIVE' && session!.phase === 'DECLARATION' && !c.defeated">
              <span class="declaration-label">Haltung:</span>
              <button class="decl-btn" [class.active]="c.declaredStance === 'NONE'"
                      (click)="setDeclaredStance(c, 'NONE')" matTooltip="Keine Haltung">
                <mat-icon>remove_circle_outline</mat-icon> Neutral
              </button>
              <button class="decl-btn aggressive" [class.active]="c.declaredStance === 'AGGRESSIVE'"
                      (click)="setDeclaredStance(c, 'AGGRESSIVE')"
                      matTooltip="Aggressiv: +3 Angriff, -3 Verteidigung, 1 Schaden">
                <mat-icon>local_fire_department</mat-icon> Aggressiv
              </button>
              <button class="decl-btn defensive" [class.active]="c.declaredStance === 'DEFENSIVE'"
                      (click)="setDeclaredStance(c, 'DEFENSIVE')"
                      matTooltip="Defensiv: -3 Angriff, +3 Verteidigung">
                <mat-icon>shield</mat-icon> Defensiv
              </button>
              <span class="declaration-label" style="margin-left:12px">Handlung:</span>
              <button class="decl-btn" [class.active]="c.declaredActionType === 'WEAPON'"
                      (click)="setDeclaredActionType(c, 'WEAPON')"
                      matTooltip="Diese Runde mit einer Waffe handeln">
                <mat-icon>sports_martial_arts</mat-icon> Waffe
              </button>
              <button class="decl-btn" [class.active]="c.declaredActionType === 'SPELL'"
                      (click)="setDeclaredActionType(c, 'SPELL')"
                      [disabled]="!isMagicCombatant(c)"
                      matTooltip="Diese Runde zaubern">
                <mat-icon>auto_fix_high</mat-icon> Zauber
              </button>
              <button mat-raised-button color="primary" class="decl-confirm"
                      *ngIf="!c.hasDeclared"
                      (click)="confirmDeclaration(c)">
                <mat-icon>check</mat-icon> Ansage bestätigen
              </button>
              <span *ngIf="c.hasDeclared" class="decl-confirmed">
                <mat-icon style="color:#4caf50;font-size:18px">check_circle</mat-icon> Angesagt
                <button mat-stroked-button class="decl-undo" (click)="undeclare(c)"
                        matTooltip="Ansage ändern">
                  <mat-icon>edit</mat-icon> Ändern
                </button>
              </span>
            </div>
            <!-- Zwei Spalten: links die Werte, rechts die Aktionen -->
            <div class="comb-columns">
              <div class="comb-col-stats">
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
                <div class="comb-stat" *ngIf="!isNoKarma(c)">
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
                <span class="def-chip phys" [matTooltip]="defenseTooltip(c, 'PHYSICAL_DEFENSE')" matTooltipClass="multiline-tooltip">
                  <mat-icon>shield</mat-icon> KV {{ pd(c) }}<span *ngIf="effectivePd(c) !== pd(c)" class="def-modified"> ({{ effectivePd(c) }})</span>
                </span>
                <span class="def-chip myst" [matTooltip]="defenseTooltip(c, 'SPELL_DEFENSE')" matTooltipClass="multiline-tooltip">
                  <mat-icon>auto_awesome</mat-icon> MV {{ sd(c) }}<span *ngIf="effectiveSd(c) !== sd(c)" class="def-modified"> ({{ effectiveSd(c) }})</span>
                </span>
                <span class="def-chip social" [matTooltip]="defenseTooltip(c, 'SOCIAL_DEFENSE')" matTooltipClass="multiline-tooltip">
                  <mat-icon>people</mat-icon> SV {{ socD(c) }}<span *ngIf="effectiveSocD(c) !== socD(c)" class="def-modified"> ({{ effectiveSocD(c) }})</span>
                </span>
              </div>
              <!-- Rüstungs-Chips (gleiche Darstellung wie auf den Charakter-Karten) -->
              <div class="comb-armor-row" *ngIf="pa(c) > 0 || ma(c) > 0">
                <span class="armor-chip phys" matTooltip="Physische Rüstung" *ngIf="pa(c) > 0">
                  🛡 {{ pa(c) }} phys.
                </span>
                <span class="armor-chip myst" matTooltip="Mystische Rüstung" *ngIf="ma(c) > 0">
                  ✨ {{ ma(c) }} myst.
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
              </div>
              <div class="comb-actions">
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE' && session!.phase === 'ACTION'"
                  class="attack-btn"
                  [disabled]="c.hasActedThisRound || c.defeated || !isActiveTurn(c)"
                  (click)="openAttackDialog(c)" matTooltip="Angreifen">
                  <mat-icon>sports_martial_arts</mat-icon><span class="btn-label">Angriff</span></button>
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE' && session!.phase === 'ACTION' && c.knockedDown && !c.defeated"
                  class="combat-option-btn standup-btn"
                  [disabled]="c.hasActedThisRound"
                  (click)="performStandUp(c)"
                  matTooltip="Aufstehen (Hauptaktion)">
                  <mat-icon>accessibility_new</mat-icon><span class="btn-label">Aufstehen</span></button>
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE' && session!.phase === 'ACTION' && c.knockedDown && !c.defeated"
                  class="combat-option-btn aufspringen-btn"
                  (click)="openAufspringenDialog(c)"
                  matTooltip="Aufspringen (GE-Probe vs 6, 2 Schaden — kann danach noch angreifen)">
                  <mat-icon>directions_run</mat-icon><span class="btn-label">Aufspringen</span></button>
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE' && session!.phase === 'ACTION' && isMagicCombatant(c) && !c.preparingSpellId"
                  class="combat-option-btn threadweave-btn" [disabled]="c.hasActedThisRound || c.defeated"
                  (click)="openThreadweaveDialog(c)"
                  matTooltip="Faden weben (Hauptaktion)">
                  <mat-icon>all_inclusive</mat-icon><span class="btn-label">Faden weben</span></button>
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE' && session!.phase === 'ACTION' && isMagicCombatant(c) && c.preparingSpellId"
                  class="combat-option-btn threadweave-btn" [disabled]="c.hasActedThisRound || c.defeated"
                  (click)="openThreadweaveDialog(c)"
                  matTooltip="Weiteren Faden weben ({{ c.threadsWoven }}/{{ c.threadsRequired }})">
                  <mat-icon>all_inclusive</mat-icon><span class="btn-label">Faden {{ c.threadsWoven }}/{{ c.threadsRequired }}</span></button>
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE' && session!.phase === 'ACTION' && canCastSpell(c)"
                  class="combat-option-btn spellcast-btn" [disabled]="c.hasActedThisRound || c.defeated"
                  (click)="openSpellCastDialog(c)"
                  matTooltip="Zauber wirken">
                  <mat-icon>auto_fix_high</mat-icon><span class="btn-label">Zaubern</span></button>
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE' && session!.phase === 'ACTION' && c.preparingSpellId && !c.defeated"
                  class="combat-option-btn cancel-spell-btn"
                  (click)="cancelSpell(c)"
                  matTooltip="Zaubervorbereitung abbrechen">
                  <mat-icon>cancel</mat-icon><span class="btn-label">Zauber abbrechen</span></button>
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE' && session!.phase === 'ACTION'"
                  class="combat-option-btn use-action" [disabled]="c.hasActedThisRound || c.defeated"
                  (click)="useAction(c)"
                  matTooltip="Aktion benutzen (Sonstiges)">
                  <mat-icon>auto_awesome</mat-icon><span class="btn-label">Aktion nutzen</span></button>
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE' && session!.phase === 'ACTION' && hasMagischeMarkierungTalent(c) && !c.defeated"
                  class="combat-option-btn magische-markierung-btn"
                  (click)="openMagischeMarkierungDialog(c)"
                  matTooltip="Magische Markierung · Freie Aktion (WAH + Rang vs. MV des Ziels, +2 Angriff/Übererfolg für Fernkampf, kostet 1 Schaden)">
                  <mat-icon>gps_fixed</mat-icon><span class="btn-label">Magische Markierung</span></button>
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE' && session!.phase === 'ACTION' && hasTauntTalent(c) && !c.defeated"
                  class="combat-option-btn taunt-btn"
                  [disabled]="!isActiveTurn(c)"
                  (click)="openTauntDialog(c)"
                  matTooltip="Verspotten (Freie Aktion · CHA + Rang vs. Soziale VK · kostet 1 Schaden)">
                  <mat-icon>sentiment_very_dissatisfied</mat-icon><span class="btn-label">Verspotten</span></button>
                <!-- Verängstigen: WIL vs. MV, −2/Erfolg auf Aktionsproben -->
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE' && session!.phase === 'ACTION' && hasFearTalent(c) && !c.defeated"
                  class="combat-option-btn fear-btn"
                  [disabled]="!isActiveTurn(c)"
                  (click)="openFearDialog(c)"
                  matTooltip="Verängstigen (Standardaktion · WIL + Rang vs. Mystische VK · 0 Überanstrengung · −2/Erfolg auf Aktionsproben für Rang Runden)">
                  <mat-icon>mood_bad</mat-icon><span class="btn-label">Verängstigen</span></button>
                <!-- Magie neutralisieren: beendet einen aktiven Effekt (Aktion + 1 Überanstrengung) -->
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE' && session!.phase === 'ACTION' && hasNeutralizeMagicTalent(c) && !c.defeated"
                  class="combat-option-btn neutralize-btn"
                  [disabled]="c.hasActedThisRound"
                  (click)="openNeutralizeMagicDialog(c)"
                  matTooltip="Magie neutralisieren: beendet einen aktiven Effekt (WIL + Rang vs. Effektstufe + 10). Verbraucht die Aktion, kostet 1 Überanstrengung.">
                  <mat-icon>auto_fix_off</mat-icon><span class="btn-label">Magie neutralisieren</span></button>
                <!-- Furcht abschütteln: Widerstandsprobe gegen Verängstigt (1×/Runde) -->
                <button mat-stroked-button
                  *ngIf="session!.status === 'ACTIVE' && session!.phase === 'ACTION' && isFeared(c) && !c.defeated"
                  class="combat-option-btn fear-resist-btn"
                  [disabled]="c.fearResistUsedThisRound"
                  (click)="resistFear(c)"
                  matTooltip="Furcht abschütteln: Willenskraftprobe vs. {{ fearResistTn(c) }} — Erfolg beendet den Verängstigt-Effekt. 1×/Runde.">
                  <mat-icon>psychology</mat-icon><span class="btn-label">Furcht abschütteln</span></button>
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE' && session!.phase === 'ACTION' && hasAcrobaticDefenseTalent(c) && !c.defeated"
                  class="combat-option-btn acrobatic-btn"
                  (click)="openAcrobaticDefenseDialog(c)"
                  matTooltip="Akrobatische Verteidigung · Freie Aktion (GES + Rang vs. höchste KV, +2 KV/Erfolg, kostet 1 Schaden)">
                  <mat-icon>self_improvement</mat-icon><span class="btn-label">Akrob. Verteidigung</span></button>
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE' && session!.phase === 'ACTION' && hasCombatSenseTalent(c) && !c.defeated"
                  class="combat-option-btn combat-sense-btn"
                  (click)="openCombatSenseDialog(c)"
                  matTooltip="Kampfsinn · Freie Aktion (WAH + Rang vs. MV des Ziels, +2 KV &amp; +2 Angriff/Erfolg, kostet 1 Schaden)">
                  <mat-icon>visibility</mat-icon><span class="btn-label">Kampfsinn</span></button>
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE' && session!.phase === 'ACTION' && hasDistractTalent(c) && !c.defeated"
                  class="combat-option-btn distract-btn"
                  [disabled]="c.hasActedThisRound || !isActiveTurn(c)"
                  (click)="openDistractDialog(c)"
                  matTooltip="Ablenken (CHA + Rang vs. Soziale VK, −1 KV/Erfolg für beide, kostet 1 Schaden)">
                  <mat-icon>record_voice_over</mat-icon><span class="btn-label">Ablenken</span></button>
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE' && session!.phase === 'ACTION' && hasIronWillTalent(c) && !c.defeated"
                  class="combat-option-btn iron-will-btn"
                  (click)="openIronWillDialog(c)"
                  matTooltip="Eiserner Wille (WIL + Rang vs. Zauberwurf, freie Aktion, kostet 1 Schaden)">
                  <mat-icon>psychology</mat-icon><span class="btn-label">Eiserner Wille</span></button>
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE' && session!.phase === 'ACTION' && hasSpotArmorFlawTalent(c) && !c.defeated"
                  class="combat-option-btn spot-flaw-btn"
                  (click)="openSpotArmorFlawDialog(c)"
                  matTooltip="Schwachstelle erkennen (WAH + Rang vs. max(MV, Rüstung) — +2 Schaden/Erfolg gegen das Ziel für Rang Runden, kostet 1 Schaden, keine Hauptaktion)">
                  <mat-icon>biotech</mat-icon><span class="btn-label">Schwachstelle</span></button>
                <!-- Riposte: nur sichtbar wenn ein Angriff aussteht -->
                <button mat-raised-button *ngIf="session!.status === 'ACTIVE' && session!.phase === 'ACTION' && hasRiposteTalent(c) && c.pendingRiposteAttackTotal >= 0 && !c.defeated"
                  class="combat-option-btn riposte-btn"
                  (click)="openRiposteDialog(c)"
                  matTooltip="Riposte! Nahkampfangriff parieren und kontern (GES + Rang vs. Angriffswurf, kostet 2 Überanstrengung)">
                  <mat-icon>sports_martial_arts</mat-icon><span class="btn-label">Riposte</span></button>
                <!-- Manövrieren: freie Aktion -->
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE' && session!.phase === 'ACTION' && hasManoeuverTalent(c) && !c.defeated"
                  class="combat-option-btn manoeuver-btn"
                  (click)="openManoeuverDialog(c)"
                  matTooltip="Manövrieren (GES + Rang vs. KV des Ziels, +2 KV &amp; +2 Angriff/Erfolg, kostet 1 Überanstrengung) — freie Aktion">
                  <mat-icon>swap_horiz</mat-icon><span class="btn-label">Manövrieren</span></button>
                <!-- Tigersprung: freie Aktion, kein Würfelwurf -->
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE' && session!.phase === 'DECLARATION' && hasTigersprungTalent(c) && !c.defeated"
                  class="combat-option-btn tigersprung-btn"
                  [disabled]="c.tigersprungUsedThisRound"
                  (click)="performTigersprung(c)"
                  matTooltip="Tigersprung: +Rang auf Initiative — nur in der Ansagephase aktivierbar, einmal/Runde, kostet 1 Überanstrengung">
                  <mat-icon>bolt</mat-icon><span class="btn-label">Tigersprung</span></button>
                <!-- Lufttanz: freie Aktion in der Ansagephase, ermöglicht Bonusangriff -->
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE' && session!.phase === 'DECLARATION' && hasLufttanzTalent(c) && !c.defeated"
                  class="combat-option-btn lufttanz-btn"
                  [disabled]="c.lufttanzActivatedThisRound"
                  (click)="performLufttanz(c)"
                  matTooltip="Lufttanz: +Rang auf Initiative; bei Initiative-Vorsprung ≥10 ein Bonus-Nahkampfangriff. Ansagephase, 1×/Runde, kostet 2 Überanstrengung.">
                  <mat-icon>air</mat-icon><span class="btn-label">Lufttanz</span></button>
                <!-- Karma auf Initiative: Disziplin-Fähigkeit ab Kreis 3 -->
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE' && session!.phase === 'DECLARATION' && canUseKarmaInitiative(c) && !c.defeated"
                  class="combat-option-btn karma-init-btn"
                  [class.active]="c.karmaInitiativeThisRound"
                  [disabled]="!c.karmaInitiativeThisRound && c.currentKarma <= 0"
                  (click)="toggleKarmaInitiative(c)"
                  matTooltip="Karma auf Initiative: 1 Karma → +W6 (Stufe 4) auf den Initiativewurf dieser Runde. Disziplin-Fähigkeit ab dem 3. Kreis.">
                  <mat-icon>auto_awesome</mat-icon><span class="btn-label">{{ c.karmaInitiativeThisRound ? 'Karma-Init ✓' : 'Karma-Init' }}</span></button>
                <!-- Lufttanz-Bonusangriff: nur wenn pending -->
                <button mat-raised-button color="warn"
                  *ngIf="session!.status === 'ACTIVE' && session!.phase === 'ACTION' && c.pendingLufttanzTargetId >= 0 && !c.defeated"
                  class="combat-option-btn lufttanz-attack-btn"
                  (click)="openLufttanzAttackDialog(c)"
                  matTooltip="Lufttanz-Zusatzangriff verfügbar! Gleiche Waffe wie der auslösende Angriff.">
                  <mat-icon>air</mat-icon><span class="btn-label">Lufttanz-Angriff</span></button>
                <!-- Blattschuss: pending Karma-Nachschuss -->
                <button mat-raised-button color="primary"
                  *ngIf="session!.status === 'ACTIVE' && session!.phase === 'ACTION' && c.pendingBlattschussDefenderId >= 0 && !c.defeated"
                  class="combat-option-btn blattschuss-pending-btn"
                  [disabled]="c.currentKarma <= 0"
                  (click)="resumeBlattschuss(c)"
                  matTooltip="Blattschuss: Karma nachschießen. {{ c.pendingBlattschussKarmaUsed }}/{{ c.pendingBlattschussRank }} eingesetzt, Wurf-Total {{ c.pendingBlattschussTotal }} vs VK {{ c.pendingBlattschussDefense }}.">
                  <mat-icon>track_changes</mat-icon><span class="btn-label">Blattschuss</span></button>
                <!-- Zweitwaffe: zweiter Angriff -->
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE' && session!.phase === 'ACTION' && hasZweitwaffeTalent(c) && !c.defeated"
                  class="combat-option-btn zweitwaffe-btn"
                  [disabled]="c.zweitWaffeUsedThisRound"
                  (click)="openZweitwaffeDialog(c)"
                  matTooltip="Zweitwaffe: zweiter Nahkampfangriff (GES + Rang vs. KV, kostet 1 Überanstrengung) — freie Aktion, 1×/Runde">
                  <mat-icon>join_full</mat-icon><span class="btn-label">Zweitwaffe</span></button>
                <!-- Nachtreten: zusätzlicher waffenloser Angriff -->
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE' && session!.phase === 'ACTION' && hasNachtretenTalent(c) && !c.defeated"
                  class="combat-option-btn nachtreten-btn"
                  [disabled]="c.nachtretenUsedThisRound"
                  (click)="openNachtretenDialog(c)"
                  matTooltip="Nachtreten: zusätzlicher waffenloser Angriff (GES + Rang vs. KV, waffenloser STR-Schaden). Einfache Aktion, 1×/Runde, kostet 1 Überanstrengung. Nur gegen Ziele mit niedrigerer Initiative.">
                  <mat-icon>sports_martial_arts</mat-icon><span class="btn-label">Nachtreten</span></button>
                <!-- Schwanzangriff: T'skrang-Rassenfähigkeit -->
                <button mat-stroked-button *ngIf="session!.status === 'ACTIVE' && session!.phase === 'ACTION' && isTskrang(c) && !c.defeated"
                  class="combat-option-btn schwanzangriff-btn"
                  [disabled]="c.schwanzangriffUsedThisRound"
                  (click)="openSchwanzangriffDialog(c)"
                  matTooltip="Schwanzangriff (T'skrang): zusätzlicher waffenloser Angriff mit dem Schwanz (Waffenloser Kampf vs. KV, STR-Schaden, optional Schwanzwaffe). 1×/Runde. −2 auf alle Proben in dieser Runde.">
                  <mat-icon>pets</mat-icon><span class="btn-label">Schwanzangriff</span></button>
                <button mat-stroked-button *ngIf="session!.status === 'SETUP'"
                  class="combat-option-btn remove-btn"
                  (click)="removeCombatant(c.id)" matTooltip="Kombattant entfernen">
                  <mat-icon>close</mat-icon><span class="btn-label">Entfernen</span>
                </button>
              </div>
            </div>
        </ng-template>

        <!-- Right: Log -->
        <div class="log-panel" [class.collapsed]="!logOpen">
          <div class="log-toggle" (click)="logOpen = !logOpen" [matTooltip]="logOpen ? 'Protokoll ausblenden' : 'Protokoll einblenden'">
            {{ logOpen ? '◀' : '▶' }}&nbsp;Protokoll
          </div>
          <div class="log-content" *ngIf="logOpen">
            <div class="section-title" style="margin-top:16px">Kampfprotokoll</div>
            <div class="log-scroll">
              <div
                *ngFor="let entry of logEntries"
                [class]="'combat-log-entry ' + (entry.success ? 'success' : isSystem(entry.actionType) ? 'system' : 'failure')">
                <span class="round-badge">R{{ entry.round }}</span>
                {{ entry.description }}
                <div class="log-roll-details" *ngIf="entry.details as d"
                     style="margin-top:4px;font-size:0.78rem;font-family:monospace;color:#bdb2a0;border-left:2px solid #4a4030;padding-left:6px">
                  <div>
                    <span style="color:#c9a84c">Angriff</span> St {{ d.attackStep }}:
                    <ng-container *ngFor="let die of d.attackRoll?.dice; let i = index"><span *ngIf="i>0"> + </span>[{{ die.rolls.join('+') }}<span *ngIf="die.exploded">★</span>]</ng-container>
                    = <strong style="color:#e0d5c0">{{ d.attackRoll?.total }}</strong><span *ngIf="d.attackKarma"> +Karma[{{ d.attackKarma.dice[0].rolls.join('+') }}]={{ d.attackKarma.total }}</span>
                    vs VK {{ d.defenseValue }}
                  </div>
                  <div *ngIf="d.attackMods?.length" style="color:#9aa0a6">↳ {{ d.attackMods.join(' · ') }}</div>
                  <ng-container *ngIf="d.hit && !d.pendingRiposte && !d.pendingDodge">
                    <div>
                      <span style="color:#ef9a9a">Schaden</span> St {{ d.damageStep }}:
                      <ng-container *ngFor="let die of d.damageRoll?.dice; let i = index"><span *ngIf="i>0"> + </span>[{{ die.rolls.join('+') }}<span *ngIf="die.exploded">★</span>]</ng-container>
                      = {{ d.damageRoll?.total }}<span *ngIf="d.damageKarma"> +Karma {{ d.damageKarma.total }}</span>
                      − {{ d.armor }} Rüst = <strong style="color:#e0d5c0">{{ d.netDamage }}</strong><span *ngIf="d.extraSuccesses > 0" style="color:#9aa0a6"> ({{ d.extraSuccesses }} Übererfolg)</span>
                      <span *ngIf="d.woundDealt" style="color:#ef5350;font-weight:700"> · WUNDE</span>
                    </div>
                    <div *ngIf="d.damageMods?.length" style="color:#9aa0a6">↳ {{ d.damageMods.join(' · ') }}</div>
                  </ng-container>
                  <div *ngIf="d.strain" style="color:#ce93d8">Strain: {{ d.strain }} Überanstrengung</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Result Modal -->
    <div class="result-modal" *ngIf="resultModal.open">
      <div class="dialog-backdrop" (click)="dismissAutofightModal(resultModal)"></div>
      <div class="dialog-box result-box" *ngIf="resultModal.result as r">
        <div class="result-outcome" [class.hit]="r.hit" [class.miss]="!r.hit">
          <mat-icon>{{ r.hit ? 'gps_fixed' : 'close' }}</mat-icon>
          {{ r.hit ? 'TREFFER' : 'VERFEHLT' }}
        </div>
        <div class="result-names">
          <span class="result-actor" [style.color]="nameColor(r.actorName)">{{ r.actorName }}</span>
          <mat-icon style="color:#555;font-size:18px">arrow_forward</mat-icon>
          <span class="result-target" [style.color]="nameColor(r.targetName)">{{ r.targetName }}</span>
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
                <span class="roll-big-target">KV {{ r.defenseValue }}</span>
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
            <div class="roll-effect-notes" *ngIf="r.defenseNotes?.length">
              <span class="effect-note defense-note" *ngFor="let note of r.defenseNotes">🛡 {{ note }}</span>
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
                  <span class="step-calc">
                    ({{ r.damageStrengthStep ?? 0 }} STR<span *ngIf="(r.damageWeaponBonus ?? 0) !== 0">
                      + {{ r.damageWeaponBonus }}<span *ngIf="r.damageWeaponName"> {{ r.damageWeaponName }}</span></span><span *ngIf="(r.damageWoundPenalty ?? 0) > 0">
                      − {{ r.damageWoundPenalty }} Wunden</span><span *ngIf="r.extraSuccesses && r.extraSuccesses > 0">
                      + {{ r.extraSuccesses * 2 }} Übererfolge</span>)
                  </span>
                </span>
                <div class="roll-block-totals">
                  <span class="roll-big-total" style="color:#ef5350">{{ r.damageRoll!.total + (r.damageKarmaRoll?.total ?? 0) }}</span>
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
                <div class="die-mini karma-die" *ngIf="r.damageKarmaRoll" matTooltip="Karma auf Schaden (Krallenhand)">
                  <span class="die-mini-sides">Karma W6</span>
                  <span class="die-mini-rolls">{{ r.damageKarmaRoll.dice[0].rolls.join(' + ') }}<span *ngIf="r.damageKarmaRoll.exploded"> 💥</span></span>
                </div>
              </div>
              <div class="roll-effect-notes" *ngIf="r.damageBonusNotes?.length">
                <span class="effect-note damage-note" *ngFor="let note of r.damageBonusNotes">✦ {{ note }}</span>
              </div>
            </div>
            <div class="wound-banner" *ngIf="r.woundDealt">
              <mat-icon>bolt</mat-icon>
              {{ r.newWounds }} WUNDE{{ (r.newWounds ?? 0) > 1 ? 'N' : '' }} erlitten!
              Gesamt: {{ r.totalWounds }} · WS {{ r.woundThreshold }}
            </div>
            <div class="defeat-banner" *ngIf="r.targetDefeated">
              <mat-icon>dangerous</mat-icon> {{ r.targetName }} ist bewusstlos!
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
        <!-- Reaktions-Prompt (Riposte und/oder Ausweichen) -->
        <div class="dodge-prompt" *ngIf="r.hitPendingRiposte && r.hitPendingDodge"
             style="border-color:#b39ddb;color:#b39ddb">
          <mat-icon>shield</mat-icon>
          Ziel kann Riposte oder Ausweichen wählen
        </div>
        <div class="dodge-prompt" *ngIf="r.hitPendingRiposte && !r.hitPendingDodge" style="border-color:#ff8a65;color:#ff8a65">
          <mat-icon>sports_martial_arts</mat-icon>
          Ziel kann Riposte versuchen (2 Überanstrengung)
        </div>
        <div class="dodge-prompt" *ngIf="r.hitPendingDodge && !r.hitPendingRiposte">
          <mat-icon>directions_run</mat-icon>
          Ziel kann Ausweichen versuchen
        </div>
        <div class="dodge-prompt" *ngIf="r.shieldStowedName"
             style="border-color:#ffb74d;color:#ffb74d">
          <mat-icon>shield</mat-icon>
          Schild abgelegt: {{ r.shieldStowedName }} — zweihändige Waffe
        </div>
        <div class="dodge-prompt" *ngIf="r.shieldRestoredName"
             style="border-color:#80cbc4;color:#80cbc4">
          <mat-icon>shield</mat-icon>
          Schild wieder angelegt: {{ r.shieldRestoredName }} (einhändige Waffe)
        </div>
        <div class="dodge-prompt" *ngIf="r.lufttanzBonusReady"
             style="border-color:#29b6f6;color:#29b6f6">
          <mat-icon>air</mat-icon>
          Lufttanz-Zusatzangriff verfügbar! (Initiative-Vorsprung +{{ r.lufttanzInitiativeDiff }} ≥ 10)
        </div>
        <div class="dodge-prompt" *ngIf="r.blattschussCanAddKarma"
             style="border-color:#a5d6a7;color:#a5d6a7">
          <mat-icon>track_changes</mat-icon>
          Blattschuss: Fehlschlag — weiteres Karma nachschießen?
          ({{ r.blattschussKarmaUsed ?? 0 }}/{{ r.blattschussRank }} eingesetzt)
        </div>
        <div *ngIf="r.blattschussCanAddKarma" style="display:flex;gap:8px;margin-top:8px">
          <button mat-raised-button color="primary" style="flex:1"
                  [disabled]="(blattschussActor()?.currentKarma ?? 0) <= 0"
                  (click)="addBlattschussKarma()">
            <mat-icon>auto_awesome</mat-icon>
            +1 Karma einsetzen ({{ blattschussActor()?.currentKarma ?? 0 }} verfügbar)
          </button>
          <button mat-stroked-button (click)="dismissModal()">Aufgeben</button>
        </div>
        <div style="display:flex;gap:8px;margin-top:16px;flex-wrap:wrap" *ngIf="r.hitPendingRiposte || r.hitPendingDodge; else closeOnly">
          <button mat-stroked-button style="flex:1;min-width:140px"
                  (click)="r.hitPendingDodge ? skipDodge() : skipRiposte()">
            Schaden annehmen
          </button>
          <button mat-raised-button color="primary" style="flex:1;min-width:140px"
                  *ngIf="r.hitPendingDodge"
                  (click)="openDodgeDialog()">
            <mat-icon>directions_run</mat-icon> Ausweichen
          </button>
          <button mat-raised-button color="accent" style="flex:1;min-width:140px"
                  *ngIf="r.hitPendingRiposte"
                  (click)="openRiposteDialogFromResult()">
            <mat-icon>sports_martial_arts</mat-icon> Riposte
          </button>
        </div>
        <ng-template #closeOnly>
          <button mat-raised-button style="width:100%;margin-top:16px" (click)="dismissAutofightModal(resultModal)">
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
      <div class="dialog-backdrop" (click)="dismissAutofightModal(dodgeModal)"></div>
      <div class="dialog-box result-box" *ngIf="dodgeModal.result as r">
        <div class="result-outcome" [class.hit]="r.success" [class.miss]="!r.success">
          <mat-icon>{{ r.success ? 'directions_run' : 'close' }}</mat-icon>
          {{ r.success ? 'AUSGEWICHEN' : 'NICHT AUSGEWICHEN' }}
        </div>
        <div class="result-names">
          <span class="result-actor" [style.color]="nameColor(r.defenderName)">{{ r.defenderName }}</span>
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
              <mat-icon>dangerous</mat-icon> {{ r.defenderName }} ist bewusstlos!
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
        <button mat-raised-button style="width:100%;margin-top:16px" (click)="dismissAutofightModal(dodgeModal)">
          Schließen
        </button>
      </div>
    </div>

    <!-- Riposte Dialog -->
    <div class="attack-dialog" *ngIf="riposteDialog.open">
      <div class="dialog-backdrop" (click)="closeRiposteDialog()"></div>
      <div class="dialog-box">
        <h3><mat-icon style="vertical-align:middle;margin-right:6px;color:#ff8a65">sports_martial_arts</mat-icon>Riposte: {{ riposteDialog.defender?.character?.name }}</h3>
        <div style="color:#888;font-size:0.85rem;margin-bottom:12px">
          Stufe: GES + Rang vs. Angriff <strong style="color:#fff">{{ riposteDialog.attackTotal }}</strong> · Kostet 2 Überanstrengung
        </div>
        <div style="display:flex;gap:8px;align-items:center">
          <label class="karma-toggle"
            [class.active]="riposteDialog.spendKarma"
            [class.disabled]="(riposteDialog.defender?.currentKarma ?? 0) <= 0"
            (click)="(riposteDialog.defender?.currentKarma ?? 0) > 0 && (riposteDialog.spendKarma = !riposteDialog.spendKarma)">
            <mat-icon>auto_awesome</mat-icon> Karma
            <span class="karma-count-badge" [class.empty]="(riposteDialog.defender?.currentKarma ?? 0) <= 0">{{ riposteDialog.defender?.currentKarma ?? 0 }}</span>
          </label>
        </div>
        <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:8px">
          <button mat-stroked-button (click)="closeRiposteDialog(); skipRiposte()">Schaden annehmen</button>
          <button mat-raised-button color="accent" (click)="performRiposte()">
            <mat-icon>sports_martial_arts</mat-icon> Riposte würfeln
          </button>
        </div>
      </div>
    </div>

    <!-- Riposte Result Modal -->
    <div class="result-modal" *ngIf="riposteModal.open">
      <div class="dialog-backdrop" (click)="riposteModal.open = false"></div>
      <div class="dialog-box result-box" *ngIf="riposteModal.result as r">
        <div class="result-outcome" [class.hit]="r.success" [class.miss]="!r.success">
          <mat-icon>{{ r.success ? 'sports_martial_arts' : (r.riposteAttempted ? 'close' : 'arrow_downward') }}</mat-icon>
          {{ r.success ? 'PARIERT!' : (r.riposteAttempted ? 'RIPOSTE FEHLGESCHLAGEN' : 'SCHADEN ANGENOMMEN') }}
        </div>
        <div *ngIf="r.success && r.counterAttack" class="result-subtitle"
             [style.color]="r.counterAttackHit ? '#ff8a65' : '#888'">
          + Gegenangriff{{ r.counterAttackHit ? '' : ' verfehlt' }}
        </div>
        <div class="result-names">
          <span class="result-actor" [style.color]="nameColor(r.defenderName)">{{ r.defenderName }}</span>
          <span style="color:#888;margin:0 6px;font-size:0.85rem">vs Angriff {{ r.attackTotal }}</span>
        </div>
        <div class="result-rolls" *ngIf="r.riposteAttempted && r.riposteRoll">
          <div class="roll-block">
            <div class="roll-block-header">
              <span class="roll-block-label">Riposte · Step {{ r.riposteStep }}</span>
              <div class="roll-block-totals">
                <span class="roll-big-total">{{ r.riposteRoll.total + (r.karmaRoll?.total ?? 0) }}</span>
                <span class="roll-big-vs">vs</span>
                <span class="roll-big-target">{{ r.attackTotal }}</span>
              </div>
            </div>
          </div>
          <ng-container *ngIf="r.counterAttack && r.counterAttackHit">
            <div class="roll-divider"></div>
            <div class="roll-row" style="background:rgba(255,138,101,0.1)">
              <span class="roll-label">Gegenangriff</span>
              <span class="roll-expr">{{ r.counterAttackTotal }} vs KV {{ r.counterArmorValue }}</span>
              <span class="roll-value" style="color:#ff8a65">−{{ r.counterNetDamage }} Schaden</span>
            </div>
          </ng-container>
          <div class="roll-row" style="background:rgba(239,83,80,0.08)">
            <span class="roll-label">Kosten</span>
            <span class="roll-expr">Überanstrengung</span>
            <span class="roll-value" style="color:#ef5350">−2</span>
          </div>
          <!-- Erhaltener Schaden bei Fehlschlag -->
          <div class="roll-row" *ngIf="!r.success && (r.incomingNetDamage ?? 0) > 0"
               style="background:rgba(239,83,80,0.12);margin-top:4px">
            <span class="roll-label">Treffer</span>
            <span class="roll-expr">Schaden erhalten</span>
            <span class="roll-value" style="color:#ef5350">−{{ r.incomingNetDamage }}</span>
          </div>
        </div>
        <!-- Schaden bei Verzicht (kein Riposte-Versuch) -->
        <div class="result-rolls" *ngIf="!r.riposteAttempted && (r.incomingNetDamage ?? 0) > 0">
          <div class="roll-row" style="background:rgba(239,83,80,0.12)">
            <span class="roll-label">Treffer</span>
            <span class="roll-expr">Schaden erhalten</span>
            <span class="roll-value" style="color:#ef5350">−{{ r.incomingNetDamage }}</span>
          </div>
        </div>
        <div style="color:#aaa;font-size:0.82rem;margin-top:8px">{{ r.description }}</div>
        <button mat-raised-button style="width:100%;margin-top:16px" (click)="riposteModal.open = false">Schließen</button>
      </div>
    </div>

    <!-- Manövrieren Dialog -->
    <div class="attack-dialog" *ngIf="manoeuverDialog.open">
      <div class="dialog-backdrop" (click)="manoeuverDialog.open = false"></div>
      <div class="dialog-box">
        <h3><mat-icon style="vertical-align:middle;margin-right:6px;color:#80deea">swap_horiz</mat-icon>Manövrieren: {{ manoeuverDialog.actor?.character?.name }}</h3>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Ziel</mat-label>
          <mat-select [(ngModel)]="manoeuverDialog.targetId">
            <mat-option *ngFor="let c of possibleTargets(manoeuverDialog.actor)" [value]="c.id">{{ cn(c) }}</mat-option>
          </mat-select>
        </mat-form-field>
        <div style="color:#888;font-size:0.85rem;margin-bottom:12px">GES + Rang vs. KV — Pro Erfolg: +2 KV &amp; +2 auf nächsten Angriff</div>
        <div style="display:flex;gap:8px;align-items:center">
          <label class="karma-toggle"
            [class.active]="manoeuverDialog.spendKarma"
            [class.disabled]="(manoeuverDialog.actor?.currentKarma ?? 0) <= 0"
            (click)="(manoeuverDialog.actor?.currentKarma ?? 0) > 0 && (manoeuverDialog.spendKarma = !manoeuverDialog.spendKarma)">
            <mat-icon>auto_awesome</mat-icon> Karma
            <span class="karma-count-badge" [class.empty]="(manoeuverDialog.actor?.currentKarma ?? 0) <= 0">{{ manoeuverDialog.actor?.currentKarma ?? 0 }}</span>
          </label>
        </div>
        <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:8px">
          <button mat-stroked-button (click)="manoeuverDialog.open = false">Abbrechen</button>
          <button mat-raised-button color="primary" [disabled]="!manoeuverDialog.targetId" (click)="performManoeuver()">
            <mat-icon>swap_horiz</mat-icon> Würfeln
          </button>
        </div>
      </div>
    </div>

    <!-- Manövrieren Result Modal -->
    <div class="result-modal" *ngIf="manoeuverModal.open">
      <div class="dialog-backdrop" (click)="dismissModal()"></div>
      <div class="dialog-box result-box" *ngIf="manoeuverModal.result as r">
        <div class="result-outcome" [class.hit]="r.success" [class.miss]="!r.success">
          <mat-icon>{{ r.success ? 'swap_horiz' : 'close' }}</mat-icon>
          {{ r.success ? 'MANÖVER GELINGT! +' + r.defenseBonus + ' KV / +' + r.attackBonus + ' Angriff' : 'MANÖVER FEHLGESCHLAGEN' }}
        </div>
        <div class="result-names">
          <span class="result-actor" [style.color]="nameColor(r.actorName)">{{ r.actorName }}</span>
          <span style="color:#888;margin:0 6px">→</span>
          <span class="result-actor" [style.color]="nameColor(r.targetName)">{{ r.targetName }}</span>
        </div>
        <div class="result-rolls">
          <div class="roll-block">
            <div class="roll-block-header">
              <span class="roll-block-label">Manövrieren · Step {{ r.rollStep }}</span>
              <div class="roll-block-totals">
                <span class="roll-big-total">{{ r.roll.total + (r.karmaRoll?.total ?? 0) }}</span>
                <span class="roll-big-vs">vs KV</span>
                <span class="roll-big-target">{{ r.defenseValue }}</span>
              </div>
            </div>
          </div>
          <div class="roll-row" style="background:rgba(128,222,234,0.08)">
            <span class="roll-label">Erfolge</span>
            <span class="roll-expr">{{ r.successes }} × +2</span>
            <span class="roll-value" style="color:#80deea">+{{ r.defenseBonus }} KV / +{{ r.attackBonus }} Ang.</span>
          </div>
          <div class="roll-row" style="background:rgba(239,83,80,0.08)">
            <span class="roll-label">Kosten</span>
            <span class="roll-expr">Überanstrengung</span>
            <span class="roll-value" style="color:#ef5350">−1</span>
          </div>
        </div>
        <button mat-raised-button style="width:100%;margin-top:16px" (click)="dismissModal()">Schließen</button>
      </div>
    </div>

    <!-- Initiative Roll Modal — alle Kombattanten in einer Übersicht -->
    <div class="result-modal" *ngIf="initiativeModal.open">
      <div class="dialog-backdrop" (click)="dismissModal()"></div>
      <div class="dialog-box result-box initiative-modal-box">
        <div class="result-outcome hit">
          <mat-icon>casino</mat-icon>
          INITIATIVE — Runde {{ initiativeModal.round }}
        </div>
        <div class="initiative-list">
          <div class="initiative-row" *ngFor="let r of initiativeModal.rolls; let i = index">
            <div class="init-order">#{{ i + 1 }}</div>
            <div class="init-info">
              <div class="init-name" [style.color]="nameColor(r.combatantName)">
                {{ r.combatantName }}
                <span class="init-tag" [class.npc]="r.npc" [class.hero]="!r.npc">{{ r.npc ? 'NPC' : 'Held' }}</span>
              </div>
              <div class="init-step">Stufe {{ r.step }} ({{ r.roll.diceExpression }})</div>
              <div class="dice-breakdown-mini init-dice">
                <div class="die-mini" *ngFor="let d of r.roll.dice" [class.exploded]="d.exploded">
                  <span class="die-mini-sides">W{{ d.sides }}</span>
                  <span class="die-mini-rolls">{{ d.rolls.join(' + ') }}<span *ngIf="d.rolls.length > 1" class="die-mini-sum"> = {{ d.total }}</span></span>
                  <span *ngIf="d.exploded" class="explode-mini">💥</span>
                </div>
                <div class="die-mini" *ngIf="r.karmaRoll" style="border-color:#d4b85a" [class.exploded]="r.karmaRoll.exploded">
                  <span class="die-mini-sides" style="color:#d4b85a">★ W{{ r.karmaRoll.dice[0].sides }}</span>
                  <span class="die-mini-rolls">{{ r.karmaRoll.dice[0].rolls.join(' + ') }}<span *ngIf="r.karmaRoll.dice[0].rolls.length > 1" class="die-mini-sum"> = {{ r.karmaRoll.total }}</span></span>
                  <span *ngIf="r.karmaRoll.exploded" class="explode-mini">💥</span>
                </div>
              </div>
              <div class="init-effects" *ngIf="r.bonusNotes?.length">
                <span class="init-effect-chip" *ngFor="let n of r.bonusNotes">✦ {{ n }}</span>
              </div>
            </div>
            <div class="init-total">{{ r.total }}</div>
          </div>
        </div>
        <button mat-raised-button color="primary" style="width:100%;margin-top:16px"
                (click)="dismissModal()">
          Auf zur Aktion!
        </button>
      </div>
    </div>

    <!-- Kampf beendet Modal (synchronisiert an alle Clients) -->
    <div class="result-modal" *ngIf="combatEndedModal.open">
      <div class="dialog-backdrop" (click)="dismissModal()"></div>
      <div class="dialog-box result-box" style="text-align:center">
        <h3 style="justify-content:center"><mat-icon style="vertical-align:middle;margin-right:6px;color:#ef9a9a">flag</mat-icon>Kampf beendet</h3>
        <p style="color:#ccc;margin:8px 0 4px">
          „{{ combatEndedModal.name }}" wurde nach {{ combatEndedModal.round }} Runde(n) beendet.
        </p>
        <p style="color:#999;font-size:0.85rem;margin:0 0 8px">
          Schaden, Wunden und Karma aller Charaktere wurden auf die Datenblätter übertragen.
        </p>
        <div style="display:flex;gap:8px;margin-top:12px">
          <button mat-stroked-button style="flex:1" (click)="dismissModal()">Schließen</button>
          <button mat-raised-button color="primary" style="flex:1" (click)="dismissModal(); router.navigate(['/combat'])">
            <mat-icon>list</mat-icon> Zur Übersicht
          </button>
        </div>
      </div>
    </div>

    <!-- Tigersprung Result Modal -->
    <div class="result-modal" *ngIf="tigersprungModal.open">
      <div class="dialog-backdrop" (click)="dismissModal()"></div>
      <div class="dialog-box result-box" *ngIf="tigersprungModal.result as r">
        <div class="result-outcome hit">
          <mat-icon>bolt</mat-icon>
          TIGERSPRUNG! +{{ r.initiativeBonus }} Initiative
        </div>
        <div class="result-names">
          <span class="result-actor" [style.color]="nameColor(r.actorName)">{{ r.actorName }}</span>
        </div>
        <div class="result-rolls">
          <div class="roll-row" style="background:rgba(255,204,0,0.08)">
            <span class="roll-label">Initiative</span>
            <span class="roll-expr">+{{ r.initiativeBonus }} (Rang {{ r.rank }})</span>
            <span class="roll-value" style="color:#ffcc00">{{ r.newInitiative }}</span>
          </div>
          <div class="roll-row" style="background:rgba(239,83,80,0.08)">
            <span class="roll-label">Kosten</span>
            <span class="roll-expr">Überanstrengung</span>
            <span class="roll-value" style="color:#ef5350">−1</span>
          </div>
        </div>
        <button mat-raised-button style="width:100%;margin-top:16px" (click)="dismissModal()">Schließen</button>
      </div>
    </div>

    <!-- Lufttanz Activation Result Modal -->
    <div class="result-modal" *ngIf="lufttanzModal.open">
      <div class="dialog-backdrop" (click)="dismissModal()"></div>
      <div class="dialog-box result-box" *ngIf="lufttanzModal.result as r">
        <div class="result-outcome hit">
          <mat-icon>air</mat-icon>
          LUFTTANZ AKTIVIERT! +{{ r.initiativeBonus }} Initiative
        </div>
        <div class="result-names">
          <span class="result-actor" [style.color]="nameColor(r.actorName)">{{ r.actorName }}</span>
        </div>
        <div class="result-rolls">
          <div class="roll-row" style="background:rgba(179,229,252,0.08)">
            <span class="roll-label">Initiative-Bonus</span>
            <span class="roll-expr">Rang {{ r.rank }} (Lufttanzstufe = GES + {{ r.rank }})</span>
            <span class="roll-value" style="color:#b3e5fc">+{{ r.initiativeBonus }}</span>
          </div>
          <div class="roll-row" style="background:rgba(129,199,132,0.08)">
            <span class="roll-label">Bonus-Angriff</span>
            <span class="roll-expr">bei Initiative-Vorsprung ≥10 nach Nahkampftreffer</span>
            <span class="roll-value" style="color:#81c784">möglich</span>
          </div>
          <div class="roll-row" style="background:rgba(239,83,80,0.08)">
            <span class="roll-label">Kosten</span>
            <span class="roll-expr">Überanstrengung</span>
            <span class="roll-value" style="color:#ef5350">−{{ r.damageTaken }}</span>
          </div>
        </div>
        <button mat-raised-button style="width:100%;margin-top:16px" (click)="dismissModal()">Schließen</button>
      </div>
    </div>

    <!-- Lufttanz Bonusangriff Dialog -->
    <div class="attack-dialog" *ngIf="lufttanzAttackDialog.open">
      <div class="dialog-backdrop" (click)="lufttanzAttackDialog.open = false"></div>
      <div class="dialog-box">
        <h3><mat-icon style="vertical-align:middle;margin-right:6px;color:#29b6f6">air</mat-icon>Lufttanz-Zusatzangriff: {{ lufttanzAttackDialog.actor?.character?.name }}</h3>
        <div style="color:#888;font-size:0.85rem;margin-bottom:12px">
          Zusatz-Nahkampfangriff mit derselben Waffe wie der auslösende Angriff.
          Verbraucht keine Hauptaktion, keine zusätzlichen Kosten.
        </div>
        <div *ngIf="lufttanzAttackDialog.actor as a" style="background:#1a1410;border:1px solid #2a2218;border-radius:6px;padding:8px;margin-bottom:8px;font-size:0.85rem">
          <div>Ziel: <strong>{{ cn(lufttanzTarget(a)!) }}</strong> (KV {{ pd(lufttanzTarget(a)!) }})</div>
          <div>Waffe: <strong>{{ lufttanzWeaponName(a) }}</strong></div>
        </div>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Bonusstufen</mat-label>
          <input matInput type="number" [(ngModel)]="lufttanzAttackDialog.bonusSteps" min="0">
        </mat-form-field>
        <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">
          <label class="karma-toggle"
            [class.active]="lufttanzAttackDialog.spendKarma"
            [class.disabled]="(lufttanzAttackDialog.actor?.currentKarma ?? 0) <= 0"
            (click)="(lufttanzAttackDialog.actor?.currentKarma ?? 0) > 0 && (lufttanzAttackDialog.spendKarma = !lufttanzAttackDialog.spendKarma)">
            <mat-icon>auto_awesome</mat-icon>
            Karma (Angriff)
            <span class="karma-count-badge" [class.empty]="(lufttanzAttackDialog.actor?.currentKarma ?? 0) <= 0">
              {{ lufttanzAttackDialog.actor?.currentKarma ?? 0 }}
            </span>
          </label>
          <label class="karma-toggle"
            *ngIf="lufttanzAttackDialog.actor && isLufttanzClawWeapon(lufttanzAttackDialog.actor)"
            [class.active]="lufttanzAttackDialog.spendKarmaForDamage"
            [class.disabled]="(lufttanzAttackDialog.actor?.currentKarma ?? 0) <= (lufttanzAttackDialog.spendKarma ? 1 : 0)"
            (click)="toggleKarmaForDamage(lufttanzAttackDialog)"
            matTooltip="Krallenhand: zusätzliches Karma auf den Schadenswurf">
            <mat-icon>local_fire_department</mat-icon>
            Karma (Schaden)
          </label>
        </div>
        <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:8px">
          <button mat-stroked-button (click)="lufttanzAttackDialog.open = false">Abbrechen</button>
          <button mat-raised-button color="warn" (click)="performLufttanzAttack()">
            <mat-icon>air</mat-icon> Zuschlagen
          </button>
        </div>
      </div>
    </div>

    <!-- Zweitwaffe Dialog -->
    <div class="attack-dialog" *ngIf="zweitwaffeDialog.open">
      <div class="dialog-backdrop" (click)="zweitwaffeDialog.open = false"></div>
      <div class="dialog-box">
        <h3><mat-icon style="vertical-align:middle;margin-right:6px;color:#ce93d8">join_full</mat-icon>Zweitwaffe: {{ zweitwaffeDialog.actor?.character?.name }}</h3>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Ziel</mat-label>
          <mat-select [(ngModel)]="zweitwaffeDialog.defenderId">
            <mat-option *ngFor="let c of possibleTargets(zweitwaffeDialog.actor)" [value]="c.id">{{ cn(c) }}</mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Nebenhand-Waffe (optional)</mat-label>
          <mat-select [(ngModel)]="zweitwaffeDialog.weaponId">
            <mat-option [value]="null">Keine Waffe</mat-option>
            <mat-option *ngFor="let e of weaponsOf(zweitwaffeDialog.actor)" [value]="e.id">{{ e.name }} (+{{ e.damageBonus }})</mat-option>
          </mat-select>
        </mat-form-field>
        <div style="color:#888;font-size:0.85rem;margin-bottom:12px">GES + Rang vs. KV — Kostet 1 Überanstrengung, einmal/Runde</div>
        <div style="display:flex;gap:8px;align-items:center">
          <label class="karma-toggle"
            [class.active]="zweitwaffeDialog.spendKarma"
            [class.disabled]="(zweitwaffeDialog.actor?.currentKarma ?? 0) <= 0"
            (click)="(zweitwaffeDialog.actor?.currentKarma ?? 0) > 0 && (zweitwaffeDialog.spendKarma = !zweitwaffeDialog.spendKarma)">
            <mat-icon>auto_awesome</mat-icon> Karma
            <span class="karma-count-badge" [class.empty]="(zweitwaffeDialog.actor?.currentKarma ?? 0) <= 0">{{ zweitwaffeDialog.actor?.currentKarma ?? 0 }}</span>
          </label>
        </div>
        <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:8px">
          <button mat-stroked-button (click)="zweitwaffeDialog.open = false">Abbrechen</button>
          <button mat-raised-button color="primary" [disabled]="!zweitwaffeDialog.defenderId" (click)="performZweitwaffe()">
            <mat-icon>join_full</mat-icon> Angreifen
          </button>
        </div>
      </div>
    </div>

    <!-- Nachtreten Dialog -->
    <div class="attack-dialog" *ngIf="nachtretenDialog.open">
      <div class="dialog-backdrop" (click)="nachtretenDialog.open = false"></div>
      <div class="dialog-box">
        <h3><mat-icon style="vertical-align:middle;margin-right:6px;color:#ffb74d">sports_martial_arts</mat-icon>Nachtreten: {{ nachtretenDialog.actor?.character?.name }}</h3>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Ziel (nur niedrigere Initiative)</mat-label>
          <mat-select [(ngModel)]="nachtretenDialog.defenderId">
            <mat-option *ngFor="let c of nachtretenTargets(nachtretenDialog.actor)" [value]="c.id">
              {{ cn(c) }} (Ini {{ c.initiative }})
            </mat-option>
          </mat-select>
        </mat-form-field>
        <div style="color:#888;font-size:0.85rem;margin-bottom:12px">
          GES + Rang vs. KV — waffenloser STR-Schaden. Einfache Aktion, kostet 1 Überanstrengung, einmal/Runde.
          <span *ngIf="!nachtretenTargets(nachtretenDialog.actor).length" style="color:#ef9a9a;display:block;margin-top:4px">
            Kein Ziel mit niedrigerer Initiative als {{ nachtretenDialog.actor?.initiative }}.
          </span>
        </div>
        <div style="display:flex;gap:8px;align-items:center">
          <label class="karma-toggle"
            [class.active]="nachtretenDialog.spendKarma"
            [class.disabled]="(nachtretenDialog.actor?.currentKarma ?? 0) <= 0"
            (click)="(nachtretenDialog.actor?.currentKarma ?? 0) > 0 && (nachtretenDialog.spendKarma = !nachtretenDialog.spendKarma)">
            <mat-icon>auto_awesome</mat-icon> Karma
            <span class="karma-count-badge" [class.empty]="(nachtretenDialog.actor?.currentKarma ?? 0) <= 0">{{ nachtretenDialog.actor?.currentKarma ?? 0 }}</span>
          </label>
        </div>
        <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:8px">
          <button mat-stroked-button (click)="nachtretenDialog.open = false">Abbrechen</button>
          <button mat-raised-button color="primary" [disabled]="!nachtretenDialog.defenderId" (click)="performNachtreten()">
            <mat-icon>sports_martial_arts</mat-icon> Nachtreten
          </button>
        </div>
      </div>
    </div>

    <!-- Schwanzangriff Dialog (T'skrang) -->
    <div class="attack-dialog" *ngIf="schwanzangriffDialog.open">
      <div class="dialog-backdrop" (click)="schwanzangriffDialog.open = false"></div>
      <div class="dialog-box" style="max-width:440px">
        <h3><mat-icon style="vertical-align:middle;margin-right:6px;color:#a5d6a7">pets</mat-icon>Schwanzangriff: {{ schwanzangriffDialog.actor?.character?.name }}</h3>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Ziel</mat-label>
          <mat-select [(ngModel)]="schwanzangriffDialog.defenderId">
            <mat-option *ngFor="let c of schwanzangriffTargets(schwanzangriffDialog.actor)" [value]="c.id">
              {{ cn(c) }} (Ini {{ c.initiative }})
            </mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Schwanzwaffe (optional)</mat-label>
          <mat-select [(ngModel)]="schwanzangriffDialog.weaponId">
            <mat-option [value]="null">Waffenlos (STR-Schaden)</mat-option>
            <mat-option *ngFor="let e of tailWeaponsOf(schwanzangriffDialog.actor)" [value]="e.id">{{ e.name }} (+{{ e.damageBonus }})</mat-option>
          </mat-select>
        </mat-form-field>
        <div style="color:#888;font-size:0.85rem;margin-bottom:12px">
          Waffenloser Kampf vs. KV — STR-Schaden (+ Schwanzwaffe). 1×/Runde. <strong style="color:#ef9a9a">−2 auf alle Proben in dieser Runde.</strong>
        </div>
        <div style="display:flex;gap:8px;align-items:center">
          <label class="karma-toggle"
            [class.active]="schwanzangriffDialog.spendKarma"
            [class.disabled]="(schwanzangriffDialog.actor?.currentKarma ?? 0) <= 0"
            (click)="(schwanzangriffDialog.actor?.currentKarma ?? 0) > 0 && (schwanzangriffDialog.spendKarma = !schwanzangriffDialog.spendKarma)">
            <mat-icon>auto_awesome</mat-icon> Karma
            <span class="karma-count-badge" [class.empty]="(schwanzangriffDialog.actor?.currentKarma ?? 0) <= 0">{{ schwanzangriffDialog.actor?.currentKarma ?? 0 }}</span>
          </label>
        </div>
        <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:8px">
          <button mat-stroked-button (click)="schwanzangriffDialog.open = false">Abbrechen</button>
          <button mat-raised-button color="primary" [disabled]="!schwanzangriffDialog.defenderId" (click)="performSchwanzangriff()">
            <mat-icon>pets</mat-icon> Schwanzangriff
          </button>
        </div>
      </div>
    </div>

    <!-- Attack Dialog -->
    <div class="attack-dialog" *ngIf="attackDialog.open">
      <div class="dialog-backdrop" (click)="closeAttackDialog()"></div>
      <div class="dialog-box">
        <h3>Angriff: {{ attackDialog.attacker?.character?.name }}</h3>
        <div class="dialog-stance-info" *ngIf="attackDialog.attacker?.declaredStance === 'AGGRESSIVE'">
          ⚔ Aggressiv angesagt: +3 Angriff / -3 Verteidigung / 1 Schaden
        </div>
        <div class="dialog-stance-info defensive" *ngIf="attackDialog.attacker?.declaredStance === 'DEFENSIVE'">
          🛡 Defensiv angesagt: -3 Angriff / +3 Verteidigung
        </div>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Ziel</mat-label>
          <mat-select [(ngModel)]="attackDialog.defenderId" (ngModelChange)="onAttackTargetChange($event)">
            <mat-option *ngFor="let c of possibleTargets()" [value]="c.id">
              {{ cn(c) }}
            </mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Waffe</mat-label>
          <mat-select [ngModel]="attackDialog.weaponId" (ngModelChange)="onAttackWeaponChange($event)">
            <mat-option [value]="null">Keine Waffe</mat-option>
            <mat-option *ngFor="let e of attackWeaponsFor(attackDialog.attacker)" [value]="e.id">
              {{ e.name }} (+{{ e.damageBonus }} Schaden){{ e.twoHanded ? ' ✋✋' : '' }}
            </mat-option>
          </mat-select>
        </mat-form-field>
        <div *ngIf="isTwoHandedWeaponSelected(attackDialog)"
             style="margin:-6px 0 10px;font-size:0.82rem;color:#ffb74d;display:flex;align-items:center;gap:6px">
          <mat-icon style="font-size:18px;height:18px;width:18px">shield</mat-icon>
          Zweihändige Waffe — ein aktives Schild wird automatisch abgelegt (außer Buckler).
        </div>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Waffentalent / -fertigkeit</mat-label>
          <mat-select [ngModel]="attackDialog.attackSource" (ngModelChange)="onAttackSourceChange($event)">
            <mat-option [value]="''">Kein Talent / keine Fertigkeit</mat-option>
            <mat-option *ngFor="let t of attackTalentsOf(attackDialog.attacker)" [value]="'t:' + t.talentDefinition.id">
              ⚔ {{ t.talentDefinition.name }} (Rang {{ t.rank }})
            </mat-option>
            <mat-option *ngFor="let s of weaponSkillsOf(attackDialog.attacker)" [value]="'s:' + s.skillDefinition.id">
              🎯 {{ s.skillDefinition.name }} (Fertigkeit, Rang {{ s.rank }})
            </mat-option>
          </mat-select>
        </mat-form-field>
        <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">
          <label class="karma-toggle"
            *ngIf="attackDialog.skillId == null"
            [class.active]="attackDialog.spendKarma"
            [class.disabled]="(attackDialog.attacker?.currentKarma ?? 0) <= 0"
            (click)="(attackDialog.attacker?.currentKarma ?? 0) > 0 && (attackDialog.spendKarma = !attackDialog.spendKarma)">
            <mat-icon>auto_awesome</mat-icon>
            Karma (Angriff)
            <span class="karma-count-badge" [class.empty]="(attackDialog.attacker?.currentKarma ?? 0) <= 0">
              {{ attackDialog.attacker?.currentKarma ?? 0 }}
            </span>
          </label>
          <span *ngIf="attackDialog.skillId != null" style="font-size:0.8rem;color:#888;display:flex;align-items:center;gap:4px">
            <mat-icon style="font-size:16px;height:16px;width:16px">block</mat-icon>
            Fertigkeit: kein Karma
          </span>
          <label class="karma-toggle"
            *ngIf="isClawWeaponSelected(attackDialog)"
            [class.active]="attackDialog.spendKarmaForDamage"
            [class.disabled]="(attackDialog.attacker?.currentKarma ?? 0) <= (attackDialog.spendKarma ? 1 : 0)"
            (click)="toggleKarmaForDamage(attackDialog)"
            matTooltip="Krallenhand: zusätzliches Karma auf den Schadenswurf">
            <mat-icon>local_fire_department</mat-icon>
            Karma (Schaden)
          </label>
          <label class="karma-toggle blattschuss-toggle"
            *ngIf="canUseBlattschuss(attackDialog)"
            [class.active]="attackDialog.useBlattschuss"
            (click)="attackDialog.useBlattschuss = !attackDialog.useBlattschuss"
            matTooltip="Blattschuss ankündigen: bei Fehlschlag bis zu Rang Karma nachschießen. Kostet 2 Schaden, 1×/Runde, nur Fernkampf.">
            <mat-icon>track_changes</mat-icon>
            Blattschuss
          </label>
        </div>
        <!-- Verzweiflungsschlag-Amulette (physisch) -->
        <div *ngIf="amuletsOf(attackDialog.attacker, false).length" class="amulet-section">
          <div class="amulet-title">🩸 Verzweiflungsschlag-Amulette (+6 ansagen)</div>
          <div class="amulet-row" *ngFor="let a of amuletsOf(attackDialog.attacker, false)">
            <span class="amulet-name">{{ a.name }}</span>
            <label class="karma-toggle amulet-toggle"
              [class.active]="attackDialog.amuletMode?.[a.id!] === 'attack'"
              (click)="toggleAmuletMode(attackDialog.amuletMode, a.id, 'attack')">
              +6 Angriff
            </label>
            <label class="karma-toggle amulet-toggle"
              [class.active]="attackDialog.amuletMode?.[a.id!] === 'damage'"
              (click)="toggleAmuletMode(attackDialog.amuletMode, a.id, 'damage')">
              +6 Schaden
            </label>
          </div>
        </div>
        <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:8px">
          <button mat-stroked-button (click)="closeAttackDialog()">Abbrechen</button>
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

    <!-- GM-Effekt Dialog -->
    <div class="attack-dialog" *ngIf="gmEffectDialog.open">
      <div class="dialog-backdrop" (click)="closeGmEffectDialog()"></div>
      <div class="dialog-box" style="max-width:440px">
        <h3><mat-icon style="vertical-align:middle;margin-right:6px;color:#80deea">auto_fix_normal</mat-icon>GM-Effekt hinzufügen</h3>

        <!-- Ziel -->
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Ziel</mat-label>
          <mat-select [(ngModel)]="gmEffectDialog.targetId">
            <mat-option *ngFor="let c of allCombatants()" [value]="c.id">
              {{ cn(c) }}{{ c.defeated ? ' (besiegt)' : '' }}
            </mat-option>
          </mat-select>
        </mat-form-field>

        <!-- Spezial-Bedingungen (manuell aktiviert, Position/Anzahl nicht automatisch berechenbar) -->
        <div style="border:1px solid #3a4a52;border-radius:6px;padding:8px;margin-bottom:12px;background:#10181c">
          <div style="font-size:12px;color:#80deea;margin-bottom:6px">Spezial-Bedingungen (Dauer aus „Runden" unten)</div>
          <div style="display:flex;gap:8px;flex-wrap:wrap">
            <button mat-stroked-button [disabled]="!gmEffectDialog.targetId"
              (click)="applyGmCondition('TOTER_WINKEL')"
              matTooltip="Angriff aus dem Toten Winkel: −2 KV/MV; das Ziel darf keine aktiven Verteidigungstalente (Ausweichen/Riposte) einsetzen.">
              <mat-icon>visibility_off</mat-icon> Toter Winkel
            </button>
            <button mat-stroked-button [disabled]="!gmEffectDialog.targetId"
              (click)="applyGmCondition('BEDRAENGT')"
              matTooltip="Bedrängt: −2 auf Angriffsproben, KV und MV. Erneut anwenden = Überwältigt (kumulativ −1 pro weiterer Quelle).">
              <mat-icon>groups</mat-icon> Bedrängt
            </button>
          </div>
        </div>

        <!-- Bonus/Malus Toggle + Stärke -->
        <div style="display:flex;gap:8px;align-items:center;margin-bottom:8px">
          <label class="karma-toggle" style="flex:0 0 auto"
            [class.active]="!gmEffectDialog.isMalus"
            (click)="gmEffectDialog.isMalus = false">
            <mat-icon>trending_up</mat-icon> Bonus
          </label>
          <label class="karma-toggle" style="flex:0 0 auto;border-color:#ef9a9a"
            [class.active]="gmEffectDialog.isMalus"
            [style.color]="gmEffectDialog.isMalus ? '#ef9a9a' : ''"
            (click)="gmEffectDialog.isMalus = true">
            <mat-icon>trending_down</mat-icon> Malus
          </label>
          <mat-form-field appearance="fill" style="flex:1">
            <mat-label>Stärke</mat-label>
            <input matInput type="number" [(ngModel)]="gmEffectDialog.magnitude" min="1" max="20">
          </mat-form-field>
        </div>

        <!-- Betroffenes Attribut -->
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Betroffenes Attribut</mat-label>
          <mat-select [(ngModel)]="gmEffectDialog.statKey">
            <mat-optgroup label="Verteidigungen">
              <mat-option value="PHYSICAL_DEFENSE">KV – Körperliche Verteidigung</mat-option>
              <mat-option value="SPELL_DEFENSE">MV – Mystische Verteidigung</mat-option>
              <mat-option value="SOCIAL_DEFENSE">SV – Soziale Verteidigung</mat-option>
              <mat-option value="ALL_DEFENSES">Alle Verteidigungen (KV + MV + SV)</mat-option>
            </mat-optgroup>
            <mat-optgroup label="Angriff & Schaden">
              <mat-option value="ATTACK_STEP">Angriffsstufe</mat-option>
              <mat-option value="DAMAGE_STEP">Schadensstufe</mat-option>
            </mat-optgroup>
            <mat-optgroup label="Sonstiges">
              <mat-option value="INITIATIVE_STEP">Initiativestufe</mat-option>
              <mat-option value="ALL_ACTIONS">Malus auf alle Proben (Angriff + alle VK)</mat-option>
              <mat-option value="MYSTIC_ARMOR">Mystische Rüstung</mat-option>
              <mat-option value="PHYSICAL_ARMOR">Physische Rüstung</mat-option>
            </mat-optgroup>
          </mat-select>
        </mat-form-field>

        <!-- Name + Dauer -->
        <div style="display:flex;gap:8px">
          <mat-form-field appearance="fill" style="flex:2">
            <mat-label>Name (optional)</mat-label>
            <input matInput [(ngModel)]="gmEffectDialog.name" placeholder="z.B. Blindheit, Segen">
          </mat-form-field>
          <mat-form-field appearance="fill" style="flex:1">
            <mat-label>Runden (−1 = permanent)</mat-label>
            <input matInput type="number" [(ngModel)]="gmEffectDialog.rounds">
          </mat-form-field>
        </div>

        <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:8px">
          <button mat-stroked-button (click)="closeGmEffectDialog()">Abbrechen</button>
          <button mat-raised-button color="primary"
                  [disabled]="!gmEffectDialog.targetId || !gmEffectDialog.statKey || !gmEffectDialog.magnitude"
                  (click)="performGmEffect()">
            <mat-icon>auto_fix_normal</mat-icon> Anwenden
          </button>
        </div>
      </div>
    </div>

    <!-- Magische Markierung Dialog -->
    <div class="attack-dialog" *ngIf="magischeMarkierungDialog.open">
      <div class="dialog-backdrop" (click)="magischeMarkierungDialog.open = false"></div>
      <div class="dialog-box">
        <h3><mat-icon style="vertical-align:middle;margin-right:6px;color:#ce93d8">gps_fixed</mat-icon>Magische Markierung: {{ magischeMarkierungDialog.actor?.character?.name }}</h3>
        <div style="color:#888;font-size:0.85rem;margin-bottom:12px">
          WAH + Rang vs. MV des Ziels · +2 Fernkampf-Angriff/Übererfolg (auf Anwender) · Freie Aktion
        </div>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Ziel</mat-label>
          <mat-select [(ngModel)]="magischeMarkierungDialog.targetId">
            <mat-option *ngFor="let c of magischeMarkierungTargets()" [value]="c.id">
              {{ cn(c) }} (MV {{ sd(c) }})
            </mat-option>
          </mat-select>
        </mat-form-field>
        <div class="fa-cost-badge">
          <mat-icon>warning</mat-icon> Kostet 1 Schaden
        </div>
        <div style="display:flex;gap:8px;align-items:center;margin-top:8px">
          <label class="karma-toggle"
            [class.active]="magischeMarkierungDialog.spendKarma"
            [class.disabled]="(magischeMarkierungDialog.actor?.currentKarma ?? 0) <= 0"
            (click)="(magischeMarkierungDialog.actor?.currentKarma ?? 0) > 0 && (magischeMarkierungDialog.spendKarma = !magischeMarkierungDialog.spendKarma)">
            <mat-icon>auto_awesome</mat-icon>
            Karma
            <span class="karma-count-badge" [class.empty]="(magischeMarkierungDialog.actor?.currentKarma ?? 0) <= 0">
              {{ magischeMarkierungDialog.actor?.currentKarma ?? 0 }}
            </span>
          </label>
        </div>
        <div style="display:flex;justify-content:flex-end;gap:8px;margin-top:8px">
          <button mat-stroked-button (click)="magischeMarkierungDialog.open = false">Abbrechen</button>
          <button mat-raised-button color="primary" (click)="performMagischeMarkierung()" [disabled]="!magischeMarkierungDialog.targetId">
            <mat-icon>gps_fixed</mat-icon> Markieren
          </button>
        </div>
      </div>
    </div>

    <!-- Magische Markierung Result Modal -->
    <div class="result-modal" *ngIf="magischeMarkierungModal.open">
      <div class="dialog-backdrop" (click)="magischeMarkierungModal.open = false"></div>
      <div class="dialog-box result-box" *ngIf="magischeMarkierungModal.result as r">
        <div class="result-outcome" [class.hit]="r.success" [class.miss]="!r.success">
          <mat-icon>{{ r.success ? 'gps_fixed' : 'close' }}</mat-icon>
          {{ r.success ? 'MARKIERT' : 'FEHLSCHLAG' }}
        </div>
        <div class="result-names">
          <span class="result-actor" [style.color]="nameColor(r.actorName)">{{ r.actorName }}</span>
          <span style="color:#ce93d8;font-weight:600;margin:0 6px">Magische Markierung</span>
          <ng-container *ngIf="r.targetName">
            <mat-icon style="color:#555;font-size:18px">arrow_forward</mat-icon>
            <span class="result-target" [style.color]="nameColor(r.targetName)">{{ r.targetName }}</span>
          </ng-container>
        </div>
        <div class="result-rolls">
          <div class="roll-block">
            <div class="roll-block-header">
              <span class="roll-block-label">Probe · Step {{ r.rollStep }}</span>
              <div class="roll-block-totals">
                <span class="roll-big-total">{{ r.roll.total + (r.karmaRoll?.total ?? 0) }}</span>
                <span class="roll-big-vs">vs</span>
                <span class="roll-big-target">MV {{ r.defenseValue }}</span>
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
            <div class="roll-row extra-success-row">
              <span class="roll-label">Bonus</span>
              <span class="roll-expr">+2 Erfolg<span *ngIf="r.extraSuccesses > 0"> + {{ r.extraSuccesses }}× +2 Übererfolg</span></span>
              <span class="roll-value extra-success">+{{ (1 + r.extraSuccesses) * 2 }}</span>
            </div>
            <div class="taunt-effect-banner" style="background:rgba(206,147,216,0.12);border-color:#ce93d8;color:#ce93d8" *ngIf="r.effectApplied">
              <mat-icon>gps_fixed</mat-icon>
              +{{ (1 + r.extraSuccesses) * 2 }} Fernkampf-Angriff bis Rundenende
            </div>
          </ng-container>
          <div class="roll-row" *ngIf="r.damageTaken > 0" style="background:rgba(239,83,80,0.08);margin-top:8px">
            <span class="roll-label">Kosten</span>
            <span class="roll-expr">Schaden für Anwender</span>
            <span class="roll-value" style="color:#ef5350">−{{ r.damageTaken }}</span>
          </div>
        </div>
        <button mat-raised-button style="width:100%;margin-top:16px" (click)="magischeMarkierungModal.open = false">
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
      <div class="dialog-backdrop" (click)="dismissAutofightModal(standUpModal)"></div>
      <div class="dialog-box result-box" *ngIf="standUpModal.result as r">
        <div class="result-outcome" [class.hit]="!r.stillKnockedDown" [class.miss]="r.stillKnockedDown">
          <mat-icon>{{ r.stillKnockedDown ? 'close' : 'accessibility_new' }}</mat-icon>
          {{ r.stillKnockedDown ? 'FEHLGESCHLAGEN' : (r.simpleStandUp ? 'AUFGESTANDEN' : 'AUFGESPRUNGEN') }}
        </div>
        <div class="result-names">
          <span class="result-actor" [style.color]="nameColor(r.actorName)">{{ r.actorName }}</span>
        </div>
        <div class="result-rolls" *ngIf="!r.simpleStandUp && r.roll">
          <div class="roll-block">
            <div class="roll-block-header">
              <span class="roll-block-label">GE-Probe · Step {{ r.rollStep }}</span>
              <div class="roll-block-totals">
                <span class="roll-big-total">{{ r.roll.total + (r.karmaRoll?.total ?? 0) }}</span>
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
        <button mat-raised-button style="width:100%;margin-top:16px" (click)="dismissAutofightModal(standUpModal)">
          Schließen
        </button>
      </div>
    </div>

    <!-- Threadweave Dialog -->
    <div class="attack-dialog" *ngIf="threadweaveDialog.open">
      <div class="dialog-backdrop" (click)="closeThreadweaveDialog()"></div>
      <div class="dialog-box">
        <h3><mat-icon style="vertical-align:middle;margin-right:6px;color:#b39ddb">all_inclusive</mat-icon>Faden weben: {{ threadweaveDialog.caster?.character?.name }}</h3>
        <div *ngIf="threadweaveDialog.caster?.preparingSpellId" style="color:#b39ddb;font-size:0.85rem;margin-bottom:12px;display:flex;align-items:center;gap:6px">
          <mat-icon style="font-size:16px;height:16px;width:16px">info</mat-icon>
          Bereitet vor: {{ spellNameOf(threadweaveDialog.caster!) }} ({{ threadweaveDialog.caster!.threadsWoven }}/{{ threadweaveDialog.caster!.threadsRequired }} Fäden)
        </div>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Zauber</mat-label>
          <mat-select [(ngModel)]="threadweaveDialog.spellId" [disabled]="!!threadweaveDialog.caster?.preparingSpellId">
            <mat-option *ngFor="let s of spellsOf(threadweaveDialog.caster)" [value]="s.spellDefinition.id">
              {{ s.spellDefinition.name }} ({{ s.spellDefinition.threads }} Fäden, SW {{ s.spellDefinition.weavingDifficulty }})
            </mat-option>
          </mat-select>
        </mat-form-field>

        <!-- Zusatzfaden: alle Pflichtfäden sind gewoben, jeder weitere Faden kauft eine Option -->
        <div class="extra-thread-box" *ngIf="threadweaveIsExtra()">
          <div class="extra-thread-head">
            <mat-icon>add_circle_outline</mat-icon> Zusatzfaden
            <span class="extra-thread-count">
              {{ extraThreadCountOf(threadweaveDialog.caster) }}/{{ weavingRankOf(threadweaveDialog.caster) }}
            </span>
          </div>

          <div class="extra-thread-hint" *ngIf="threadweaveOptions().length === 0">
            {{ threadweaveSpell()?.name }} bietet keine Zusatzfäden — alle Fäden sind bereits gewoben.
          </div>
          <div class="extra-thread-hint" *ngIf="threadweaveOptions().length > 0 && threadweaveExtraExhausted()">
            Maximum erreicht: {{ weavingRankOf(threadweaveDialog.caster) }} Zusatzfäden (Fadenweben-Rang).
          </div>

          <mat-form-field appearance="fill" style="width:100%"
            *ngIf="threadweaveOptions().length > 0 && !threadweaveExtraExhausted()">
            <mat-label>Option</mat-label>
            <mat-select [(ngModel)]="threadweaveDialog.extraOptionIndex">
              <mat-option *ngFor="let o of threadweaveOptions(); let i = index; trackBy: trackByOptionIndex" [value]="i">
                {{ o.label }}<span *ngIf="o.type === 'DISPLAY'"> — Spielleiter</span>
              </mat-option>
            </mat-select>
          </mat-form-field>
          <div class="extra-thread-note" *ngIf="threadweaveOptions().length > 0 && !threadweaveExtraExhausted()">
            Nur „Wirkungsstufe" wird automatisch verrechnet. Alle übrigen Optionen werden nur
            protokolliert — Reichweite, Ziele, Dauer und Nicht-Kampf-Boni kennt die Engine nicht.
          </div>
        </div>

        <div style="display:flex;gap:8px;align-items:center">
          <label class="karma-toggle"
            [class.active]="threadweaveDialog.spendKarma"
            [class.disabled]="(threadweaveDialog.caster?.currentKarma ?? 0) <= 0"
            (click)="(threadweaveDialog.caster?.currentKarma ?? 0) > 0 && (threadweaveDialog.spendKarma = !threadweaveDialog.spendKarma)">
            <mat-icon>auto_awesome</mat-icon>
            Karma
            <span class="karma-count-badge" [class.empty]="(threadweaveDialog.caster?.currentKarma ?? 0) <= 0">
              {{ threadweaveDialog.caster?.currentKarma ?? 0 }}
            </span>
          </label>
        </div>
        <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:8px">
          <button mat-stroked-button (click)="closeThreadweaveDialog()">Abbrechen</button>
          <button mat-raised-button color="primary" (click)="performThreadweave()" [disabled]="threadweaveBlocked()">
            <mat-icon>all_inclusive</mat-icon> {{ threadweaveIsExtra() ? 'Zusatzfaden weben' : 'Faden weben' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Threadweave Result Modal -->
    <div class="result-modal" *ngIf="threadweaveModal.open">
      <div class="dialog-backdrop" (click)="threadweaveModal.open = false"></div>
      <div class="dialog-box result-box" *ngIf="threadweaveModal.result as r">
        <div class="result-outcome" [class.hit]="r.success" [class.miss]="!r.success">
          <mat-icon>{{ r.success ? 'all_inclusive' : 'close' }}</mat-icon>
          {{ r.success ? 'FADEN GEWOBEN' : 'FEHLGESCHLAGEN' }}
        </div>
        <div class="result-names">
          <span class="result-actor" [style.color]="nameColor(r.casterName)">{{ r.casterName }}</span>
          <span style="color:#b39ddb;font-weight:600;margin:0 6px">{{ r.spellName }}</span>
        </div>
        <div class="result-rolls">
          <div class="roll-block">
            <div class="roll-block-header">
              <span class="roll-block-label">Fadenweben · Step {{ r.rollStep }}</span>
              <div class="roll-block-totals">
                <span class="roll-big-total">{{ r.roll.total + (r.karmaRoll?.total ?? 0) }}</span>
                <span class="roll-big-vs">vs</span>
                <span class="roll-big-target">SW {{ r.targetNumber }}</span>
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
          <div class="roll-divider"></div>
          <div class="thread-progress-bar">
            <span class="thread-label">Fäden</span>
            <div class="thread-dots">
              <span *ngFor="let woven of threadDots(r)" [class]="'thread-dot ' + (woven ? 'woven' : 'empty')"></span>
            </div>
            <span class="thread-count">{{ r.threadsWoven }}/{{ r.threadsRequired }}</span>
          </div>
          <div class="extra-thread-result" *ngIf="r.extraThread && r.extraThreadLabel">
            <mat-icon>add_circle_outline</mat-icon>
            <span>{{ r.extraThreadLabel }}</span>
            <span class="extra-thread-count">{{ r.extraThreadCount }}/{{ r.extraThreadMax }}</span>
          </div>
          <div class="spell-ready-banner" *ngIf="r.readyToCast">
            <mat-icon>auto_fix_high</mat-icon> Bereit zum Wirken!
          </div>
        </div>
        <button mat-raised-button style="width:100%;margin-top:16px" (click)="threadweaveModal.open = false">
          Schließen
        </button>
      </div>
    </div>

    <!-- Spell Cast Dialog -->
    <div class="attack-dialog" *ngIf="spellCastDialog.open">
      <div class="dialog-backdrop" (click)="closeSpellCastDialog()"></div>
      <div class="dialog-box">
        <h3><mat-icon style="vertical-align:middle;margin-right:6px;color:#ce93d8">auto_fix_high</mat-icon>Zauber wirken: {{ spellCastDialog.caster?.character?.name }}</h3>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Zauber</mat-label>
          <mat-select [(ngModel)]="spellCastDialog.spellId">
            <mat-option *ngFor="let s of readySpellsOf(spellCastDialog.caster)" [value]="s.spellDefinition.id">
              {{ s.spellDefinition.name }}
              <span *ngIf="s.spellDefinition.effectType === 'DAMAGE'"> (Schaden)</span>
              <span *ngIf="s.spellDefinition.effectType === 'HEAL'"> (Heilung)</span>
              <span *ngIf="s.spellDefinition.effectType === 'BUFF'"> (Buff)</span>
              <span *ngIf="s.spellDefinition.effectType === 'DEBUFF'"> (Debuff)</span>
            </mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="fill" style="width:100%" *ngIf="spellNeedsTarget()">
          <mat-label>Ziel</mat-label>
          <mat-select [(ngModel)]="spellCastDialog.targetId" (ngModelChange)="onSpellCastTargetChange($event)">
            <mat-option *ngFor="let c of spellTargets()" [value]="c.id">
              {{ cn(c) }}
            </mat-option>
          </mat-select>
        </mat-form-field>
        <div style="display:flex;gap:8px;align-items:center">
          <label class="karma-toggle"
            [class.active]="spellCastDialog.spendKarma"
            [class.disabled]="(spellCastDialog.caster?.currentKarma ?? 0) <= 0"
            (click)="(spellCastDialog.caster?.currentKarma ?? 0) > 0 && (spellCastDialog.spendKarma = !spellCastDialog.spendKarma)">
            <mat-icon>auto_awesome</mat-icon>
            Karma
            <span class="karma-count-badge" [class.empty]="(spellCastDialog.caster?.currentKarma ?? 0) <= 0">
              {{ spellCastDialog.caster?.currentKarma ?? 0 }}
            </span>
          </label>
        </div>
        <!-- Verzweiflungsschlag-Amulette (Zauber) -->
        <div *ngIf="amuletsOf(spellCastDialog.caster, true).length" class="amulet-section">
          <div class="amulet-title">🩸 Verzweiflungsschlag-Amulette (+6 ansagen)</div>
          <div class="amulet-row" *ngFor="let a of amuletsOf(spellCastDialog.caster, true)">
            <span class="amulet-name">{{ a.name }}</span>
            <label class="karma-toggle amulet-toggle"
              [class.active]="spellCastDialog.amuletMode?.[a.id!] === 'attack'"
              (click)="toggleAmuletMode(spellCastDialog.amuletMode, a.id, 'attack')">
              +6 Zauberwurf
            </label>
            <label class="karma-toggle amulet-toggle"
              [class.active]="spellCastDialog.amuletMode?.[a.id!] === 'damage'"
              (click)="toggleAmuletMode(spellCastDialog.amuletMode, a.id, 'damage')">
              +6 Schaden
            </label>
          </div>
        </div>
        <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:8px">
          <button mat-stroked-button (click)="closeSpellCastDialog()">Abbrechen</button>
          <button mat-raised-button color="warn" (click)="performSpellCast()" [disabled]="!spellCastDialog.spellId">
            <mat-icon>auto_fix_high</mat-icon> Wirken
          </button>
        </div>
      </div>
    </div>

    <!-- Ablenken Dialog -->
    <div class="attack-dialog" *ngIf="distractDialog.open">
      <div class="dialog-backdrop" (click)="closeDistractDialog()"></div>
      <div class="dialog-box">
        <h3><mat-icon style="vertical-align:middle;margin-right:6px;color:#ffab91">record_voice_over</mat-icon>Ablenken: {{ distractDialog.actor?.character?.name }}</h3>
        <div style="color:#888;font-size:0.85rem;margin-bottom:12px">
          CHA + Rang vs. Soziale VK · −1 KV/Erfolg für Anwender &amp; Ziel · Toter Winkel für Verbündete
        </div>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Ziel</mat-label>
          <mat-select [(ngModel)]="distractDialog.targetId" (ngModelChange)="onDistractTargetChange($event)">
            <mat-option *ngFor="let c of distractTargets()" [value]="c.id">
              {{ cn(c) }} (SV {{ socD(c) }})
            </mat-option>
          </mat-select>
        </mat-form-field>
        <div class="fa-cost-badge">
          <mat-icon>warning</mat-icon> Kostet 1 Schaden · Anwender erhält auch −KV!
        </div>
        <div style="display:flex;gap:8px;align-items:center;margin-top:8px">
          <label class="karma-toggle"
            [class.active]="distractDialog.spendKarma"
            [class.disabled]="(distractDialog.actor?.currentKarma ?? 0) <= 0"
            (click)="(distractDialog.actor?.currentKarma ?? 0) > 0 && (distractDialog.spendKarma = !distractDialog.spendKarma)">
            <mat-icon>auto_awesome</mat-icon>
            Karma
            <span class="karma-count-badge" [class.empty]="(distractDialog.actor?.currentKarma ?? 0) <= 0">
              {{ distractDialog.actor?.currentKarma ?? 0 }}
            </span>
          </label>
        </div>
        <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:8px">
          <button mat-stroked-button (click)="closeDistractDialog()">Abbrechen</button>
          <button mat-raised-button color="warn" (click)="performDistract()" [disabled]="!distractDialog.targetId">
            <mat-icon>record_voice_over</mat-icon> Ablenken
          </button>
        </div>
      </div>
    </div>

    <!-- Ablenken Result Modal -->
    <div class="result-modal" *ngIf="distractModal.open">
      <div class="dialog-backdrop" (click)="dismissModal()"></div>
      <div class="dialog-box result-box" *ngIf="distractModal.result as r">
        <div class="result-outcome" [class.hit]="r.success" [class.miss]="!r.success">
          <mat-icon>{{ r.success ? 'record_voice_over' : 'close' }}</mat-icon>
          {{ r.success ? 'ABGELENKT!' : 'FEHLSCHLAG' }}
        </div>
        <div class="result-names">
          <span class="result-actor" [style.color]="nameColor(r.actorName)">{{ r.actorName }}</span>
          <mat-icon style="color:#555;font-size:18px">arrow_forward</mat-icon>
          <span class="result-target" [style.color]="nameColor(r.targetName)">{{ r.targetName }}</span>
        </div>
        <div class="result-rolls">
          <div class="roll-block">
            <div class="roll-block-header">
              <span class="roll-block-label">Ablenken · Step {{ r.rollStep }}</span>
              <div class="roll-block-totals">
                <span class="roll-big-total">{{ r.roll.total + (r.karmaRoll?.total ?? 0) }}</span>
                <span class="roll-big-vs">vs</span>
                <span class="roll-big-target">SV {{ r.socialDefense }}</span>
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
            <div class="roll-row extra-success-row">
              <span class="roll-label">Erfolge</span>
              <span class="roll-expr">{{ r.successes }}× → −{{ r.targetPenalty }} KV</span>
              <span class="roll-value extra-success">{{ r.successes }}</span>
            </div>
            <div class="distract-effect-row">
              <div class="distract-effect-badge self">
                <mat-icon>shield</mat-icon> Anwender −{{ r.actorPenalty }} KV
              </div>
              <div class="distract-effect-badge target">
                <mat-icon>shield</mat-icon> Ziel −{{ r.targetPenalty }} KV (Toter Winkel)
              </div>
            </div>
          </ng-container>
          <div class="roll-row" style="background:rgba(239,83,80,0.08);margin-top:4px">
            <span class="roll-label">Kosten</span>
            <span class="roll-expr">Überanstrengung</span>
            <span class="roll-value" style="color:#ef5350">−1</span>
          </div>
        </div>
        <button mat-raised-button style="width:100%;margin-top:16px" (click)="dismissModal()">
          Schließen
        </button>
      </div>
    </div>

    <!-- Schwachstelle erkennen Dialog -->
    <div class="attack-dialog" *ngIf="spotArmorFlawDialog.open">
      <div class="dialog-backdrop" (click)="spotArmorFlawDialog.open = false"></div>
      <div class="dialog-box">
        <h3><mat-icon style="vertical-align:middle;margin-right:6px;color:#80cbc4">biotech</mat-icon>Schwachstelle erkennen: {{ spotArmorFlawDialog.actor?.character?.name }}</h3>
        <div style="color:#888;font-size:0.85rem;margin-bottom:12px">
          WAH + Rang vs. max(MV, physische Rüstung) · +2 Schaden/Erfolg gegen das Ziel für Rang Runden · keine Hauptaktion
        </div>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Ziel</mat-label>
          <mat-select [(ngModel)]="spotArmorFlawDialog.targetId">
            <mat-option *ngFor="let c of spotArmorFlawTargets()" [value]="c.id">
              {{ cn(c) }} (MV {{ sd(c) }}, Rüstung {{ pa(c) }})
            </mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Bonusstufen</mat-label>
          <input matInput type="number" [(ngModel)]="spotArmorFlawDialog.bonusSteps" min="0">
        </mat-form-field>
        <div class="fa-cost-badge">
          <mat-icon>warning</mat-icon> Kostet 1 Schaden · keine Hauptaktion
        </div>
        <div style="display:flex;gap:8px;align-items:center;margin-top:8px">
          <label class="karma-toggle"
            [class.active]="spotArmorFlawDialog.spendKarma"
            [class.disabled]="(spotArmorFlawDialog.actor?.currentKarma ?? 0) <= 0"
            (click)="(spotArmorFlawDialog.actor?.currentKarma ?? 0) > 0 && (spotArmorFlawDialog.spendKarma = !spotArmorFlawDialog.spendKarma)">
            <mat-icon>auto_awesome</mat-icon>
            Karma
            <span class="karma-count-badge" [class.empty]="(spotArmorFlawDialog.actor?.currentKarma ?? 0) <= 0">
              {{ spotArmorFlawDialog.actor?.currentKarma ?? 0 }}
            </span>
          </label>
        </div>
        <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:8px">
          <button mat-stroked-button (click)="spotArmorFlawDialog.open = false">Abbrechen</button>
          <button mat-raised-button color="warn" (click)="performSpotArmorFlaw()" [disabled]="!spotArmorFlawDialog.targetId">
            <mat-icon>biotech</mat-icon> Analysieren
          </button>
        </div>
      </div>
    </div>

    <!-- Schwachstelle erkennen Result Modal -->
    <div class="result-modal" *ngIf="spotArmorFlawModal.open">
      <div class="dialog-backdrop" (click)="dismissModal()"></div>
      <div class="dialog-box result-box" *ngIf="spotArmorFlawModal.result as r">
        <div class="result-outcome" [class.hit]="r.success" [class.miss]="!r.success">
          <mat-icon>{{ r.success ? 'biotech' : 'close' }}</mat-icon>
          {{ r.success ? 'SCHWACHSTELLE GEFUNDEN!' : 'KEINE LÜCKE ENTDECKT' }}
        </div>
        <div class="result-names">
          <span class="result-actor" [style.color]="nameColor(r.actorName)">{{ r.actorName }}</span>
          <mat-icon style="color:#555;font-size:18px">arrow_forward</mat-icon>
          <span class="result-target" [style.color]="nameColor(r.targetName)">{{ r.targetName }}</span>
        </div>
        <div class="result-rolls">
          <div class="roll-block">
            <div class="roll-block-header">
              <span class="roll-block-label">Probe · Step {{ r.rollStep }}</span>
              <div class="roll-block-totals">
                <span class="roll-big-total">{{ r.roll.total + (r.karmaRoll?.total ?? 0) }}</span>
                <span class="roll-big-vs">vs</span>
                <span class="roll-big-target">{{ r.targetNumber }}</span>
              </div>
            </div>
            <div style="font-size:0.78rem;color:#888;margin-top:4px">
              TN = max(MV {{ r.spellDefense }}, Rüstung {{ r.physicalArmor }})
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
            <div class="roll-row extra-success-row">
              <span class="roll-label">Erfolge</span>
              <span class="roll-expr">{{ r.successes }}× → +{{ r.damageBonus }} Schaden vs. {{ r.targetName }}</span>
              <span class="roll-value extra-success">{{ r.successes }}</span>
            </div>
            <div class="roll-row" style="background:rgba(129,199,132,0.08)">
              <span class="roll-label">Dauer</span>
              <span class="roll-expr">physische Angriffe (Nahkampf/Fernkampf, keine Zauber)</span>
              <span class="roll-value" style="color:#81c784">{{ r.duration }} Runden</span>
            </div>
          </ng-container>
          <div class="roll-row" style="background:rgba(239,83,80,0.08);margin-top:4px">
            <span class="roll-label">Kosten</span>
            <span class="roll-expr">Überanstrengung</span>
            <span class="roll-value" style="color:#ef5350">−{{ r.strainCost }}</span>
          </div>
        </div>
        <button mat-raised-button style="width:100%;margin-top:16px" (click)="dismissModal()">
          Schließen
        </button>
      </div>
    </div>

    <!-- Eiserner Wille Dialog -->
    <div class="attack-dialog" *ngIf="ironWillDialog.open">
      <div class="dialog-backdrop" (click)="ironWillDialog.open = false"></div>
      <div class="dialog-box">
        <h3><mat-icon style="vertical-align:middle;margin-right:6px;color:#b0bec5">psychology</mat-icon>Eiserner Wille: {{ ironWillDialog.actor?.character?.name }}</h3>
        <div style="color:#888;font-size:0.85rem;margin-bottom:12px">
          WIL + Rang vs. Zauberwurf · Freie Aktion · Kostet 1 Schaden · Magischen Effekt abwehren
        </div>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Angriffswurf des Zauberers</mat-label>
          <input matInput type="number" [(ngModel)]="ironWillDialog.attackTotal" min="1">
        </mat-form-field>
        <div class="fa-cost-badge">
          <mat-icon>warning</mat-icon> Kostet 1 Schaden (Überanstrengung)
        </div>
        <div style="display:flex;gap:8px;align-items:center;margin-top:8px">
          <label class="karma-toggle"
            [class.active]="ironWillDialog.spendKarma"
            [class.disabled]="(ironWillDialog.actor?.currentKarma ?? 0) <= 0"
            (click)="(ironWillDialog.actor?.currentKarma ?? 0) > 0 && (ironWillDialog.spendKarma = !ironWillDialog.spendKarma)">
            <mat-icon>auto_awesome</mat-icon>
            Karma
            <span class="karma-count-badge" [class.empty]="(ironWillDialog.actor?.currentKarma ?? 0) <= 0">
              {{ ironWillDialog.actor?.currentKarma ?? 0 }}
            </span>
          </label>
        </div>
        <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:8px">
          <button mat-stroked-button (click)="ironWillDialog.open = false">Abbrechen</button>
          <button mat-raised-button color="primary" (click)="performIronWill()" [disabled]="!ironWillDialog.attackTotal">
            <mat-icon>psychology</mat-icon> Widerstehen
          </button>
        </div>
      </div>
    </div>

    <!-- Eiserner Wille Result Modal -->
    <div class="result-modal" *ngIf="ironWillModal.open">
      <div class="dialog-backdrop" (click)="dismissModal()"></div>
      <div class="dialog-box result-box" *ngIf="ironWillModal.result as r">
        <div class="result-outcome" [class.hit]="r.success" [class.miss]="!r.success">
          <mat-icon>{{ r.success ? 'psychology' : 'close' }}</mat-icon>
          {{ r.success ? (r.effectNegated ? 'ABGEWEHRT!' : 'ERFOLG') : 'DURCHGEDRUNGEN' }}
        </div>
        <div class="result-names">
          <span class="result-actor" [style.color]="nameColor(r.actorName)">{{ r.actorName }}</span>
          <span style="color:#b0bec5;font-weight:600;margin-left:8px">Eiserner Wille</span>
        </div>
        <div class="result-rolls">
          <div class="roll-block">
            <div class="roll-block-header">
              <span class="roll-block-label">Widerstand · Step {{ r.rollStep }}</span>
              <div class="roll-block-totals">
                <span class="roll-big-total" [style.color]="r.success ? '#4caf50' : '#ef5350'">{{ r.roll.total + (r.karmaRoll?.total ?? 0) }}</span>
                <span class="roll-big-vs">vs</span>
                <span class="roll-big-target">{{ r.attackTotal }}</span>
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
            <div class="taunt-resisted-banner" *ngIf="r.effectNegated">
              <mat-icon>psychology</mat-icon>
              Magischer Effekt erfolgreich abgewehrt!
            </div>
            <div style="color:#888;font-size:0.82rem;text-align:center;padding:8px" *ngIf="!r.effectNegated">
              Kein aktiver magischer Effekt zum Abwehren gefunden.
            </div>
          </ng-container>
          <div class="roll-row" style="background:rgba(239,83,80,0.08);margin-top:4px">
            <span class="roll-label">Kosten</span>
            <span class="roll-expr">Überanstrengung</span>
            <span class="roll-value" style="color:#ef5350">−1</span>
          </div>
        </div>
        <button mat-raised-button style="width:100%;margin-top:16px" (click)="dismissModal()">
          Schließen
        </button>
      </div>
    </div>

    <!-- Akrobatische Verteidigung Dialog -->
    <div class="attack-dialog" *ngIf="acrobaticDialog.open">
      <div class="dialog-backdrop" (click)="acrobaticDialog.open = false"></div>
      <div class="dialog-box">
        <h3><mat-icon style="vertical-align:middle;margin-right:6px;color:#80cbc4">self_improvement</mat-icon>Akrobatische Verteidigung: {{ acrobaticDialog.actor?.character?.name }}</h3>
        <div style="color:#888;font-size:0.85rem;margin-bottom:12px">
          GES + Rang vs. höchste KV aller Gegner · +2 KV pro Erfolg · Einfache Aktion · Kostet 1 Schaden
        </div>
        <div class="fa-cost-badge">
          <mat-icon>warning</mat-icon> Kostet 1 Schaden (Überanstrengung)
        </div>
        <div style="display:flex;gap:8px;align-items:center;margin-top:8px">
          <label class="karma-toggle"
            [class.active]="acrobaticDialog.spendKarma"
            [class.disabled]="(acrobaticDialog.actor?.currentKarma ?? 0) <= 0"
            (click)="(acrobaticDialog.actor?.currentKarma ?? 0) > 0 && (acrobaticDialog.spendKarma = !acrobaticDialog.spendKarma)">
            <mat-icon>auto_awesome</mat-icon>
            Karma
            <span class="karma-count-badge" [class.empty]="(acrobaticDialog.actor?.currentKarma ?? 0) <= 0">
              {{ acrobaticDialog.actor?.currentKarma ?? 0 }}
            </span>
          </label>
        </div>
        <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:8px">
          <button mat-stroked-button (click)="acrobaticDialog.open = false">Abbrechen</button>
          <button mat-raised-button color="primary" (click)="performAcrobaticDefense()">
            <mat-icon>self_improvement</mat-icon> Würfeln
          </button>
        </div>
      </div>
    </div>

    <!-- Akrobatische Verteidigung Result Modal -->
    <div class="result-modal" *ngIf="acrobaticModal.open">
      <div class="dialog-backdrop" (click)="dismissAutofightModal(acrobaticModal)"></div>
      <div class="dialog-box result-box" *ngIf="acrobaticModal.result as r">
        <div class="result-outcome" [class.hit]="r.success" [class.miss]="!r.success">
          <mat-icon>{{ r.success ? 'self_improvement' : 'close' }}</mat-icon>
          {{ r.success ? '+' + r.bonusApplied + ' KV!' : 'FEHLSCHLAG' }}
        </div>
        <div class="result-names">
          <span class="result-actor" [style.color]="nameColor(r.actorName)">{{ r.actorName }}</span>
          <span style="color:#80cbc4;font-weight:600;margin-left:8px">Akrobatische Verteidigung</span>
        </div>
        <div class="result-rolls">
          <div class="roll-block">
            <div class="roll-block-header">
              <span class="roll-block-label">Probe · Step {{ r.rollStep }}</span>
              <div class="roll-block-totals">
                <span class="roll-big-total">{{ r.roll.total + (r.karmaRoll?.total ?? 0) }}</span>
                <span class="roll-big-vs">vs</span>
                <span class="roll-big-target">KV {{ r.targetNumber }}</span>
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
            <div class="roll-row extra-success-row">
              <span class="roll-label">Erfolge</span>
              <span class="roll-expr">{{ r.successes }}× → +{{ r.bonusApplied }} KV</span>
              <span class="roll-value extra-success">{{ r.successes }}</span>
            </div>
            <div class="taunt-effect-banner" style="background:rgba(128,203,196,0.15);border-color:#80cbc4;color:#80cbc4">
              <mat-icon>shield</mat-icon>
              +{{ r.bonusApplied }} KV bis Rundenende
            </div>
          </ng-container>
          <div class="roll-row" style="background:rgba(239,83,80,0.08);margin-top:4px">
            <span class="roll-label">Kosten</span>
            <span class="roll-expr">Überanstrengung</span>
            <span class="roll-value" style="color:#ef5350">−1</span>
          </div>
        </div>
        <button mat-raised-button style="width:100%;margin-top:16px" (click)="dismissAutofightModal(acrobaticModal)">
          Schließen
        </button>
      </div>
    </div>

    <!-- Kampfsinn Dialog -->
    <div class="attack-dialog" *ngIf="combatSenseDialog.open">
      <div class="dialog-backdrop" (click)="combatSenseDialog.open = false"></div>
      <div class="dialog-box">
        <h3><mat-icon style="vertical-align:middle;margin-right:6px;color:#ffcc80">visibility</mat-icon>Kampfsinn: {{ combatSenseDialog.actor?.character?.name }}</h3>
        <div style="color:#888;font-size:0.85rem;margin-bottom:12px">
          WAH + Rang vs. MV des Ziels · +2 KV &amp; +2 Angriff pro Erfolg · Nur gegen Gegner mit niedrigerer Initiative
        </div>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Ziel (niedrigere Initiative)</mat-label>
          <mat-select [(ngModel)]="combatSenseDialog.targetId">
            <mat-option *ngFor="let c of combatSenseTargets()" [value]="c.id">
              {{ cn(c) }} (Ini {{ c.initiative }}, MV {{ sd(c) }})
            </mat-option>
          </mat-select>
        </mat-form-field>
        <div class="fa-cost-badge">
          <mat-icon>warning</mat-icon> Kostet 1 Schaden (Überanstrengung)
        </div>
        <div style="display:flex;gap:8px;align-items:center;margin-top:8px">
          <label class="karma-toggle"
            [class.active]="combatSenseDialog.spendKarma"
            [class.disabled]="(combatSenseDialog.actor?.currentKarma ?? 0) <= 0"
            (click)="(combatSenseDialog.actor?.currentKarma ?? 0) > 0 && (combatSenseDialog.spendKarma = !combatSenseDialog.spendKarma)">
            <mat-icon>auto_awesome</mat-icon>
            Karma
            <span class="karma-count-badge" [class.empty]="(combatSenseDialog.actor?.currentKarma ?? 0) <= 0">
              {{ combatSenseDialog.actor?.currentKarma ?? 0 }}
            </span>
          </label>
        </div>
        <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:8px">
          <button mat-stroked-button (click)="combatSenseDialog.open = false">Abbrechen</button>
          <button mat-raised-button color="primary" (click)="performCombatSense()" [disabled]="!combatSenseDialog.targetId">
            <mat-icon>visibility</mat-icon> Würfeln
          </button>
        </div>
      </div>
    </div>

    <!-- Kampfsinn Result Modal -->
    <div class="result-modal" *ngIf="combatSenseModal.open">
      <div class="dialog-backdrop" (click)="dismissAutofightModal(combatSenseModal)"></div>
      <div class="dialog-box result-box" *ngIf="combatSenseModal.result as r">
        <div class="result-outcome" [class.hit]="r.success" [class.miss]="!r.success">
          <mat-icon>{{ r.success ? 'visibility' : 'close' }}</mat-icon>
          {{ r.success ? 'KAMPFSINN!' : 'FEHLSCHLAG' }}
        </div>
        <div class="result-names">
          <span class="result-actor" [style.color]="nameColor(r.actorName)">{{ r.actorName }}</span>
          <mat-icon style="color:#555;font-size:18px">arrow_forward</mat-icon>
          <span class="result-target" [style.color]="nameColor(r.targetName)">{{ r.targetName }}</span>
        </div>
        <div class="result-rolls">
          <div class="roll-block">
            <div class="roll-block-header">
              <span class="roll-block-label">Kampfsinn · Step {{ r.rollStep }}</span>
              <div class="roll-block-totals">
                <span class="roll-big-total">{{ r.roll.total + (r.karmaRoll?.total ?? 0) }}</span>
                <span class="roll-big-vs">vs</span>
                <span class="roll-big-target">MV {{ r.mysticDefense }}</span>
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
            <div class="roll-row extra-success-row">
              <span class="roll-label">Erfolge</span>
              <span class="roll-expr">{{ r.successes }}× → +{{ r.defenseBonus }} KV / +{{ r.attackBonus }} Angriff</span>
              <span class="roll-value extra-success">{{ r.successes }}</span>
            </div>
            <div class="combat-sense-effect-row">
              <div class="combat-sense-effect-badge defense">
                <mat-icon>shield</mat-icon> +{{ r.defenseBonus }} KV
              </div>
              <div class="combat-sense-effect-badge attack">
                <mat-icon>sports_martial_arts</mat-icon> +{{ r.attackBonus }} Angriff
              </div>
            </div>
          </ng-container>
          <div class="roll-row" style="background:rgba(239,83,80,0.08);margin-top:4px">
            <span class="roll-label">Kosten</span>
            <span class="roll-expr">Überanstrengung</span>
            <span class="roll-value" style="color:#ef5350">−1</span>
          </div>
        </div>
        <button mat-raised-button style="width:100%;margin-top:16px" (click)="dismissAutofightModal(combatSenseModal)">
          Schließen
        </button>
      </div>
    </div>

    <!-- Taunt (Verspotten) Dialog -->
    <div class="attack-dialog" *ngIf="tauntDialog.open">
      <div class="dialog-backdrop" (click)="closeTauntDialog()"></div>
      <div class="dialog-box">
        <h3><mat-icon style="vertical-align:middle;margin-right:6px;color:#ef9a9a">sentiment_very_dissatisfied</mat-icon>Verspotten: {{ tauntDialog.actor?.character?.name }}</h3>
        <div style="color:#888;font-size:0.85rem;margin-bottom:12px">
          CHA + Rang vs. Soziale VK · Freie Aktion · Kostet 1 Schaden
        </div>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Ziel</mat-label>
          <mat-select [(ngModel)]="tauntDialog.targetId" (ngModelChange)="onTauntTargetChange($event)">
            <mat-option *ngFor="let c of tauntTargets()" [value]="c.id">
              {{ cn(c) }} (SV {{ socD(c) }})
            </mat-option>
          </mat-select>
        </mat-form-field>
        <div class="fa-cost-badge">
          <mat-icon>warning</mat-icon> Kostet 1 Schaden (Überanstrengung)
        </div>
        <div style="display:flex;gap:8px;align-items:center;margin-top:8px">
          <label class="karma-toggle"
            [class.active]="tauntDialog.spendKarma"
            [class.disabled]="(tauntDialog.actor?.currentKarma ?? 0) <= 0"
            (click)="(tauntDialog.actor?.currentKarma ?? 0) > 0 && (tauntDialog.spendKarma = !tauntDialog.spendKarma)">
            <mat-icon>auto_awesome</mat-icon>
            Karma
            <span class="karma-count-badge" [class.empty]="(tauntDialog.actor?.currentKarma ?? 0) <= 0">
              {{ tauntDialog.actor?.currentKarma ?? 0 }}
            </span>
          </label>
        </div>
        <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:8px">
          <button mat-stroked-button (click)="closeTauntDialog()">Abbrechen</button>
          <button mat-raised-button color="warn" (click)="performTaunt()" [disabled]="!tauntDialog.targetId">
            <mat-icon>sentiment_very_dissatisfied</mat-icon> Verspotten
          </button>
        </div>
      </div>
    </div>

    <!-- Taunt Result Modal -->
    <div class="result-modal" *ngIf="tauntModal.open">
      <div class="dialog-backdrop" (click)="dismissModal()"></div>
      <div class="dialog-box result-box" *ngIf="tauntModal.result as r">
        <div class="result-outcome" [class.hit]="r.success && !r.resisted" [class.miss]="!r.success || r.resisted">
          <mat-icon>{{ r.success && !r.resisted ? 'sentiment_very_dissatisfied' : 'close' }}</mat-icon>
          {{ r.success && !r.resisted ? 'VERSPOTTET!' : (r.resisted ? 'WIDERSTANDEN' : 'VERFEHLT') }}
        </div>
        <div class="result-names">
          <span class="result-actor" [style.color]="nameColor(r.actorName)">{{ r.actorName }}</span>
          <mat-icon style="color:#555;font-size:18px">arrow_forward</mat-icon>
          <span class="result-target" [style.color]="nameColor(r.targetName)">{{ r.targetName }}</span>
        </div>
        <div class="result-rolls">
          <!-- Main roll -->
          <div class="roll-block">
            <div class="roll-block-header">
              <span class="roll-block-label">Verspotten · Step {{ r.rollStep }}</span>
              <div class="roll-block-totals">
                <span class="roll-big-total">{{ r.roll.total + (r.karmaRoll?.total ?? 0) }}</span>
                <span class="roll-big-vs">vs</span>
                <span class="roll-big-target">SV {{ r.socialDefense }}</span>
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
          <!-- Success details -->
          <ng-container *ngIf="r.success">
            <div class="roll-divider"></div>
            <div class="roll-row extra-success-row" *ngIf="r.extraSuccesses > 0">
              <span class="roll-label">Übererfolge</span>
              <span class="roll-expr">{{ r.extraSuccesses }}× → −{{ r.penalty }} auf Proben + SV</span>
              <span class="roll-value extra-success">{{ r.extraSuccesses }}</span>
            </div>
            <!-- Starrsinn counter -->
            <div class="roll-block starrsinn-block" *ngIf="r.resistRoll">
              <div class="roll-block-header">
                <span class="roll-block-label">Starrsinn · Step {{ r.resistStep }}</span>
                <div class="roll-block-totals">
                  <span class="roll-big-total" [style.color]="r.resisted ? '#4caf50' : '#888'">{{ r.resistRoll.total }}</span>
                  <span class="roll-big-vs">vs</span>
                  <span class="roll-big-target" style="font-size:1.2rem;color:#888">{{ r.roll.total + (r.karmaRoll?.total ?? 0) }}</span>
                </div>
              </div>
              <div class="dice-breakdown-mini">
                <div class="die-mini" *ngFor="let d of r.resistRoll.dice" [class.exploded]="d.exploded">
                  <span class="die-mini-sides">W{{ d.sides }}</span>
                  <span class="die-mini-rolls">{{ d.rolls.join(' + ') }}<span *ngIf="d.rolls.length > 1" class="die-mini-sum"> = {{ d.total }}</span></span>
                  <span *ngIf="d.exploded" class="explode-mini">💥</span>
                </div>
              </div>
            </div>
            <!-- Effect banner -->
            <div class="taunt-effect-banner" *ngIf="!r.resisted && r.penalty > 0">
              <mat-icon>sentiment_very_dissatisfied</mat-icon>
              −{{ r.penalty }} auf alle Proben + SV für {{ r.duration }} Runden
            </div>
            <div class="taunt-resisted-banner" *ngIf="r.resisted">
              <mat-icon>psychology</mat-icon>
              Starrsinn! Wirkung negiert.
            </div>
          </ng-container>
          <!-- Actor damage cost -->
          <div class="roll-row" style="background:rgba(239,83,80,0.08);margin-top:4px">
            <span class="roll-label">Kosten</span>
            <span class="roll-expr">Überanstrengung</span>
            <span class="roll-value" style="color:#ef5350">−1</span>
          </div>
        </div>
        <button mat-raised-button style="width:100%;margin-top:16px" (click)="dismissModal()">
          Schließen
        </button>
      </div>
    </div>

    <!-- Verängstigen Dialog -->
    <div class="attack-dialog" *ngIf="fearDialog.open">
      <div class="dialog-backdrop" (click)="fearDialog.open = false"></div>
      <div class="dialog-box" style="max-width:440px">
        <h3><mat-icon style="vertical-align:middle;margin-right:6px;color:#b39ddb">mood_bad</mat-icon>Verängstigen: {{ fearDialog.actor?.character?.name }}</h3>
        <div style="color:#888;font-size:0.85rem;margin-bottom:12px">
          WIL + Rang vs. Mystische VK · Standardaktion · 0 Überanstrengung
          <br>−2/Erfolg auf Aktionsproben für Rang Runden. Das Ziel darf jede Runde eine WIL-Probe zum Abschütteln ablegen.
        </div>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Ziel</mat-label>
          <mat-select [(ngModel)]="fearDialog.targetId">
            <mat-option *ngFor="let c of fearTargets()" [value]="c.id">
              {{ cn(c) }} (MV {{ sd(c) }})
            </mat-option>
          </mat-select>
        </mat-form-field>
        <div style="display:flex;gap:8px;align-items:center;margin-top:8px">
          <label class="karma-toggle"
            [class.active]="fearDialog.spendKarma"
            [class.disabled]="(fearDialog.actor?.currentKarma ?? 0) <= 0"
            (click)="(fearDialog.actor?.currentKarma ?? 0) > 0 && (fearDialog.spendKarma = !fearDialog.spendKarma)">
            <mat-icon>auto_awesome</mat-icon>
            Karma
            <span class="karma-count-badge" [class.empty]="(fearDialog.actor?.currentKarma ?? 0) <= 0">
              {{ fearDialog.actor?.currentKarma ?? 0 }}
            </span>
          </label>
        </div>
        <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:8px">
          <button mat-stroked-button (click)="fearDialog.open = false">Abbrechen</button>
          <button mat-raised-button color="warn" (click)="performFear()" [disabled]="!fearDialog.targetId">
            <mat-icon>mood_bad</mat-icon> Verängstigen
          </button>
        </div>
      </div>
    </div>

    <!-- Verängstigen Result Modal -->
    <div class="result-modal" *ngIf="fearModal.open">
      <div class="dialog-backdrop" (click)="dismissModal()"></div>
      <div class="dialog-box result-box" *ngIf="fearModal.result as r">
        <div class="result-outcome" [class.hit]="r.success" [class.miss]="!r.success">
          <mat-icon>{{ r.success ? 'mood_bad' : 'close' }}</mat-icon>
          {{ r.success ? 'VERÄNGSTIGT!' : 'VERFEHLT' }}
        </div>
        <div class="result-names">
          <span class="result-actor" [style.color]="nameColor(r.actorName)">{{ r.actorName }}</span>
          <mat-icon style="color:#555;font-size:18px">arrow_forward</mat-icon>
          <span class="result-target" [style.color]="nameColor(r.targetName)">{{ r.targetName }}</span>
        </div>
        <div class="result-rolls">
          <div class="roll-block">
            <div class="roll-block-header">
              <span class="roll-block-label">Verängstigen · Step {{ r.rollStep }}</span>
              <div class="roll-block-totals">
                <span class="roll-big-total">{{ r.roll.total + (r.karmaRoll?.total ?? 0) }}</span>
                <span class="roll-big-vs">vs</span>
                <span class="roll-big-target">MV {{ r.spellDefense }}</span>
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
            <div class="roll-row extra-success-row">
              <span class="roll-label">Erfolge</span>
              <span class="roll-expr">{{ r.successes }}× → −{{ r.penalty }} auf Aktionsproben</span>
              <span class="roll-value extra-success">{{ r.successes }}</span>
            </div>
            <div class="taunt-effect-banner">
              <mat-icon>mood_bad</mat-icon>
              −{{ r.penalty }} auf Aktionsproben für {{ r.duration }} Runden · Abschütteln: WIL-Probe vs. {{ r.resistTargetNumber }}
            </div>
          </ng-container>
        </div>
        <button mat-raised-button style="width:100%;margin-top:16px" (click)="dismissModal()">
          Schließen
        </button>
      </div>
    </div>

    <!-- Magie neutralisieren: Auswahldialog (bei allen Clients synchron geöffnet) -->
    <div class="result-modal" *ngIf="neutralizeSelectModal.open">
      <div class="dialog-backdrop" (click)="dismissModal()"></div>
      <div class="dialog-box" style="max-width:560px">
        <h3><mat-icon style="vertical-align:middle;margin-right:6px;color:#80deea">auto_fix_off</mat-icon>Magie neutralisieren: {{ neutralizeSelectModal.actorName }}</h3>
        <div style="color:#888;font-size:0.85rem;margin-bottom:12px">
          WIL + Rang {{ neutralizeSelectModal.rank }} vs. <strong>Effektstufe + 10</strong> ·
          Verbraucht die Aktion der Runde · kostet 1 Überanstrengung.
          <br>Welche Effekte neutralisierbar sind, entscheidet der Spielleiter — es stehen alle zur Auswahl.
        </div>

        <div *ngIf="!neutralizeSelectModal.effects.length"
          style="padding:12px;background:#1e1a16;border:1px solid #3a3028;border-radius:6px;color:#777;font-size:13px">
          Aktuell liegen keine Effekte auf Kombattanten.
        </div>

        <ng-container *ngIf="neutralizeSelectModal.effects.length">
          <mat-form-field appearance="fill" style="width:100%">
            <mat-label>Zu neutralisierender Effekt</mat-label>
            <mat-select [(ngModel)]="neutralizeSelectModal.selection">
              <mat-option *ngFor="let e of neutralizeSelectModal.effects; trackBy: trackByEffectKey" [value]="e.key">
                {{ e.combatantName }} — {{ e.name }}
                <span style="color:#888">({{ e.remainingRounds < 0 ? 'permanent' : e.remainingRounds + ' Rd' }})</span>
              </mat-option>
            </mat-select>
          </mat-form-field>

          <div style="display:flex;gap:12px;align-items:center;flex-wrap:wrap">
            <mat-form-field appearance="fill" style="width:170px"
              matTooltip="Effekte haben keine eigene Stufe — hier die Stufe des auslösenden Zaubers/Talents eintragen.">
              <mat-label>Stufe des Effekts</mat-label>
              <input matInput type="number" min="0" [(ngModel)]="neutralizeSelectModal.effectLevel">
            </mat-form-field>
            <div style="font-size:0.9rem;color:#c9a84c">
              Mindestwurf: <strong>{{ (neutralizeSelectModal.effectLevel || 0) + 10 }}</strong>
              <span style="color:#888;font-size:0.8rem"> (Stufe + 10)</span>
            </div>
            <label class="karma-toggle"
              [class.active]="neutralizeSelectModal.spendKarma"
              [class.disabled]="(neutralizeActor()?.currentKarma ?? 0) <= 0"
              (click)="(neutralizeActor()?.currentKarma ?? 0) > 0 && (neutralizeSelectModal.spendKarma = !neutralizeSelectModal.spendKarma)">
              <mat-icon>auto_awesome</mat-icon> Karma
              <span class="karma-count-badge" [class.empty]="(neutralizeActor()?.currentKarma ?? 0) <= 0">
                {{ neutralizeActor()?.currentKarma ?? 0 }}
              </span>
            </label>
          </div>
        </ng-container>

        <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:12px">
          <button mat-stroked-button (click)="dismissModal()">Abbrechen</button>
          <button mat-raised-button color="primary"
            [disabled]="!neutralizeSelectModal.selection"
            (click)="performNeutralizeMagic()">
            <mat-icon>auto_fix_off</mat-icon> Neutralisieren
          </button>
        </div>
      </div>
    </div>

    <!-- Magie neutralisieren: Ergebnis -->
    <div class="result-modal" *ngIf="neutralizeModal.open">
      <div class="dialog-backdrop" (click)="dismissModal()"></div>
      <div class="dialog-box result-box" *ngIf="neutralizeModal.result as r">
        <div class="result-outcome" [class.hit]="r.success" [class.miss]="!r.success">
          <mat-icon>{{ r.success ? 'auto_fix_off' : 'close' }}</mat-icon>
          {{ r.success ? 'NEUTRALISIERT!' : 'FEHLGESCHLAGEN' }}
        </div>
        <div class="result-names">
          <span class="result-actor" [style.color]="nameColor(r.actorName)">{{ r.actorName }}</span>
          <mat-icon style="color:#555;font-size:18px">arrow_forward</mat-icon>
          <span class="result-target" [style.color]="nameColor(r.targetName)">{{ r.targetName }}</span>
        </div>
        <div class="result-rolls">
          <div class="roll-block">
            <div class="roll-block-header">
              <span class="roll-block-label">Magie neutralisieren · Step {{ r.rollStep }}</span>
              <div class="roll-block-totals">
                <span class="roll-big-total">{{ r.roll.total + (r.karmaRoll?.total ?? 0) }}</span>
                <span class="roll-big-vs">vs</span>
                <span class="roll-big-target">MW {{ r.targetNumber }}</span>
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
          <div class="roll-row">
            <span class="roll-label">Effekt</span>
            <span class="roll-expr">{{ r.effectName }} · Stufe {{ r.effectLevel }} + 10</span>
            <span class="roll-value">{{ r.targetNumber }}</span>
          </div>
          <div class="taunt-effect-banner" *ngIf="r.effectRemoved">
            <mat-icon>auto_fix_off</mat-icon>
            „{{ r.effectName }}" auf {{ r.targetName }} beendet
          </div>
          <div class="roll-row" style="background:rgba(239,83,80,0.08);margin-top:4px">
            <span class="roll-label">Kosten</span>
            <span class="roll-expr">Aktion der Runde + Überanstrengung</span>
            <span class="roll-value" style="color:#ef5350">−1</span>
          </div>
        </div>
        <button mat-raised-button style="width:100%;margin-top:16px" (click)="dismissModal()">
          Schließen
        </button>
      </div>
    </div>

    <!-- Furcht-abschütteln Result Modal -->
    <div class="result-modal" *ngIf="fearResistModal.open">
      <div class="dialog-backdrop" (click)="dismissModal()"></div>
      <div class="dialog-box result-box" *ngIf="fearResistModal.result as r">
        <div class="result-outcome" [class.hit]="r.success" [class.miss]="!r.success">
          <mat-icon>{{ r.success ? 'psychology' : 'close' }}</mat-icon>
          {{ r.success ? 'FURCHT ABGESCHÜTTELT!' : 'WEITER VERÄNGSTIGT' }}
        </div>
        <div class="result-names">
          <span class="result-actor" [style.color]="nameColor(r.targetName)">{{ r.targetName }}</span>
        </div>
        <div class="result-rolls">
          <div class="roll-block">
            <div class="roll-block-header">
              <span class="roll-block-label">Willenskraft · Step {{ r.resistStep }}</span>
              <div class="roll-block-totals">
                <span class="roll-big-total" [style.color]="r.success ? '#4caf50' : '#ef5350'">{{ r.roll.total }}</span>
                <span class="roll-big-vs">vs</span>
                <span class="roll-big-target">{{ r.targetNumber }}</span>
              </div>
            </div>
            <div class="dice-breakdown-mini">
              <div class="die-mini" *ngFor="let d of r.roll.dice" [class.exploded]="d.exploded">
                <span class="die-mini-sides">W{{ d.sides }}</span>
                <span class="die-mini-rolls">{{ d.rolls.join(' + ') }}<span *ngIf="d.rolls.length > 1" class="die-mini-sum"> = {{ d.total }}</span></span>
                <span *ngIf="d.exploded" class="explode-mini">💥</span>
              </div>
            </div>
          </div>
        </div>
        <button mat-raised-button style="width:100%;margin-top:16px" (click)="dismissModal()">
          Schließen
        </button>
      </div>
    </div>

    <!-- Spell Cast Result Modal -->
    <div class="result-modal" *ngIf="spellCastModal.open">
      <div class="dialog-backdrop" (click)="dismissAutofightModal(spellCastModal)"></div>
      <div class="dialog-box result-box" *ngIf="spellCastModal.result as r">
        <div class="result-outcome" [class.hit]="r.success" [class.miss]="!r.success">
          <mat-icon>{{ r.success ? 'auto_fix_high' : 'close' }}</mat-icon>
          {{ r.success ? spellOutcomeLabel(r) : 'FEHLGESCHLAGEN' }}
        </div>
        <div class="result-names">
          <span class="result-actor" [style.color]="nameColor(r.casterName)">{{ r.casterName }}</span>
          <span style="color:#ce93d8;font-weight:600;margin:0 6px">{{ r.spellName }}</span>
          <ng-container *ngIf="r.targetName">
            <mat-icon style="color:#555;font-size:18px">arrow_forward</mat-icon>
            <span class="result-target" [style.color]="nameColor(r.targetName)">{{ r.targetName }}</span>
          </ng-container>
        </div>
        <div class="result-rolls">
          <!-- Cast roll block -->
          <div class="roll-block">
            <div class="roll-block-header">
              <span class="roll-block-label">Spruchzauberei · Step {{ r.castStep }}</span>
              <div class="roll-block-totals">
                <span class="roll-big-total">{{ r.castRoll.total + (r.karmaRoll?.total ?? 0) }}</span>
                <span class="roll-big-vs">vs</span>
                <span class="roll-big-target">MV {{ r.defenseValue }}</span>
              </div>
            </div>
            <div class="dice-breakdown-mini">
              <div class="die-mini" *ngFor="let d of r.castRoll.dice" [class.exploded]="d.exploded">
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

          <!-- Damage spells -->
          <ng-container *ngIf="r.success && r.effectType === 'DAMAGE' && r.damageRoll">
            <div class="roll-divider"></div>
            <div class="roll-row extra-success-row" *ngIf="r.damageStepBonus && r.damageStepBonus > 0">
              <span class="roll-label">Übererfolge</span>
              <span class="roll-expr">{{ r.extraSuccesses }}× → +{{ r.damageStepBonus }} Stufen</span>
              <span class="roll-value extra-success">+{{ r.damageStepBonus }}</span>
            </div>
            <div class="roll-block" style="background:rgba(206,147,216,0.06);border:1px solid #4a2050">
              <div class="roll-block-header">
                <span class="roll-block-label">
                  Zauberschaden · Step {{ r.damageStep }}
                  <span class="step-calc" *ngIf="(r.damageStepBonus ?? 0) > 0 || (r.extraThreadEffectStep ?? 0) > 0">
                    ({{ spellDamageBaseStep(r) }}<span
                      *ngIf="(r.damageStepBonus ?? 0) > 0"> + {{ r.damageStepBonus }} Übererfolge</span><span
                      *ngIf="(r.extraThreadEffectStep ?? 0) > 0"> + {{ r.extraThreadEffectStep }} Zusatzfäden</span>)
                  </span>
                </span>
                <div class="roll-block-totals">
                  <span class="roll-big-total" style="color:#ce93d8">{{ r.damageRoll!.total }}</span>
                  <ng-container *ngIf="(r.armorValue ?? 0) > 0">
                    <span class="roll-big-vs">−</span>
                    <span class="roll-big-total" style="color:#888;font-size:1.4rem">{{ r.armorValue }}</span>
                    <span class="roll-big-vs">=</span>
                    <span class="roll-big-total" style="color:#ba68c8">{{ r.netDamage }}</span>
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
              <mat-icon>dangerous</mat-icon> {{ r.targetName }} ist bewusstlos!
            </div>
            <ng-container *ngIf="r.knockdownResult as kd">
              <div class="knockdown-banner" [class.knocked]="kd.knockedDown" [class.stood]="!kd.knockedDown">
                <mat-icon>{{ kd.knockedDown ? 'airline_seat_flat' : 'accessibility_new' }}</mat-icon>
                STR {{ kd.roll.total }} vs {{ kd.targetNumber }} →
                {{ kd.knockedDown ? 'NIEDERGESCHLAGEN!' : 'Bleibt stehen' }}
              </div>
            </ng-container>
          </ng-container>

          <!-- Heal spells -->
          <ng-container *ngIf="r.success && r.effectType === 'HEAL'">
            <div class="roll-divider"></div>
            <div class="spell-heal-banner">
              <mat-icon>healing</mat-icon> {{ r.healedAmount }} Schaden geheilt
            </div>
          </ng-container>

          <!-- Buff/Debuff spells -->
          <ng-container *ngIf="r.success && (r.effectType === 'BUFF' || r.effectType === 'DEBUFF')">
            <div class="roll-divider"></div>
            <div class="spell-effect-banner" [class.buff]="r.effectType === 'BUFF'" [class.debuff]="r.effectType === 'DEBUFF'">
              <mat-icon>{{ r.effectType === 'BUFF' ? 'arrow_upward' : 'arrow_downward' }}</mat-icon>
              {{ r.effectApplied }} ({{ r.effectDuration }} Runden)
            </div>
          </ng-container>

          <!-- Zusatzfäden: verrechnet wird nur die Wirkungsstufe, der Rest ist GM-Sache -->
          <ng-container *ngIf="r.extraThreadLabels?.length">
            <div class="roll-divider"></div>
            <div class="extra-thread-head">
              <mat-icon>add_circle_outline</mat-icon> Zusatzfäden
              <span class="extra-thread-count" *ngIf="r.extraThreadEffectStep">
                Wirkungsstufe +{{ r.extraThreadEffectStep }}
              </span>
            </div>
            <div class="extra-thread-result" *ngFor="let l of r.extraThreadLabels; trackBy: trackByOptionIndex">
              <span>{{ l }}</span>
            </div>
          </ng-container>
        </div>
        <button mat-raised-button style="width:100%;margin-top:16px" (click)="dismissAutofightModal(spellCastModal)">
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

    .tracker-body { display: flex; gap: 16px; flex: 1; overflow: hidden; }
    .combatants-panel { flex: 1; min-width: 0; overflow-y: auto; }
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

    .phase-badge {
      display: inline-flex; align-items: center; padding: 6px 12px;
      border-radius: 16px; font-size: 0.85rem; font-weight: 600;
      border: 1px solid transparent;
    }
    .phase-badge.phase-declaration {
      background: rgba(255,193,7,0.1); color: #ffb300; border-color: rgba(255,179,0,0.4);
    }
    .phase-badge.phase-action {
      background: rgba(76,175,80,0.1); color: #66bb6a; border-color: rgba(102,187,106,0.4);
    }
    .stance-badge {
      display: inline-flex; align-items: center; padding: 2px 8px;
      border-radius: 10px; font-size: 0.75rem; font-weight: 600;
    }
    .stance-badge.aggressive { background: rgba(255,112,67,0.15); color: #ff7043; border: 1px solid #ff7043; }
    .stance-badge.defensive { background: rgba(66,165,245,0.15); color: #42a5f5; border: 1px solid #42a5f5; }

    .dialog-stance-info {
      padding: 8px 12px; margin-bottom: 12px; border-radius: 6px;
      background: rgba(255,112,67,0.1); color: #ff7043; border: 1px solid #ff7043;
      font-size: 0.9rem; font-weight: 600;
    }
    .dialog-stance-info.defensive {
      background: rgba(66,165,245,0.1); color: #42a5f5; border-color: #42a5f5;
    }
    .declaration-row {
      display: flex; align-items: center; flex-wrap: wrap; gap: 6px;
      padding: 8px; margin: 6px 0; border-radius: 6px;
      background: rgba(255,179,0,0.06); border: 1px dashed rgba(255,179,0,0.3);
    }
    .declaration-label { font-size: 0.78rem; color: #aaa; font-weight: 600; }
    .decl-btn {
      display: inline-flex; align-items: center; gap: 4px;
      padding: 4px 10px; border-radius: 6px; border: 1px solid #3a3028;
      background: transparent; color: #aaa; font-size: 0.82rem; cursor: pointer;
      white-space: nowrap;
      mat-icon { font-size: 15px; height: 15px; width: 15px; flex-shrink: 0; }
    }
    .decl-btn:hover:not(:disabled) { border-color: #c9a84c; color: #c9a84c; }
    .decl-btn.active { border-color: #c9a84c; color: #c9a84c; background: rgba(201,168,76,0.12); }
    .decl-btn.aggressive.active { border-color: #ff7043; color: #ff7043; background: rgba(255,112,67,0.12); }
    .decl-btn.defensive.active { border-color: #42a5f5; color: #42a5f5; background: rgba(66,165,245,0.12); }
    .decl-btn:disabled { opacity: 0.35; cursor: not-allowed; }
    .decl-confirm { margin-left: auto; }
    .decl-confirmed { display: inline-flex; align-items: center; gap: 4px; color: #4caf50; font-size: 0.82rem; margin-left: auto; }
    .decl-undo {
      margin-left: 6px;
      mat-icon { font-size: 15px; height: 15px; width: 15px; flex-shrink: 0; margin-right: 2px; }
    }

    .comb-header {
      display: flex; justify-content: space-between; align-items: center;
      gap: 8px; flex-wrap: wrap; margin-bottom: 8px;
    }
    .comb-title { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; min-width: 0; }
    /* Auto-Checkbox + Zustands-Badges — stehen mit Name/Disziplin in der Kopfzeile */
    .comb-meta { display: flex; align-items: center; gap: 6px; flex-shrink: 0; }

    /* Werte links, Aktionsbuttons rechts untereinander */
    .comb-columns { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 10px; align-items: start; }
    .comb-col-stats { min-width: 0; }
    .initiative-badge {
      background: #3a3028; color: #c9a84c; width: 32px; height: 32px;
      border-radius: 50%; display: flex; align-items: center; justify-content: center;
      font-weight: bold; font-size: 0.85rem; flex-shrink: 0;
    }
    /* Aktionsspalte: feste Breite, Buttons untereinander. Die feste Breite ist wichtig —
       ohne sie quetscht eine lange Button-Liste die Icons auf 0 Breite. */
    .comb-actions {
      display: flex; flex-direction: column; align-items: stretch;
      gap: 4px; width: 172px; flex-shrink: 0;
    }
    /* Buttons füllen die Spalte und richten Icon + Text linksbündig aus */
    .comb-actions > button {
      width: 100%; justify-content: flex-start; margin: 0; overflow: hidden;
    }
    /* Lange Namen werden gekürzt statt aus dem Button zu laufen — der Tooltip nennt den vollen Text */
    .btn-label {
      flex: 1; min-width: 0; text-align: left;
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }
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
      white-space: nowrap; flex-shrink: 0;
      mat-icon { font-size: 16px; height: 16px; width: 16px; flex-shrink: 0; }
      &.aggressive.active { border-color: #ff7043; color: #ff7043; background: rgba(255,112,67,0.1); }
      &.defensive.active { border-color: #42a5f5; color: #42a5f5; background: rgba(66,165,245,0.1); }
      &.fear-resist-btn { border-color: #2f6f68; color: #80cbc4; background: rgba(128,203,196,0.08); }
      &.fear-resist-btn:not([disabled]):hover { border-color: #80cbc4; background: rgba(128,203,196,0.16); }
      &.remove-btn { border-color: #5c2b2b; color: #ef9a9a; }
      &.remove-btn:not([disabled]):hover { border-color: #ef9a9a; background: rgba(239,154,154,0.1); }
      &.magische-markierung-btn { border-color: #3a1a40; color: #ce93d8; }
      &.magische-markierung-btn:not([disabled]):hover { border-color: #ce93d8; background: rgba(206,147,216,0.1); }
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
    .dialog-state-badge {
      font-size: 11px; font-weight: 600; color: #80cbc4; background: rgba(0,188,212,0.1);
      border: 1px solid rgba(0,188,212,0.4); border-radius: 8px; padding: 2px 7px;
      cursor: default; white-space: nowrap;
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
      display: flex; gap: 6px; flex-wrap: wrap; padding: 6px 0 2px; border-top: 1px solid #2a2520;
    }
    .def-chip {
      display: inline-flex; align-items: center; gap: 3px;
      padding: 2px 8px; border-radius: 10px; font-size: 11px; font-weight: 600;
      mat-icon { font-size: 13px; height: 13px; width: 13px; }
      &.phys   { background: rgba(66,165,245,0.12);  border: 1px solid #1a3a5a; color: #42a5f5; }
      &.myst   { background: rgba(171,71,188,0.12);  border: 1px solid #3a1a50; color: #ab47bc; }
      &.social { background: rgba(255,167,38,0.12);  border: 1px solid #5a3a1a; color: #ffa726; }
    }
    .comb-armor-row { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 6px; }
    .armor-chip {
      padding: 2px 8px; border-radius: 10px; font-size: 11px;
      &.phys { background: rgba(66,165,245,0.12); border: 1px solid #1a3a5a; color: #42a5f5; }
      &.myst { background: rgba(171,71,188,0.12); border: 1px solid #3a1a50; color: #ab47bc; }
    }
    .def-modified { color: #ffcc80; font-weight: 700; }
    .effects-row { display: flex; flex-wrap: wrap; gap: 2px; }

    .log-panel {
      display: flex; flex-direction: row; flex-shrink: 0;
      width: 380px; transition: width 0.3s ease; overflow: hidden;
      &.collapsed { width: 28px; }
    }
    .log-toggle {
      width: 28px; flex-shrink: 0; display: flex; align-items: center; justify-content: center;
      cursor: pointer; background: #2a2520; border-radius: 4px;
      writing-mode: vertical-rl; font-size: 0.75rem; color: #aaa; letter-spacing: 1px;
      user-select: none;
      &:hover { background: #3a3530; color: #fff; }
    }
    .log-content { flex: 1; display: flex; flex-direction: column; overflow: hidden; padding-left: 8px; min-width: 0; }
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
    .result-subtitle {
      text-align: center; font-size: 1rem; font-weight: 700;
      letter-spacing: 0.08em; margin-top: -4px; margin-bottom: 4px;
    }
    .result-names {
      display: flex; align-items: center; justify-content: center; gap: 8px;
      margin-bottom: 16px; font-size: 0.9rem;
    }
    .result-actor { font-weight: 600; }
    .result-target { font-weight: 600; }

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
    .amulet-section { margin-top: 10px; padding: 8px 10px; border: 1px solid #6d3a3a; border-radius: 8px; background: rgba(160,60,60,0.08); }
    .amulet-title { font-size: 0.8rem; color: #e0a0a0; margin-bottom: 6px; }
    .amulet-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 4px; }
    .amulet-name { flex: 1; min-width: 120px; font-size: 0.85rem; color: #ddd; }
    .amulet-toggle { padding: 3px 10px; font-size: 0.8rem; }
    .amulet-toggle.active { border-color: #ef9a9a; color: #ef9a9a; background: rgba(239,154,154,0.12); }
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
    .effect-note.damage-note { color: #80cbc4; background: rgba(128,203,196,0.1); }
    .effect-note.defense-note { color: #90caf9; background: rgba(144,202,249,0.1); }

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
    .combat-option-btn.taunt-btn { color: #ef9a9a; border-color: #4a1a1a; }
    .combat-option-btn.taunt-btn:not([disabled]):hover { border-color: #ef9a9a; background: rgba(239,154,154,0.1); }
    .combat-option-btn.acrobatic-btn { color: #80cbc4; border-color: #1a3a38; }
    .combat-option-btn.acrobatic-btn:not([disabled]):hover { border-color: #80cbc4; background: rgba(128,203,196,0.1); }
    .combat-option-btn.combat-sense-btn { color: #ffcc80; border-color: #3a2e10; }
    .combat-option-btn.combat-sense-btn:not([disabled]):hover { border-color: #ffcc80; background: rgba(255,204,128,0.1); }
    .combat-option-btn.distract-btn { color: #ffab91; border-color: #3a1a10; }
    .combat-option-btn.distract-btn:not([disabled]):hover { border-color: #ffab91; background: rgba(255,171,145,0.1); }
    .combat-option-btn.spot-flaw-btn { color: #80cbc4; border-color: #1e3a36; }
    .combat-option-btn.spot-flaw-btn:not([disabled]):hover { border-color: #80cbc4; background: rgba(128,203,196,0.1); }
    .combat-option-btn.iron-will-btn { color: #b0bec5; border-color: #2a3038; }
    .combat-option-btn.iron-will-btn:not([disabled]):hover { border-color: #b0bec5; background: rgba(176,190,197,0.1); }
    .combat-option-btn.riposte-btn { color: #ff8a65; border-color: #4a2010; background: rgba(255,138,101,0.12); }
    .combat-option-btn.riposte-btn:not([disabled]):hover { border-color: #ff8a65; background: rgba(255,138,101,0.22); }
    .combat-option-btn.manoeuver-btn { color: #80deea; border-color: #1a3a40; }
    .combat-option-btn.manoeuver-btn:not([disabled]):hover { border-color: #80deea; background: rgba(128,222,234,0.1); }
    .combat-option-btn.tigersprung-btn { color: #ffee58; border-color: #3a3010; }
    .combat-option-btn.tigersprung-btn:not([disabled]):hover { border-color: #ffee58; background: rgba(255,238,88,0.1); }
    .combat-option-btn.lufttanz-btn { color: #b3e5fc; border-color: #1a3040; }
    .combat-option-btn.lufttanz-btn:not([disabled]):hover { border-color: #b3e5fc; background: rgba(179,229,252,0.1); }
    .combat-option-btn.lufttanz-attack-btn { color: #fff; background: #29b6f6; }
    .combat-option-btn.lufttanz-attack-btn:hover { background: #03a9f4; }
    .combat-option-btn.blattschuss-pending-btn { color: #fff; background: #66bb6a; }
    .combat-option-btn.blattschuss-pending-btn:hover:not([disabled]) { background: #4caf50; }
    .karma-toggle.blattschuss-toggle.active { color: #a5d6a7; border-color: #a5d6a7; background: rgba(165,214,167,0.15); }

    .initiative-modal-box { max-width: 640px; }
    .initiative-list { display: flex; flex-direction: column; gap: 8px; margin-top: 12px; max-height: 60vh; overflow-y: auto; }
    .initiative-row {
      display: grid; grid-template-columns: 36px 1fr auto; gap: 12px; align-items: center;
      background: #1e1a16; border: 1px solid #2a2218; border-radius: 8px; padding: 10px 14px;
    }
    .init-order { font-size: 1.1rem; font-weight: 700; color: #c9a84c; text-align: center; }
    .init-info { min-width: 0; }
    .init-name { font-weight: 600; font-size: 0.95rem; display: flex; align-items: center; gap: 8px; }
    .init-tag { font-size: 0.65rem; padding: 1px 6px; border-radius: 8px; font-weight: 700; }
    .init-tag.hero { color: #66bb6a; background: rgba(102,187,106,0.12); }
    .init-tag.npc { color: #ef5350; background: rgba(239,83,80,0.12); }
    .init-step { font-size: 0.78rem; color: #888; margin: 2px 0 4px; }
    .init-dice { gap: 6px; flex-wrap: wrap; }
    .init-total {
      font-size: 1.6rem; font-weight: 800; color: #ffcc00;
      min-width: 56px; text-align: right;
    }
    .init-effects { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 6px; }
    .init-effect-chip {
      font-size: 0.72rem; color: #c9a84c;
      background: rgba(201,168,76,0.1); border: 1px solid rgba(201,168,76,0.3);
      border-radius: 10px; padding: 1px 8px;
    }
    .combat-option-btn.zweitwaffe-btn { color: #ce93d8; border-color: #3a1a40; }
    .combat-option-btn.zweitwaffe-btn:not([disabled]):hover { border-color: #ce93d8; background: rgba(206,147,216,0.1); }
    .combat-option-btn.nachtreten-btn { color: #ffb74d; border-color: #4a3413; }
    .combat-option-btn.nachtreten-btn:not([disabled]):hover { border-color: #ffb74d; background: rgba(255,183,77,0.1); }
    .combat-option-btn.schwanzangriff-btn { color: #a5d6a7; border-color: #2e5a2e; }
    .combat-option-btn.schwanzangriff-btn:not([disabled]):hover { border-color: #a5d6a7; background: rgba(165,214,167,0.1); }
    .combat-option-btn.karma-init-btn { color: #d4b85a; border-color: #6b5a1e; }
    .combat-option-btn.karma-init-btn:not([disabled]):hover { border-color: #d4b85a; background: rgba(212,184,90,0.1); }
    .combat-option-btn.karma-init-btn.active { border-color: #d4b85a; color: #d4b85a; background: rgba(212,184,90,0.15); }
    .combat-option-btn.fear-btn { color: #b39ddb; border-color: #3a2c55; }
    .combat-option-btn.fear-btn:not([disabled]):hover { border-color: #b39ddb; background: rgba(179,157,219,0.1); }
    .combat-option-btn.fear-resist-btn { color: #80cbc4; border-color: #2e6e66; }
    .combat-option-btn.fear-resist-btn:not([disabled]):hover { border-color: #80cbc4; background: rgba(128,203,196,0.1); }
    .combat-option-btn.neutralize-btn { color: #80deea; border-color: #1e4c52; }
    .combat-option-btn.neutralize-btn:not([disabled]):hover { border-color: #80deea; background: rgba(128,222,234,0.1); }
    .distract-effect-row { display: flex; gap: 8px; }
    .distract-effect-badge {
      flex: 1; display: flex; align-items: center; gap: 6px; justify-content: center;
      border-radius: 6px; padding: 8px; font-weight: 700; font-size: 0.85rem;
      mat-icon { font-size: 16px; height: 16px; width: 16px; }
      &.self { background: rgba(239,83,80,0.12); border: 1px solid #ef5350; color: #ef9a9a; }
      &.target { background: rgba(255,171,145,0.12); border: 1px solid #ffab91; color: #ffab91; }
    }
    .combat-sense-effect-row { display: flex; gap: 8px; }
    .combat-sense-effect-badge {
      flex: 1; display: flex; align-items: center; gap: 6px; justify-content: center;
      border-radius: 6px; padding: 8px; font-weight: 700; font-size: 0.9rem;
      mat-icon { font-size: 18px; height: 18px; width: 18px; }
      &.defense { background: rgba(128,203,196,0.15); border: 1px solid #80cbc4; color: #80cbc4; }
      &.attack { background: rgba(255,204,128,0.15); border: 1px solid #ffcc80; color: #ffcc80; }
    }
    .taunt-effect-banner {
      display: flex; align-items: center; gap: 6px; justify-content: center;
      background: rgba(239,154,154,0.15); border: 1px solid #ef9a9a;
      border-radius: 6px; padding: 8px; color: #ef9a9a;
      font-weight: 700; font-size: 0.9rem;
      mat-icon { font-size: 18px; height: 18px; width: 18px; }
    }
    .taunt-resisted-banner {
      display: flex; align-items: center; gap: 6px; justify-content: center;
      background: rgba(76,175,80,0.12); border: 1px solid #4caf50;
      border-radius: 6px; padding: 8px; color: #4caf50;
      font-weight: 700; font-size: 0.9rem;
      mat-icon { font-size: 18px; height: 18px; width: 18px; }
    }
    .starrsinn-block { background: #1a1e1a; border: 1px solid #2a3a2a; }

    /* Spell badges & buttons */
    .spell-prep-badge {
      font-size: 10px; font-weight: 700; color: #b39ddb; background: rgba(179,157,219,0.15);
      border: 1px solid #b39ddb; border-radius: 8px; padding: 2px 7px;
      letter-spacing: 0.03em;
    }
    .extra-thread-box {
      border: 1px solid #4a3070;
      border-radius: 6px;
      padding: 10px 12px;
      margin-bottom: 12px;
      background: rgba(179,157,219,0.06);
    }
    .extra-thread-head {
      display: flex; align-items: center; gap: 6px;
      color: #b39ddb; font-size: 0.85rem; font-weight: 500; margin-bottom: 8px;
      mat-icon { font-size: 18px; height: 18px; width: 18px; flex-shrink: 0; }
    }
    .extra-thread-count {
      margin-left: auto;
      background: rgba(179,157,219,0.18);
      border-radius: 10px; padding: 1px 8px; font-size: 0.78rem;
    }
    .extra-thread-hint { color: #ffb74d; font-size: 0.8rem; line-height: 1.4; }
    .extra-thread-note { color: #999; font-size: 0.75rem; line-height: 1.4; margin-top: 2px; }
    .extra-thread-result {
      display: flex; align-items: center; gap: 6px;
      margin-top: 10px; padding: 6px 10px;
      border: 1px solid #4a3070; border-radius: 6px;
      background: rgba(179,157,219,0.1); color: #b39ddb; font-size: 0.85rem;
      mat-icon { font-size: 18px; height: 18px; width: 18px; flex-shrink: 0; }
    }
    .combat-option-btn.threadweave-btn { color: #b39ddb; border-color: #4a3070; }
    .combat-option-btn.threadweave-btn:not([disabled]):hover { border-color: #b39ddb; background: rgba(179,157,219,0.1); }
    .combat-option-btn.spellcast-btn { color: #ce93d8; border-color: #5a2060; }
    .combat-option-btn.spellcast-btn:not([disabled]):hover { border-color: #ce93d8; background: rgba(206,147,216,0.1); }
    .combat-option-btn.cancel-spell-btn { color: #888; border-color: #3a3028; }
    .combat-option-btn.cancel-spell-btn:hover { border-color: #ef5350; color: #ef5350; }
    .thread-progress-bar {
      display: flex; align-items: center; gap: 8px;
      background: #1e1a16; border-radius: 5px; padding: 8px 12px;
    }
    .thread-label { color: #888; font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.05em; min-width: 40px; }
    .thread-dots { display: flex; gap: 6px; flex: 1; }
    .thread-dot {
      width: 14px; height: 14px; border-radius: 50%; border: 2px solid #555;
      &.woven { background: #b39ddb; border-color: #b39ddb; }
      &.empty { background: none; }
    }
    .thread-count { color: #b39ddb; font-weight: 700; font-size: 1rem; min-width: 30px; text-align: right; }
    .spell-ready-banner {
      display: flex; align-items: center; gap: 6px; justify-content: center;
      background: rgba(206,147,216,0.15); border: 1px solid #ce93d8;
      border-radius: 6px; padding: 8px; color: #ce93d8;
      font-weight: 700; font-size: 0.95rem;
      mat-icon { font-size: 18px; height: 18px; width: 18px; }
    }
    .spell-heal-banner {
      display: flex; align-items: center; gap: 6px; justify-content: center;
      background: rgba(76,175,80,0.15); border: 1px solid #4caf50;
      border-radius: 6px; padding: 8px; color: #4caf50;
      font-weight: 700; font-size: 1rem;
      mat-icon { font-size: 18px; height: 18px; width: 18px; }
    }
    .spell-effect-banner {
      display: flex; align-items: center; gap: 6px; justify-content: center;
      border-radius: 6px; padding: 8px; font-weight: 700; font-size: 0.9rem;
      mat-icon { font-size: 18px; height: 18px; width: 18px; }
      &.buff { background: rgba(76,175,80,0.15); border: 1px solid #4caf50; color: #4caf50; }
      &.debuff { background: rgba(239,83,80,0.15); border: 1px solid #ef5350; color: #ef5350; }
    }

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
  logOpen = true;
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
    /** Alternativ zum Talent: Waffen-Fertigkeit. Wenn gesetzt, ist kein Karma erlaubt. */
    skillId?: number;
    /** Composite-Auswahlwert des Angriffsbasis-Dropdowns: 't:<id>' | 's:<id>' | ''. */
    attackSource?: string;
    weaponId?: number;
    bonusSteps: number;
    spendKarma: boolean;
    spendKarmaForDamage?: boolean;
    useBlattschuss?: boolean;
    aggressiveAttack: boolean;
    defensiveStance: boolean;
    /** Amulett-IDs → 'attack' | 'damage' | undefined (nicht angewandt). */
    amuletMode?: Record<number, 'attack' | 'damage'>;
  } = { open: false, bonusSteps: 0, spendKarma: false, spendKarmaForDamage: false, useBlattschuss: false, aggressiveAttack: false, defensiveStance: false, amuletMode: {} };

  /** Letztes Angriffsziel pro Kombattant (combatantId → defenderId) */
  private lastTargetMap = new Map<number, number>();
  /** Letzte gewählte Waffe pro Kombattant (combatantId → equipmentId) */
  private lastWeaponMap = new Map<number, number>();
  /** Letztes gewähltes Waffentalent pro Kombattant (combatantId → talentDefinitionId) */
  private lastTalentMap = new Map<number, number>();

  private autofightCombatants = new Set<number>();
  private autofightPending = false;

  effectDialog: {
    open: boolean;
    target?: CombatantState;
    name: string;
    description: string;
    rounds: number;
    negative: boolean;
  } = { open: false, name: '', description: '', rounds: -1, negative: false };

  gmEffectDialog: {
    open: boolean;
    targetId?: number;
    isMalus: boolean;
    magnitude: number;
    statKey: string;
    name: string;
    rounds: number;
  } = { open: false, isMalus: false, magnitude: 3, statKey: '', name: '', rounds: 1 };

  magischeMarkierungDialog: {
    open: boolean;
    actor?: CombatantState;
    targetId?: number;
    spendKarma: boolean;
  } = { open: false, spendKarma: false };

  magischeMarkierungModal: { open: boolean; result?: FreeActionResult } = { open: false };

  dodgeDialog: {
    open: boolean;
    defenderId?: number;
    attackTotal: number;
    bonusSteps: number;
    spendKarma: boolean;
  } = { open: false, attackTotal: 0, bonusSteps: 0, spendKarma: false };

  dodgeModal: { open: boolean; result?: DodgeResult } = { open: false };

  riposteDialog: {
    open: boolean;
    defender?: CombatantState;
    attackTotal: number;
    spendKarma: boolean;
  } = { open: false, attackTotal: 0, spendKarma: false };

  riposteModal: { open: boolean; result?: RiposteResult } = { open: false };

  combatEndedModal: { open: boolean; name?: string; round?: number } = { open: false };

  manoeuverDialog: {
    open: boolean;
    actor?: CombatantState;
    targetId?: number;
    spendKarma: boolean;
  } = { open: false, spendKarma: false };

  manoeuverModal: { open: boolean; result?: ManoeuverResult } = { open: false };

  tigersprungModal: { open: boolean; result?: TigersprungResult } = { open: false };

  lufttanzModal: { open: boolean; result?: LufttanzActivationResult } = { open: false };
  lufttanzAttackDialog: {
    open: boolean;
    actor?: CombatantState;
    bonusSteps: number;
    spendKarma: boolean;
    spendKarmaForDamage?: boolean;
  } = { open: false, bonusSteps: 0, spendKarma: false, spendKarmaForDamage: false };

  zweitwaffeDialog: {
    open: boolean;
    actor?: CombatantState;
    defenderId?: number;
    weaponId?: number;
    spendKarma: boolean;
  } = { open: false, spendKarma: false };

  nachtretenDialog: {
    open: boolean;
    actor?: CombatantState;
    defenderId?: number;
    spendKarma: boolean;
  } = { open: false, spendKarma: false };

  schwanzangriffDialog: {
    open: boolean;
    actor?: CombatantState;
    defenderId?: number;
    weaponId?: number;
    spendKarma: boolean;
  } = { open: false, spendKarma: false };

  aufspringenDialog: {
    open: boolean;
    combatant?: CombatantState;
  } = { open: false };

  standUpModal: { open: boolean; result?: StandUpResult } = { open: false };

  threadweaveDialog: {
    open: boolean;
    caster?: CombatantState;
    spellId?: number;
    spendKarma: boolean;
    /** Bei einem Zusatzfaden: Index der gewählten Option. */
    extraOptionIndex?: number;
  } = { open: false, spendKarma: false };

  threadweaveModal: { open: boolean; result?: ThreadweaveResult } = { open: false };

  spellCastDialog: {
    open: boolean;
    caster?: CombatantState;
    spellId?: number;
    targetId?: number;
    spendKarma: boolean;
    /** Amulett-IDs → 'attack' (Zauberwurf) | 'damage' (Schadenswurf). */
    amuletMode?: Record<number, 'attack' | 'damage'>;
  } = { open: false, spendKarma: false, amuletMode: {} };

  spellCastModal: { open: boolean; result?: SpellCastResult } = { open: false };

  tauntDialog: {
    open: boolean;
    actor?: CombatantState;
    targetId?: number;
    bonusSteps: number;
    spendKarma: boolean;
  } = { open: false, bonusSteps: 0, spendKarma: false };

  tauntModal: { open: boolean; result?: TauntResult } = { open: false };

  fearDialog: {
    open: boolean;
    actor?: CombatantState;
    targetId?: number;
    spendKarma: boolean;
  } = { open: false, spendKarma: false };

  fearModal: { open: boolean; result?: FearResult } = { open: false };

  fearResistModal: { open: boolean; result?: FearResistResult } = { open: false };

  /** Auswahldialog für Magie neutralisieren — via WebSocket bei allen Clients geöffnet. */
  neutralizeSelectModal: {
    open: boolean;
    actorCombatantId?: number;
    actorName?: string;
    rank?: number;
    /**
     * Snapshot der Effektliste beim Öffnen. WICHTIG: nicht im Template berechnen —
     * allActiveEffects() erzeugt neue Objekte, was im *ngFor eine Change-Detection-Endlosschleife auslöst.
     */
    effects: EffectChoice[];
    /** Auswahl: 'combatantId:effectId' */
    selection?: string;
    effectLevel: number;
    spendKarma: boolean;
  } = { open: false, effects: [], effectLevel: 5, spendKarma: false };

  neutralizeModal: { open: boolean; result?: NeutralizeMagicResult } = { open: false };

  acrobaticDialog: {
    open: boolean;
    actor?: CombatantState;
    spendKarma: boolean;
  } = { open: false, spendKarma: false };

  acrobaticModal: { open: boolean; result?: AcrobaticDefenseResult } = { open: false };

  combatSenseDialog: {
    open: boolean;
    actor?: CombatantState;
    targetId?: number;
    spendKarma: boolean;
  } = { open: false, spendKarma: false };

  combatSenseModal: { open: boolean; result?: CombatSenseResult } = { open: false };

  distractDialog: {
    open: boolean;
    actor?: CombatantState;
    targetId?: number;
    spendKarma: boolean;
  } = { open: false, spendKarma: false };

  distractModal: { open: boolean; result?: DistractResult } = { open: false };

  spotArmorFlawDialog: {
    open: boolean;
    actor?: CombatantState;
    targetId?: number;
    bonusSteps: number;
    spendKarma: boolean;
  } = { open: false, bonusSteps: 0, spendKarma: false };

  spotArmorFlawModal: { open: boolean; result?: SpotArmorFlawResult } = { open: false };

  ironWillDialog: {
    open: boolean;
    actor?: CombatantState;
    attackTotal: number;
    spendKarma: boolean;
  } = { open: false, attackTotal: 0, spendKarma: false };

  ironWillModal: { open: boolean; result?: IronWillResult } = { open: false };

  constructor(
    private route: ActivatedRoute,
    public router: Router,
    private combatService: CombatService,
    private characterService: CharacterService,
    private wsService: WebSocketService,
    private snack: MatSnackBar
  ) {}

  /** Letzte Runde, für die das Initiative-Modal bereits angezeigt wurde — verhindert Doppel-Trigger (HTTP + WS). */
  private shownInitiativeRound = 0;
  initiativeModal: { open: boolean; rolls?: InitiativeRollDetail[]; round?: number } = { open: false };

  /** Letzte vom Backend empfangene Modal-Version — verhindert wiederholtes Öffnen + Out-of-Order-WS-Broadcasts. */
  private lastSeenModalVersion = 0;

  ngOnInit(): void {
    const id = +this.route.snapshot.params['id'];
    this.combatService.findById(id).subscribe({
      next: s => {
        this.session = s;
        this.logEntries = this.toLogEntries(s.log);
        this.syncLiveModal(s);
      },
      error: err => {
        this.loadError = `Session konnte nicht geladen werden (${err.status ?? err.message}).`;
      }
    });
    this.characterService.findAll().subscribe(c => this.allCharacters = c);

    this.wsSub = this.wsService.subscribeToSession(id).subscribe(s => {
      this.session = s;
      this.logEntries = this.toLogEntries(s.log);
      this.syncLiveModal(s);
      this.scheduleAutofight();
    });
  }

  /**
   * Synchronisiert lokale Modal-Sichtbarkeit mit dem Server-State. Wird bei jedem Session-Update
   * (HTTP/WS) aufgerufen. Öffnet das passende Modal lokal oder schließt alle, wenn der Server
   * dismiss meldet.
   */
  private syncLiveModal(s: CombatSession): void {
    const m = s.liveModal;
    if (!m) return;
    // Out-of-order-Broadcasts ignorieren: nur Versionen größer als die zuletzt gesehene anwenden.
    if (m.version <= this.lastSeenModalVersion) return;
    this.lastSeenModalVersion = m.version;
    if (!m.type) {
      // Dismiss → alle Result-Modale lokal schließen
      this.closeAllResultModals();
      return;
    }
    this.openLocalModalForType(m.type, m.payload);
  }

  private openLocalModalForType(type: string, payload: any): void {
    // Vorher alle anderen Modale schließen, damit kein Stack entsteht.
    this.closeAllResultModals();
    switch (type) {
      case 'ATTACK_RESULT':
        this.lastResult = payload;
        this.resultModal = { open: true, result: payload };
        break;
      case 'INITIATIVE': {
        const round = payload?.round ?? 0;
        if (round > this.shownInitiativeRound) this.shownInitiativeRound = round;
        this.initiativeModal = { open: true, rolls: payload?.rolls ?? [], round };
        break;
      }
      case 'TIGERSPRUNG':
        this.tigersprungModal = { open: true, result: payload };
        break;
      case 'LUFTTANZ':
        this.lufttanzModal = { open: true, result: payload };
        break;
      case 'TAUNT':
        this.tauntModal = { open: true, result: payload };
        break;
      case 'DISTRACT':
        this.distractModal = { open: true, result: payload };
        break;
      case 'ACROBATIC_DEFENSE':
        this.acrobaticModal = { open: true, result: payload };
        break;
      case 'COMBAT_SENSE':
        this.combatSenseModal = { open: true, result: payload };
        break;
      case 'IRON_WILL':
        this.ironWillModal = { open: true, result: payload };
        break;
      case 'MANOEUVER':
        this.manoeuverModal = { open: true, result: payload };
        break;
      case 'SPOT_ARMOR_FLAW':
        this.spotArmorFlawModal = { open: true, result: payload };
        break;
      case 'DODGE':
        this.dodgeModal = { open: true, result: payload };
        break;
      case 'RIPOSTE':
        this.riposteModal = { open: true, result: payload };
        break;
      case 'COMBAT_ENDED':
        this.combatEndedModal = { open: true, name: payload?.name, round: payload?.round };
        break;
      case 'FEAR':
        this.fearModal = { open: true, result: payload };
        break;
      case 'FEAR_RESIST':
        this.fearResistModal = { open: true, result: payload };
        break;
      case 'NEUTRALIZE_MAGIC_SELECT':
        this.neutralizeSelectModal = {
          open: true,
          actorCombatantId: payload?.actorCombatantId,
          actorName: payload?.actorName,
          rank: payload?.rank,
          effects: this.allActiveEffects(), // einmaliger Snapshot — nicht im Template berechnen!
          selection: undefined,
          effectLevel: 5,
          spendKarma: false
        };
        break;
      case 'NEUTRALIZE_MAGIC':
        this.neutralizeModal = { open: true, result: payload };
        break;
    }
  }

  private closeAllResultModals(): void {
    this.resultModal.open = false;
    this.initiativeModal.open = false;
    this.tigersprungModal.open = false;
    this.lufttanzModal.open = false;
    if (this.tauntModal) this.tauntModal.open = false;
    if (this.distractModal) this.distractModal.open = false;
    if (this.acrobaticModal) this.acrobaticModal.open = false;
    if (this.combatSenseModal) this.combatSenseModal.open = false;
    if (this.ironWillModal) this.ironWillModal.open = false;
    if (this.manoeuverModal) this.manoeuverModal.open = false;
    if (this.spotArmorFlawModal) this.spotArmorFlawModal.open = false;
    if (this.dodgeModal) this.dodgeModal.open = false;
    if (this.riposteModal) this.riposteModal.open = false;
    if (this.combatEndedModal) this.combatEndedModal.open = false;
    if (this.fearModal) this.fearModal.open = false;
    if (this.fearResistModal) this.fearResistModal.open = false;
    if (this.neutralizeSelectModal) this.neutralizeSelectModal.open = false;
    if (this.neutralizeModal) this.neutralizeModal.open = false;
    if (this.standUpModal) this.standUpModal.open = false;
    if (this.threadweaveModal) this.threadweaveModal.open = false;
    if (this.spellCastModal) this.spellCastModal.open = false;
  }

  /** Wird von allen "Schließen"-Buttons der Result-Modale aufgerufen. Backend bumpt Version → alle Clients schließen. */
  dismissModal(): void {
    if (!this.session) return;
    // Sofortiges lokales Schließen für UX-Schnelligkeit; Server-Broadcast bestätigt es.
    this.closeAllResultModals();
    this.combatService.dismissModal(this.session.id).subscribe({
      next: s => { this.session = s; this.lastSeenModalVersion = s.liveModal?.version ?? this.lastSeenModalVersion; },
      error: err => { console.warn('Modal-Dismiss fehlgeschlagen:', err); }
    });
  }

  /**
   * Öffnet das Initiative-Modal, wenn die Session eine neue Initiative-Probe meldet
   * (lastInitiativeRollRound > zuletzt gezeigte Runde + Rolls vorhanden).
   */
  private maybeOpenInitiativeModal(s: CombatSession): void {
    const round = s.lastInitiativeRollRound ?? 0;
    if (round > this.shownInitiativeRound && (s.lastInitiativeRolls?.length ?? 0) > 0) {
      this.shownInitiativeRound = round;
      this.initiativeModal = { open: true, rolls: s.lastInitiativeRolls, round };
    }
  }

  ngOnDestroy(): void {
    this.autofightCombatants.clear();
    const id = this.session?.id ?? +this.route.snapshot.params['id'];
    this.wsService.unsubscribeFromSession(id);
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

  cn(c: CombatantState): string {
    return c.displayName ?? c.character.name;
  }

  nameColor(name: string): string {
    const isHero = this.heroes().some(c => c.character.name === name);
    return isHero ? '#a5d6a7' : '#ef9a9a';
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

  // --- Ansagephase ---

  setDeclaredStance(c: CombatantState, stance: 'NONE' | 'AGGRESSIVE' | 'DEFENSIVE'): void {
    c.declaredStance = stance as any;
  }

  setDeclaredActionType(c: CombatantState, actionType: 'WEAPON' | 'SPELL'): void {
    c.declaredActionType = actionType as any;
  }

  confirmDeclaration(c: CombatantState): void {
    if (!this.session) return;
    this.combatService.declareAction(this.session.id, c.id, c.declaredStance, c.declaredActionType)
      .subscribe(s => { this.session = s; this.syncLiveModal(s); });
  }

  undeclare(c: CombatantState): void {
    if (!this.session) return;
    this.combatService.undeclareAction(this.session.id, c.id)
      .subscribe(s => this.session = s);
  }

  declarationProgress(): string {
    const combs = (this.session?.combatants ?? []).filter(c => !c.defeated);
    const done = combs.filter(c => c.hasDeclared).length;
    return `${done}/${combs.length}`;
  }

  openAttackDialog(attacker: CombatantState): void {
    const equipment = attacker.character.equipment ?? [];
    const talents = attacker.character.talents ?? [];

    // Letzte Waffe wiederherstellen, sonst beste Waffe
    const lastWeaponId = this.lastWeaponMap.get(attacker.id!);
    const lastWeapon = lastWeaponId != null
      ? equipment.find(e => e.id === lastWeaponId && e.type === 'WEAPON')
      : undefined;
    const bestWeapon = equipment
      .filter(e => e.type === 'WEAPON')
      .sort((a, b) => b.damageBonus - a.damageBonus)[0];
    const weapon = lastWeapon ?? bestWeapon;

    // Letztes Talent wiederherstellen, sonst Auto-Wahl basierend auf Waffe
    const lastTalentId = this.lastTalentMap.get(attacker.id!);
    const lastTalent = lastTalentId != null
      ? talents.find(t => t.talentDefinition.id === lastTalentId)
      : undefined;
    const talent = lastTalent ?? this.defaultTalentForWeapon(attacker, weapon?.clawWeapon);

    // Letztes Ziel als Default (falls noch vorhanden und nicht besiegt)
    const lastDefenderId = this.lastTargetMap.get(attacker.id!);
    const lastDefender = lastDefenderId != null
      ? this.session?.combatants.find(c => c.id === lastDefenderId && !c.defeated)
      : undefined;

    this.attackDialog = {
      open: true,
      attacker,
      defenderId: lastDefender?.id,
      talentId: talent?.talentDefinition.id,
      skillId: undefined,
      attackSource: talent ? 't:' + talent.talentDefinition.id : '',
      weaponId: weapon?.id,
      bonusSteps: 0,
      spendKarma: false,
      spendKarmaForDamage: false,
      useBlattschuss: false,
      aggressiveAttack: false,
      defensiveStance: false,
      amuletMode: {}
    };
    // Dialog-Status für andere Spieler sichtbar machen
    this.pushDialogState(
      attacker.id,
      this.currentAttackActionLabel(),
      lastDefender?.character.name,
      weapon?.name
    );
  }

  /** True wenn Blattschuss verwendet werden kann: Talent vorhanden, RANGED-Talent gewählt, nicht bereits diese Runde benutzt. */
  canUseBlattschuss(dialog: { attacker?: CombatantState; talentId?: number }): boolean {
    if (!dialog.attacker || dialog.attacker.blattschussUsedThisRound) return false;
    const hasTalent = (dialog.attacker.character.talents ?? [])
      .some(t => t.talentDefinition.name === 'Blattschuss');
    if (!hasTalent) return false;
    const talentName = (dialog.attacker.character.talents ?? [])
      .find(t => t.talentDefinition.id === dialog.talentId)?.talentDefinition.name ?? '';
    return talentName === 'Projektilwaffen' || talentName === 'Wurfwaffen';
  }

  /** Anwender des aktuell ausstehenden Blattschuss-Angriffs (per Result-Modal-Akteursnamen). */
  blattschussActor(): CombatantState | undefined {
    const r = this.resultModal.result;
    if (!r?.blattschussCanAddKarma || !this.session) return undefined;
    return this.session.combatants.find(c => c.character.name === r.actorName);
  }

  /** Direkt vom Kombattanten-Button: Karma nachschießen (auch nach Schließen des Modals). */
  resumeBlattschuss(actor: CombatantState): void {
    if (!this.session || actor.pendingBlattschussDefenderId < 0) return;
    this.combatService.performBlattschussAddKarma(this.session.id, actor.id).subscribe({
      next: result => {
        this.lastResult = result;
        this.resultModal = { open: true, result };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => this.snack.open('Fehler: ' + (err?.error?.message ?? err.message), 'OK', { duration: 5000 })
    });
  }

  /** Setzt einen weiteren Karmawürfel auf den ausstehenden Blattschuss-Angriff. */
  addBlattschussKarma(): void {
    const r = this.resultModal.result;
    if (!this.session || !r?.blattschussCanAddKarma) return;
    const actor = this.session.combatants.find(c => c.character.name === r.actorName);
    if (!actor) return;
    this.combatService.performBlattschussAddKarma(this.session.id, actor.id).subscribe({
      next: result => {
        this.lastResult = result;
        this.resultModal = { open: true, result };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => this.snack.open('Fehler: ' + (err?.error?.message ?? err.message), 'OK', { duration: 5000 })
    });
  }

  /**
   * Wählt das passende Angriffstalent: bei Krallenhand "Waffenloser Kampf", sonst
   * das ranghöchste Angriffstalent. Fallback wenn keine Talente: undefined.
   */
  private defaultTalentForWeapon(attacker: CombatantState, isClaw?: boolean) {
    const talents = attacker.character.talents ?? [];
    if (isClaw) {
      const unarmed = talents.find(t => t.talentDefinition.name === 'Waffenloser Kampf');
      if (unarmed) return unarmed;
    }
    return talents
      .filter(t => t.talentDefinition.attackTalent)
      .sort((a, b) => b.rank - a.rank)[0];
  }

  /** Reagiert auf Waffenwechsel im Angriffsdialog: bei Wechsel auf Krallenhand wird "Waffenloser Kampf" vorgewählt. */
  onAttackWeaponChange(weaponId: number | undefined): void {
    this.attackDialog.weaponId = weaponId;
    if (!this.attackDialog.attacker || weaponId == null) return;
    const weapon = (this.attackDialog.attacker.character.equipment ?? [])
      .find(e => e.id === weaponId);
    if (weapon?.clawWeapon) {
      const unarmed = (this.attackDialog.attacker.character.talents ?? [])
        .find(t => t.talentDefinition.name === 'Waffenloser Kampf');
      if (unarmed) {
        this.attackDialog.talentId = unarmed.talentDefinition.id;
        this.attackDialog.skillId = undefined;
        this.attackDialog.attackSource = 't:' + unarmed.talentDefinition.id;
      }
    }
    // Dialog-Status mit aktualisierter Waffe pushen
    this.pushDialogState(
      this.attackDialog.attacker.id,
      this.currentAttackActionLabel(),
      this.combatantNameById(this.attackDialog.defenderId),
      weapon?.name
    );
  }

  /** True wenn die im Dialog ausgewählte Waffe eine Krallenhand ist. */
  isClawWeaponSelected(dialog: { attacker?: CombatantState; weaponId?: number }): boolean {
    if (!dialog.attacker || !dialog.weaponId) return false;
    return !!(dialog.attacker.character.equipment ?? [])
      .find(e => e.id === dialog.weaponId)?.clawWeapon;
  }

  /** True, wenn die im Dialog gewählte Waffe zweihändig ist (→ Schild wird abgelegt). */
  isTwoHandedWeaponSelected(dialog: { attacker?: CombatantState; weaponId?: number }): boolean {
    if (!dialog.attacker || !dialog.weaponId) return false;
    return !!(dialog.attacker.character.equipment ?? [])
      .find(e => e.id === dialog.weaponId)?.twoHanded;
  }

  /** Toggle für Karma-auf-Schaden, mit Karma-Ausreichend-Check (1 für Angriff + 1 für Schaden). */
  toggleKarmaForDamage(dialog: { attacker?: CombatantState; spendKarma: boolean; spendKarmaForDamage?: boolean }): void {
    const karma = dialog.attacker?.currentKarma ?? 0;
    const required = (dialog.spendKarma ? 1 : 0) + 1;
    if (!dialog.spendKarmaForDamage && karma < required) return;
    dialog.spendKarmaForDamage = !dialog.spendKarmaForDamage;
  }

  possibleTargets(actor?: CombatantState): CombatantState[] {
    const a = actor ?? this.attackDialog.attacker;
    const excludeId = a?.id;
    const base = (this.session?.combatants ?? []).filter(c => c.id !== excludeId && !c.defeated);
    // Angriffsdialog: Reichweite der gewählten Angriffsart (nur wenn Karte aktiv).
    // Manövrieren/Zweitwaffe (actor gesetzt) sind Nahkampf → angrenzend.
    if (actor) return this.filterByMapRange(actor, base, 1);
    return this.filterByMapRange(a, base, this.currentAttackRange());
  }

  // --- Kampfkarte: Fenster + Reichweiten ---

  openMapWindow(): void {
    if (!this.session) return;
    window.open(`/combat/${this.session.id}/map`, 'earthdawn-map-' + this.session.id,
      'width=1280,height=860');
  }

  enableMap(): void {
    if (!this.session) return;
    const w = prompt('Kartenbreite in Feldern (8–60):', '24');
    if (w === null) return;
    const h = prompt('Kartenhöhe in Feldern (6–40):', '16');
    if (h === null) return;
    this.combatService.configureMap(this.session.id, true, Number(w) || 24, Number(h) || 16)
      .subscribe({
        next: s => { this.session = s; this.openMapWindow(); },
        error: err => this.snack.open('Fehler: ' + (err?.error?.message ?? err?.message), 'OK', { duration: 4000 })
      });
  }

  /** Hexdistanz zwischen zwei platzierten Kombattanten; null wenn einer nicht platziert ist. */
  mapDistanceBetween(a?: CombatantState, b?: CombatantState): number | null {
    if (!this.session?.mapEnabled) return null;
    if (a?.mapQ == null || a?.mapR == null || b?.mapQ == null || b?.mapR == null) return null;
    return hexDistance(a.mapQ, a.mapR, b.mapQ, b.mapR);
  }

  /**
   * Filtert Ziele nach Kartendistanz. Kein Filter, wenn die Karte aus ist, der Akteur nicht
   * platziert ist oder keine Reichweite bestimmbar ist; unplatzierte Ziele bleiben wählbar.
   */
  private filterByMapRange(actor: CombatantState | undefined, targets: CombatantState[],
                           maxRange: number | null): CombatantState[] {
    if (!this.session?.mapEnabled || maxRange == null) return targets;
    if (actor?.mapQ == null || actor?.mapR == null) return targets;
    return targets.filter(t => {
      const d = this.mapDistanceBetween(actor, t);
      return d == null || d <= maxRange;
    });
  }

  /** Maximale Reichweite der aktuell im Angriffsdialog gewählten Angriffsart (Felder). */
  private currentAttackRange(): number | null {
    if (!this.attackDialog.open) return null;
    if (this.resolveActionType() === 'RANGED_ATTACK') {
      const w = this.attackWeaponsFor(this.attackDialog.attacker)
        .find(e => e.id === this.attackDialog.weaponId);
      // Weit-Reichweite der Waffe; ohne gepflegte Reichweite kein Filter
      return w?.rangeLong ?? w?.rangeMedium ?? w?.rangeShort ?? null;
    }
    return 1; // Nahkampf: gegenüberstehen = angrenzendes Feld
  }

  /** Waffen-Angriffstalente, die im Angriffsdialog auswählbar sind. Spruchzauberei läuft über den
   *  Zauber-/Wirken-Flow und gehört nicht in den Waffenangriff. */
  private static readonly WEAPON_ATTACK_TALENTS = ['Nahkampfwaffen', 'Projektilwaffen', 'Wurfwaffen', 'Waffenloser Kampf'];

  attackTalentsOf(c?: CombatantState) {
    return (c?.character.talents ?? [])
      .filter(t => CombatTrackerComponent.WEAPON_ATTACK_TALENTS.includes(t.talentDefinition.name))
      .sort((a, b) => b.rank - a.rank);
  }

  weaponsOf(c?: CombatantState) {
    return (c?.character.equipment ?? []).filter(e => e.type === 'WEAPON').sort((a, b) => b.damageBonus - a.damageBonus);
  }

  /**
   * Waffen, die im Angriffsdialog zum gewählten Talent/zur Fertigkeit passen:
   * alle ohne Zuordnung (rückwärtskompatibel) plus die dem gewählten Angriffstalent zugeordneten.
   * Ohne gewähltes Talent werden alle Waffen angeboten.
   */
  attackWeaponsFor(c?: CombatantState) {
    const all = this.weaponsOf(c);
    const src = this.selectedAttackSourceName();
    if (!src) return all;
    return all.filter(w => !w.attackTalentName || w.attackTalentName === src);
  }

  /** Geladene Verzweiflungsschlag-Amulette des Kombattanten, gefiltert nach Zauber/physisch. */
  amuletsOf(c: CombatantState | undefined, forSpell: boolean) {
    return (c?.character.equipment ?? [])
      .filter(e => e.type === 'AMULET' && e.charged !== false && !!e.amuletForSpell === forSpell);
  }

  /** Schaltet ein Amulett zwischen 'attack', 'damage' und nicht-gewählt um. */
  toggleAmuletMode(modeMap: Record<number, 'attack' | 'damage'> | undefined, id: number | undefined, mode: 'attack' | 'damage'): void {
    if (!modeMap || id == null) return;
    if (modeMap[id] === mode) delete modeMap[id];
    else modeMap[id] = mode;
  }

  /** Equipment-IDs der Amulette, die im angegebenen Modus angewandt werden. */
  selectedAmuletIds(modeMap: Record<number, 'attack' | 'damage'> | undefined, mode: 'attack' | 'damage'): number[] {
    if (!modeMap) return [];
    return Object.keys(modeMap)
      .filter(k => modeMap[+k] === mode)
      .map(k => +k);
  }

  /** Name der aktuell gewählten Angriffsbasis (Talent ODER Waffen-Fertigkeit). */
  private selectedAttackSourceName(): string {
    if (this.attackDialog.skillId != null) {
      return (this.attackDialog.attacker?.character.skills ?? [])
        .find(s => s.skillDefinition.id === this.attackDialog.skillId)?.skillDefinition.name ?? '';
    }
    return (this.attackDialog.attacker?.character.talents ?? [])
      .find(t => t.talentDefinition.id === this.attackDialog.talentId)?.talentDefinition.name ?? '';
  }

  private resolveActionType(): AttackActionRequest['actionType'] {
    const name = this.selectedAttackSourceName();
    if (name === 'Projektilwaffen' || name === 'Wurfwaffen') return 'RANGED_ATTACK';
    if (name === 'Spruchzauberei') return 'SPELL_ATTACK';
    return 'MELEE_ATTACK';
  }

  /** Waffen-Fertigkeiten des Kombattanten, die wie Angriffstalente nutzbar sind. */
  weaponSkillsOf(c?: CombatantState) {
    const names = ['Nahkampfwaffen', 'Projektilwaffen', 'Wurfwaffen'];
    return (c?.character.skills ?? [])
      .filter(s => names.includes(s.skillDefinition.name))
      .sort((a, b) => b.rank - a.rank);
  }

  /** Auswahl im Angriffsbasis-Dropdown ('t:<id>' Talent | 's:<id>' Fertigkeit | '' keine). */
  onAttackSourceChange(val: string): void {
    this.attackDialog.attackSource = val;
    if (val?.startsWith('s:')) {
      this.attackDialog.skillId = +val.slice(2);
      this.attackDialog.talentId = undefined;
      this.attackDialog.spendKarma = false; // Fertigkeiten erlauben kein Karma
    } else if (val?.startsWith('t:')) {
      this.attackDialog.talentId = +val.slice(2);
      this.attackDialog.skillId = undefined;
    } else {
      this.attackDialog.talentId = undefined;
      this.attackDialog.skillId = undefined;
    }
    // Gewählte Waffe verwerfen, wenn sie zum neuen Talent nicht (mehr) passt.
    if (this.attackDialog.weaponId != null
        && !this.attackWeaponsFor(this.attackDialog.attacker).some(w => w.id === this.attackDialog.weaponId)) {
      this.attackDialog.weaponId = undefined;
    }
    if (this.attackDialog.attacker) {
      this.pushDialogState(
        this.attackDialog.attacker.id,
        this.currentAttackActionLabel(),
        this.combatantNameById(this.attackDialog.defenderId),
        this.weaponNameById(this.attackDialog.attacker, this.attackDialog.weaponId)
      );
    }
  }

  performAttack(): void {
    if (!this.session || !this.attackDialog.attacker || !this.attackDialog.defenderId) return;
    const req: AttackActionRequest = {
      sessionId: this.session.id,
      attackerCombatantId: this.attackDialog.attacker.id,
      defenderCombatantId: this.attackDialog.defenderId,
      actionType: this.resolveActionType(),
      talentId: this.attackDialog.talentId ?? undefined,
      skillId: this.attackDialog.skillId ?? undefined,
      weaponId: this.attackDialog.weaponId ?? undefined,
      bonusSteps: this.attackDialog.bonusSteps,
      spendKarma: this.attackDialog.skillId != null ? false : this.attackDialog.spendKarma,
      spendKarmaForDamage: this.attackDialog.spendKarmaForDamage,
      useBlattschuss: this.attackDialog.useBlattschuss,
      aggressiveAttack: this.attackDialog.aggressiveAttack,
      defensiveStance: this.attackDialog.defensiveStance,
      amuletAttackIds: this.selectedAmuletIds(this.attackDialog.amuletMode, 'attack'),
      amuletDamageIds: this.selectedAmuletIds(this.attackDialog.amuletMode, 'damage')
    };
    this.combatService.performAttack(req).subscribe({
      next: result => {
        this.lastResult = result;
        this.lastTargetMap.set(req.attackerCombatantId, req.defenderCombatantId);
        if (req.weaponId != null) this.lastWeaponMap.set(req.attackerCombatantId, req.weaponId);
        else this.lastWeaponMap.delete(req.attackerCombatantId);
        if (req.talentId != null) this.lastTalentMap.set(req.attackerCombatantId, req.talentId);
        else this.lastTalentMap.delete(req.attackerCombatantId);
        this.clearDialogState(req.attackerCombatantId);
        this.attackDialog.open = false;
        this.resultModal = { open: true, result };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => {
        this.snack.open(err?.error?.message ?? 'Angriff fehlgeschlagen.', 'OK', { duration: 3500 });
      }
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
    const base = c.character.physicalDefense ?? Math.floor((c.character.dexterity + 3) / 2);
    const shieldBonus = (c.character.equipment ?? [])
      .filter(e => e.type === 'SHIELD' && e.active !== false)
      .reduce((sum, e) => sum + (e.physicalDefenseBonus ?? 0), 0);
    return base + (c.character.physicalDefenseBonus ?? 0) + shieldBonus;
  }

  sd(c: CombatantState): number {
    const base = c.character.spellDefense ?? Math.floor((c.character.perception + 3) / 2);
    const shieldBonus = (c.character.equipment ?? [])
      .filter(e => e.type === 'SHIELD' && e.active !== false)
      .reduce((sum, e) => sum + (e.mysticDefenseBonus ?? 0), 0);
    return base + (c.character.spellDefenseBonus ?? 0) + shieldBonus;
  }

  socD(c: CombatantState): number {
    return (c.character.socialDefense ?? Math.floor((c.character.charisma + 3) / 2))
      + (c.character.socialDefenseBonus ?? 0);
  }

  private effectiveDefense(c: CombatantState, stat: string): number {
    return (c.activeEffects ?? []).flatMap(e => e.modifiers)
      .filter(m => m.targetStat === stat && m.operation === 'ADD')
      .reduce((sum, m) => sum + m.value, 0);
  }

  effectivePd(c: CombatantState): number { return this.pd(c) + this.effectiveDefense(c, 'PHYSICAL_DEFENSE'); }
  effectiveSd(c: CombatantState): number { return this.sd(c) + this.effectiveDefense(c, 'SPELL_DEFENSE'); }
  effectiveSocD(c: CombatantState): number { return this.socD(c) + this.effectiveDefense(c, 'SOCIAL_DEFENSE'); }

  /** Aufschlüsselung einer Verteidigung (Basis + alle Boni/Mali) als mehrzeiliger Tooltip. */
  defenseTooltip(c: CombatantState, stat: 'PHYSICAL_DEFENSE' | 'SPELL_DEFENSE' | 'SOCIAL_DEFENSE'): string {
    const ch = c.character;
    let label: string, base: number, configBonus: number;
    let shieldField: 'physicalDefenseBonus' | 'mysticDefenseBonus' | null;
    let total: number;
    if (stat === 'PHYSICAL_DEFENSE') {
      label = 'KV – Körperliche Verteidigung';
      base = ch.physicalDefense ?? Math.floor((ch.dexterity + 3) / 2);
      configBonus = ch.physicalDefenseBonus ?? 0;
      shieldField = 'physicalDefenseBonus';
      total = this.effectivePd(c);
    } else if (stat === 'SPELL_DEFENSE') {
      label = 'MV – Mystische Verteidigung';
      base = ch.spellDefense ?? Math.floor((ch.perception + 3) / 2);
      configBonus = ch.spellDefenseBonus ?? 0;
      shieldField = 'mysticDefenseBonus';
      total = this.effectiveSd(c);
    } else {
      label = 'SV – Soziale Verteidigung';
      base = ch.socialDefense ?? Math.floor((ch.charisma + 3) / 2);
      configBonus = ch.socialDefenseBonus ?? 0;
      shieldField = null;
      total = this.effectiveSocD(c);
    }

    const sign = (v: number) => (v >= 0 ? '+' : '') + v;
    const lines: string[] = [label, 'Basis: ' + base];
    if (configBonus !== 0) lines.push('Konfig-Bonus: ' + sign(configBonus));
    if (shieldField) {
      for (const e of (ch.equipment ?? [])) {
        if (e.type !== 'SHIELD') continue;
        const v = (shieldField === 'physicalDefenseBonus' ? e.physicalDefenseBonus : e.mysticDefenseBonus) ?? 0;
        if (v === 0) continue;
        if (e.active === false) lines.push('Schild ' + e.name + ': ' + sign(v) + ' (abgelegt, zählt nicht)');
        else lines.push('Schild ' + e.name + ': ' + sign(v));
      }
    }
    for (const eff of (c.activeEffects ?? [])) {
      for (const m of (eff.modifiers ?? [])) {
        if (m.targetStat === stat && m.operation === 'ADD' && m.value !== 0) {
          lines.push(eff.name + ': ' + sign(m.value));
        }
      }
    }
    lines.push('— — —');
    lines.push('Gesamt: ' + total);
    return lines.join('\n');
  }

  pa(c: CombatantState): number {
    const equipBonus = (c.character.equipment ?? []).filter(e => e.type === 'ARMOR').reduce((s, e) => s + (e.physicalArmor ?? 0), 0);
    return (c.character.physicalArmor ?? 0) + equipBonus;
  }

  ma(c: CombatantState): number {
    const equipBonus = (c.character.equipment ?? []).filter(e => e.type === 'ARMOR').reduce((s, e) => s + (e.mysticalArmor ?? 0), 0);
    // Natürliche mystische Rüstung aus Willenskraft (ED4): min(6, WIL/5)
    const natural = Math.min(6, Math.max(0, Math.floor((c.character.willpower ?? 0) / 5)));
    const base = c.character.mysticArmor ?? natural;
    return base + equipBonus;
  }

  damagePercent(c: CombatantState): number {
    return Math.min(100, (c.currentDamage / this.ur(c)) * 100);
  }

  woundDots(c: CombatantState): boolean[] {
    return Array.from({ length: 5 }, (_, i) => i < c.wounds);
  }

  isNoKarma(c: CombatantState): boolean {
    return c.character.discipline?.name === 'Keine Disziplin';
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
    return ['INITIATIVE', 'ROUND_CHANGE', 'EFFECT_ADDED', 'EFFECT_REMOVED', 'VALUE_CHANGED', 'MAP_MOVE'].includes(actionType);
  }

  /** Protokoll-Einträge fürs UI: chronologisch absteigend (neueste oben) + geparste Wurf-Details. */
  private toLogEntries(log: any[] | undefined): any[] {
    return (log ?? [])
      .map(e => ({ ...e, details: e.rollDetailsJson ? this.safeParseJson(e.rollDetailsJson) : null }))
      .reverse();
  }

  private safeParseJson(s: string): any {
    try { return JSON.parse(s); } catch { return null; }
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

  hasMagischeMarkierungTalent(c: CombatantState): boolean {
    return (c.character.talents ?? []).some(t => t.talentDefinition.name === 'Magische Markierung');
  }

  magischeMarkierungTargets(): CombatantState[] {
    return (this.session?.combatants ?? [])
      .filter(c => c.id !== this.magischeMarkierungDialog.actor?.id && !c.defeated);
  }

  openMagischeMarkierungDialog(actor: CombatantState): void {
    this.magischeMarkierungDialog = { open: true, actor, targetId: undefined, spendKarma: false };
  }

  performMagischeMarkierung(): void {
    const actor = this.magischeMarkierungDialog.actor;
    if (!this.session || !actor || !this.magischeMarkierungDialog.targetId) return;
    const talent = (actor.character.talents ?? [])
      .find(t => t.talentDefinition.name === 'Magische Markierung');
    if (!talent) return;
    const req: FreeActionRequest = {
      sessionId: this.session.id,
      actorCombatantId: actor.id,
      targetCombatantId: this.magischeMarkierungDialog.targetId,
      talentId: talent.talentDefinition.id,
      bonusSteps: 0,
      spendKarma: this.magischeMarkierungDialog.spendKarma
    };
    this.combatService.performFreeAction(this.session.id, req).subscribe({
      next: result => {
        this.magischeMarkierungDialog.open = false;
        this.magischeMarkierungModal = { open: true, result };
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

  // --- Spellcasting ---

  private readonly MAGIC_DISCIPLINES = ['Elementarist', 'Illusionist', 'Magier', 'Geisterbeschwörer'];

  isMagicCombatant(c: CombatantState): boolean {
    return this.MAGIC_DISCIPLINES.includes(c.character.discipline?.name ?? '');
  }

  /** Talente, die als Zaubermatrize zählen (normal oder erweitert). */
  private static readonly MATRIX_TALENTS = ['Zaubermatritze', 'Erweiterte Matrize'];

  /** Fadenweben-Talent je Disziplin — spiegelt SpellService.WEAVING_TALENT_MAP. */
  private static readonly WEAVING_TALENT_BY_DISCIPLINE: Record<string, string> = {
    'Elementarist': 'Elementarismus',
    'Illusionist': 'Illusionismus',
    'Magier': 'Magie',
    'Geisterbeschwörer': 'Geisterbeschwörung'
  };

  /** IDs aller Zauber, die in einer (normalen oder erweiterten) Matrize des Charakters liegen. */
  matrixSpellIds(c?: CombatantState): Set<number> {
    const ids = new Set<number>();
    for (const t of c?.character.talents ?? []) {
      if (CombatTrackerComponent.MATRIX_TALENTS.includes(t.talentDefinition.name) && t.assignedSpell) {
        ids.add(t.assignedSpell.id);
      }
    }
    return ids;
  }

  /** Nur Zauber, die in einer Matrize einliegen — im Kampf sind ausschließlich diese wirkbar. */
  spellsOf(c?: CombatantState): CharacterSpell[] {
    const inMatrix = this.matrixSpellIds(c);
    return (c?.character.spells ?? []).filter(s => inMatrix.has(s.spellDefinition.id));
  }

  readySpellsOf(c?: CombatantState): CharacterSpell[] {
    if (!c) return [];
    const inMatrix = this.matrixSpellIds(c);
    // If a spell is being prepared and threads are complete, only that spell can be cast
    if (c.preparingSpellId && c.threadsWoven >= c.threadsRequired) {
      return (c.character.spells ?? []).filter(s => s.spellDefinition.id === c.preparingSpellId && inMatrix.has(s.spellDefinition.id));
    }
    // Also show 0-thread spells that can be cast without weaving — nur aus einer Matrize
    return (c.character.spells ?? []).filter(s => s.spellDefinition.threads === 0 && inMatrix.has(s.spellDefinition.id));
  }

  canCastSpell(c: CombatantState): boolean {
    return this.isMagicCombatant(c) && this.readySpellsOf(c).length > 0;
  }

  spellNameOf(c: CombatantState): string {
    if (!c.preparingSpellId) return '';
    const spell = (c.character.spells ?? []).find(s => s.spellDefinition.id === c.preparingSpellId);
    return spell?.spellDefinition.name ?? '???';
  }

  openThreadweaveDialog(c: CombatantState): void {
    const spellName = c.preparingSpellId ? this.spellNameOf(c) : (this.spellsOf(c)[0]?.spellDefinition.name);
    this.pushDialogState(c.id, 'WEAVE', undefined, undefined, spellName);
    this.threadweaveDialog = {
      open: true,
      caster: c,
      spellId: c.preparingSpellId ?? (this.spellsOf(c).length > 0 ? this.spellsOf(c)[0].spellDefinition.id : undefined),
      spendKarma: false,
      extraOptionIndex: undefined
    };
  }

  // --- Zusatzfäden ---

  /** Zauber, der im Fadenweben-Dialog gerade gewählt ist. */
  threadweaveSpell(): SpellDefinition | undefined {
    const c = this.threadweaveDialog.caster;
    const id = this.threadweaveDialog.spellId;
    if (!c || !id) return undefined;
    return (c.character.spells ?? []).find(s => s.spellDefinition.id === id)?.spellDefinition;
  }

  threadweaveOptions(): SpellThreadOption[] {
    return this.threadweaveSpell()?.threadOptions ?? [];
  }

  /** Fadenweben-Rang = Obergrenze für Zusatzfäden. 0 = Talent fehlt. */
  weavingRankOf(c?: CombatantState): number {
    const talentName = CombatTrackerComponent.WEAVING_TALENT_BY_DISCIPLINE[c?.character.discipline?.name ?? ''];
    if (!talentName) return 0;
    return (c?.character.talents ?? []).find(t => t.talentDefinition.name === talentName)?.rank ?? 0;
  }

  /** Anzahl bereits gewobener Zusatzfäden (CSV-Länge). */
  extraThreadCountOf(c?: CombatantState): number {
    const csv = c?.extraThreadChoices;
    if (!csv) return 0;
    return csv.split(',').filter(t => t.trim() !== '').length;
  }

  /** Pflichtfäden für diesen Zauber — erweiterte Matrize hat bereits einen Faden vorgewoben. */
  private requiredThreadsFor(c: CombatantState, spell: SpellDefinition): number {
    const enhanced = (c.character.talents ?? []).some(
      t => t.talentDefinition.name === 'Erweiterte Matrize' && t.assignedSpell?.id === spell.id);
    return Math.max(0, spell.threads - (enhanced ? 1 : 0));
  }

  /**
   * Ist der nächste Faden ein Zusatzfaden? Spiegelt die Backend-Regel: alle Pflichtfäden gewoben.
   * Vor Beginn der Vorbereitung zählt der Bedarf laut Zauber (0 → sofort Zusatzfaden, z.B. Blitz).
   */
  threadweaveIsExtra(): boolean {
    const c = this.threadweaveDialog.caster;
    const spell = this.threadweaveSpell();
    if (!c || !spell) return false;
    if (c.preparingSpellId === spell.id) return c.threadsWoven >= c.threadsRequired;
    return this.requiredThreadsFor(c, spell) === 0;
  }

  /** Sind bereits alle erlaubten Zusatzfäden (= Fadenweben-Rang) gewoben? */
  threadweaveExtraExhausted(): boolean {
    const c = this.threadweaveDialog.caster;
    if (!c || !this.threadweaveIsExtra()) return false;
    return this.extraThreadCountOf(c) >= this.weavingRankOf(c);
  }

  /** Weben blockiert: kein Zauber, oder Zusatzfaden ohne (mögliche) Option. */
  threadweaveBlocked(): boolean {
    if (!this.threadweaveDialog.spellId) return true;
    if (!this.threadweaveIsExtra()) return false;
    if (this.threadweaveOptions().length === 0) return true;
    if (this.threadweaveExtraExhausted()) return true;
    return this.threadweaveDialog.extraOptionIndex === undefined;
  }

  trackByOptionIndex(index: number): number {
    return index;
  }

  /**
   * Grundstufe des Zauberschadens ohne die aufgeschlagenen Boni — also Wirkungsstufe + WIL-Stufe.
   * Übererfolge und Zusatzfäden werden in der Klammer separat ausgewiesen.
   */
  spellDamageBaseStep(r: SpellCastResult): number {
    return (r.damageStep ?? 0) - (r.damageStepBonus ?? 0) - (r.extraThreadEffectStep ?? 0);
  }

  performThreadweave(): void {
    if (!this.session || !this.threadweaveDialog.caster || !this.threadweaveDialog.spellId) return;
    const req: ThreadweaveRequest = {
      sessionId: this.session.id,
      casterCombatantId: this.threadweaveDialog.caster.id,
      spellId: this.threadweaveDialog.spellId,
      spendKarma: this.threadweaveDialog.spendKarma,
      extraThreadOptionIndex: this.threadweaveIsExtra() ? this.threadweaveDialog.extraOptionIndex : undefined
    };
    this.combatService.weaveThread(this.session.id, req).subscribe({
      next: result => {
        if (this.threadweaveDialog.caster) this.clearDialogState(this.threadweaveDialog.caster.id);
        this.threadweaveDialog.open = false;
        this.threadweaveModal = { open: true, result };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => {
        const msg = err?.error?.message ?? err?.message ?? JSON.stringify(err);
        this.snack.open('Fehler: ' + msg, 'OK', { duration: 5000 });
      }
    });
  }

  openSpellCastDialog(c: CombatantState): void {
    const readySpells = this.readySpellsOf(c);
    const firstSpell = readySpells[0]?.spellDefinition;
    this.pushDialogState(c.id, 'SPELL', undefined, undefined, firstSpell?.name);
    this.spellCastDialog = {
      open: true,
      caster: c,
      spellId: firstSpell?.id,
      targetId: undefined,
      spendKarma: false,
      amuletMode: {}
    };
  }

  spellNeedsTarget(): boolean {
    if (!this.spellCastDialog.spellId || !this.spellCastDialog.caster) return false;
    const spell = this.spellsOf(this.spellCastDialog.caster)
      .find(s => s.spellDefinition.id === this.spellCastDialog.spellId)?.spellDefinition;
    if (!spell) return false;
    return spell.effectType === 'DAMAGE' || spell.effectType === 'DEBUFF' || !!spell.requiresTarget;
  }

  spellTargets(): CombatantState[] {
    if (!this.spellCastDialog.caster) return [];
    const spell = this.spellsOf(this.spellCastDialog.caster)
      .find(s => s.spellDefinition.id === this.spellCastDialog.spellId)?.spellDefinition;
    if (!spell) return [];
    const caster = this.spellCastDialog.caster;
    // Reichweite in Feldern: 0 = Selbst/Berührung → angrenzend (und sich selbst)
    const range = Math.max(1, spell.rangeHexes ?? 10);
    if (spell.effectType === 'BUFF' || spell.effectType === 'HEAL' || spell.requiresTarget) {
      // Friendly targets (same side) — inkl. des Zauberers selbst (Selbstbuff möglich)
      const base = (this.session?.combatants ?? []).filter(c => !c.defeated);
      return this.filterByMapRange(caster, base, range);
    }
    // Enemy targets
    const base = (this.session?.combatants ?? []).filter(c => c.id !== caster?.id && !c.defeated);
    return this.filterByMapRange(caster, base, range);
  }

  performSpellCast(): void {
    if (!this.session || !this.spellCastDialog.caster || !this.spellCastDialog.spellId) return;
    const req: SpellCastRequest = {
      sessionId: this.session.id,
      casterCombatantId: this.spellCastDialog.caster.id,
      targetCombatantId: this.spellCastDialog.targetId,
      spellId: this.spellCastDialog.spellId,
      spendKarma: this.spellCastDialog.spendKarma,
      amuletCastIds: this.selectedAmuletIds(this.spellCastDialog.amuletMode, 'attack'),
      amuletDamageIds: this.selectedAmuletIds(this.spellCastDialog.amuletMode, 'damage')
    };
    this.combatService.castSpell(this.session.id, req).subscribe({
      next: result => {
        if (this.spellCastDialog.caster) this.clearDialogState(this.spellCastDialog.caster.id);
        this.spellCastDialog.open = false;
        this.spellCastModal = { open: true, result };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => {
        const msg = err?.error?.message ?? err?.message ?? JSON.stringify(err);
        this.snack.open('Fehler: ' + msg, 'OK', { duration: 5000 });
      }
    });
  }

  cancelSpell(c: CombatantState): void {
    if (!this.session) return;
    this.combatService.cancelSpellPreparation(this.session.id, c.id).subscribe({
      next: () => this.combatService.findById(this.session!.id).subscribe(s => this.session = s),
      error: err => this.snack.open('Fehler: ' + (err?.error?.message ?? err.message), 'OK', { duration: 4000 })
    });
  }

  threadDots(r: ThreadweaveResult): boolean[] {
    return Array.from({ length: r.threadsRequired }, (_, i) => i < r.threadsWoven);
  }

  spellOutcomeLabel(r: SpellCastResult): string {
    switch (r.effectType) {
      case 'DAMAGE': return 'TREFFER';
      case 'HEAL': return 'GEHEILT';
      case 'BUFF': return 'VERSTÄRKT';
      case 'DEBUFF': return 'GESCHWÄCHT';
      default: return 'ERFOLG';
    }
  }

  // --- Ablenken ---

  hasDistractTalent(c: CombatantState): boolean {
    return (c.character.talents ?? []).some(t => t.talentDefinition.name === 'Ablenken');
  }

  distractTargets(): CombatantState[] {
    return (this.session?.combatants ?? []).filter(c =>
      c.id !== this.distractDialog.actor?.id && !c.defeated
    );
  }

  openDistractDialog(actor: CombatantState): void {
    this.pushDialogState(actor.id, 'DISTRACT');
    this.distractDialog = { open: true, actor, targetId: undefined, spendKarma: false };
  }

  performDistract(): void {
    if (!this.session || !this.distractDialog.actor || !this.distractDialog.targetId) return;
    const req: DistractRequest = {
      sessionId: this.session.id,
      actorCombatantId: this.distractDialog.actor.id,
      targetCombatantId: this.distractDialog.targetId,
      bonusSteps: 0,
      spendKarma: this.distractDialog.spendKarma
    };
    this.combatService.performDistract(this.session.id, req).subscribe({
      next: result => {
        if (this.distractDialog.actor) this.clearDialogState(this.distractDialog.actor.id);
        this.distractDialog.open = false;
        this.distractModal = { open: true, result };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => {
        const msg = err?.error?.message ?? err?.message ?? JSON.stringify(err);
        this.snack.open('Fehler: ' + msg, 'OK', { duration: 5000 });
      }
    });
  }

  // --- Schwachstelle erkennen ---

  hasSpotArmorFlawTalent(c: CombatantState): boolean {
    return (c.character.talents ?? []).some(t => t.talentDefinition.name === 'Schwachstelle erkennen');
  }

  spotArmorFlawTargets(): CombatantState[] {
    return (this.session?.combatants ?? []).filter(c =>
      c.id !== this.spotArmorFlawDialog.actor?.id && !c.defeated
    );
  }

  openSpotArmorFlawDialog(actor: CombatantState): void {
    this.spotArmorFlawDialog = { open: true, actor, targetId: undefined, bonusSteps: 0, spendKarma: false };
  }

  performSpotArmorFlaw(): void {
    if (!this.session || !this.spotArmorFlawDialog.actor || !this.spotArmorFlawDialog.targetId) return;
    const req: SpotArmorFlawRequest = {
      sessionId: this.session.id,
      actorCombatantId: this.spotArmorFlawDialog.actor.id,
      targetCombatantId: this.spotArmorFlawDialog.targetId,
      bonusSteps: this.spotArmorFlawDialog.bonusSteps,
      spendKarma: this.spotArmorFlawDialog.spendKarma
    };
    this.combatService.performSpotArmorFlaw(this.session.id, req).subscribe({
      next: result => {
        this.spotArmorFlawDialog.open = false;
        this.spotArmorFlawModal = { open: true, result };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => {
        const msg = err?.error?.message ?? err?.message ?? JSON.stringify(err);
        this.snack.open('Fehler: ' + msg, 'OK', { duration: 5000 });
      }
    });
  }

  // --- Eiserner Wille ---

  hasIronWillTalent(c: CombatantState): boolean {
    return (c.character.talents ?? []).some(t => t.talentDefinition.name === 'Eiserner Wille');
  }

  openIronWillDialog(actor: CombatantState): void {
    this.ironWillDialog = { open: true, actor, attackTotal: 0, spendKarma: false };
  }

  performIronWill(): void {
    if (!this.session || !this.ironWillDialog.actor || !this.ironWillDialog.attackTotal) return;
    this.combatService.performIronWill(
      this.session.id,
      this.ironWillDialog.actor.id,
      this.ironWillDialog.attackTotal,
      this.ironWillDialog.spendKarma
    ).subscribe({
      next: result => {
        this.ironWillDialog.open = false;
        this.ironWillModal = { open: true, result };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => {
        const msg = err?.error?.message ?? err?.message ?? JSON.stringify(err);
        this.snack.open('Fehler: ' + msg, 'OK', { duration: 5000 });
      }
    });
  }

  // --- Akrobatische Verteidigung ---

  hasAcrobaticDefenseTalent(c: CombatantState): boolean {
    return (c.character.talents ?? []).some(t => t.talentDefinition.name === 'Akrobatische Verteidigung');
  }

  openAcrobaticDefenseDialog(actor: CombatantState): void {
    this.acrobaticDialog = { open: true, actor, spendKarma: false };
  }

  performAcrobaticDefense(): void {
    if (!this.session || !this.acrobaticDialog.actor) return;
    this.combatService.performAcrobaticDefense(
      this.session.id,
      this.acrobaticDialog.actor.id,
      0,
      this.acrobaticDialog.spendKarma
    ).subscribe({
      next: result => {
        this.acrobaticDialog.open = false;
        this.acrobaticModal = { open: true, result };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => {
        const msg = err?.error?.message ?? err?.message ?? JSON.stringify(err);
        this.snack.open('Fehler: ' + msg, 'OK', { duration: 5000 });
      }
    });
  }

  // --- Kampfsinn ---

  hasCombatSenseTalent(c: CombatantState): boolean {
    return (c.character.talents ?? []).some(t => t.talentDefinition.name === 'Kampfsinn');
  }

  combatSenseTargets(): CombatantState[] {
    const actor = this.combatSenseDialog.actor;
    if (!actor) return [];
    // Only enemies with strictly lower initiative (higher initiativeOrder)
    return (this.session?.combatants ?? []).filter(c =>
      c.id !== actor.id && !c.defeated && c.initiativeOrder > actor.initiativeOrder
    );
  }

  openCombatSenseDialog(actor: CombatantState): void {
    this.combatSenseDialog = { open: true, actor, targetId: undefined, spendKarma: false };
  }

  performCombatSense(): void {
    if (!this.session || !this.combatSenseDialog.actor || !this.combatSenseDialog.targetId) return;
    const req: CombatSenseRequest = {
      sessionId: this.session.id,
      actorCombatantId: this.combatSenseDialog.actor.id,
      targetCombatantId: this.combatSenseDialog.targetId,
      bonusSteps: 0,
      spendKarma: this.combatSenseDialog.spendKarma
    };
    this.combatService.performCombatSense(this.session.id, req).subscribe({
      next: result => {
        this.combatSenseDialog.open = false;
        this.combatSenseModal = { open: true, result };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => {
        const msg = err?.error?.message ?? err?.message ?? JSON.stringify(err);
        this.snack.open('Fehler: ' + msg, 'OK', { duration: 5000 });
      }
    });
  }

  // --- Verspotten ---

  hasTauntTalent(c: CombatantState): boolean {
    return (c.character.talents ?? []).some(t => t.talentDefinition.name === 'Verspotten');
  }

  tauntTargets(): CombatantState[] {
    return (this.session?.combatants ?? []).filter(c => c.id !== this.tauntDialog.actor?.id && !c.defeated);
  }

  openTauntDialog(actor: CombatantState): void {
    this.pushDialogState(actor.id, 'TAUNT');
    this.tauntDialog = {
      open: true,
      actor,
      targetId: undefined,
      bonusSteps: 0,
      spendKarma: false
    };
  }

  performTaunt(): void {
    if (!this.session || !this.tauntDialog.actor || !this.tauntDialog.targetId) return;
    const req: TauntRequest = {
      sessionId: this.session.id,
      actorCombatantId: this.tauntDialog.actor.id,
      targetCombatantId: this.tauntDialog.targetId,
      bonusSteps: this.tauntDialog.bonusSteps,
      spendKarma: this.tauntDialog.spendKarma
    };
    this.combatService.performTaunt(this.session.id, req).subscribe({
      next: result => {
        if (this.tauntDialog.actor) this.clearDialogState(this.tauntDialog.actor.id);
        this.tauntDialog.open = false;
        this.tauntModal = { open: true, result };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => {
        const msg = err?.error?.message ?? err?.message ?? JSON.stringify(err);
        this.snack.open('Fehler: ' + msg, 'OK', { duration: 5000 });
      }
    });
  }

  // ── Verängstigen ───────────────────────────────────────────────────────────

  hasFearTalent(c: CombatantState): boolean {
    return (c.character.talents ?? []).some(t => t.talentDefinition.name === 'Verängstigen');
  }

  /** True, wenn der Kombattant den Verängstigt-Effekt trägt. */
  isFeared(c: CombatantState): boolean {
    return (c.activeEffects ?? []).some(e => e.name === 'Verängstigt');
  }

  /** Mindestwurf der Widerstandsprobe aus dem Verängstigt-Effekt (0 wenn keiner). */
  fearResistTn(c: CombatantState): number {
    return (c.activeEffects ?? []).find(e => e.name === 'Verängstigt')?.resistTargetNumber ?? 0;
  }

  fearTargets(): CombatantState[] {
    return (this.session?.combatants ?? []).filter(c => c.id !== this.fearDialog.actor?.id && !c.defeated);
  }

  openFearDialog(actor: CombatantState): void {
    this.fearDialog = { open: true, actor, targetId: undefined, spendKarma: false };
  }

  performFear(): void {
    if (!this.session || !this.fearDialog.actor || !this.fearDialog.targetId) return;
    const req: FearRequest = {
      sessionId: this.session.id,
      actorCombatantId: this.fearDialog.actor.id,
      targetCombatantId: this.fearDialog.targetId,
      bonusSteps: 0,
      spendKarma: this.fearDialog.spendKarma
    };
    this.combatService.performFear(this.session.id, req).subscribe({
      next: result => {
        this.fearDialog.open = false;
        this.fearModal = { open: true, result };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => this.snack.open('Fehler: ' + (err?.error?.message ?? err.message), 'OK', { duration: 5000 })
    });
  }

  resistFear(c: CombatantState): void {
    if (!this.session) return;
    this.combatService.resistFear(this.session.id, c.id).subscribe({
      next: result => {
        this.fearResistModal = { open: true, result };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => this.snack.open('Fehler: ' + (err?.error?.message ?? err.message), 'OK', { duration: 5000 })
    });
  }

  // ── Magie neutralisieren ───────────────────────────────────────────────────

  hasNeutralizeMagicTalent(c: CombatantState): boolean {
    return (c.character.talents ?? []).some(t => t.talentDefinition.name === 'Magie neutralisieren');
  }

  trackByEffectKey(_i: number, e: EffectChoice): string { return e.key; }

  /**
   * Alle aktiven Effekte aller Kombattanten — Auswahlliste für Magie neutralisieren.
   * Der GM entscheidet, was neutralisierbar ist, daher wird nicht gefiltert.
   * NUR beim Öffnen des Dialogs aufrufen (Snapshot) — erzeugt neue Objekte und würde
   * im Template eine Change-Detection-Endlosschleife auslösen.
   */
  allActiveEffects(): EffectChoice[] {
    const out: EffectChoice[] = [];
    for (const c of this.session?.combatants ?? []) {
      for (const e of c.activeEffects ?? []) {
        if (e.id == null) continue;
        out.push({
          key: `${c.id}:${e.id}`,
          combatantId: c.id,
          combatantName: this.cn(c),
          effectId: e.id,
          name: e.name,
          remainingRounds: e.remainingRounds
        });
      }
    }
    return out;
  }

  /** Der Kombattant, der Magie neutralisieren anwendet (aus dem synchronisierten Dialog). */
  neutralizeActor(): CombatantState | undefined {
    const id = this.neutralizeSelectModal.actorCombatantId;
    return id == null ? undefined : this.session?.combatants.find(c => c.id === id);
  }

  openNeutralizeMagicDialog(c: CombatantState): void {
    if (!this.session) return;
    // Öffnet das Modal via Backend-Broadcast bei allen Clients
    this.combatService.openNeutralizeMagicDialog(this.session.id, c.id).subscribe({
      error: err => this.snack.open('Fehler: ' + (err?.error?.message ?? err.message), 'OK', { duration: 5000 })
    });
  }

  performNeutralizeMagic(): void {
    const m = this.neutralizeSelectModal;
    if (!this.session || !m.selection || m.actorCombatantId == null) return;
    const [combatantId, effectId] = m.selection.split(':').map(Number);
    const req: NeutralizeMagicRequest = {
      sessionId: this.session.id,
      actorCombatantId: m.actorCombatantId,
      targetCombatantId: combatantId,
      effectId,
      effectLevel: m.effectLevel || 0,
      bonusSteps: 0,
      spendKarma: m.spendKarma
    };
    this.combatService.performNeutralizeMagic(this.session.id, req).subscribe({
      next: result => {
        this.neutralizeSelectModal.open = false;
        this.neutralizeModal = { open: true, result };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => this.snack.open('Fehler: ' + (err?.error?.message ?? err.message), 'OK', { duration: 5000 })
    });
  }

  // ── Autofight ──────────────────────────────────────────────────────────────

  isAutofight(c: CombatantState): boolean {
    return this.autofightCombatants.has(c.id);
  }

  toggleAutofight(c: CombatantState, checked: boolean): void {
    if (checked) {
      this.autofightCombatants.add(c.id);
      this.scheduleAutofight();
    } else {
      this.autofightCombatants.delete(c.id);
    }
  }

  dismissAutofightModal(modal: { open: boolean }): void {
    if (!modal.open) return;
    modal.open = false;
    // Synchronisiert: andere Zuschauer schließen ihr Modal ebenfalls über WS-Broadcast.
    if (this.session) {
      this.combatService.dismissModal(this.session.id).subscribe({
        next: s => { this.session = s; this.lastSeenModalVersion = s.liveModal?.version ?? this.lastSeenModalVersion; },
        error: () => { /* lokal bleibt geschlossen */ }
      });
    }
    this.scheduleAutofight();
  }

  private autoCloseModal(modal: { open: boolean }): void {
    setTimeout(() => { this.dismissAutofightModal(modal); }, 4000);
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
    const talent = (actor.character.talents ?? []).find(t => t.talentDefinition.name === 'Kampfsinn');
    if (!talent) return false;
    if (actor.activeEffects.some(e => e.name === 'Akrobatische Verteidigung')) return false;
    const usesThisRound = actor.activeEffects.filter(e => e.name === 'Kampfsinn (KV)').length;
    return usesThisRound < talent.rank;
  }

  private combatSenseTarget(actor: CombatantState): CombatantState | undefined {
    const alreadyTargeted = new Set<string>(
      actor.activeEffects
        .filter(e => e.name === 'Kampfsinn (KV)' && e.description)
        .map(e => { const m = e.description!.match(/gegen (.+) \(Kampfsinn\)/); return m ? m[1] : ''; })
        .filter(n => n !== '')
    );
    return this.session?.combatants.find(
      c => !c.defeated && c.npc !== actor.npc &&
           c.initiativeOrder > actor.initiativeOrder &&
           !alreadyTargeted.has(c.character.name)
    );
  }

  private canUseAcrobaticDefense(actor: CombatantState): boolean {
    if (!(actor.character.talents ?? []).some(t => t.talentDefinition.name === 'Akrobatische Verteidigung')) return false;
    return !actor.activeEffects.some(
      e => e.name === 'Kampfsinn' || e.name === 'Akrobatische Verteidigung'
    );
  }

  private scheduleAutofight(): void {
    if (this.autofightCombatants.size === 0 || this.autofightPending) return;
    if (this.resultModal.open || this.standUpModal.open || this.combatSenseModal.open ||
        this.acrobaticModal.open || this.spellCastModal.open || this.dodgeModal.open) return;
    this.autofightPending = true;
    setTimeout(() => {
      this.autofightPending = false;
      if (this.resultModal.open || this.standUpModal.open || this.combatSenseModal.open ||
          this.acrobaticModal.open || this.spellCastModal.open || this.dodgeModal.open) return;
      this.runAutofightStep();
    }, 600);
  }

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
        const actionType = this.autofightDeclareType(undeclared);
        const stance = actionType === 'SPELL' ? 'NONE' : this.autofightStance(undeclared);
        this.combatService.declareAction(sessionId, undeclared.id, stance, actionType).subscribe({
          next: s => { this.session = s; this.logEntries = this.toLogEntries(s.log); this.scheduleAutofight(); },
          error: err => this.snack.open('Autofight (Ansage): ' + (err?.error?.message ?? err.message), 'OK', { duration: 3000 })
        });
      }
      return;
    }

    // ACTION PHASE
    if (this.session.phase === 'ACTION') {
      const active = this.session.combatants.filter(c => !c.defeated);
      if (active.every(c => c.hasActedThisRound)) return;

      const actor = this.session.combatants.find(c => !c.defeated && !c.hasActedThisRound);
      if (!actor) return;
      if (!this.autofightCombatants.has(actor.id)) return;

      // Knocked down → stand up
      if (actor.knockedDown) {
        this.combatService.standUp(sessionId, actor.id).subscribe({
          next: result => {
            this.standUpModal = { open: true, result };
            this.autoCloseModal(this.standUpModal);
            this.combatService.findById(sessionId).subscribe(s => {
              this.session = s; this.logEntries = this.toLogEntries(s.log);
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
          next: s => { this.session = s; this.logEntries = this.toLogEntries(s.log); this.scheduleAutofight(); },
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
                this.session = s; this.logEntries = this.toLogEntries(s.log);
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
              this.session = s; this.logEntries = this.toLogEntries(s.log);
            });
          },
          error: err => this.snack.open('Autofight (Akrobatik): ' + (err?.error?.message ?? err.message), 'OK', { duration: 3000 })
        });
        return;
      }

      // Main action: 0-thread spell for magic users
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
                this.session = s; this.logEntries = this.toLogEntries(s.log);
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
                    this.session = s; this.logEntries = this.toLogEntries(s.log);
                  });
                },
                error: err => this.snack.open('Autofight (Ausweichen): ' + (err?.error?.message ?? err.message), 'OK', { duration: 3000 })
              });
            } else {
              // Manual defender: keep result modal open; WS update after dodge resolution resumes autofight
              this.combatService.findById(sessionId).subscribe(s => {
                this.session = s; this.logEntries = this.toLogEntries(s.log);
              });
            }
          } else {
            this.autoCloseModal(this.resultModal);
            this.combatService.findById(sessionId).subscribe(s => {
              this.session = s; this.logEntries = this.toLogEntries(s.log);
            });
          }
        },
        error: err => this.snack.open('Autofight (Angriff): ' + (err?.error?.message ?? err.message), 'OK', { duration: 3000 })
      });
    }
  }

  // --- Riposte ---

  hasRiposteTalent(c: CombatantState): boolean {
    return (c.character.talents ?? []).some(t => t.talentDefinition.name === 'Riposte');
  }

  openRiposteDialog(c: CombatantState): void {
    this.pushDialogState(c.id, 'RIPOSTE');
    this.riposteDialog = { open: true, defender: c, attackTotal: c.pendingRiposteAttackTotal, spendKarma: false };
    this.resultModal = { open: false };
  }

  openRiposteDialogFromResult(): void {
    if (!this.resultModal.result?.riposteDefenderId) return;
    const defender = this.session?.combatants.find(c => c.id === this.resultModal.result!.riposteDefenderId);
    if (!defender) return;
    this.pushDialogState(defender.id, 'RIPOSTE');
    this.riposteDialog = {
      open: true, defender,
      attackTotal: defender.pendingRiposteAttackTotal,
      spendKarma: false
    };
    this.resultModal = { open: false };
  }

  skipRiposte(): void {
    const defenderId = this.resultModal.result?.riposteDefenderId ?? this.riposteDialog.defender?.id;
    if (!this.session || !defenderId) return;
    const req: RiposteRequest = {
      sessionId: this.session.id,
      defenderCombatantId: defenderId,
      bonusSteps: 0, spendKarma: false,
      riposteAttempted: false
    };
    this.combatService.performRiposte(this.session.id, req).subscribe({
      next: result => {
        this.clearDialogState(defenderId);
        this.riposteModal = { open: true, result };
        this.resultModal = { open: false };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => this.snack.open('Riposte: ' + (err?.error?.message ?? err.message), 'OK', { duration: 4000 })
    });
  }

  performRiposte(): void {
    const defender = this.riposteDialog.defender;
    if (!this.session || !defender) return;
    const req: RiposteRequest = {
      sessionId: this.session.id,
      defenderCombatantId: defender.id,
      bonusSteps: 0,
      spendKarma: this.riposteDialog.spendKarma,
      riposteAttempted: true
    };
    this.combatService.performRiposte(this.session.id, req).subscribe({
      next: result => {
        this.clearDialogState(defender.id);
        this.riposteDialog.open = false;
        this.riposteModal = { open: true, result };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => this.snack.open('Fehler: ' + (err?.error?.message ?? err.message), 'OK', { duration: 5000 })
    });
  }

  // --- Manövrieren ---

  hasManoeuverTalent(c: CombatantState): boolean {
    return (c.character.talents ?? []).some(t => t.talentDefinition.name === 'Manövrieren');
  }

  openManoeuverDialog(actor: CombatantState): void {
    this.manoeuverDialog = { open: true, actor, targetId: undefined, spendKarma: false };
  }

  performManoeuver(): void {
    const actor = this.manoeuverDialog.actor;
    if (!this.session || !actor || !this.manoeuverDialog.targetId) return;
    const req: ManoeuverRequest = {
      sessionId: this.session.id,
      actorCombatantId: actor.id,
      targetCombatantId: this.manoeuverDialog.targetId,
      bonusSteps: 0,
      spendKarma: this.manoeuverDialog.spendKarma
    };
    this.combatService.performManoeuver(this.session.id, req).subscribe({
      next: result => {
        this.manoeuverDialog.open = false;
        this.manoeuverModal = { open: true, result };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => this.snack.open('Fehler: ' + (err?.error?.message ?? err.message), 'OK', { duration: 5000 })
    });
  }

  // --- Tigersprung ---

  hasTigersprungTalent(c: CombatantState): boolean {
    return (c.character.talents ?? []).some(t => t.talentDefinition.name === 'Tigersprung');
  }

  /** Anzeige in der Ansagephase: 'S5' oder 'S5+3' wenn ON_INITIATIVE-Boni aktiv sind. */
  initiativeStepLabel(c: CombatantState): string {
    const base = c.baseInitiativeStep ?? 0;
    const total = c.currentInitiativeStep ?? base;
    const bonus = total - base;
    return bonus > 0 ? `S${base}+${bonus}` : `S${base}`;
  }

  performTigersprung(c: CombatantState): void {
    if (!this.session) return;
    this.combatService.performTigersprung(this.session.id, c.id).subscribe({
      next: result => {
        this.tigersprungModal = { open: true, result };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => this.snack.open('Fehler: ' + (err?.error?.message ?? err.message), 'OK', { duration: 5000 })
    });
  }

  // --- Lufttanz ---

  hasLufttanzTalent(c: CombatantState): boolean {
    return (c.character.talents ?? []).some(t => t.talentDefinition.name === 'Lufttanz');
  }

  /** Disziplinen, die ab dem 3. Kreis Karma auf ihre Initiative einsetzen dürfen. */
  private static readonly KARMA_INIT_DISCIPLINES = ['Dieb', 'Kundschafter', 'Luftsegler', 'Schütze'];

  canUseKarmaInitiative(c: CombatantState): boolean {
    const disc = c.character?.discipline?.name;
    return !!disc
      && CombatTrackerComponent.KARMA_INIT_DISCIPLINES.includes(disc)
      && (c.character?.circle ?? 0) >= 3;
  }

  toggleKarmaInitiative(c: CombatantState): void {
    if (!this.session) return;
    const spend = !c.karmaInitiativeThisRound;
    this.combatService.setKarmaInitiative(this.session.id, c.id, spend).subscribe({
      next: s => this.session = s,
      error: err => this.snack.open('Fehler: ' + (err?.error?.message ?? err.message), 'OK', { duration: 5000 })
    });
  }

  performLufttanz(c: CombatantState): void {
    if (!this.session) return;
    this.combatService.performLufttanz(this.session.id, c.id).subscribe({
      next: result => {
        this.lufttanzModal = { open: true, result };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => this.snack.open('Fehler: ' + (err?.error?.message ?? err.message), 'OK', { duration: 5000 })
    });
  }

  openLufttanzAttackDialog(actor: CombatantState): void {
    this.lufttanzAttackDialog = {
      open: true, actor, bonusSteps: 0, spendKarma: false, spendKarmaForDamage: false
    };
  }

  /** Hilfs-Lookup: Ziel des ausstehenden Lufttanz-Bonusangriffs. */
  lufttanzTarget(actor: CombatantState): CombatantState | undefined {
    return this.session?.combatants.find(c => c.id === actor.pendingLufttanzTargetId);
  }

  /** Hilfs-Lookup: Waffenname für den Lufttanz-Bonusangriff. */
  lufttanzWeaponName(actor: CombatantState): string {
    const id = actor.pendingLufttanzWeaponId;
    if (id == null || id < 0) return 'Keine Waffe';
    return (actor.character.equipment ?? []).find(e => e.id === id)?.name ?? '(unbekannt)';
  }

  /** True wenn die Lufttanz-Waffe eine Krallenhand ist (Karma-für-Schaden möglich). */
  isLufttanzClawWeapon(actor: CombatantState): boolean {
    const id = actor.pendingLufttanzWeaponId;
    if (id == null || id < 0) return false;
    return !!(actor.character.equipment ?? []).find(e => e.id === id)?.clawWeapon;
  }

  performLufttanzAttack(): void {
    const actor = this.lufttanzAttackDialog.actor;
    if (!this.session || !actor) return;
    const req: LufttanzAttackRequest = {
      sessionId: this.session.id,
      attackerCombatantId: actor.id,
      bonusSteps: this.lufttanzAttackDialog.bonusSteps,
      spendKarma: this.lufttanzAttackDialog.spendKarma,
      spendKarmaForDamage: this.lufttanzAttackDialog.spendKarmaForDamage
    };
    this.combatService.performLufttanzAttack(this.session.id, req).subscribe({
      next: result => {
        this.lastResult = result;
        this.lufttanzAttackDialog.open = false;
        this.resultModal = { open: true, result };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => this.snack.open('Fehler: ' + (err?.error?.message ?? err.message), 'OK', { duration: 5000 })
    });
  }

  // --- Zweitwaffe ---

  hasZweitwaffeTalent(c: CombatantState): boolean {
    return (c.character.talents ?? []).some(t => t.talentDefinition.name === 'Zweitwaffe');
  }

  openZweitwaffeDialog(actor: CombatantState): void {
    // Pre-select the character's designated secondary weapon, if set and still present
    const secondaryId = actor.character.secondaryWeaponId;
    const hasWeapon = secondaryId != null && (actor.character.equipment ?? []).some(e => e.id === secondaryId);
    this.zweitwaffeDialog = { open: true, actor, defenderId: undefined, weaponId: hasWeapon ? secondaryId : undefined, spendKarma: false };
  }

  performZweitwaffe(): void {
    const actor = this.zweitwaffeDialog.actor;
    if (!this.session || !actor || !this.zweitwaffeDialog.defenderId) return;
    const req: ZweitwaffeRequest = {
      sessionId: this.session.id,
      actorCombatantId: actor.id,
      defenderCombatantId: this.zweitwaffeDialog.defenderId,
      weaponId: this.zweitwaffeDialog.weaponId ?? undefined,
      bonusSteps: 0,
      spendKarma: this.zweitwaffeDialog.spendKarma
    };
    this.combatService.performZweitwaffe(this.session.id, req).subscribe({
      next: result => {
        this.zweitwaffeDialog.open = false;
        this.resultModal = { open: true, result };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => this.snack.open('Fehler: ' + (err?.error?.message ?? err.message), 'OK', { duration: 5000 })
    });
  }

  // --- Nachtreten ---

  hasNachtretenTalent(c: CombatantState): boolean {
    return (c.character.talents ?? []).some(t => t.talentDefinition.name === 'Nachtreten');
  }

  /** Mögliche Ziele für Nachtreten: nicht besiegt, nicht der Anwender, niedrigere Initiative. */
  nachtretenTargets(actor?: CombatantState): CombatantState[] {
    if (!actor) return [];
    return (this.session?.combatants ?? [])
      .filter(c => c.id !== actor.id && !c.defeated && c.initiative < actor.initiative);
  }

  openNachtretenDialog(actor: CombatantState): void {
    this.nachtretenDialog = { open: true, actor, defenderId: undefined, spendKarma: false };
  }

  performNachtreten(): void {
    const actor = this.nachtretenDialog.actor;
    if (!this.session || !actor || !this.nachtretenDialog.defenderId) return;
    const req: NachtretenRequest = {
      sessionId: this.session.id,
      actorCombatantId: actor.id,
      defenderCombatantId: this.nachtretenDialog.defenderId,
      bonusSteps: 0,
      spendKarma: this.nachtretenDialog.spendKarma
    };
    this.combatService.performNachtreten(this.session.id, req).subscribe({
      next: result => {
        this.nachtretenDialog.open = false;
        this.resultModal = { open: true, result };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => this.snack.open('Fehler: ' + (err?.error?.message ?? err.message), 'OK', { duration: 5000 })
    });
  }

  // --- Schwanzangriff (T'skrang) ---

  isTskrang(c: CombatantState): boolean {
    return c.character?.race === 'TSKRANG';
  }

  /** Am Schwanz befestigte Waffen des Kombattanten. */
  tailWeaponsOf(c?: CombatantState) {
    return (c?.character.equipment ?? []).filter(e => e.type === 'WEAPON' && e.tailWeapon);
  }

  /** Mögliche Ziele für den Schwanzangriff: nicht besiegt, nicht der Anwender. */
  schwanzangriffTargets(actor?: CombatantState): CombatantState[] {
    if (!actor) return [];
    return (this.session?.combatants ?? []).filter(c => c.id !== actor.id && !c.defeated);
  }

  openSchwanzangriffDialog(actor: CombatantState): void {
    this.schwanzangriffDialog = { open: true, actor, defenderId: undefined, weaponId: undefined, spendKarma: false };
  }

  performSchwanzangriff(): void {
    const actor = this.schwanzangriffDialog.actor;
    if (!this.session || !actor || !this.schwanzangriffDialog.defenderId) return;
    const req: SchwanzangriffRequest = {
      sessionId: this.session.id,
      actorCombatantId: actor.id,
      defenderCombatantId: this.schwanzangriffDialog.defenderId,
      weaponId: this.schwanzangriffDialog.weaponId ?? undefined,
      bonusSteps: 0,
      spendKarma: this.schwanzangriffDialog.spendKarma
    };
    this.combatService.performSchwanzangriff(this.session.id, req).subscribe({
      next: result => {
        this.schwanzangriffDialog.open = false;
        this.resultModal = { open: true, result };
        this.combatService.findById(this.session!.id).subscribe(s => this.session = s);
      },
      error: err => this.snack.open('Fehler: ' + (err?.error?.message ?? err.message), 'OK', { duration: 5000 })
    });
  }

  // =====================================================================
  // GM-Effekt  (Meister trägt beliebige Boni/Mali auf Kombattanten ein)
  // =====================================================================

  /** Alle Kombattanten der Session (auch besiegte, GM kann alles). */
  allCombatants(): CombatantState[] {
    return this.session?.combatants ?? [];
  }

  openGmEffectDialog(): void {
    this.gmEffectDialog = {
      open: true,
      targetId: undefined,
      isMalus: false,
      magnitude: 3,
      statKey: 'PHYSICAL_DEFENSE',
      name: '',
      rounds: 1
    };
  }

  closeGmEffectDialog(): void {
    this.gmEffectDialog.open = false;
  }

  performGmEffect(): void {
    if (!this.session || !this.gmEffectDialog.targetId || !this.gmEffectDialog.statKey) return;

    const sign = this.gmEffectDialog.isMalus ? -1 : 1;
    const val  = sign * Math.abs(this.gmEffectDialog.magnitude || 1);

    // Stat-Schlüssel → ModifierEntry-Liste
    const modifiers: Array<{ targetStat: string; triggerContext: string }> =
      this.gmStatToModifiers(this.gmEffectDialog.statKey);

    const statLabel = this.gmStatLabel(this.gmEffectDialog.statKey);
    const signLabel = this.gmEffectDialog.isMalus ? '−' : '+';
    const name = this.gmEffectDialog.name.trim()
      || `GM: ${signLabel}${Math.abs(val)} ${statLabel}`;

    const effect: ActiveEffect = {
      name,
      description: `GM-Effekt: ${signLabel}${Math.abs(val)} auf ${statLabel}`,
      sourceType: 'MANUAL',
      negative: this.gmEffectDialog.isMalus,
      remainingRounds: this.gmEffectDialog.rounds,
      modifiers: modifiers.map(m => ({
        targetStat: m.targetStat,
        operation: 'ADD' as const,
        value: val,
        triggerContext: m.triggerContext
      }))
    };

    this.combatService.addEffect(this.session.id, this.gmEffectDialog.targetId, effect)
      .subscribe({
        next: s => {
          this.session = s;
          this.closeGmEffectDialog();
          const targetName = this.combatantNameById(this.gmEffectDialog.targetId);
          this.snack.open(
            `${name} auf ${targetName ?? '?'} angewendet.`, 'OK', { duration: 3000 }
          );
        },
        error: err => this.snack.open(
          'Fehler: ' + (err?.error?.message ?? err.message), 'OK', { duration: 4000 }
        )
      });
  }

  /** Wendet eine manuell aktivierte GM-Spezialbedingung (Toter Winkel / Bedrängt) auf das Ziel an. */
  applyGmCondition(type: 'TOTER_WINKEL' | 'BEDRAENGT'): void {
    if (!this.session || !this.gmEffectDialog.targetId) return;
    const rounds = this.gmEffectDialog.rounds ?? 1;
    const label = type === 'TOTER_WINKEL' ? 'Toter Winkel' : 'Bedrängt';
    this.combatService.applyGmCondition(this.session.id, this.gmEffectDialog.targetId, type, rounds).subscribe({
      next: s => {
        this.session = s;
        const targetName = this.combatantNameById(this.gmEffectDialog.targetId);
        this.closeGmEffectDialog();
        this.snack.open(`${label} auf ${targetName ?? '?'} angewendet.`, 'OK', { duration: 3000 });
      },
      error: err => this.snack.open('Fehler: ' + (err?.error?.message ?? err.message), 'OK', { duration: 4000 })
    });
  }

  /** Baut die ModifierEntry-Definitionen für den gewählten Stat-Schlüssel. */
  private gmStatToModifiers(key: string): Array<{ targetStat: string; triggerContext: string }> {
    switch (key) {
      case 'PHYSICAL_DEFENSE': return [{ targetStat: 'PHYSICAL_DEFENSE', triggerContext: 'ALWAYS' }];
      case 'SPELL_DEFENSE':    return [{ targetStat: 'SPELL_DEFENSE',    triggerContext: 'ALWAYS' }];
      case 'SOCIAL_DEFENSE':   return [{ targetStat: 'SOCIAL_DEFENSE',   triggerContext: 'ALWAYS' }];
      case 'ALL_DEFENSES':     return [
        { targetStat: 'PHYSICAL_DEFENSE', triggerContext: 'ALWAYS' },
        { targetStat: 'SPELL_DEFENSE',    triggerContext: 'ALWAYS' },
        { targetStat: 'SOCIAL_DEFENSE',   triggerContext: 'ALWAYS' }
      ];
      case 'ATTACK_STEP':     return [{ targetStat: 'ATTACK_STEP',     triggerContext: 'ALWAYS' }];
      case 'DAMAGE_STEP':     return [{ targetStat: 'DAMAGE_STEP',     triggerContext: 'ON_DAMAGE_DEALT' }];
      case 'INITIATIVE_STEP': return [{ targetStat: 'INITIATIVE_STEP', triggerContext: 'ALWAYS' }];
      case 'ALL_ACTIONS':     return [
        { targetStat: 'ATTACK_STEP',      triggerContext: 'ALWAYS' },
        { targetStat: 'PHYSICAL_DEFENSE', triggerContext: 'ALWAYS' },
        { targetStat: 'SPELL_DEFENSE',    triggerContext: 'ALWAYS' },
        { targetStat: 'SOCIAL_DEFENSE',   triggerContext: 'ALWAYS' }
      ];
      case 'MYSTIC_ARMOR':    return [{ targetStat: 'MYSTIC_ARMOR',    triggerContext: 'ALWAYS' }];
      case 'PHYSICAL_ARMOR':  return [{ targetStat: 'PHYSICAL_ARMOR',  triggerContext: 'ALWAYS' }];
      default: return [{ targetStat: key, triggerContext: 'ALWAYS' }];
    }
  }

  /** Lesbarer Name für einen Stat-Schlüssel (für Auto-Naming). */
  private gmStatLabel(key: string): string {
    const map: Record<string, string> = {
      'PHYSICAL_DEFENSE': 'KV',
      'SPELL_DEFENSE':    'MV',
      'SOCIAL_DEFENSE':   'SV',
      'ALL_DEFENSES':     'alle VK',
      'ATTACK_STEP':      'Angriff',
      'DAMAGE_STEP':      'Schaden',
      'INITIATIVE_STEP':  'Initiative',
      'ALL_ACTIONS':      'Angriff+VK',
      'MYSTIC_ARMOR':     'myst. Rüstung',
      'PHYSICAL_ARMOR':   'phys. Rüstung'
    };
    return map[key] ?? key;
  }

  // =====================================================================
  // Dialog-State Live-Push  (zeigt Mitspielern was ein Spieler plant)
  // =====================================================================

  /** Sendet den aktuellen Dialog-Status via WebSocket an alle Zuschauer. Fehler werden stillschweigend ignoriert. */
  private pushDialogState(combatantId: number, actionType: string,
                          targetName?: string, weaponName?: string, spellName?: string): void {
    if (!this.session) return;
    this.combatService.updateDialogState(this.session.id, combatantId,
      { actionType, targetName, weaponName, spellName })
      .subscribe({ error: () => {} });
  }

  /** Löscht den Dialog-Status des Kombattanten (Dialog wurde geschlossen). */
  private clearDialogState(combatantId: number): void {
    if (!this.session) return;
    this.combatService.updateDialogState(this.session.id, combatantId, { actionType: null })
      .subscribe({ error: () => {} });
  }

  /** Name eines Kombattanten anhand ID aus der aktuellen Session. */
  private combatantNameById(id?: number): string | undefined {
    if (id == null) return undefined;
    return this.session?.combatants.find(c => c.id === id)?.character.name ?? undefined;
  }

  /** Name einer Waffe anhand ID aus dem Equipment eines Kombattanten. */
  private weaponNameById(attacker?: CombatantState, weaponId?: number): string | undefined {
    if (!attacker || weaponId == null) return undefined;
    return (attacker.character.equipment ?? []).find(e => e.id === weaponId)?.name ?? undefined;
  }

  /** Aktionstyp-Label basierend auf der aktuell gewählten Angriffsbasis (Talent/Fertigkeit). */
  private currentAttackActionLabel(): string {
    return this.resolveActionType();
  }

  /** Schließt den Angriffsdialog und löscht den Dialog-Status. */
  closeAttackDialog(): void {
    if (this.attackDialog.attacker) this.clearDialogState(this.attackDialog.attacker.id);
    this.attackDialog.open = false;
  }

  /** Zielwechsel im Angriffsdialog → Dialog-Status aktualisieren. */
  onAttackTargetChange(id: number): void {
    this.attackDialog.defenderId = id;
    if (!this.attackDialog.attacker) return;
    this.pushDialogState(
      this.attackDialog.attacker.id,
      this.currentAttackActionLabel(),
      this.combatantNameById(id),
      this.weaponNameById(this.attackDialog.attacker, this.attackDialog.weaponId)
    );
  }

  /** Schließt den Verspotten-Dialog und löscht den Dialog-Status. */
  closeTauntDialog(): void {
    if (this.tauntDialog.actor) this.clearDialogState(this.tauntDialog.actor.id);
    this.tauntDialog.open = false;
  }

  /** Zielwechsel im Verspotten-Dialog → Dialog-Status aktualisieren. */
  onTauntTargetChange(id: number): void {
    this.tauntDialog.targetId = id;
    if (!this.tauntDialog.actor) return;
    this.pushDialogState(this.tauntDialog.actor.id, 'TAUNT', this.combatantNameById(id));
  }

  /** Schließt den Ablenken-Dialog und löscht den Dialog-Status. */
  closeDistractDialog(): void {
    if (this.distractDialog.actor) this.clearDialogState(this.distractDialog.actor.id);
    this.distractDialog.open = false;
  }

  /** Zielwechsel im Ablenken-Dialog → Dialog-Status aktualisieren. */
  onDistractTargetChange(id: number): void {
    this.distractDialog.targetId = id;
    if (!this.distractDialog.actor) return;
    this.pushDialogState(this.distractDialog.actor.id, 'DISTRACT', this.combatantNameById(id));
  }

  /** Schließt den Fadenweben-Dialog und löscht den Dialog-Status. */
  closeThreadweaveDialog(): void {
    if (this.threadweaveDialog.caster) this.clearDialogState(this.threadweaveDialog.caster.id);
    this.threadweaveDialog.open = false;
  }

  /** Schließt den Zauberwirken-Dialog und löscht den Dialog-Status. */
  closeSpellCastDialog(): void {
    if (this.spellCastDialog.caster) this.clearDialogState(this.spellCastDialog.caster.id);
    this.spellCastDialog.open = false;
  }

  /** Zielwechsel im Zauberwirken-Dialog → Dialog-Status aktualisieren. */
  onSpellCastTargetChange(id: number): void {
    this.spellCastDialog.targetId = id;
    if (!this.spellCastDialog.caster) return;
    const spellName = this.spellsOf(this.spellCastDialog.caster)
      .find(s => s.spellDefinition.id === this.spellCastDialog.spellId)?.spellDefinition.name;
    this.pushDialogState(this.spellCastDialog.caster.id, 'SPELL',
      this.combatantNameById(id), undefined, spellName);
  }

  /** Schließt den Riposte-Dialog und löscht den Dialog-Status. */
  closeRiposteDialog(): void {
    if (this.riposteDialog.defender) this.clearDialogState(this.riposteDialog.defender.id);
    this.riposteDialog.open = false;
  }

  /**
   * Badge-Text für den Dialog-Status eines Kombattanten (sichtbar für andere Spieler).
   * Gibt null zurück wenn kein Dialog offen.
   */
  dialogStateBadge(c: CombatantState): string | null {
    const ds = this.session?.activeDialogs?.[c.id];
    if (!ds?.actionType) return null;
    let icon = '…';
    if (ds.actionType === 'MELEE_ATTACK') icon = '⚔';
    else if (ds.actionType === 'RANGED_ATTACK') icon = '🏹';
    else if (ds.actionType === 'SPELL_ATTACK') icon = '✨';
    else if (ds.actionType === 'SPELL') icon = '✨';
    else if (ds.actionType === 'WEAVE') icon = '🌀';
    else if (ds.actionType === 'TAUNT') icon = '💬';
    else if (ds.actionType === 'DISTRACT') icon = '📢';
    else if (ds.actionType === 'RIPOSTE') icon = '🤺';
    let label = icon;
    if (ds.targetName) label += ' → ' + ds.targetName;
    if (ds.spellName) label += ' (' + ds.spellName + ')';
    return label;
  }
}
