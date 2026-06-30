import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDividerModule } from '@angular/material/divider';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { CharacterService } from '../../services/character.service';
import { ReferenceService } from '../../services/reference.service';
import { DiceService } from '../../services/dice.service';
import { ActiveUserService } from '../../services/active-user.service';
import { AmuletRechargeResult, ArztResult, Character, CharacterTalent, DerivedStats, DrinkPotionResult, TalentDefinition, SkillDefinition, DisciplineDefinition, Equipment, HolzhautResult, RecoveryTestResult, SpellDefinition, RACES } from '../../models/character.model';
import { ProbeResult } from '../../models/dice.model';

@Component({
  selector: 'app-character-sheet',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatCardModule, MatButtonModule, MatIconModule, MatTabsModule,
    MatFormFieldModule, MatInputModule, MatSelectModule,
    MatDividerModule, MatSnackBarModule, MatTooltipModule,
    MatCheckboxModule
  ],
  template: `
    <div class="sheet-container" *ngIf="character">
      <!-- Header -->
      <div class="sheet-header">
        <div>
          <button mat-icon-button (click)="router.navigate(['/characters'])">
            <mat-icon>arrow_back</mat-icon>
          </button>
          <span class="char-name">{{ character.name }}</span>
          <span class="char-sub">{{ character.playerName }} · {{ raceLabel(character.race) }}{{ character.race ? ' · ' : '' }}{{ character.discipline?.name }} Kreis {{ character.circle }}</span>
        </div>
        <div class="header-actions">
          <button mat-icon-button *ngIf="isGm()"
            (click)="toggleGmCharacter()"
            [matTooltip]="character.gmCharacter ? 'Spielleiter-Charakter (klicken zum Aufheben)' : 'Als Spielleiter-Charakter markieren'"
            [style.color]="character.gmCharacter ? '#c9a84c' : '#555'">
            <mat-icon>{{ character.gmCharacter ? 'lock' : 'lock_open' }}</mat-icon>
          </button>
          <button mat-stroked-button (click)="recalculate()" matTooltip="Abgeleitete Werte neu berechnen">
            <mat-icon>calculate</mat-icon> Neu berechnen
          </button>
          <button mat-raised-button color="primary" (click)="save()">
            <mat-icon>save</mat-icon> Speichern
          </button>
        </div>
      </div>

      <!-- Status Bar (always visible) -->
      <div class="status-bar">
        <!-- Damage Track -->
        <div class="status-block damage-block">
          <div class="status-label">Schaden</div>
          <div class="damage-track">
            <div class="damage-track__fill" [style.width.%]="damagePercent()"></div>
          </div>
          <div class="stat-row">
            <span class="stat-value">{{ character.currentDamage }}</span>
            <span class="stat-sep">/</span>
            <span class="stat-max">{{ derived?.unconsciousnessRating ?? character.toughness * 2 }}</span>
            <div class="ctrl-btns">
              <button mat-icon-button (click)="adjustField('damage', -1)" matTooltip="Heilen">
                <mat-icon>healing</mat-icon>
              </button>
              <button mat-icon-button color="warn" (click)="adjustField('damage', 1)" matTooltip="Schaden nehmen">
                <mat-icon>bolt</mat-icon>
              </button>
            </div>
          </div>
        </div>

        <!-- Wounds -->
        <div class="status-block">
          <div class="status-label">Wunden</div>
          <div class="wound-dots">
            <div class="dot" *ngFor="let w of woundDots()" [class.filled]="w"></div>
          </div>
          <div class="ctrl-btns" style="margin-top:4px">
            <button mat-icon-button (click)="adjustField('wounds', -1)"><mat-icon>remove</mat-icon></button>
            <span style="min-width:20px;text-align:center">{{ character.wounds }}</span>
            <button mat-icon-button color="warn" (click)="adjustField('wounds', 1)"><mat-icon>add</mat-icon></button>
          </div>
        </div>

        <!-- Karma -->
        <div class="status-block">
          <div class="status-label">Karma</div>
          <ng-container *ngIf="!hasNoKarma(); else noKarmaBlock">
            <div class="ctrl-btns" style="margin-top:4px">
              <button mat-icon-button (click)="adjustField('karma', -1)"><mat-icon>remove</mat-icon></button>
              <span style="min-width:40px;text-align:center">{{ character.karmaCurrent }}/{{ character.karmaMax }}</span>
              <button mat-icon-button (click)="adjustField('karma', 1)"><mat-icon>add</mat-icon></button>
            </div>
            <div class="karma-modifier-row">
              <span class="karma-mod-label">Modifikator</span>
              <input type="number" class="karma-mod-input" [(ngModel)]="character.karmaModifier"
                (change)="saveKarmaModifier()" min="1" max="20">
              <span class="karma-mod-hint">× Kreis {{ character.circle }} = {{ character.karmaModifier * character.circle }} Max</span>
            </div>
            <button mat-stroked-button class="ritual-btn"
              (click)="karmaRitual()"
              [disabled]="character.karmaCurrent >= character.karmaMax"
              matTooltip="Karmaritual: Karma auf Maximum auffüllen">
              <mat-icon>auto_awesome</mat-icon> Karmaritual
            </button>
          </ng-container>
          <ng-template #noKarmaBlock>
            <span style="font-size:12px;color:#555;margin-top:6px;display:block">Kein Karma</span>
          </ng-template>
        </div>

        <!-- Währung -->
        <div class="status-block currency-block">
          <div class="status-label">Währung</div>
          <div class="currency-row" *ngFor="let cur of currencies">
            <span class="currency-icon" [style.color]="cur.color">{{ cur.icon }}</span>
            <span class="currency-val">{{ getCurrencyValue(cur.field) }}</span>
            <div class="ctrl-btns">
              <button mat-icon-button (click)="adjustField(cur.field, -1)"><mat-icon>remove</mat-icon></button>
              <button mat-icon-button (click)="adjustField(cur.field, 1)"><mat-icon>add</mat-icon></button>
            </div>
            <input type="number" class="currency-input" [(ngModel)]="currencyDelta[cur.field]"
              placeholder="Δ" (keydown.enter)="addCurrency(cur.field)">
            <button mat-icon-button (click)="addCurrency(cur.field)" matTooltip="Betrag addieren">
              <mat-icon>input</mat-icon>
            </button>
          </div>
        </div>
      </div>

      <!-- Tabs -->
      <mat-tab-group class="sheet-tabs">

        <!-- Attribute -->
        <mat-tab label="Attribute">
          <div class="tab-content">
            <div class="two-col">
              <div class="attr-grid">
                <div class="section-title">Attribute</div>
                <div class="attr-row-item" *ngFor="let a of attributeFields">
                  <span class="attr-label">{{ a.label }}</span>
                  <div class="attr-ctrl">
                    <button mat-icon-button (click)="adjustAttr(a.key, -1)"><mat-icon>remove</mat-icon></button>
                    <span class="attr-val">{{ getAttr(a.key) }}</span>
                    <button mat-icon-button (click)="adjustAttr(a.key, 1)"><mat-icon>add</mat-icon></button>
                  </div>
                  <span class="attr-step" matTooltip="Stufe des Attributwerts">Stufe {{ attrToStep(getAttr(a.key)) }}</span>
                </div>
                <div class="circle-row">
                  <span class="derived-label">Disziplin</span>
                  <mat-select [(ngModel)]="character.discipline" [compareWith]="compareDiscipline"
                              (ngModelChange)="onDisciplineChange()" style="width:160px" placeholder="Keine">
                    <mat-option [value]="null">— keine —</mat-option>
                    <mat-option *ngFor="let d of disciplines" [value]="d">{{ d.name }}</mat-option>
                  </mat-select>
                </div>
                <div class="circle-row">
                  <span class="derived-label">Rasse</span>
                  <mat-select [(ngModel)]="character.race" style="width:160px" placeholder="Keine Auswahl">
                    <mat-option [value]="null">— keine —</mat-option>
                    <mat-option *ngFor="let r of races" [value]="r.value">{{ r.label }}</mat-option>
                  </mat-select>
                </div>
                <div class="circle-row">
                  <span class="derived-label">Kreis</span>
                  <mat-select [(ngModel)]="character.circle" (ngModelChange)="onCircleChange()" style="width:80px">
                    <mat-option *ngFor="let n of circles" [value]="n">{{ n }}</mat-option>
                  </mat-select>
                </div>
              </div>

              <div class="derived-grid" *ngIf="derived">
                <div class="section-title">Abgeleitete Werte</div>
                <div class="derived-item" *ngFor="let d of derivedFields"
                     [matTooltip]="derivedTooltip(d.key)" [matTooltipDisabled]="!derivedTooltip(d.key)">
                  <span class="derived-label">
                    {{ d.label }}
                    <span class="derived-note" *ngIf="derivedNote(d.key) as note">{{ note }}</span>
                  </span>
                  <span class="derived-val">{{ getDerived(d.key) }}</span>
                </div>

                <div class="section-title" style="margin-top:14px">Verteidigungs-Boni</div>
                <div class="defense-bonus-row" *ngFor="let b of defenseBonusFields">
                  <span class="derived-label">{{ b.label }}</span>
                  <div class="ctrl-btns">
                    <button mat-icon-button (click)="adjustField(b.field, -1)"><mat-icon>remove</mat-icon></button>
                    <span class="bonus-val" [class.positive]="getDefenseBonus(b.field) > 0" [class.negative]="getDefenseBonus(b.field) < 0">
                      {{ getDefenseBonus(b.field) >= 0 ? '+' : '' }}{{ getDefenseBonus(b.field) }}
                    </span>
                    <button mat-icon-button (click)="adjustField(b.field, 1)"><mat-icon>add</mat-icon></button>
                  </div>
                </div>

                <div class="section-title" style="margin-top:14px">Weitere Boni</div>
                <div class="defense-bonus-row" *ngFor="let b of statBonusFields">
                  <span class="derived-label">{{ b.label }}</span>
                  <div class="ctrl-btns">
                    <button mat-icon-button (click)="adjustField(b.field, -1)"><mat-icon>remove</mat-icon></button>
                    <span class="bonus-val" [class.positive]="getDefenseBonus(b.field) > 0" [class.negative]="getDefenseBonus(b.field) < 0">
                      {{ getDefenseBonus(b.field) >= 0 ? '+' : '' }}{{ getDefenseBonus(b.field) }}
                    </span>
                    <button mat-icon-button (click)="adjustField(b.field, 1)"><mat-icon>add</mat-icon></button>
                  </div>
                </div>

                <!-- Holzhaut -->
                <ng-container *ngIf="hasHolzhautTalent()">
                  <div class="section-title" style="margin-top:14px">Holzhaut</div>
                  <div class="holzhaut-row">
                    <div class="holzhaut-status">
                      <span class="holzhaut-icon">🌳</span>
                      <span *ngIf="isHolzhautActive()" class="holzhaut-active">
                        Aktiv · <strong>+{{ holzhautBonus() }}</strong> auf BW & TD
                      </span>
                      <span *ngIf="!isHolzhautActive()" class="holzhaut-inactive">Nicht aktiv</span>
                    </div>
                    <div class="holzhaut-actions">
                      <button mat-stroked-button color="primary" (click)="useHolzhaut()"
                              [matTooltip]="isHolzhautActive() ? 'Neu wirken (überschreibt aktuellen Bonus)' : 'Holzhaut wirken: ZÄH-Stufe + Talentrang. Kostet 1 Erholungsprobe.'">
                        <mat-icon>park</mat-icon>
                        {{ isHolzhautActive() ? 'Neu wirken' : 'Wirken' }}
                      </button>
                      <button mat-stroked-button color="accent" *ngIf="isHolzhautActive()" (click)="endHolzhaut()"
                              matTooltip="Effekt beenden: aktueller Schaden wird um den Bonuswert reduziert (Puffer-Heilung).">
                        <mat-icon>healing</mat-icon> Beenden
                      </button>
                    </div>
                  </div>
                  <div class="holzhaut-result" *ngIf="lastHolzhaut">
                    <div class="holzhaut-detail" *ngIf="lastHolzhaut.roll as r">
                      Probe: ZÄH-Stufe {{ lastHolzhaut.toughnessStep }} + Rang {{ lastHolzhaut.rank }}
                      = Stufe {{ lastHolzhaut.rollStep }} ({{ r.diceExpression }})
                      → <strong class="holzhaut-total">{{ r.total }}</strong>
                      <span *ngIf="lastHolzhaut.previousBonus > 0"> · ersetzt vorigen Bonus +{{ lastHolzhaut.previousBonus }}</span>
                    </div>
                    <div class="holzhaut-detail" *ngIf="!lastHolzhaut.roll && lastHolzhaut.healed >= 0">
                      Beendet · <strong class="holzhaut-total">{{ lastHolzhaut.healed }}</strong> Schaden geheilt
                      (Puffer war +{{ lastHolzhaut.previousBonus }})
                    </div>
                  </div>
                </ng-container>
              </div>
            </div>

            <div class="legend-row">
              <div class="section-title">Legendenpunkte</div>
              <div class="attr-ctrl">
                <button mat-icon-button (click)="adjustField('legendPoints', -100)"><mat-icon>remove</mat-icon></button>
                <span class="attr-val">{{ character.legendPoints }}</span>
                <button mat-icon-button (click)="adjustField('legendPoints', 100)"><mat-icon>add</mat-icon></button>
              </div>
            </div>
          </div>
        </mat-tab>

        <!-- Talente -->
        <mat-tab label="Talente & Fertigkeiten">
          <div class="tab-content">
            <div class="two-col">
              <!-- Talente -->
              <div>
                <div class="section-header">
                  <div class="section-title">Talente</div>
                  <mat-form-field appearance="fill" style="width:200px">
                    <mat-label>Talent hinzufügen</mat-label>
                    <mat-select [(ngModel)]="selectedTalentId" (ngModelChange)="addTalent()">
                      <mat-option *ngFor="let t of availableTalentsForDropdown()" [value]="t.id"
                        [disabled]="isTalentMaxed(t)">
                        {{ t.name }}{{ talentInstanceLabel(t) }}
                      </mat-option>
                    </mat-select>
                  </mat-form-field>
                </div>
                <div class="talent-list">
                  <div class="talent-item" *ngFor="let ct of sortedTalents(); let i = index">
                    <div class="talent-info">
                      <span class="talent-name">{{ ct.talentDefinition.name }}{{ talentInstanceSuffix(ct) }}</span>
                      <span class="talent-attr">{{ ct.talentDefinition.attribute }}</span>
                    </div>
                    <div class="rank-ctrl" *ngIf="!ct.talentDefinition.rankFromCircle">
                      <button mat-icon-button (click)="updateTalentRank(ct, ct.rank - 1)"><mat-icon>remove</mat-icon></button>
                      <span class="rank-val">Rang {{ ct.rank }}</span>
                      <button mat-icon-button (click)="updateTalentRank(ct, ct.rank + 1)"><mat-icon>add</mat-icon></button>
                    </div>
                    <div class="rank-ctrl" *ngIf="ct.talentDefinition.rankFromCircle"
                         style="opacity:0.6" matTooltip="Rang entspricht immer dem Kreis des Charakters">
                      <span class="rank-val">Rang {{ ct.rank }} <span style="font-size:0.75em;color:#888">(= Kreis)</span></span>
                    </div>
                    <button mat-icon-button (click)="rollProbe(ct.talentDefinition.id, null)"
                      [disabled]="!ct.talentDefinition.testable" matTooltip="Probe würfeln" color="accent">
                      <mat-icon>casino</mat-icon>
                    </button>
                    <button mat-icon-button color="warn" (click)="removeTalent(ct.id)">
                      <mat-icon>close</mat-icon>
                    </button>
                  </div>
                </div>
              </div>

              <!-- Fertigkeiten -->
              <div>
                <div class="section-header">
                  <div class="section-title">Fertigkeiten</div>
                  <mat-form-field appearance="fill" style="width:200px">
                    <mat-label>Fertigkeit hinzufügen</mat-label>
                    <mat-select [(ngModel)]="selectedSkillId" (ngModelChange)="addSkill()">
                      <mat-option *ngFor="let s of availableSkills" [value]="s.id">{{ s.name }}</mat-option>
                    </mat-select>
                  </mat-form-field>
                </div>
                <div class="talent-list">
                  <div class="talent-item" *ngFor="let cs of character.skills">
                    <div class="talent-info">
                      <span class="talent-name">{{ cs.skillDefinition.name }}</span>
                      <span class="talent-attr">{{ cs.skillDefinition.attribute }}</span>
                    </div>
                    <div class="rank-ctrl">
                      <button mat-icon-button (click)="updateSkillRank(cs, cs.rank - 1)"><mat-icon>remove</mat-icon></button>
                      <span class="rank-val">Rang {{ cs.rank }}</span>
                      <button mat-icon-button (click)="updateSkillRank(cs, cs.rank + 1)"><mat-icon>add</mat-icon></button>
                    </div>
                    <button mat-icon-button (click)="rollProbe(null, cs.skillDefinition.id)" matTooltip="Probe würfeln" color="accent">
                      <mat-icon>casino</mat-icon>
                    </button>
                    <button mat-icon-button color="warn" (click)="removeSkill(cs.id)">
                      <mat-icon>close</mat-icon>
                    </button>
                  </div>
                </div>
              </div>
            </div>

            <!-- Probe Result -->
            <div class="probe-result" *ngIf="lastProbe">
              <div class="section-title">Letzter Wurf: {{ lastProbe.probeName }}</div>
              <div style="display:flex;align-items:center;gap:16px">
                <div class="roll-total">{{ lastProbe.total }}</div>
                <div>
                  <div>Step {{ lastProbe.step }} ({{ lastProbe.diceExpression }}) vs TN
                    <span class="tn-input">
                      <input type="number" [(ngModel)]="probeTargetNumber" style="width:40px;background:#333;border:1px solid #555;color:#fff;padding:2px 4px;border-radius:3px">
                    </span>
                  </div>
                  <div [class]="'success-degree ' + degreeClass(lastProbe)">
                    {{ lastProbe.successDegree }}
                    <span *ngIf="lastProbe.extraSuccesses > 0"> (+{{ lastProbe.extraSuccesses }})</span>
                  </div>
                  <div *ngIf="(lastProbe.equipmentBonus ?? 0) > 0" style="font-size:12px;color:#80cbc4">
                    inkl. +{{ lastProbe.equipmentBonus }} Ausrüstung
                  </div>
                </div>
                <div class="dice-details">
                  <span class="die-result" *ngFor="let d of lastProbe.dice" [class.exploded]="d.exploded">
                    d{{ d.sides }}: {{ d.rolls.join('+') }} = {{ d.total }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </mat-tab>

        <!-- Ausrüstung -->
        <mat-tab label="Ausrüstung">
          <div class="tab-content">

            <!-- Waffen -->
            <div class="equip-section">
              <div class="section-title">Waffen</div>
              <div class="equip-list">
                <div class="equip-item" *ngFor="let e of weapons()">
                  <div class="equip-name">
                    <span *ngIf="e.clawWeapon" class="claw-icon" matTooltip="Magische Krallenhand (vom Talent verwaltet)">🐾</span>
                    {{ e.name }}
                  </div>
                  <div class="equip-stats">
                    <span class="equip-badge weapon">+{{ e.damageBonus }} Schaden</span>
                    <span class="equip-badge" *ngIf="e.attackTalentName" style="background:rgba(66,165,245,0.15);border:1px solid #42a5f5;color:#90caf9" matTooltip="Im Kampf nur bei diesem Angriffstalent/-fertigkeit wählbar">⚔ {{ e.attackTalentName }}</span>
                    <span class="equip-badge" *ngIf="e.tailWeapon" style="background:rgba(129,199,132,0.15);border:1px solid #81c784;color:#a5d6a7" matTooltip="Schwanzwaffe (T'skrang-Schwanzangriff)">🦎 Schwanzwaffe</span>
                    <span class="equip-badge two-handed-badge" *ngIf="e.twoHanded" matTooltip="Zweihändig: kann nicht zusammen mit einem Schild geführt werden (außer Buckler)">✋✋ Zweihändig</span>
                    <span class="equip-badge claw-badge" *ngIf="e.clawWeapon" matTooltip="Krallenhand: ersetzt STR-Stufe, Karma auf Schaden möglich, nicht entwaffenbar">Krallenhand</span>
                    <span class="equip-badge secondary-weapon" *ngIf="character.secondaryWeaponId === e.id" matTooltip="Wird als Zweitwaffe verwendet">⚔ Zweitwaffe</span>
                    <span class="equip-desc" *ngIf="e.description">{{ e.description }}</span>
                  </div>
                  <button mat-icon-button *ngIf="!e.clawWeapon"
                    [style.color]="character.secondaryWeaponId === e.id ? '#ce93d8' : '#555'"
                    (click)="toggleSecondaryWeapon(e)"
                    [matTooltip]="character.secondaryWeaponId === e.id ? 'Als Zweitwaffe abwählen' : 'Als Zweitwaffe festlegen'">
                    <mat-icon>{{ character.secondaryWeaponId === e.id ? 'join_full' : 'join_inner' }}</mat-icon>
                  </button>
                  <button mat-icon-button color="warn" (click)="removeEquipment(e)"
                          [disabled]="e.clawWeapon"
                          [matTooltip]="e.clawWeapon ? 'Wird vom Krallenhand-Talent verwaltet' : 'Entfernen'">
                    <mat-icon>delete</mat-icon>
                  </button>
                </div>
                <div class="equip-empty" *ngIf="!weapons().length">Keine Waffen eingetragen</div>
              </div>
              <div class="equip-add-form">
                <mat-form-field appearance="fill" style="flex:2">
                  <mat-label>Name</mat-label>
                  <input matInput [(ngModel)]="newWeapon.name" placeholder="z.B. Langschwert">
                </mat-form-field>
                <mat-form-field appearance="fill" style="width:130px">
                  <mat-label>Schadensbonus</mat-label>
                  <input matInput type="number" [(ngModel)]="newWeapon.damageBonus" min="0">
                </mat-form-field>
                <mat-form-field appearance="fill" style="width:150px" matTooltip="Zweihändige Waffen können nicht mit Schild geführt werden (außer Buckler)">
                  <mat-label>Hände</mat-label>
                  <mat-select [(ngModel)]="newWeapon.twoHanded">
                    <mat-option [value]="false">Einhändig</mat-option>
                    <mat-option [value]="true">Zweihändig</mat-option>
                  </mat-select>
                </mat-form-field>
                <mat-form-field appearance="fill" style="width:180px" matTooltip="Im Kampf ist diese Waffe nur bei diesem Angriffstalent/-fertigkeit wählbar. Leer = bei jedem Angriff verfügbar.">
                  <mat-label>Angriffstalent</mat-label>
                  <mat-select [(ngModel)]="newWeapon.attackTalentName">
                    <mat-option [value]="''">— beliebig —</mat-option>
                    <mat-option *ngFor="let n of weaponAttackTalents" [value]="n">{{ n }}</mat-option>
                  </mat-select>
                </mat-form-field>
                <mat-form-field appearance="fill" style="width:150px" matTooltip="Am Schwanz befestigte Waffe (bis Größe 2) — nur für den T'skrang-Schwanzangriff wählbar.">
                  <mat-label>Schwanzwaffe</mat-label>
                  <mat-select [(ngModel)]="newWeapon.tailWeapon">
                    <mat-option [value]="false">Nein</mat-option>
                    <mat-option [value]="true">Ja (Schwanz)</mat-option>
                  </mat-select>
                </mat-form-field>
                <mat-form-field appearance="fill" style="flex:3">
                  <mat-label>Beschreibung (optional)</mat-label>
                  <input matInput [(ngModel)]="newWeapon.description">
                </mat-form-field>
                <button mat-stroked-button [disabled]="!newWeapon.name.trim()" (click)="addWeapon()">
                  <mat-icon>add</mat-icon> Hinzufügen
                </button>
              </div>
            </div>

            <mat-divider style="margin:16px 0"></mat-divider>

            <!-- Rüstungen -->
            <div class="equip-section">
              <div class="section-title">Rüstungen</div>
              <div class="equip-list">
                <div class="equip-item" *ngFor="let e of armors()"
                     [style.opacity]="e.active === false ? '0.45' : '1'">
                  <mat-checkbox
                    [checked]="e.active !== false"
                    (change)="toggleEquipmentActive(e)"
                    [matTooltip]="e.active !== false ? 'Angelegt (klicken zum Ablegen)' : 'Abgelegt (klicken zum Anlegen)'">
                  </mat-checkbox>
                  <div class="equip-name">{{ e.name }}</div>
                  <div class="equip-stats">
                    <span class="equip-badge armor-phys" matTooltip="Physische Rüstung">{{ e.physicalArmor }} phys.</span>
                    <span class="equip-badge armor-myst" matTooltip="Mystische Rüstung (gegen Zauber)">{{ e.mysticalArmor }} myst.</span>
                    <span class="equip-badge armor-init" *ngIf="e.initiativePenalty > 0" matTooltip="Initiativemalus der Rüstung">−{{ e.initiativePenalty }} Init.</span>
                    <span class="equip-badge inactive-badge" *ngIf="e.active === false" matTooltip="Dieses Rüstungsstück ist abgelegt und wird nicht gewertet">abgelegt</span>
                    <span class="equip-desc" *ngIf="e.description">{{ e.description }}</span>
                  </div>
                  <button mat-icon-button color="warn" (click)="removeEquipment(e)" matTooltip="Entfernen">
                    <mat-icon>delete</mat-icon>
                  </button>
                </div>
                <div class="equip-empty" *ngIf="!armors().length">Keine Rüstung eingetragen</div>
              </div>
              <div class="equip-add-form">
                <mat-form-field appearance="fill" style="flex:2">
                  <mat-label>Name</mat-label>
                  <input matInput [(ngModel)]="newArmor.name" placeholder="z.B. Kettenhemd">
                </mat-form-field>
                <mat-form-field appearance="fill" style="width:130px">
                  <mat-label>Phys. Rüstung</mat-label>
                  <input matInput type="number" [(ngModel)]="newArmor.physicalArmor" min="0">
                </mat-form-field>
                <mat-form-field appearance="fill" style="width:130px">
                  <mat-label>Myst. Rüstung</mat-label>
                  <input matInput type="number" [(ngModel)]="newArmor.mysticalArmor" min="0">
                </mat-form-field>
                <mat-form-field appearance="fill" style="width:130px" matTooltip="Wird von der Initiativestufe abgezogen">
                  <mat-label>Init.-Malus</mat-label>
                  <input matInput type="number" [(ngModel)]="newArmor.initiativePenalty" min="0">
                </mat-form-field>
                <mat-form-field appearance="fill" style="flex:3">
                  <mat-label>Beschreibung (optional)</mat-label>
                  <input matInput [(ngModel)]="newArmor.description">
                </mat-form-field>
                <button mat-stroked-button [disabled]="!newArmor.name.trim()" (click)="addArmor()">
                  <mat-icon>add</mat-icon> Hinzufügen
                </button>
              </div>
            </div>

            <mat-divider style="margin:16px 0"></mat-divider>

            <!-- Schilde -->
            <div class="equip-section">
              <div class="section-title">Schilde</div>
              <div class="equip-list">
                <div class="equip-item" *ngFor="let e of shields()"
                     [style.opacity]="e.active === false ? '0.45' : '1'">
                  <mat-checkbox
                    [checked]="e.active !== false"
                    (change)="toggleEquipmentActive(e)"
                    [matTooltip]="e.active !== false ? 'Ausgerüstet (klicken zum Ablegen)' : 'Abgelegt (klicken zum Ausrüsten)'">
                  </mat-checkbox>
                  <div class="equip-name">{{ e.name }}</div>
                  <div class="equip-stats">
                    <span class="equip-badge shield-phys" *ngIf="e.physicalDefenseBonus > 0" matTooltip="+KV (Körperliche Verteidigung)">+{{ e.physicalDefenseBonus }} KV</span>
                    <span class="equip-badge shield-myst" *ngIf="e.mysticDefenseBonus > 0" matTooltip="+MV (Mystische Verteidigung)">+{{ e.mysticDefenseBonus }} MV</span>
                    <span class="equip-badge armor-init" *ngIf="e.initiativePenalty > 0" matTooltip="Initiativemalus">−{{ e.initiativePenalty }} Init.</span>
                    <span class="equip-badge buckler-badge" *ngIf="e.buckler" matTooltip="Buckler: kann auch mit zweihändigen Waffen geführt werden">🛡 Buckler</span>
                    <span class="equip-badge inactive-badge" *ngIf="e.active === false && !e.autoStowed" matTooltip="Dieses Schild ist abgelegt und wird nicht gewertet">abgelegt</span>
                    <span class="equip-badge inactive-badge" *ngIf="e.active === false && e.autoStowed" matTooltip="Wegen einer zweihändigen Waffe automatisch abgelegt — wird beim nächsten Einhandangriff wieder angelegt">⚔ autom. abgelegt</span>
                    <span class="equip-desc" *ngIf="e.description">{{ e.description }}</span>
                  </div>
                  <button mat-icon-button color="warn" (click)="removeEquipment(e)" matTooltip="Entfernen">
                    <mat-icon>delete</mat-icon>
                  </button>
                </div>
                <div class="equip-empty" *ngIf="!shields().length">Kein Schild eingetragen</div>
              </div>
              <div class="equip-add-form">
                <mat-form-field appearance="fill" style="flex:2">
                  <mat-label>Name</mat-label>
                  <input matInput [(ngModel)]="newShield.name" placeholder="z.B. Rundschild">
                </mat-form-field>
                <mat-form-field appearance="fill" style="width:120px" matTooltip="Bonus auf Körperliche Verteidigung">
                  <mat-label>+KV</mat-label>
                  <input matInput type="number" [(ngModel)]="newShield.physicalDefenseBonus" min="0">
                </mat-form-field>
                <mat-form-field appearance="fill" style="width:120px" matTooltip="Bonus auf Mystische Verteidigung">
                  <mat-label>+MV</mat-label>
                  <input matInput type="number" [(ngModel)]="newShield.mysticDefenseBonus" min="0">
                </mat-form-field>
                <mat-form-field appearance="fill" style="width:120px" matTooltip="Wird von der Initiativestufe abgezogen">
                  <mat-label>Init.-Malus</mat-label>
                  <input matInput type="number" [(ngModel)]="newShield.initiativePenalty" min="0">
                </mat-form-field>
                <mat-checkbox [(ngModel)]="newShield.buckler" style="margin:0 8px"
                  matTooltip="Buckler: darf auch mit zweihändigen Waffen geführt werden">Buckler</mat-checkbox>
                <mat-form-field appearance="fill" style="flex:3">
                  <mat-label>Beschreibung (optional)</mat-label>
                  <input matInput [(ngModel)]="newShield.description">
                </mat-form-field>
                <button mat-stroked-button [disabled]="!newShield.name.trim()" (click)="addShield()">
                  <mat-icon>add</mat-icon> Hinzufügen
                </button>
              </div>
            </div>

            <mat-divider style="margin:16px 0"></mat-divider>

            <!-- Verzweiflungsschlag-Amulette -->
            <div class="equip-section">
              <div class="section-title">Verzweiflungsschlag-Amulette</div>
              <div style="font-size:12px;color:#999;margin:-4px 0 10px">
                Vor dem Wurf ansagen (wie Karma): <strong style="color:#c9a84c">+6</strong> auf Angriffs-/Zauberwurf
                <em>oder</em> Schadenswurf. Jedes Amulett kostet <strong style="color:#ef9a9a">3 Blutmagie</strong>
                (dauerhaft −3 auf Bewusstlosigkeits- &amp; Todesschwelle). Aufladen: Erholungsprobe ≥ 3 opfern.
              </div>
              <div class="equip-list">
                <div class="equip-item" *ngFor="let e of amulets()">
                  <div class="equip-name">
                    <span matTooltip="Verzweiflungsschlag-Amulett">🩸</span>
                    {{ e.name }}
                  </div>
                  <div class="equip-stats">
                    <span class="equip-badge" [ngClass]="e.amuletForSpell ? 'shield-myst' : 'weapon'">
                      {{ e.amuletForSpell ? 'Zauber' : 'Physisch' }}
                    </span>
                    <span class="equip-badge weapon">+{{ e.amuletStepBonus ?? 6 }} Stufen</span>
                    <span class="equip-badge armor-init" matTooltip="Blutmagie-Schaden: dauerhaft −{{ e.bloodMagicDamage ?? 3 }} auf Bewusstlosigkeits-/Todesschwelle">
                      −{{ e.bloodMagicDamage ?? 3 }} Blutmagie
                    </span>
                    <span class="equip-badge" [ngClass]="e.charged !== false ? 'shield-phys' : 'inactive-badge'"
                          [matTooltip]="e.charged !== false ? 'Einsatzbereit' : 'Entladen — Erholungsprobe ≥3 opfern zum Aufladen'">
                      {{ e.charged !== false ? '⚡ geladen' : 'entladen' }}
                    </span>
                    <span class="equip-desc" *ngIf="e.description">{{ e.description }}</span>
                  </div>
                  <button mat-stroked-button color="accent"
                          *ngIf="e.charged === false"
                          [disabled]="getRecoveryTestsRemaining() <= 0"
                          (click)="rechargeAmulet(e)"
                          [matTooltip]="getRecoveryTestsRemaining() > 0 ? 'Erholungsprobe opfern (≥3 lädt auf, sonst heilt sie regulär)' : 'Keine Erholungsproben mehr übrig'">
                    <mat-icon>bolt</mat-icon> Aufladen
                  </button>
                  <button mat-icon-button color="warn" (click)="removeEquipment(e)" matTooltip="Entfernen">
                    <mat-icon>delete</mat-icon>
                  </button>
                </div>
                <div class="equip-empty" *ngIf="!amulets().length">Keine Amulette eingetragen</div>
              </div>

              <!-- Letztes Aufladeergebnis -->
              <div class="heal-result" *ngIf="lastAmuletRecharge" style="margin-top:8px">
                <mat-icon [style.color]="lastAmuletRecharge.recharged ? '#66bb6a' : '#ef9a9a'">
                  {{ lastAmuletRecharge.recharged ? 'bolt' : 'healing' }}
                </mat-icon>
                <div>
                  <div class="heal-name">{{ lastAmuletRecharge.amuletName }}</div>
                  <div class="heal-detail">
                    Erholungswurf Stufe {{ lastAmuletRecharge.rollStep }} → <strong>{{ lastAmuletRecharge.roll?.total }}</strong>
                    <span *ngIf="lastAmuletRecharge.recharged"> · <strong style="color:#66bb6a">aufgeladen</strong> (Heilung geopfert)</span>
                    <span *ngIf="!lastAmuletRecharge.recharged"> · &lt;3 → stattdessen <strong class="heal-amount">{{ lastAmuletRecharge.healed }} LP geheilt</strong></span>
                  </div>
                </div>
              </div>

              <div class="equip-add-form">
                <mat-form-field appearance="fill" style="flex:2">
                  <mat-label>Name</mat-label>
                  <input matInput [(ngModel)]="newAmulet.name" placeholder="z.B. Verzweiflungsschlag-Amulett">
                </mat-form-field>
                <mat-form-field appearance="fill" style="width:160px">
                  <mat-label>Art</mat-label>
                  <mat-select [(ngModel)]="newAmulet.amuletForSpell">
                    <mat-option [value]="false">Physischer Angriff</mat-option>
                    <mat-option [value]="true">Zauber</mat-option>
                  </mat-select>
                </mat-form-field>
                <mat-form-field appearance="fill" style="flex:3">
                  <mat-label>Beschreibung (optional)</mat-label>
                  <input matInput [(ngModel)]="newAmulet.description">
                </mat-form-field>
                <button mat-stroked-button [disabled]="!newAmulet.name.trim()" (click)="addAmulet()">
                  <mat-icon>add</mat-icon> Hinzufügen
                </button>
              </div>
            </div>

            <mat-divider style="margin:16px 0"></mat-divider>

            <!-- Verbandszeug (Arzt-Verbrauchsgegenstand) -->
            <div class="equip-section">
              <div class="section-title">Verbandszeug</div>
              <div style="font-size:12px;color:#999;margin:-4px 0 10px">
                Verbrauchsgegenstand für Arztproben — pro Behandlung wird <strong>1 Anwendung</strong> verbraucht.
              </div>
              <div class="potion-list">
                <div class="potion-item" *ngFor="let v of verbandszeug()">
                  <div class="potion-info">
                    <mat-icon class="potion-icon" style="color:#80cbc4">medical_services</mat-icon>
                    <div>
                      <div class="potion-name">{{ v.name }}</div>
                      <div class="potion-desc" *ngIf="v.description">{{ v.description }}</div>
                    </div>
                  </div>
                  <div class="potion-qty">
                    <button mat-icon-button (click)="adjustPotionQty(v, -1)" [disabled]="v.quantity <= 0"><mat-icon>remove</mat-icon></button>
                    <span class="qty-val">{{ v.quantity }}×</span>
                    <button mat-icon-button (click)="adjustPotionQty(v, 1)"><mat-icon>add</mat-icon></button>
                  </div>
                  <button mat-icon-button color="warn" (click)="removeEquipment(v)" matTooltip="Entfernen">
                    <mat-icon>delete</mat-icon>
                  </button>
                </div>
                <div class="equip-empty" *ngIf="!verbandszeug().length">Kein Verbandszeug im Inventar</div>
              </div>
              <div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin-top:8px">
                <mat-form-field appearance="fill" style="width:90px">
                  <mat-label>Anzahl</mat-label>
                  <input matInput type="number" [(ngModel)]="newVerbandszeugQty" min="1">
                </mat-form-field>
                <button mat-stroked-button (click)="addVerbandszeug()">
                  <mat-icon>medical_services</mat-icon> Verbandszeug hinzufügen
                </button>
              </div>
            </div>

            <mat-divider style="margin:16px 0"></mat-divider>

            <!-- Sonstige Ausrüstung (GEAR) — Probenboni -->
            <div class="equip-section">
              <div class="section-title">Sonstige Ausrüstung</div>
              <div style="font-size:12px;color:#999;margin:-4px 0 10px">
                Gegenstände mit Probenbonus (z.B. <strong>Leichte Stiefel: +2 auf Heimlicher Schritt</strong>).
                Der Bonus wird automatisch auf die passende Talent-/Fertigkeitsprobe addiert.
              </div>
              <div class="equip-list">
                <div class="equip-item" *ngFor="let g of gear()">
                  <div class="equip-name">👢 {{ g.name }}</div>
                  <div class="equip-stats">
                    <span class="equip-badge shield-phys" *ngIf="g.probeBonusTalentName">
                      +{{ g.probeBonusValue }} auf {{ g.probeBonusTalentName }}
                    </span>
                    <span class="equip-desc" *ngIf="g.description">{{ g.description }}</span>
                  </div>
                  <button mat-icon-button color="warn" (click)="removeEquipment(g)" matTooltip="Entfernen">
                    <mat-icon>delete</mat-icon>
                  </button>
                </div>
                <div class="equip-empty" *ngIf="!gear().length">Keine sonstige Ausrüstung</div>
              </div>
              <div style="margin:8px 0;display:flex;gap:8px;flex-wrap:wrap">
                <button mat-stroked-button (click)="addLeichteStiefel()"
                  matTooltip="Leichte Stiefel: +2 auf Heimlicher Schritt">
                  <mat-icon>add</mat-icon> Leichte Stiefel
                </button>
                <button mat-stroked-button (click)="addSchwimmkristall()"
                  matTooltip="Schwimmkristall: +3 auf Schwimmen, erlaubt Unterwasseratmung von Rang Minuten">
                  <mat-icon>add</mat-icon> Schwimmkristall
                </button>
              </div>
              <div class="equip-add-form">
                <mat-form-field appearance="fill" style="flex:2">
                  <mat-label>Name</mat-label>
                  <input matInput [(ngModel)]="newGear.name" placeholder="z.B. Leichte Stiefel">
                </mat-form-field>
                <mat-form-field appearance="fill" style="flex:2">
                  <mat-label>Bonus auf Talent/Fertigkeit</mat-label>
                  <mat-select [(ngModel)]="newGear.probeBonusTalentName">
                    <mat-option *ngFor="let n of probeTargetNames()" [value]="n">{{ n }}</mat-option>
                  </mat-select>
                </mat-form-field>
                <mat-form-field appearance="fill" style="width:100px">
                  <mat-label>Bonus</mat-label>
                  <input matInput type="number" [(ngModel)]="newGear.probeBonusValue" min="0">
                </mat-form-field>
                <button mat-stroked-button [disabled]="!newGear.name.trim() || !newGear.probeBonusTalentName" (click)="addGear()">
                  <mat-icon>add</mat-icon> Hinzufügen
                </button>
              </div>
            </div>

          </div>
        </mat-tab>

        <!-- Sprüche -->
        <mat-tab label="Sprüche" *ngIf="isMagicUser()">
          <div class="tab-content">

            <!-- Zaubermatrizen -->
            <div *ngIf="spellMatrices().length > 0" class="matrix-section">
              <div class="section-title" style="margin-bottom:10px">Zaubermatrizen</div>
              <div class="matrix-list">
                <div class="matrix-item" *ngFor="let m of spellMatrices(); let i = index">
                  <div class="matrix-header">
                    <span class="matrix-label">{{ isEnhancedMatrix(m) ? 'Erw. Matrize' : 'Matrix' }} {{ i + 1 }}</span>
                    <span class="matrix-rank">Rang {{ m.rank }} · max. Kreis {{ m.rank }}<span *ngIf="isEnhancedMatrix(m)"> · 1 Faden vorgewoben</span></span>
                  </div>
                  <mat-form-field appearance="fill" style="flex:1;min-width:200px">
                    <mat-label>Zauber zuweisen</mat-label>
                    <mat-select [value]="m.assignedSpell?.id ?? null"
                                (selectionChange)="assignSpellToMatrix(m, $event.value)">
                      <mat-option [value]="null">— Leer —</mat-option>
                      <mat-option *ngFor="let cs of spellsForMatrix(m)" [value]="cs.spellDefinition.id">
                        {{ cs.spellDefinition.name }} (Kreis {{ cs.spellDefinition.circle }})
                      </mat-option>
                    </mat-select>
                  </mat-form-field>
                  <div class="matrix-spell-info" *ngIf="m.assignedSpell">
                    <span class="spell-badge" [ngClass]="spellTypeBadgeClass(m.assignedSpell)">{{ spellTypeLabel(m.assignedSpell) }}</span>
                    <span class="spell-threads" *ngIf="m.assignedSpell.threads > 0 && !isEnhancedMatrix(m)">
                      {{ m.assignedSpell.threads }} {{ m.assignedSpell.threads === 1 ? 'Faden' : 'Fäden' }}
                    </span>
                    <span class="spell-threads" *ngIf="m.assignedSpell.threads > 0 && isEnhancedMatrix(m)" matTooltip="1 Faden ist durch die erweiterte Matrize bereits gewoben">
                      noch {{ matrixRemainingThreads(m) }} {{ matrixRemainingThreads(m) === 1 ? 'Faden' : 'Fäden' }} (1 vorgewoben)
                    </span>
                    <span class="spell-threads" *ngIf="m.assignedSpell.threads === 0">Sofortzauber</span>
                  </div>
                  <button mat-icon-button color="accent"
                          (click)="rollProbe(m.talentDefinition.id, null)"
                          matTooltip="Zaubermatritze-Probe würfeln (WN + Rang {{ m.rank }})">
                    <mat-icon>casino</mat-icon>
                  </button>
                </div>
              </div>
              <mat-divider style="margin:16px 0"></mat-divider>
            </div>

            <div class="section-header">
              <div class="section-title">Gelernte Zauber</div>
              <mat-form-field appearance="fill" style="width:250px">
                <mat-label>Zauber hinzufügen</mat-label>
                <mat-select [(ngModel)]="selectedSpellId" (ngModelChange)="addSpell()">
                  <mat-option *ngFor="let s of availableSpells" [value]="s.id">
                    {{ s.name }} (Kreis {{ s.circle }})
                  </mat-option>
                </mat-select>
              </mat-form-field>
            </div>

            <div class="spell-list">
              <div class="spell-item" *ngFor="let cs of character.spells">
                <div class="spell-info">
                  <span class="spell-name">{{ cs.spellDefinition.name }}</span>
                  <span class="spell-circle">Kreis {{ cs.spellDefinition.circle }}</span>
                </div>
                <div class="spell-details">
                  <span class="spell-badge" [ngClass]="spellTypeBadgeClass(cs.spellDefinition)">
                    {{ spellTypeLabel(cs.spellDefinition) }}
                  </span>
                  <span class="spell-threads" *ngIf="cs.spellDefinition.threads > 0">
                    {{ cs.spellDefinition.threads }} {{ cs.spellDefinition.threads === 1 ? 'Faden' : 'Fäden' }} (FW {{ cs.spellDefinition.weavingDifficulty }})
                  </span>
                  <span class="spell-threads" *ngIf="cs.spellDefinition.threads === 0">Sofortzauber</span>
                  <span class="spell-effect">{{ cs.spellDefinition.description }}</span>
                </div>
                <button mat-icon-button color="warn" (click)="removeSpell(cs.id)" matTooltip="Entfernen">
                  <mat-icon>close</mat-icon>
                </button>
              </div>
              <div class="equip-empty" *ngIf="!character.spells?.length">Keine Zauber gelernt</div>
            </div>
          </div>
        </mat-tab>

        <!-- Erholung -->
        <mat-tab label="Erholung">
          <div class="tab-content">
            <div class="section-title" style="margin-bottom:12px">Erholungsproben</div>

            <div class="recovery-info" style="display:flex;gap:16px;flex-wrap:wrap;margin-bottom:12px;font-size:14px">
              <span>ZÄH-Stufe: <strong>{{ attrToStep(character.toughness) }}</strong></span>
              <span *ngIf="character.wounds > 0" style="color:#ef5350">Wunden-Abzug: −{{ character.wounds }}</span>
              <span>Effektive Stufe: <strong>{{ getRecoveryRollStep() }}</strong></span>
            </div>

            <div style="display:flex;align-items:center;gap:12px;margin-bottom:16px">
              <span style="font-size:13px;color:#aaa">Heute verbleibend:</span>
              <span *ngFor="let i of recoverySlotArray()">
                <mat-icon [style.color]="i < getRecoveryTestsRemaining() ? '#66bb6a' : '#555'" style="font-size:20px;height:20px;width:20px">
                  {{ i < getRecoveryTestsRemaining() ? 'radio_button_checked' : 'radio_button_unchecked' }}
                </mat-icon>
              </span>
              <span style="font-size:13px;color:#ccc">{{ getRecoveryTestsRemaining() }} / {{ getRecoveryTestsMax() }}</span>
            </div>

            <!-- Ausstehender Erholungstrank-Bonus -->
            <div *ngIf="(character.pendingRecoveryBonus ?? 0) > 0"
              style="display:flex;align-items:center;gap:8px;margin-bottom:12px;padding:8px 12px;background:#1a3a2a;border-radius:6px;border:1px solid #2e7d52">
              <mat-icon style="color:#66bb6a">local_drink</mat-icon>
              <span style="color:#a5d6a7;font-size:13px">
                Erholungstrank aktiv: <strong>+{{ character.pendingRecoveryBonus }} Stufen</strong> auf nächste Probe
              </span>
            </div>

            <!-- Arzt-Wundpflege: nächste Probe ohne Wundabzug -->
            <div *ngIf="character.arztWoundPenaltyNegated"
              style="display:flex;align-items:center;gap:8px;margin-bottom:12px;padding:8px 12px;background:#13302e;border-radius:6px;border:1px solid #2e6e66">
              <mat-icon style="color:#80cbc4">medical_services</mat-icon>
              <span style="color:#a7d8d2;font-size:13px">
                Arzt-Wundpflege aktiv: nächste Erholungsprobe <strong>ohne Wundabzug</strong> (−{{ character.wounds }} entfällt)
              </span>
            </div>

            <!-- Karma auf Erholungsprobe: Disziplin-Fähigkeit (3./5. Kreis) -->
            <div *ngIf="canUseKarmaRecovery()"
              style="display:flex;align-items:center;gap:8px;margin-bottom:12px;padding:8px 12px;background:#2a2410;border-radius:6px;border:1px solid #6b5a1e">
              <label style="display:flex;align-items:center;gap:8px;cursor:pointer;font-size:13px;color:#d4b85a;margin:0">
                <input type="checkbox" [(ngModel)]="recoveryUseKarma" [disabled]="character.karmaCurrent <= 0">
                <mat-icon style="color:#d4b85a;font-size:18px;height:18px;width:18px">auto_awesome</mat-icon>
                Karma einsetzen: +W6 (Stufe 4) auf die Probe — {{ character.karmaCurrent }} verfügbar
              </label>
            </div>

            <div style="display:flex;gap:8px;margin-bottom:20px">
              <button mat-raised-button color="primary"
                [disabled]="getRecoveryTestsRemaining() <= 0"
                (click)="doRecoveryTest()"
                [matTooltip]="'Würfelt ZÄH-Stufe ' + getRecoveryRollStep() + (( character.pendingRecoveryBonus ?? 0) > 0 ? ' +' + character.pendingRecoveryBonus + ' Bonus' : '') + (canUseKarmaRecovery() && recoveryUseKarma ? ' +Karma (W6)' : '') + ' und heilt LP'">
                <mat-icon>healing</mat-icon> Erholungsprobe würfeln
              </button>
              <button mat-stroked-button (click)="resetRecoveryTests()" matTooltip="Neuer Tag — Proben auffüllen">
                <mat-icon>refresh</mat-icon> Neuer Tag
              </button>
            </div>

            <!-- Letztes Ergebnis -->
            <div class="heal-result" *ngIf="lastRecovery">
              <mat-icon style="color:#66bb6a">healing</mat-icon>
              <div>
                <div class="heal-name" *ngIf="lastRecovery.potionName">{{ lastRecovery.potionName }}</div>
                <div class="heal-detail">
                  Stufe {{ lastRecovery.rollStep }}<ng-container *ngIf="lastRecovery.bonusSteps > 0"> +{{ lastRecovery.bonusSteps }} Bonus</ng-container>
                  ({{ lastRecovery.roll?.diceExpression }}<ng-container *ngIf="lastRecovery.karmaRoll"> + Karma {{ lastRecovery.karmaRoll.total }}</ng-container>) → <strong class="heal-amount">{{ lastRecovery.healed }} LP geheilt</strong>
                  <span *ngIf="lastRecovery.remainingDamage > 0"> · {{ lastRecovery.remainingDamage }} LP verbleibend</span>
                  <span *ngIf="lastRecovery.remainingDamage === 0"> · Vollständig geheilt!</span>
                  <em *ngIf="lastRecovery.usedExtraSlot" style="color:#90caf9"> · Extra-Probe</em>
                </div>
                <div class="heal-detail" style="font-family:monospace;color:#bba89a;margin-top:2px" *ngIf="lastRecovery.roll">
                  Würfel:
                  <ng-container *ngFor="let d of lastRecovery.roll.dice; let i = index"><ng-container *ngIf="i > 0"> + </ng-container>[{{ d.rolls.join('+') }}<ng-container *ngIf="d.exploded">★</ng-container>]</ng-container>
                  = <strong style="color:#e0d5c0">{{ lastRecovery.roll.total }}</strong>
                  <ng-container *ngIf="lastRecovery.karmaRoll">
                    · Karma [{{ lastRecovery.karmaRoll.dice[0].rolls.join('+') }}<ng-container *ngIf="lastRecovery.karmaRoll.dice[0].exploded">★</ng-container>] = {{ lastRecovery.karmaRoll.total }}
                    → Gesamt <strong style="color:#e0d5c0">{{ lastRecovery.roll.total + lastRecovery.karmaRoll.total }}</strong>
                  </ng-container>
                </div>
              </div>
            </div>

            <mat-divider style="margin:20px 0"></mat-divider>

            <!-- Tränke -->
            <div class="section-title" style="margin-bottom:12px">Tränke</div>

            <div class="potion-list">
              <div class="potion-item" *ngFor="let p of potions()">
                <div class="potion-info">
                  <mat-icon class="potion-icon">{{ p.extraRecovery ? 'local_pharmacy' : 'local_drink' }}</mat-icon>
                  <div>
                    <div class="potion-name">{{ p.name }}</div>
                    <div class="potion-formula" *ngIf="p.extraRecovery">Extra-Probe + +{{ p.healStep }} Stufen</div>
                    <div class="potion-formula" *ngIf="!p.extraRecovery">+{{ p.healStep }} Stufen (verbraucht Probe)</div>
                    <div class="potion-desc" *ngIf="p.description">{{ p.description }}</div>
                  </div>
                </div>
                <div class="potion-qty">
                  <button mat-icon-button (click)="adjustPotionQty(p, -1)" [disabled]="p.quantity <= 0"><mat-icon>remove</mat-icon></button>
                  <span class="qty-val">{{ p.quantity }}×</span>
                  <button mat-icon-button (click)="adjustPotionQty(p, 1)"><mat-icon>add</mat-icon></button>
                </div>
                <button mat-raised-button color="accent"
                  [disabled]="p.quantity <= 0"
                  (click)="drinkPotion(p)"
                  [matTooltip]="p.extraRecovery ? 'Sofortige Extra-Probe + +' + p.healStep + ' Stufen (kein Slot-Verbrauch)' : '+' + p.healStep + ' Stufen Bonus auf nächste reguläre Probe'">
                  <mat-icon>healing</mat-icon> Trinken
                </button>
                <button mat-icon-button color="warn" (click)="removeEquipment(p)" matTooltip="Entfernen">
                  <mat-icon>delete</mat-icon>
                </button>
              </div>
              <div class="equip-empty" *ngIf="!potions().length">Keine Tränke im Inventar</div>
            </div>

            <mat-divider style="margin:20px 0"></mat-divider>

            <!-- Trank hinzufügen -->
            <div class="section-title" style="margin-bottom:8px">Trank hinzufügen</div>
            <div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap">
              <mat-form-field appearance="fill" style="width:90px">
                <mat-label>Anzahl</mat-label>
                <input matInput type="number" [(ngModel)]="newPotionQty" min="1">
              </mat-form-field>
              <button mat-stroked-button (click)="addErholungstrank()" matTooltip="+7 Stufen Bonus auf nächste reguläre Erholungsprobe">
                <mat-icon>local_drink</mat-icon> Erholungstrank
              </button>
              <button mat-stroked-button (click)="addHeiltrank()" matTooltip="Sofortige Extra-Erholungsprobe + +7 Stufen">
                <mat-icon>local_pharmacy</mat-icon> Heiltrank
              </button>
            </div>
          </div>
        </mat-tab>

        <!-- Arzt -->
        <mat-tab label="Arzt">
          <div class="tab-content">
            <div class="section-title" style="margin-bottom:12px">Arzt-Fertigkeit anwenden</div>

            <div style="font-size:13px;color:#aaa;margin-bottom:16px">
              Ein Charakter mit der <strong style="color:#c9a84c">Arzt</strong>-Fertigkeit behandelt diesen Charakter.
              <br>Wurf: WAH-Stufe + Rang vs. festem Mindestwurf <strong style="color:#ef9a9a">5</strong> (Verletzungen und Wunden).
              Verbraucht <strong>1× Verbandszeug</strong> des Heilers. Erfolg: +Rang auf nächste Erholungsprobe
              und der <strong>Wundabzug</strong> der nächsten Erholungsprobe entfällt.
            </div>

            <div *ngIf="(character?.wounds ?? 0) === 0"
              style="padding:12px;background:#1e1a16;border:1px solid #3a3028;border-radius:6px;color:#777;font-size:13px;margin-bottom:16px">
              Kein Verwundeter — Arztbehandlung nur bei Wunden möglich.
            </div>

            <ng-container *ngIf="(character?.wounds ?? 0) > 0">
              <div style="display:flex;align-items:center;gap:12px;margin-bottom:16px;flex-wrap:wrap">
                <mat-form-field appearance="fill" style="flex:1;min-width:200px">
                  <mat-label>Behandelnder Charakter</mat-label>
                  <mat-select [(ngModel)]="selectedHealerId">
                    <mat-option *ngFor="let c of healerCandidates()" [value]="c.id">
                      {{ c.name }} (Arzt Rang {{ arztRankOf(c) }}, Verbandszeug {{ verbandszeugCount(c) }}×)
                    </mat-option>
                  </mat-select>
                </mat-form-field>
                <button mat-raised-button color="accent"
                  [disabled]="!selectedHealerId || selectedHealerVerbandszeug() <= 0"
                  (click)="doArzt()"
                  [matTooltip]="selectedHealerVerbandszeug() > 0 ? 'WAH-Stufe + Rang vs. MW 5 — verbraucht 1 Verbandszeug' : 'Heiler hat kein Verbandszeug'">
                  <mat-icon>medical_services</mat-icon> Arztprobe
                </button>
              </div>

              <div *ngIf="selectedHealerId && selectedHealerVerbandszeug() <= 0"
                style="padding:10px;background:#2a1a1a;border:1px solid #5a2a2a;border-radius:6px;color:#ef9a9a;font-size:13px;margin-bottom:16px">
                Der gewählte Heiler hat kein Verbandszeug — Arztprobe nicht möglich.
              </div>

              <div class="arzt-result" *ngIf="lastArzt">
                <mat-icon [style.color]="lastArzt.success ? '#66bb6a' : '#ef5350'">
                  {{ lastArzt.success ? 'check_circle' : 'cancel' }}
                </mat-icon>
                <div>
                  <div class="arzt-name">{{ lastArzt.healerName }} behandelt {{ lastArzt.woundedName }}</div>
                  <div class="arzt-detail">
                    Stufe {{ lastArzt.rollStep }} (WN {{ lastArzt.perStep }} + Rang {{ lastArzt.skillRank }})
                    vs. MW {{ lastArzt.targetNumber }}
                    → <strong [style.color]="lastArzt.success ? '#66bb6a' : '#ef5350'">{{ lastArzt.roll?.total }}</strong>
                    <span *ngIf="lastArzt.success"> · <strong class="arzt-bonus">+{{ lastArzt.bonusGranted }} Bonus-Stufen</strong> auf nächste Erholungsprobe</span>
                    <span *ngIf="lastArzt.woundPenaltyNegated"> · <strong style="color:#80cbc4">Wundabzug der nächsten Erholungsprobe entfällt</strong></span>
                    <span *ngIf="!lastArzt.success"> · Fehlschlag</span>
                    <div style="color:#999;font-size:12px;margin-top:2px">Verbandszeug übrig: {{ lastArzt.verbandszeugRemaining }}×</div>
                  </div>
                </div>
              </div>
            </ng-container>
          </div>
        </mat-tab>

        <!-- Notizen -->
        <mat-tab label="Notizen">
          <div class="tab-content">
            <textarea
              class="notes-area"
              [(ngModel)]="character.notes"
              (blur)="saveNotes()"
              placeholder="Charakter-Notizen, Geschichte, Ausrüstung...">
            </textarea>
          </div>
        </mat-tab>

      </mat-tab-group>
    </div>
  `,
  styles: [`
    .sheet-container { padding: 20px; max-width: 1200px; }
    .sheet-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .char-name { font-family: 'Cinzel', serif; font-size: 1.4rem; color: #c9a84c; margin: 0 12px; }
    .char-sub { color: #888; font-size: 0.85rem; }
    .header-actions { display: flex; gap: 8px; }

    .status-bar {
      display: flex; gap: 16px; flex-wrap: wrap;
      background: #2a2520; border: 1px solid #3a3028;
      border-radius: 8px; padding: 16px; margin-bottom: 16px;
    }
    .status-block { min-width: 140px; }
    .status-label { font-size: 11px; text-transform: uppercase; letter-spacing: 0.1em; color: #9c7b3c; margin-bottom: 6px; }
    .stat-row { display: flex; align-items: center; gap: 4px; }
    .stat-value { font-size: 1.3rem; font-weight: bold; color: #e0d5c0; }
    .stat-sep { color: #666; }
    .stat-max { color: #888; }

    .currency-block { min-width: 200px; }
    .currency-row { display: flex; align-items: center; gap: 4px; margin-bottom: 4px; }
    .currency-icon { font-size: 1rem; min-width: 20px; }
    .currency-val { min-width: 40px; font-weight: bold; color: #e0d5c0; }
    .currency-input { width: 50px; background: #333; border: 1px solid #555; color: #fff; padding: 2px 4px; border-radius: 3px; }

    .ctrl-btns { display: flex; align-items: center; }

    .tab-content { padding: 16px; }
    .two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }

    .attr-grid, .derived-grid { }
    .attr-row-item {
      display: flex; align-items: center; gap: 8px;
      padding: 6px 0; border-bottom: 1px solid #2a2520;
    }
    .attr-label { flex: 1; color: #c0b090; font-size: 0.9rem; }
    .attr-ctrl { display: flex; align-items: center; gap: 4px; }
    .attr-val { min-width: 32px; text-align: center; font-size: 1.1rem; font-weight: bold; color: #c9a84c; }
    .attr-step { min-width: 60px; text-align: right; font-size: 0.8rem; color: #666; }

    .derived-item { display: flex; justify-content: space-between; padding: 4px 0; border-bottom: 1px solid #2a2520; }
    .derived-label { color: #999; font-size: 0.85rem; display: flex; align-items: baseline; gap: 6px; }
    .derived-note { color: #b39ddb; font-size: 0.7rem; font-style: italic; }
    .derived-val { font-weight: bold; color: #e0d5c0; }
    .inline-input { background: transparent; border: none; border-bottom: 1px solid #555; color: #e0d5c0; width: 120px; }

    .circle-row { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
    .legend-row { display: flex; align-items: center; gap: 16px; margin-top: 16px; }

    .section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
    .talent-list { display: flex; flex-direction: column; gap: 4px; }
    .talent-item {
      display: flex; align-items: center; gap: 8px;
      background: #2a2520; border-radius: 6px; padding: 6px 8px;
    }
    .talent-info { flex: 1; }
    .talent-name { font-size: 0.9rem; color: #e0d5c0; }
    .talent-attr { font-size: 0.75rem; color: #666; margin-left: 6px; }
    .rank-ctrl { display: flex; align-items: center; gap: 2px; }
    .rank-val { min-width: 50px; text-align: center; font-size: 0.85rem; color: #c9a84c; }

    .probe-result { margin-top: 16px; background: #1e1e1e; border: 1px solid #555; border-radius: 8px; padding: 12px; }
    .roll-total { font-size: 2.5rem; font-weight: bold; color: #c9a84c; min-width: 60px; text-align: center; }
    .dice-details { display: flex; flex-wrap: wrap; gap: 4px; }

    .karma-modifier-row {
      display: flex; align-items: center; gap: 6px; margin: 6px 0 4px; flex-wrap: wrap;
    }
    .karma-mod-label { font-size: 11px; color: #666; }
    .karma-mod-input {
      width: 48px; background: #1e1a16; border: 1px solid #3a3028; border-radius: 4px;
      color: #c9a84c; text-align: center; font-size: 0.9rem; padding: 2px 4px;
      &:focus { outline: none; border-color: #c9a84c; }
    }
    .karma-mod-hint { font-size: 11px; color: #888; }

    .ritual-btn {
      margin-top: 6px; font-size: 11px; padding: 0 8px; height: 28px; line-height: 28px;
      color: #c9a84c; border-color: #4a3a20;
      mat-icon { font-size: 14px; height: 14px; width: 14px; margin-right: 4px; }
      &:not([disabled]):hover { background: rgba(201,168,76,0.1); }
      &[disabled] { opacity: 0.35; }
    }

    .notes-area {
      width: 100%; min-height: 300px; background: #2a2520;
      border: 1px solid #3a3028; border-radius: 4px;
      color: #e0d5c0; padding: 12px; font-family: 'Roboto', sans-serif; font-size: 0.9rem;
      resize: vertical; box-sizing: border-box;
    }

    .equip-section { }
    .equip-list { display: flex; flex-direction: column; gap: 6px; margin: 8px 0 12px; }
    .equip-item {
      display: flex; align-items: center; gap: 10px;
      background: #1e1a16; border: 1px solid #3a3028; border-radius: 6px; padding: 8px 12px;
    }
    .equip-name { font-weight: 600; color: #e0d5c0; min-width: 140px; display: flex; align-items: center; gap: 6px; }
    .claw-icon { font-size: 1.1rem; }
    .equip-stats { display: flex; align-items: center; gap: 8px; flex: 1; flex-wrap: wrap; }
    .equip-badge {
      border-radius: 10px; padding: 2px 10px; font-size: 0.78rem; font-weight: 700;
      &.weapon { background: rgba(255,112,67,0.15); color: #ff7043; }
      &.secondary-weapon { background: rgba(206,147,216,0.15); color: #ce93d8; border-color: #3a1a40; }
      &.claw-badge { background: rgba(141,110,99,0.18); color: #d7ccc8; border: 1px solid #5d4037; }
      &.armor-phys { background: rgba(66,165,245,0.15); color: #42a5f5; }
      &.armor-myst { background: rgba(171,71,188,0.15); color: #ab47bc; }
      &.armor-init { background: rgba(255,167,38,0.15); color: #ffa726; }
      &.shield-phys { background: rgba(102,187,106,0.15); color: #66bb6a; }
      &.shield-myst { background: rgba(171,71,188,0.15); color: #ce93d8; }
      &.inactive-badge { background: rgba(120,120,120,0.15); color: #888; border-color: #555; font-style: italic; }
    }

    .defense-bonus-row {
      display: flex; align-items: center; gap: 8px;
      padding: 3px 0; border-bottom: 1px solid #2a2520;
    }
    .bonus-val {
      min-width: 36px; text-align: center; font-size: 0.95rem; font-weight: bold; color: #888;
      &.positive { color: #66bb6a; }
      &.negative { color: #ef5350; }
    }
    .equip-desc { font-size: 0.78rem; color: #666; font-style: italic; }
    .equip-empty { color: #555; font-size: 0.82rem; font-style: italic; padding: 4px 0 8px; }
    .equip-add-form { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 4px; }

    .matrix-section { }
    .matrix-list { display: flex; flex-direction: column; gap: 10px; }
    .matrix-item {
      display: flex; align-items: center; gap: 12px; flex-wrap: wrap;
      background: rgba(100, 60, 180, 0.08); border: 1px solid rgba(100, 60, 180, 0.25);
      border-radius: 8px; padding: 10px 14px;
    }
    .matrix-header { display: flex; flex-direction: column; min-width: 100px; }
    .matrix-label { font-family: 'Cinzel', serif; color: #b39ddb; font-size: 0.95rem; font-weight: 600; }
    .matrix-rank { font-size: 0.75rem; color: #666; margin-top: 2px; }
    .matrix-spell-info { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }

    .spell-list { display: flex; flex-direction: column; gap: 6px; }
    .spell-item {
      display: flex; align-items: center; gap: 10px;
      background: #1e1a16; border: 1px solid #3a3028; border-radius: 6px; padding: 8px 12px;
    }
    .spell-info { min-width: 160px; }
    .spell-name { font-weight: 600; color: #e0d5c0; display: block; }
    .spell-circle { font-size: 0.75rem; color: #666; }
    .spell-details { display: flex; align-items: center; gap: 8px; flex: 1; flex-wrap: wrap; }
    .spell-badge {
      border-radius: 10px; padding: 2px 10px; font-size: 0.78rem; font-weight: 700;
      &.spell-damage { background: rgba(255,112,67,0.15); color: #ff7043; }
      &.spell-buff { background: rgba(102,187,106,0.15); color: #66bb6a; }
      &.spell-debuff { background: rgba(239,83,80,0.15); color: #ef5350; }
      &.spell-heal { background: rgba(66,165,245,0.15); color: #42a5f5; }
    }
    .spell-threads { font-size: 0.78rem; color: #ab47bc; }
    .spell-effect { font-size: 0.78rem; color: #888; font-style: italic; }

    .potion-list { display: flex; flex-direction: column; gap: 8px; margin-bottom: 12px; }
    .potion-item {
      display: flex; align-items: center; gap: 12px;
      background: #1e1a16; border: 1px solid #3a3028; border-radius: 8px; padding: 10px 14px;
    }
    .potion-info { display: flex; align-items: center; gap: 10px; flex: 1; }
    .potion-icon { color: #66bb6a; font-size: 1.8rem; height: 1.8rem; width: 1.8rem; }
    .potion-name { font-weight: 600; color: #e0d5c0; font-size: 0.95rem; }
    .potion-formula { font-size: 0.78rem; color: #9c7b3c; }
    .potion-desc { font-size: 0.75rem; color: #666; font-style: italic; margin-top: 2px; }
    .potion-qty { display: flex; align-items: center; gap: 4px; }
    .qty-val { min-width: 28px; text-align: center; font-weight: bold; color: #c9a84c; font-size: 1rem; }

    .heal-result {
      display: flex; align-items: center; gap: 12px;
      background: rgba(102,187,106,0.08); border: 1px solid rgba(102,187,106,0.3);
      border-radius: 8px; padding: 12px 16px; margin-top: 12px;
      mat-icon { font-size: 1.8rem; height: 1.8rem; width: 1.8rem; }
    }
    .heal-name { font-weight: 600; color: #e0d5c0; font-size: 0.9rem; }
    .heal-detail { font-size: 0.85rem; color: #999; margin-top: 2px; }
    .heal-amount { color: #66bb6a; font-size: 1rem; }

    .holzhaut-row {
      display: flex; align-items: center; justify-content: space-between;
      gap: 12px; padding: 8px 4px;
    }
    .holzhaut-status { display: flex; align-items: center; gap: 8px; font-size: 0.92rem; }
    .holzhaut-icon { font-size: 1.2rem; }
    .holzhaut-active { color: #81c784; }
    .holzhaut-active strong { color: #c5e1a5; font-weight: 700; }
    .holzhaut-inactive { color: #777; font-style: italic; }
    .holzhaut-actions { display: flex; gap: 8px; }
    .holzhaut-result {
      background: rgba(129,199,132,0.08); border: 1px solid rgba(129,199,132,0.3);
      border-radius: 6px; padding: 8px 12px; margin-top: 6px;
    }
    .holzhaut-detail { font-size: 0.82rem; color: #999; }
    .holzhaut-total { color: #81c784; font-size: 0.95rem; }

    .arzt-result {
      display: flex; align-items: center; gap: 12px;
      background: rgba(102,187,106,0.08); border: 1px solid rgba(102,187,106,0.25);
      border-radius: 8px; padding: 12px 16px; margin-top: 8px;
      mat-icon { font-size: 1.8rem; height: 1.8rem; width: 1.8rem; }
    }
    .arzt-name { font-weight: 600; color: #e0d5c0; font-size: 0.9rem; }
    .arzt-detail { font-size: 0.85rem; color: #999; margin-top: 2px; }
    .arzt-bonus { color: #66bb6a; }
  `]
})
export class CharacterSheetComponent implements OnInit {
  character?: Character;
  derived?: DerivedStats;
  lastProbe?: ProbeResult;
  probeTargetNumber = 10;
  selectedTalentId?: number;
  selectedSkillId?: number;
  availableTalents: TalentDefinition[] = [];
  availableSkills: SkillDefinition[] = [];

