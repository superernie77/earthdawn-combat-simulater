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
import { CharacterService } from '../../services/character.service';
import { ReferenceService } from '../../services/reference.service';
import { DiceService } from '../../services/dice.service';
import { Character, DerivedStats, TalentDefinition, SkillDefinition, DisciplineDefinition, Equipment, SpellDefinition } from '../../models/character.model';
import { ProbeResult } from '../../models/dice.model';

@Component({
  selector: 'app-character-sheet',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatCardModule, MatButtonModule, MatIconModule, MatTabsModule,
    MatFormFieldModule, MatInputModule, MatSelectModule,
    MatDividerModule, MatSnackBarModule, MatTooltipModule
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
          <span class="char-sub">{{ character.playerName }} · {{ character.discipline?.name }} Kreis {{ character.circle }}</span>
        </div>
        <div class="header-actions">
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
                  <span class="derived-label">Kreis</span>
                  <mat-select [(ngModel)]="character.circle" (ngModelChange)="onCircleChange()" style="width:80px">
                    <mat-option *ngFor="let n of circles" [value]="n">{{ n }}</mat-option>
                  </mat-select>
                </div>
              </div>

              <div class="derived-grid" *ngIf="derived">
                <div class="section-title">Abgeleitete Werte</div>
                <div class="derived-item" *ngFor="let d of derivedFields">
                  <span class="derived-label">{{ d.label }}</span>
                  <span class="derived-val">{{ getDerived(d.key) }}</span>
                </div>
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
                      <mat-option *ngFor="let t of availableTalents" [value]="t.id">{{ t.name }}</mat-option>
                    </mat-select>
                  </mat-form-field>
                </div>
                <div class="talent-list">
                  <div class="talent-item" *ngFor="let ct of character.talents">
                    <div class="talent-info">
                      <span class="talent-name">{{ ct.talentDefinition.name }}</span>
                      <span class="talent-attr">{{ ct.talentDefinition.attribute }}</span>
                    </div>
                    <div class="rank-ctrl">
                      <button mat-icon-button (click)="updateTalentRank(ct, ct.rank - 1)"><mat-icon>remove</mat-icon></button>
                      <span class="rank-val">Rang {{ ct.rank }}</span>
                      <button mat-icon-button (click)="updateTalentRank(ct, ct.rank + 1)"><mat-icon>add</mat-icon></button>
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
                  <div class="equip-name">{{ e.name }}</div>
                  <div class="equip-stats">
                    <span class="equip-badge weapon">+{{ e.damageBonus }} Schaden</span>
                    <span class="equip-desc" *ngIf="e.description">{{ e.description }}</span>
                  </div>
                  <button mat-icon-button color="warn" (click)="removeEquipment(e)" matTooltip="Entfernen">
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
                <div class="equip-item" *ngFor="let e of armors()">
                  <div class="equip-name">{{ e.name }}</div>
                  <div class="equip-stats">
                    <span class="equip-badge armor-phys" matTooltip="Physische Rüstung">{{ e.physicalArmor }} phys.</span>
                    <span class="equip-badge armor-myst" matTooltip="Mystische Rüstung (gegen Zauber)">{{ e.mysticalArmor }} myst.</span>
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
                <mat-form-field appearance="fill" style="flex:3">
                  <mat-label>Beschreibung (optional)</mat-label>
                  <input matInput [(ngModel)]="newArmor.description">
                </mat-form-field>
                <button mat-stroked-button [disabled]="!newArmor.name.trim()" (click)="addArmor()">
                  <mat-icon>add</mat-icon> Hinzufügen
                </button>
              </div>
            </div>

          </div>
        </mat-tab>

        <!-- Sprüche -->
        <mat-tab label="Sprüche" *ngIf="isMagicUser()">
          <div class="tab-content">
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
    .derived-label { color: #999; font-size: 0.85rem; }
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
    .equip-name { font-weight: 600; color: #e0d5c0; min-width: 140px; }
    .equip-stats { display: flex; align-items: center; gap: 8px; flex: 1; flex-wrap: wrap; }
    .equip-badge {
      border-radius: 10px; padding: 2px 10px; font-size: 0.78rem; font-weight: 700;
      &.weapon { background: rgba(255,112,67,0.15); color: #ff7043; }
      &.armor-phys { background: rgba(66,165,245,0.15); color: #42a5f5; }
      &.armor-myst { background: rgba(171,71,188,0.15); color: #ab47bc; }
    }
    .equip-desc { font-size: 0.78rem; color: #666; font-style: italic; }
    .equip-empty { color: #555; font-size: 0.82rem; font-style: italic; padding: 4px 0 8px; }
    .equip-add-form { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 4px; }

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

  newWeapon: { name: string; damageBonus: number; description: string } = { name: '', damageBonus: 0, description: '' };
  newArmor: { name: string; physicalArmor: number; mysticalArmor: number; description: string } = { name: '', physicalArmor: 0, mysticalArmor: 0, description: '' };
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

  derivedFields = [
    { key: 'physicalDefense', label: 'KV (Körperliche Verteidigung)' },
    { key: 'spellDefense', label: 'MV (Mystische Verteidigung)' },
    { key: 'socialDefense', label: 'SV (Soziale Verteidigung)' },
    { key: 'woundThreshold', label: 'Wundenschwelle' },
    { key: 'unconsciousnessRating', label: 'Bewusstlosigkeitsschwelle' },
    { key: 'deathRating', label: 'Todesschwelle' },
    { key: 'initiativeStep', label: 'Initiativestufe' },
    { key: 'recoveryStep', label: 'Erholungsstufe' },
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
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    const id = +this.route.snapshot.params['id'];
    this.characterService.findById(id).subscribe(c => {
      this.character = c;
      this.loadDerived();
      this.loadAvailableSpells();
    });
    this.refService.getTalents().subscribe(t => this.availableTalents = t);
    this.refService.getSkills().subscribe(s => this.availableSkills = s);
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
    }).subscribe(r => {
      this.lastProbe = r;
      if (r.karmaUsed) {
        this.characterService.findById(this.character!.id!).subscribe(c => this.character = c);
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

  weapons(): Equipment[] {
    return (this.character?.equipment ?? []).filter(e => e.type === 'WEAPON');
  }

  armors(): Equipment[] {
    return (this.character?.equipment ?? []).filter(e => e.type === 'ARMOR');
  }

  addWeapon(): void {
    if (!this.character?.id || !this.newWeapon.name.trim()) return;
    const eq: Equipment = { name: this.newWeapon.name.trim(), type: 'WEAPON', damageBonus: this.newWeapon.damageBonus, physicalArmor: 0, mysticalArmor: 0, description: this.newWeapon.description };
    this.characterService.addEquipment(this.character.id, eq).subscribe(c => {
      this.character = c;
      this.newWeapon = { name: '', damageBonus: 0, description: '' };
    });
  }

  addArmor(): void {
    if (!this.character?.id || !this.newArmor.name.trim()) return;
    const eq: Equipment = { name: this.newArmor.name.trim(), type: 'ARMOR', damageBonus: 0, physicalArmor: this.newArmor.physicalArmor, mysticalArmor: this.newArmor.mysticalArmor, description: this.newArmor.description };
    this.characterService.addEquipment(this.character.id, eq).subscribe(c => {
      this.character = c;
      this.newArmor = { name: '', physicalArmor: 0, mysticalArmor: 0, description: '' };
    });
  }

  removeEquipment(e: Equipment): void {
    if (!this.character?.id || !e.id) return;
    this.characterService.removeEquipment(this.character.id, e.id).subscribe(c => {
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