  selectedSpellId?: number;
  availableSpells: SpellDefinition[] = [];

  /** Angriffstalente/-fertigkeiten, denen eine Waffe zugeordnet werden kann. */
  weaponAttackTalents = ['Nahkampfwaffen', 'Projektilwaffen', 'Wurfwaffen', 'Waffenloser Kampf'];

  newWeapon: { name: string; damageBonus: number; twoHanded: boolean; attackTalentName: string; tailWeapon: boolean; description: string } = { name: '', damageBonus: 0, twoHanded: false, attackTalentName: '', tailWeapon: false, description: '' };
  newArmor: { name: string; physicalArmor: number; mysticalArmor: number; initiativePenalty: number; description: string } = { name: '', physicalArmor: 0, mysticalArmor: 0, initiativePenalty: 0, description: '' };
  newShield: { name: string; physicalDefenseBonus: number; mysticDefenseBonus: number; initiativePenalty: number; buckler: boolean; description: string } = { name: '', physicalDefenseBonus: 0, mysticDefenseBonus: 0, initiativePenalty: 0, buckler: false, description: '' };
  newPotionQty: number = 1;
  newAmulet: { name: string; amuletForSpell: boolean; description: string } = { name: '', amuletForSpell: false, description: '' };
  newGear: { name: string; probeBonusTalentName: string; probeBonusValue: number } = { name: '', probeBonusTalentName: '', probeBonusValue: 0 };
  lastAmuletRecharge?: AmuletRechargeResult;
  lastRecovery?: RecoveryTestResult;
  recoveryUseKarma = false;
  lastHolzhaut?: HolzhautResult;
  lastArzt?: ArztResult;
  allCharacters: Character[] = [];
  selectedHealerId?: number;
  newVerbandszeugQty: number = 3;
  currencyDelta: Record<string, number> = { gold: 0, silver: 0, copper: 0 };

  attributeFields = [
    { key: 'dexterity', label: 'Geschicklichkeit (GE)' },
    { key: 'strength', label: 'Stärke (ST)' },
    { key: 'toughness', label: 'Zähigkeit (ZÄ)' },
    { key: 'perception', label: 'Wahrnehmung (WN)' },
    { key: 'willpower', label: 'Willenskraft (WK)' },
    { key: 'charisma', label: 'Charisma (CH)' },
  ];

  circles = Array.from({ length: 15 }, (_, i) => i + 1);
  races = RACES;
  disciplines: DisciplineDefinition[] = [];

  derivedFields = [
    { key: 'physicalDefense', label: 'KV (Körperliche Verteidigung)' },
    { key: 'spellDefense', label: 'MV (Mystische Verteidigung)' },
    { key: 'socialDefense', label: 'SV (Soziale Verteidigung)' },
    { key: 'woundThreshold', label: 'Wundenschwelle' },
    { key: 'unconsciousnessRating', label: 'Bewusstlosigkeitsschwelle' },
    { key: 'deathRating', label: 'Todesschwelle' },
    { key: 'initiativeStep', label: 'Initiativestufe' },
    { key: 'recoveryStep', label: 'Erholungsstufe' },
    { key: 'physicalArmor', label: 'Rüstung (physisch)' },
    { key: 'mysticArmor', label: 'Rüstung (mystisch)' },
  ];

  defenseBonusFields = [
    { field: 'physicalDefenseBonus', label: 'KV-Bonus' },
    { field: 'spellDefenseBonus',    label: 'MV-Bonus' },
    { field: 'socialDefenseBonus',   label: 'SV-Bonus' },
  ];

  statBonusFields = [
    { field: 'healthBonus',     label: 'Lebenspunkte-Bonus (BW & TD)' },
    { field: 'initiativeBonus', label: 'Initiative-Bonus' },
    { field: 'recoveryBonus',   label: 'Erholungsstufen-Bonus' },
  ];

  currencies = [
    { field: 'gold', label: 'Gold', icon: 'G', color: '#ffd700' },
    { field: 'silver', label: 'Silber', icon: 'S', color: '#c0c0c0' },
    { field: 'copper', label: 'Kupfer', icon: 'K', color: '#b87333' },
  ];

  constructor(
    private route: ActivatedRoute,
    public router: Router,
    private characterService: CharacterService,
    private refService: ReferenceService,
    private diceService: DiceService,
    private activeUserService: ActiveUserService,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    const id = +this.route.snapshot.params['id'];
    this.characterService.findById(id).subscribe(c => {
      // Access guard: non-GM users cannot view GM characters
      if (c.gmCharacter && !this.activeUserService.activeUser?.gamemaster) {
        this.router.navigate(['/characters']);
        this.snack.open('Dieser Charakter ist nicht zugänglich.', 'OK', { duration: 2500 });
        return;
      }
      this.character = c;
      this.loadDerived();
      this.loadAvailableSpells();
    });
    this.refService.getTalents().subscribe(t => this.availableTalents = t);
    this.refService.getSkills().subscribe(s => this.availableSkills = s);
    this.refService.getDisciplines().subscribe(d => this.disciplines = d);
    this.characterService.findAll().subscribe(all => this.allCharacters = all);
  }

  isGm(): boolean {
    return this.activeUserService.activeUser?.gamemaster === true;
  }

  toggleGmCharacter(): void {
    if (!this.character?.id) return;
    const updated = { ...this.character, gmCharacter: !this.character.gmCharacter };
    this.characterService.update(this.character.id, updated as any).subscribe(c => {
      this.character = c;
      this.snack.open(
        c.gmCharacter ? 'Als Spielleiter-Charakter markiert' : 'Spielleiter-Markierung aufgehoben',
        'OK', { duration: 1500 }
      );
    });
  }

  loadDerived(): void {
    if (!this.character?.id) return;
    this.characterService.getDerived(this.character.id).subscribe(d => this.derived = d);
  }

  save(): void {
    if (!this.character?.id) return;
    this.characterService.update(this.character.id, this.character).subscribe(c => {
      this.character = c;
      this.loadDerived();
      this.snack.open('Gespeichert!', 'OK', { duration: 1500 });
    });
  }

  saveNotes(): void {
    if (!this.character?.id) return;
    this.characterService.updateNotes(this.character.id, this.character.notes).subscribe();
  }

  recalculate(): void {
    if (!this.character?.id) return;
    this.characterService.update(this.character.id, this.character).subscribe(() => {
      this.characterService.recalculate(this.character!.id!).subscribe(c => {
        this.character = c;
        this.loadDerived();
        this.snack.open('Abgeleitete Werte neu berechnet.', 'OK', { duration: 2000 });
      });
    });
  }

  onDisciplineChange(): void {
    if (!this.character?.id) return;
    if (this.hasNoKarma()) {
      this.character.karmaModifier = 0;
      this.character.karmaMax = 0;
      this.character.karmaCurrent = 0;
    } else if (this.character.karmaModifier === 0) {
      this.character.karmaModifier = 5;
    }
    this.characterService.update(this.character.id, this.character).subscribe(c => {
      this.character = c;
      this.loadDerived();
      this.loadAvailableSpells();
    });
  }

  onCircleChange(): void {
    if (!this.character?.id) return;
    this.characterService.update(this.character.id, this.character).subscribe(c => {
      this.character = c;
      this.loadDerived();
    });
  }

  adjustField(field: string, delta: number): void {
    if (!this.character?.id) return;
    this.characterService.updateField(this.character.id, field, delta).subscribe(c => {
      this.character = c;
      if (field.endsWith('Bonus') || field === 'dexterity' || field === 'perception' || field === 'charisma') {
        this.loadDerived();
      }
    });
  }

  addCurrency(field: string): void {
    const delta = this.currencyDelta[field];
    if (delta && this.character?.id) {
      this.adjustField(field, delta);
      this.currencyDelta[field] = 0;
    }
  }

  adjustAttr(key: string, delta: number): void {
    this.adjustField(key, delta);
  }

  raceLabel(race?: string | null): string {
    return RACES.find(r => r.value === race)?.label ?? '';
  }

  hasNoKarma(): boolean {
    return this.character?.discipline?.name === 'Keine Disziplin';
  }

  compareDiscipline(a: DisciplineDefinition | null, b: DisciplineDefinition | null): boolean {
    return a?.id === b?.id;
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

  getAttr(key: string): number {
    return (this.character as any)?.[key] ?? 0;
  }

  getDerived(key: string): number {
    return (this.derived as any)?.[key] ?? 0;
  }

  /** Natürliche mystische Rüstung anhand der Willenskraft (ED4): min(6, WIL/5). */
  naturalMysticArmor(): number {
    const wil = this.character?.willpower ?? 0;
    return Math.min(6, Math.max(0, Math.floor(wil / 5)));
  }

  shieldKVBonus(): number {
    return this.shields().filter(s => s.active !== false).reduce((sum, s) => sum + (s.physicalDefenseBonus ?? 0), 0);
  }

  /** Kleiner Hinweistext, der unter dem Label angezeigt wird. */
  derivedNote(key: string): string | null {
    if (key === 'physicalDefense') {
      const bonus = this.shieldKVBonus();
      return bonus > 0 ? `+${bonus} Schild` : null;
    }
    if (key === 'mysticArmor') {
      const nat = this.naturalMysticArmor();
      return nat > 0 ? `+${nat} aus WIL` : null;
    }
    if (key === 'unconsciousnessRating' || key === 'deathRating') {
      const parts: string[] = [];
      if (this.holzhautBonus() > 0) parts.push(`+${this.holzhautBonus()} Holzhaut`);
      if (this.bloodMagicDamage() > 0) parts.push(`−${this.bloodMagicDamage()} Blutmagie`);
      if (parts.length) return parts.join(' · ');
    }
    return null;
  }

  /** Blutmagie-Schaden getragener Amulette (vom Backend via derived). */
  bloodMagicDamage(): number {
    return this.derived?.bloodMagicDamage ?? 0;
  }

  /** Tooltip mit ausführlicher Erklärung. */
  derivedTooltip(key: string): string {
    if (key === 'physicalDefense') {
      const bonus = this.shieldKVBonus();
      if (bonus <= 0) return '';
      const names = this.shields()
        .filter(s => s.active !== false && (s.physicalDefenseBonus ?? 0) > 0)
        .map(s => `${s.name} +${s.physicalDefenseBonus}`)
        .join(', ');
      return `Schildbonus auf KV: ${names}`;
    }
    if (key === 'mysticArmor') {
      const wil = this.character?.willpower ?? 0;
      const nat = this.naturalMysticArmor();
      return `Natürliche mystische Rüstung aus Willenskraft ${wil}: ${nat} (Tabelle: 1-4=0, 5-9=1, 10-14=2, 15-19=3, 20-24=4, 25-29=5, 30+=6). Ausrüstungs-Boni werden zusätzlich addiert.`;
    }
    if (key === 'unconsciousnessRating' || key === 'deathRating') {
      const parts: string[] = [];
      if (this.holzhautBonus() > 0) parts.push(`Holzhaut-Bonus +${this.holzhautBonus()}`);
      if (this.bloodMagicDamage() > 0) parts.push(`Blutmagie-Abzug −${this.bloodMagicDamage()} (Amulette)`);
      if (parts.length) return 'Inklusive ' + parts.join(' und ');
    }
    return '';
  }

  // --- Holzhaut ---

  /** True, wenn der Charakter das Holzhaut-Talent besitzt. */
  hasHolzhautTalent(): boolean {
    return !!this.character?.talents?.some(t => t.talentDefinition.name === 'Holzhaut');
  }

  /** Aktueller Holzhaut-Bonus (vom Backend via derived geliefert). */
  holzhautBonus(): number {
    return this.derived?.holzhautBonus ?? this.character?.holzhautBonus ?? 0;
  }

  isHolzhautActive(): boolean {
    return this.holzhautBonus() > 0;
  }

  useHolzhaut(): void {
    if (!this.character?.id) return;
    this.characterService.useHolzhaut(this.character.id).subscribe({
      next: result => {
        this.lastHolzhaut = result;
        if (this.character) {
          this.character.holzhautBonus = result.bonus;
        }
        this.loadDerived();
        const msg = result.previousBonus > 0
          ? `Holzhaut neu gewirkt: +${result.bonus} (vorher +${result.previousBonus})`
          : `Holzhaut gewirkt: +${result.bonus}`;
        this.snack.open(msg, 'OK', { duration: 2500 });
      },
      error: err => {
        this.snack.open(err?.error?.message ?? 'Holzhaut konnte nicht gewirkt werden.', 'OK', { duration: 3000 });
      }
    });
  }

  endHolzhaut(): void {
    if (!this.character?.id || !this.isHolzhautActive()) return;
    this.characterService.endHolzhaut(this.character.id).subscribe({
      next: result => {
        this.lastHolzhaut = result;
        if (this.character) {
          this.character.holzhautBonus = 0;
          this.character.currentDamage = Math.max(0, this.character.currentDamage - result.healed);
        }
        this.loadDerived();
        this.snack.open(
          `Holzhaut beendet · ${result.healed} Schaden geheilt (Puffer war +${result.previousBonus})`,
          'OK', { duration: 2500 }
        );
      },
      error: err => {
        this.snack.open(err?.error?.message ?? 'Holzhaut konnte nicht beendet werden.', 'OK', { duration: 3000 });
      }
    });
  }

  getDefenseBonus(field: string): number {
    return (this.character as any)?.[field] ?? 0;
  }

  getCurrencyValue(field: string): number {
    return (this.character as any)?.[field] ?? 0;
  }

  damagePercent(): number {
    if (!this.character) return 0;
    const ur = this.derived?.unconsciousnessRating ?? this.character.toughness * 2;
    return Math.min(100, (this.character.currentDamage / ur) * 100);
  }

  woundDots(): boolean[] {
    const max = 5;
    return Array.from({ length: max }, (_, i) => i < (this.character?.wounds ?? 0));
  }

  karmaDots(): boolean[] {
    const max = Math.min(this.character?.karmaMax ?? 10, 20);
    return Array.from({ length: max }, (_, i) => i < (this.character?.karmaCurrent ?? 0));
  }

  addTalent(): void {
    if (!this.character?.id || !this.selectedTalentId) return;
    this.characterService.addTalent(this.character.id, this.selectedTalentId).subscribe(c => {
      this.character = c;
      this.selectedTalentId = undefined;
    });
  }

  removeTalent(talentId: number): void {
    if (!this.character?.id) return;
    this.characterService.removeTalent(this.character.id, talentId).subscribe(() => {
      this.character!.talents = this.character!.talents.filter(t => t.id !== talentId);
    });
  }

  updateTalentRank(ct: any, rank: number): void {
    if (!this.character?.id || rank < 1) return;
    this.characterService.updateTalentRank(this.character.id, ct.id, rank).subscribe(() => {
      ct.rank = rank;
    });
  }

  addSkill(): void {
    if (!this.character?.id || !this.selectedSkillId) return;
    this.characterService.addSkill(this.character.id, this.selectedSkillId).subscribe(c => {
      this.character = c;
      this.selectedSkillId = undefined;
    });
  }

  removeSkill(skillId: number): void {
    if (!this.character?.id) return;
    this.characterService.removeSkill(this.character.id, skillId).subscribe(() => {
      this.character!.skills = this.character!.skills.filter(s => s.id !== skillId);
    });
  }

  updateSkillRank(cs: any, rank: number): void {
    if (!this.character?.id || rank < 1) return;
    this.characterService.updateSkillRank(this.character.id, cs.id, rank).subscribe(() => {
      cs.rank = rank;
    });
  }

  rollProbe(talentId: number | null, skillId: number | null): void {
    if (!this.character?.id) return;
    this.diceService.probe({
      characterId: this.character.id,
      talentId: talentId ?? undefined,
      skillId: skillId ?? undefined,
      bonusSteps: 0,
      targetNumber: this.probeTargetNumber,
      spendKarma: false
    }).subscribe({
      next: r => {
        this.lastProbe = r;
        if (r.karmaUsed) {
          this.characterService.findById(this.character!.id!).subscribe(c => this.character = c);
        }
      },
      error: err => {
        const msg = err?.error?.message ?? err?.message ?? 'Unbekannter Fehler';
        this.snack.open(`Probe-Fehler: ${msg}`, 'OK', { duration: 5000 });
      }
    });
  }

  saveKarmaModifier(): void {
    if (!this.character?.id) return;
    this.characterService.update(this.character.id!, this.character).subscribe(c => {
      this.character = c;
      this.characterService.recalculate(this.character.id!).subscribe(updated => this.character = updated);
    });
  }

  karmaRitual(): void {
    if (!this.character?.id) return;
    const delta = this.character.karmaMax - this.character.karmaCurrent;
    if (delta <= 0) return;
    this.adjustField('karma', delta);
    this.snack.open('Karmaritual vollzogen — Karma aufgefüllt!', 'OK', { duration: 2000 });
  }

  degreeClass(p: ProbeResult): string {
    if (!p.success) return 'failure';
    return `success-${Math.min(p.extraSuccesses, 4)}`;
  }

  healerCandidates(): Character[] {
    return this.allCharacters.filter(c =>
      c.id !== this.character?.id &&
      c.skills?.some(s => s.skillDefinition.name === 'Arzt')
    );
  }

  arztRankOf(c: Character): number {
    return c.skills?.find(s => s.skillDefinition.name === 'Arzt')?.rank ?? 0;
  }

  /** Verbandszeug-Anwendungen des aktuell gewählten Heilers. */
  selectedHealerVerbandszeug(): number {
    const healer = this.allCharacters.find(c => c.id === this.selectedHealerId);
    return this.verbandszeugCount(healer);
  }

  doArzt(): void {
    if (!this.character?.id || !this.selectedHealerId) return;
    this.characterService.applyArzt(this.character.id, this.selectedHealerId).subscribe({
      next: result => {
        this.lastArzt = result;
        // Verbandszeug wird bei jeder Anwendung verbraucht → Heilerliste auffrischen
        this.characterService.findAll().subscribe(all => this.allCharacters = all);
        if (result.success) {
          this.characterService.findById(this.character!.id!).subscribe(c => { this.character = c; });
          this.snack.open(
            `${result.healerName}: Erfolg! +${result.bonusGranted} Bonus-Stufen + Wundabzug aufgehoben (MW ${result.targetNumber}, Wurf: ${result.roll?.total})`,
            'OK', { duration: 4000 }
          );
        } else {
          this.snack.open(
            `${result.healerName}: Fehlschlag (MW ${result.targetNumber}, Wurf: ${result.roll?.total}) — Verbandszeug verbraucht`,
            'OK', { duration: 3000 }
          );
        }
      },
      error: err => this.snack.open(err?.error?.message ?? 'Fehler bei Arztprobe', 'OK', { duration: 3000 })
    });
  }

  sortedTalents() {
    return [...(this.character?.talents ?? [])].sort((a, b) => b.rank - a.rank);
  }

  /** Alle Matrizen-Instanzen (normale + erweiterte) des Charakters. */
  spellMatrices(): CharacterTalent[] {
    return (this.character?.talents ?? [])
      .filter(ct => ct.talentDefinition.name === 'Zaubermatritze'
                 || ct.talentDefinition.name === 'Erweiterte Matrize');
  }

  /** Noch zu webende Fäden des Matrix-Zaubers — erweiterte Matrize hat bereits 1 Faden vorgewoben. */
  matrixRemainingThreads(m: CharacterTalent): number {
    const threads = m.assignedSpell?.threads ?? 0;
    return Math.max(0, threads - (this.isEnhancedMatrix(m) ? 1 : 0));
  }

  /** True, wenn diese Matrix eine "Erweiterte Matrize" ist (1 Faden vorgewoben). */
  isEnhancedMatrix(m: CharacterTalent): boolean {
    return m.talentDefinition.name === 'Erweiterte Matrize';
  }

  /** Gelernte Zauber des Charakters, die in diese Matrix passen (Kreis ≤ Rang der Matrix). */
  spellsForMatrix(matrix: CharacterTalent) {
    return (this.character?.spells ?? [])
      .filter(cs => cs.spellDefinition.circle <= matrix.rank);
  }

  assignSpellToMatrix(matrix: CharacterTalent, spellId: number | null): void {
    if (!this.character?.id) return;
    this.characterService.assignSpellToMatrix(this.character.id, matrix.id, spellId).subscribe({
      next: c => { this.character = c; },
      error: err => this.snack.open(err?.error?.message ?? 'Zuweisung fehlgeschlagen.', 'OK', { duration: 3000 })
    });
  }

  /** Dropdown-Liste: normale Talente nur wenn noch nicht gelernt; Multi-Instance-Talente immer, solange unter maxInstances. */
  availableTalentsForDropdown(): TalentDefinition[] {
    const learnedIds = new Set(this.character?.talents?.map(ct => ct.talentDefinition.id) ?? []);
    return this.availableTalents.filter(t => {
      const max = t.maxInstances ?? 1;
      if (max === 1) return !learnedIds.has(t.id);
      // Multi-instance: zeige immer, Deaktivierung via isTalentMaxed()
      return true;
    });
  }

  /** Wie viele Instanzen des Talents der Charakter bereits hat. */
  talentInstanceCount(t: TalentDefinition): number {
    return this.character?.talents?.filter(ct => ct.talentDefinition.id === t.id).length ?? 0;
  }

  /** Ist das maxInstances-Limit erreicht? */
  isTalentMaxed(t: TalentDefinition): boolean {
    return this.talentInstanceCount(t) >= (t.maxInstances ?? 1);
  }

  /** Label im Dropdown z.B. " (1/3)" für Zaubermatritze. */
  talentInstanceLabel(t: TalentDefinition): string {
    if ((t.maxInstances ?? 1) <= 1) return '';
    return ` (${this.talentInstanceCount(t)}/${t.maxInstances})`;
  }

  /** Suffix in der Talentliste z.B. " 1" / " 2" / " 3" wenn mehrere Instanzen vorhanden. */
  talentInstanceSuffix(ct: CharacterTalent): string {
    if ((ct.talentDefinition.maxInstances ?? 1) <= 1) return '';
    const instances = this.character?.talents?.filter(t => t.talentDefinition.id === ct.talentDefinition.id) ?? [];
    const idx = instances.findIndex(t => t.id === ct.id);
    return ' ' + (idx + 1);
  }

  weapons(): Equipment[] {
    return (this.character?.equipment ?? []).filter(e => e.type === 'WEAPON');
  }

  armors(): Equipment[] {
    return (this.character?.equipment ?? []).filter(e => e.type === 'ARMOR');
  }

  shields(): Equipment[] {
    return (this.character?.equipment ?? []).filter(e => e.type === 'SHIELD');
  }

  potions(): Equipment[] {
    return (this.character?.equipment ?? []).filter(e => e.type === 'POTION');
  }

  amulets(): Equipment[] {
    return (this.character?.equipment ?? []).filter(e => e.type === 'AMULET');
  }

  verbandszeug(): Equipment[] {
    return (this.character?.equipment ?? []).filter(e => e.type === 'VERBANDSZEUG');
  }

  gear(): Equipment[] {
    return (this.character?.equipment ?? []).filter(e => e.type === 'GEAR');
  }

  /** Namen aller Talente + Fertigkeiten des Charakters (für die GEAR-Bonus-Zuordnung). */
  probeTargetNames(): string[] {
    const talents = (this.character?.talents ?? []).map(t => t.talentDefinition.name);
    const skills = (this.character?.skills ?? []).map(s => s.skillDefinition.name);
    return Array.from(new Set([...talents, ...skills])).sort();
  }

  addGear(): void {
    if (!this.character?.id || !this.newGear.name.trim() || !this.newGear.probeBonusTalentName) return;
    const eq: Equipment = {
      name: this.newGear.name.trim(), type: 'GEAR',
      damageBonus: 0, physicalArmor: 0, mysticalArmor: 0, initiativePenalty: 0,
      physicalDefenseBonus: 0, mysticDefenseBonus: 0, quantity: 1, healStep: 0,
      probeBonusTalentName: this.newGear.probeBonusTalentName,
      probeBonusValue: this.newGear.probeBonusValue
    };
    this.characterService.addEquipment(this.character.id, eq).subscribe(c => {
      this.character = c;
      this.newGear = { name: '', probeBonusTalentName: '', probeBonusValue: 0 };
    });
  }

  /** Schnellanlage: Leichte Stiefel (+2 auf Heimlicher Schritt). */
  addLeichteStiefel(): void {
    if (!this.character?.id) return;
    const eq: Equipment = {
      name: 'Leichte Stiefel', type: 'GEAR',
      damageBonus: 0, physicalArmor: 0, mysticalArmor: 0, initiativePenalty: 0,
      physicalDefenseBonus: 0, mysticDefenseBonus: 0, quantity: 1, healStep: 0,
      probeBonusTalentName: 'Heimlicher Schritt', probeBonusValue: 2
    };
    this.characterService.addEquipment(this.character.id, eq).subscribe(c => { this.character = c; });
  }

  /** Schnellanlage: Schwimmkristall (+3 auf Schwimmen, erlaubt Unterwasseratmung von Rang Minuten). */
  addSchwimmkristall(): void {
    if (!this.character?.id) return;
    const eq: Equipment = {
      name: 'Schwimmkristall', type: 'GEAR',
      damageBonus: 0, physicalArmor: 0, mysticalArmor: 0, initiativePenalty: 0,
      physicalDefenseBonus: 0, mysticDefenseBonus: 0, quantity: 1, healStep: 0,
      probeBonusTalentName: 'Schwimmen', probeBonusValue: 3,
      description: 'Erlaubt Unterwasseratmung von Rang Minuten.'
    };
    this.characterService.addEquipment(this.character.id, eq).subscribe(c => { this.character = c; });
  }

  /** Gesamtzahl verbleibender Verbandszeug-Anwendungen eines (Heiler-)Charakters. */
  verbandszeugCount(c?: Character): number {
    return (c?.equipment ?? []).filter(e => e.type === 'VERBANDSZEUG').reduce((sum, e) => sum + (e.quantity ?? 0), 0);
  }

  addVerbandszeug(): void {
    if (!this.character?.id) return;
    const eq: Equipment = {
      name: 'Verbandszeug', type: 'VERBANDSZEUG',
      damageBonus: 0, physicalArmor: 0, mysticalArmor: 0, initiativePenalty: 0,
      physicalDefenseBonus: 0, mysticDefenseBonus: 0,
      quantity: Math.max(1, this.newVerbandszeugQty), healStep: 0
    };
    this.characterService.addEquipment(this.character.id, eq).subscribe(c => {
      this.character = c;
      this.newVerbandszeugQty = 3;
    });
  }

  addAmulet(): void {
    if (!this.character?.id || !this.newAmulet.name.trim()) return;
    const eq: Equipment = {
      name: this.newAmulet.name.trim(), type: 'AMULET',
      damageBonus: 0, physicalArmor: 0, mysticalArmor: 0, initiativePenalty: 0,
      physicalDefenseBonus: 0, mysticDefenseBonus: 0, quantity: 1, healStep: 0,
      description: this.newAmulet.description,
      amuletForSpell: this.newAmulet.amuletForSpell, charged: true,
      amuletStepBonus: 6, bloodMagicDamage: 3
    };
    this.characterService.addEquipment(this.character.id, eq).subscribe(c => {
      this.character = c;
      this.newAmulet = { name: '', amuletForSpell: false, description: '' };
      this.loadDerived();
    });
  }

  rechargeAmulet(a: Equipment): void {
    if (!this.character?.id || !a.id) return;
    this.characterService.rechargeAmulet(this.character.id, a.id).subscribe({
      next: result => {
        this.lastAmuletRecharge = result;
        this.characterService.findById(this.character!.id!).subscribe(c => { this.character = c; });
        if (result.recharged) {
          this.snack.open(`${result.amuletName} aufgeladen (Wurf ${result.roll?.total} ≥ 3, Erholungsprobe geopfert).`, 'OK', { duration: 4000 });
        } else {
          this.snack.open(`Aufladen gescheitert (Wurf ${result.roll?.total} < 3) — stattdessen ${result.healed} LP geheilt.`, 'OK', { duration: 4000 });
        }
      },
      error: err => this.snack.open(err?.error?.message ?? 'Fehler beim Aufladen', 'OK', { duration: 3000 })
    });
  }

  addWeapon(): void {
    if (!this.character?.id || !this.newWeapon.name.trim()) return;
    const eq: Equipment = { name: this.newWeapon.name.trim(), type: 'WEAPON', damageBonus: this.newWeapon.damageBonus, physicalArmor: 0, mysticalArmor: 0, initiativePenalty: 0, physicalDefenseBonus: 0, mysticDefenseBonus: 0, quantity: 1, healStep: 0, twoHanded: this.newWeapon.twoHanded, attackTalentName: this.newWeapon.attackTalentName || undefined, tailWeapon: this.newWeapon.tailWeapon, description: this.newWeapon.description };
    this.characterService.addEquipment(this.character.id, eq).subscribe(c => {
      this.character = c;
      this.newWeapon = { name: '', damageBonus: 0, twoHanded: false, attackTalentName: '', tailWeapon: false, description: '' };
    });
  }

  addArmor(): void {
    if (!this.character?.id || !this.newArmor.name.trim()) return;
    const eq: Equipment = { name: this.newArmor.name.trim(), type: 'ARMOR', damageBonus: 0, physicalArmor: this.newArmor.physicalArmor, mysticalArmor: this.newArmor.mysticalArmor, initiativePenalty: this.newArmor.initiativePenalty, physicalDefenseBonus: 0, mysticDefenseBonus: 0, quantity: 1, healStep: 0, description: this.newArmor.description };
    this.characterService.addEquipment(this.character.id, eq).subscribe(c => {
      this.character = c;
      this.newArmor = { name: '', physicalArmor: 0, mysticalArmor: 0, initiativePenalty: 0, description: '' };
      this.loadDerived();
    });
  }

  addErholungstrank(): void {
    if (!this.character?.id) return;
    const eq: Equipment = {
      name: 'Erholungstrank', type: 'POTION',
      damageBonus: 0, physicalArmor: 0, mysticalArmor: 0,
      initiativePenalty: 0, physicalDefenseBonus: 0, mysticDefenseBonus: 0,
      quantity: Math.max(1, this.newPotionQty), healStep: 7,
      extraRecovery: false
    };
    this.characterService.addEquipment(this.character.id, eq).subscribe(c => {
      this.character = c;
      this.newPotionQty = 1;
    });
  }

  addHeiltrank(): void {
    if (!this.character?.id) return;
    const eq: Equipment = {
      name: 'Heiltrank', type: 'POTION',
      damageBonus: 0, physicalArmor: 0, mysticalArmor: 0,
      initiativePenalty: 0, physicalDefenseBonus: 0, mysticDefenseBonus: 0,
      quantity: Math.max(1, this.newPotionQty), healStep: 7,
      extraRecovery: true
    };
    this.characterService.addEquipment(this.character.id, eq).subscribe(c => {
      this.character = c;
      this.newPotionQty = 1;
    });
  }

  /** Disziplinen mit Karmabonus auf Erholungsproben → benötigter Mindestkreis. */
  private static readonly KARMA_RECOVERY_DISCIPLINES: Record<string, number> = {
    'Elementarist': 3, 'Krieger': 3, 'Luftpirat': 3, 'Tiermeister': 3, 'Waffenschmied': 3, 'Kundschafter': 5,
  };

  canUseKarmaRecovery(): boolean {
    const disc = this.character?.discipline?.name;
    if (!disc) return false;
    const min = CharacterSheetComponent.KARMA_RECOVERY_DISCIPLINES[disc];
    return min != null && (this.character?.circle ?? 0) >= min;
  }

  doRecoveryTest(): void {
    if (!this.character?.id) return;
    const useKarma = this.canUseKarmaRecovery() && this.recoveryUseKarma && (this.character.karmaCurrent ?? 0) > 0;
    this.characterService.performRecoveryTest(this.character.id, useKarma).subscribe({
      next: result => {
        this.lastRecovery = result;
        this.recoveryUseKarma = false;
        this.characterService.findById(this.character!.id!).subscribe(c => { this.character = c; });
        const karmaTxt = result.karmaRoll ? ` + Karma ${result.karmaRoll.total}` : '';
        this.snack.open(`Erholungsprobe: ${result.healed} LP geheilt (Stufe ${result.rollStep}, Wurf: ${result.roll?.total}${karmaTxt})`, 'OK', { duration: 4000 });
      },
      error: err => this.snack.open(err?.error?.message ?? 'Fehler bei Erholungsprobe', 'OK', { duration: 3000 })
    });
  }

  drinkPotion(potion: Equipment): void {
    if (!this.character?.id || !potion.id || potion.quantity <= 0) return;
    this.characterService.drinkPotion(this.character.id, potion.id).subscribe({
      next: (result: DrinkPotionResult) => {
        this.characterService.findById(this.character!.id!).subscribe(c => { this.character = c; });
        if (result.extraRecovery && result.recovery) {
          this.lastRecovery = result.recovery;
          const bonus = result.recovery.bonusSteps > 0 ? ` +${result.recovery.bonusSteps} Bonus` : '';
          this.snack.open(`${potion.name}: ${result.recovery.healed} LP geheilt (Stufe ${result.recovery.rollStep}${bonus}, Wurf: ${result.recovery.roll?.total})`, 'OK', { duration: 4000 });
        } else {
          this.snack.open(`${potion.name}: +${result.pendingBonus} Stufen Bonus für nächste Erholungsprobe`, 'OK', { duration: 3000 });
        }
      },
      error: err => this.snack.open(err?.error?.message ?? 'Fehler beim Trinken', 'OK', { duration: 3000 })
    });
  }

  resetRecoveryTests(): void {
    if (!this.character?.id) return;
    this.characterService.resetRecoveryTests(this.character.id).subscribe(c => {
      this.character = c;
      this.snack.open('Erholungsproben für neuen Tag aufgefüllt.', 'OK', { duration: 2000 });
    });
  }

  getRecoveryTestsMax(): number {
    const t = this.character?.toughness ?? 0;
    if (t <= 6)  return 1;
    if (t <= 12) return 2;
    if (t <= 18) return 3;
    if (t <= 24) return 4;
    return 5;
  }

  getRecoveryTestsRemaining(): number {
    const r = this.character?.recoveryTestsRemaining;
    return r != null ? r : this.getRecoveryTestsMax();
  }

  getRecoveryRollStep(): number {
    if (!this.character) return 1;
    const woundPenalty = this.character.arztWoundPenaltyNegated ? 0 : this.character.wounds;
    return Math.max(1, this.attrToStep(this.character.toughness) - woundPenalty);
  }

  recoverySlotArray(): number[] {
    return Array.from({ length: this.getRecoveryTestsMax() }, (_, i) => i);
  }

  adjustPotionQty(potion: Equipment, delta: number): void {
    if (!this.character?.id || !potion.id) return;
    const newQty = Math.max(0, potion.quantity + delta);
    this.characterService.updateEquipmentQuantity(this.character.id, potion.id, newQty).subscribe(c => {
      this.character = c;
    });
  }

  addShield(): void {
    if (!this.character?.id || !this.newShield.name.trim()) return;
    const eq: Equipment = { name: this.newShield.name.trim(), type: 'SHIELD', damageBonus: 0, physicalArmor: 0, mysticalArmor: 0, initiativePenalty: this.newShield.initiativePenalty, physicalDefenseBonus: this.newShield.physicalDefenseBonus, mysticDefenseBonus: this.newShield.mysticDefenseBonus, quantity: 1, healStep: 0, buckler: this.newShield.buckler, description: this.newShield.description };
    this.characterService.addEquipment(this.character.id, eq).subscribe(c => {
      this.character = c;
      this.newShield = { name: '', physicalDefenseBonus: 0, mysticDefenseBonus: 0, initiativePenalty: 0, buckler: false, description: '' };
      this.loadDerived();
    });
  }

  /**
   * Schaltet eine Rüstung/Schild zwischen aktiv (angelegt) und inaktiv (abgelegt) um.
   * Aktiv-Setzen deaktiviert automatisch alle anderen Stücke desselben Typs (Exklusivität).
   */
  toggleEquipmentActive(e: Equipment): void {
    if (!this.character?.id || !e.id) return;
    const newActive = e.active === false; // false → true; true/undefined → false
    this.characterService.setEquipmentActive(this.character.id, e.id, newActive).subscribe({
      next: c => { this.character = c; this.loadDerived(); },
      error: err => this.snack.open(err?.error?.message ?? 'Konnte nicht geändert werden.', 'OK', { duration: 3000 })
    });
  }

  removeEquipment(e: Equipment): void {
    if (!this.character?.id || !e.id) return;
    if (e.clawWeapon) {
      this.snack.open('Krallenhand wird vom Talent verwaltet — entferne stattdessen das Talent.', 'OK', { duration: 3000 });
      return;
    }
    this.characterService.removeEquipment(this.character.id, e.id).subscribe({
      next: c => { this.character = c; this.loadDerived(); },
      error: err => this.snack.open(err?.error?.message ?? 'Konnte nicht entfernt werden.', 'OK', { duration: 3000 })
    });
  }

  toggleSecondaryWeapon(e: Equipment): void {
    if (!this.character?.id) return;
    this.character.secondaryWeaponId = this.character.secondaryWeaponId === e.id ? undefined : e.id;
    this.characterService.update(this.character.id, this.character).subscribe(c => {
      this.character = c;
    });
  }

  // --- Zauber ---

  private static MAGIC_DISCIPLINES = ['Elementarist', 'Illusionist', 'Magier', 'Geisterbeschwörer'];

  isMagicUser(): boolean {
    return !!this.character?.discipline && CharacterSheetComponent.MAGIC_DISCIPLINES.includes(this.character.discipline.name);
  }

  loadAvailableSpells(): void {
    if (!this.isMagicUser()) return;
    this.characterService.getSpells(this.character!.discipline!.name).subscribe(spells => {
      this.availableSpells = spells.filter(s =>
        !this.character!.spells?.some(cs => cs.spellDefinition.id === s.id)
      );
    });
  }

  addSpell(): void {
    if (!this.character?.id || !this.selectedSpellId) return;
    this.characterService.addSpell(this.character.id, this.selectedSpellId).subscribe(c => {
      this.character = c;
      this.selectedSpellId = undefined;
      this.loadAvailableSpells();
    });
  }

  removeSpell(spellId: number): void {
    if (!this.character?.id) return;
    this.characterService.removeSpell(this.character.id, spellId).subscribe(() => {
      this.character!.spells = this.character!.spells.filter(s => s.id !== spellId);
      this.loadAvailableSpells();
    });
  }

  spellTypeLabel(spell: SpellDefinition): string {
    switch (spell.effectType) {
      case 'DAMAGE': return 'Schaden ' + spell.effectStep;
      case 'BUFF':   return 'Buff';
      case 'DEBUFF': return 'Debuff';
      case 'HEAL':   return 'Heilung ' + spell.effectStep;
    }
  }

  spellTypeBadgeClass(spell: SpellDefinition): string {
    switch (spell.effectType) {
      case 'DAMAGE': return 'spell-badge spell-damage';
      case 'BUFF':   return 'spell-badge spell-buff';
      case 'DEBUFF': return 'spell-badge spell-debuff';
      case 'HEAL':   return 'spell-badge spell-heal';
    }
  }
}
