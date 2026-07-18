import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Subscription } from 'rxjs';

import { CombatService } from '../../services/combat.service';
import { WebSocketService } from '../../services/websocket.service';
import { ActiveUserService } from '../../services/active-user.service';
import { CombatSession, CombatantState, MapObstacle, ObstacleType, CombatLog } from '../../models/combat.model';
import { hexNeighbors, hexInBounds, reachableHexes } from '../../services/hex-util';

/** Werkzeug der GM-Palette. */
type MapTool = 'SELECT' | 'PLACE' | 'ERASE' | ObstacleType;

interface HexCell { q: number; r: number; cx: number; cy: number; points: string; }

interface AttackAnim {
  id: number;
  kind: 'melee' | 'ranged' | 'spell';
  x1: number; y1: number; x2: number; y2: number;
}

/**
 * Kampfkarte im eigenen Fenster (Route /combat/:id/map).
 *
 * Reiner Zuschauer + Kartensteuerung: liest denselben WebSocket-Broadcast wie der Tracker
 * und spricht ausschließlich die /map-Endpoints an — keinerlei Kampf-Logik.
 * Darstellung: 2D-Hexgitter, vertikal gestaucht für leichte Schrägsicht (Ebenen später).
 */
@Component({
  selector: 'app-combat-map',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatButtonModule, MatTooltipModule, MatSnackBarModule],
  template: `
    <div class="map-shell" *ngIf="session as s">
      <!-- Kopfzeile -->
      <div class="map-header">
        <span class="map-title">🗺 {{ s.name }}</span>
        <span class="map-round" *ngIf="s.status === 'ACTIVE'">Runde {{ s.round }} · {{ s.phase === 'DECLARATION' ? 'Ansage' : 'Aktion' }}</span>
        <span class="map-turn" *ngIf="activeTurn() as t">
          <mat-icon>play_arrow</mat-icon> {{ cn(t) }} ist dran
          <span class="move-budget">({{ remainingMove(t) }}/{{ effectiveMovement(t) }} Felder)</span>
        </span>
        <span class="spacer"></span>
        <span class="gm-badge" *ngIf="isGm()" matTooltip="Spielleiter-Werkzeuge aktiv">SL</span>
      </div>

      <!-- GM-Palette -->
      <div class="map-palette" *ngIf="isGm()">
        <button class="pal-btn" [class.active]="tool === 'SELECT'" (click)="setTool('SELECT')"
                matTooltip="Auswählen / Bewegen">
          <mat-icon>touch_app</mat-icon> Auswahl
        </button>
        <span class="pal-sep"></span>
        <button class="pal-btn" [class.active]="tool === 'PLACE' && placeCombatantId === c.id"
                *ngFor="let c of unplacedCombatants(); trackBy: trackById"
                (click)="startPlacing(c)" matTooltip="Auf der Karte platzieren">
          <mat-icon>person_pin_circle</mat-icon> {{ cn(c) }}
        </button>
        <span class="pal-sep" *ngIf="unplacedCombatants().length > 0"></span>
        <button class="pal-btn" [class.active]="tool === 'WALL'" (click)="setTool('WALL')" matTooltip="Wand setzen">🧱 Wand</button>
        <button class="pal-btn" [class.active]="tool === 'DOOR'" (click)="setTool('DOOR')" matTooltip="Tür setzen (Klick im Auswahlmodus öffnet/schließt)">🚪 Tür</button>
        <button class="pal-btn" [class.active]="tool === 'TREE'" (click)="setTool('TREE')" matTooltip="Baum setzen">🌳 Baum</button>
        <button class="pal-btn" [class.active]="tool === 'ROCK'" (click)="setTool('ROCK')" matTooltip="Fels setzen">🪨 Fels</button>
        <button class="pal-btn" [class.active]="tool === 'FURNITURE'" (click)="setTool('FURNITURE')" matTooltip="Möbel setzen">🪑 Möbel</button>
        <span class="pal-sep"></span>
        <button class="pal-btn erase" [class.active]="tool === 'ERASE'" (click)="setTool('ERASE')"
                matTooltip="Hindernis oder Kombattant vom Feld entfernen">
          <mat-icon>backspace</mat-icon> Entfernen
        </button>
      </div>

      <!-- Karte -->
      <div class="map-scroll">
        <svg [attr.viewBox]="viewBox" [style.width.px]="svgWidth" [style.height.px]="svgHeight"
             class="hex-svg" (contextmenu)="$event.preventDefault()">
          <defs>
            <radialGradient id="tok-hero" cx="35%" cy="30%" r="80%">
              <stop offset="0%" stop-color="#6d5b35"/><stop offset="100%" stop-color="#2c2417"/>
            </radialGradient>
            <radialGradient id="tok-npc" cx="35%" cy="30%" r="80%">
              <stop offset="0%" stop-color="#6b3230"/><stop offset="100%" stop-color="#291413"/>
            </radialGradient>
            <radialGradient id="tree-crown" cx="35%" cy="30%" r="80%">
              <stop offset="0%" stop-color="#5d8a4a"/><stop offset="100%" stop-color="#2c4a22"/>
            </radialGradient>
          </defs>

          <!-- Gitter -->
          <g>
            <polygon *ngFor="let h of cells; trackBy: trackByCell"
                     [attr.points]="h.points"
                     [attr.transform]="'translate(' + h.cx + ',' + h.cy + ')'"
                     class="hex"
                     [class.reachable]="reachable.has(h.q + ',' + h.r)"
                     [class.hover-target]="tool !== 'SELECT'"
                     (click)="hexClick(h.q, h.r)"/>
          </g>

          <!-- Hindernisse -->
          <g *ngFor="let o of s.obstacles ?? []; trackBy: trackById"
             [attr.transform]="'translate(' + cx(o.q, o.r) + ',' + cy(o.q, o.r) + ')'"
             class="obstacle" (click)="obstacleClick(o)">
            <ng-container [ngSwitch]="o.type">
              <g *ngSwitchCase="'WALL'">
                <rect x="-18" y="-16" width="36" height="26" rx="2" fill="#57534c" stroke="#2e2b27" stroke-width="1.5"/>
                <rect x="-18" y="-16" width="36" height="7" rx="2" fill="#6e695f"/>
                <line x1="-6" y1="-9" x2="-6" y2="10" stroke="#2e2b27" stroke-width="1"/>
                <line x1="7" y1="-16" x2="7" y2="-9" stroke="#2e2b27" stroke-width="1"/>
              </g>
              <g *ngSwitchCase="'DOOR'">
                <rect x="-14" y="-18" width="28" height="30" rx="2"
                      [attr.fill]="o.doorOpen ? '#3a3028' : '#7a5230'"
                      stroke="#4a3520" stroke-width="2"/>
                <circle *ngIf="!o.doorOpen" cx="8" cy="-2" r="2.2" fill="#c9a84c"/>
                <text *ngIf="o.doorOpen" y="4" text-anchor="middle" font-size="12" fill="#c9a84c">⌐</text>
              </g>
              <g *ngSwitchCase="'TREE'">
                <rect x="-3" y="2" width="6" height="10" fill="#5a4327"/>
                <ellipse cy="-6" rx="15" ry="13" fill="url(#tree-crown)" stroke="#233c1b" stroke-width="1.5"/>
              </g>
              <g *ngSwitchCase="'ROCK'">
                <ellipse cy="3" rx="15" ry="10" fill="#75716a" stroke="#44413c" stroke-width="1.5"/>
                <ellipse cx="-4" cy="-1" rx="8" ry="6" fill="#8a867e"/>
              </g>
              <g *ngSwitchCase="'FURNITURE'">
                <rect x="-15" y="-8" width="30" height="14" rx="2" fill="#8a6a3f" stroke="#4a3520" stroke-width="1.5"/>
                <rect x="-13" y="6" width="4" height="7" fill="#5a4327"/>
                <rect x="9" y="6" width="4" height="7" fill="#5a4327"/>
              </g>
            </ng-container>
          </g>

          <!-- Kombattanten-Tokens -->
          <g *ngFor="let c of placedCombatants(); trackBy: trackById"
             [attr.transform]="'translate(' + cx(c.mapQ!, c.mapR!) + ',' + cy(c.mapQ!, c.mapR!) + ')'"
             class="token" [class.defeated]="c.defeated" [class.active-turn]="isActiveTurn(c)"
             [class.selected]="selected?.id === c.id"
             (click)="tokenClick(c); $event.stopPropagation()">
            <ellipse cy="10" rx="14" ry="5" fill="rgba(0,0,0,0.45)"/>
            <circle r="14" [attr.fill]="c.npc ? 'url(#tok-npc)' : 'url(#tok-hero)'"
                    [attr.stroke]="c.npc ? '#ef5350' : '#c9a84c'" stroke-width="2.5"/>
            <text y="5" text-anchor="middle" font-size="13" font-weight="bold"
                  [attr.fill]="c.npc ? '#ffb3b0' : '#e8d5a0'">{{ initialOf(c) }}</text>
            <!-- HP-Balken -->
            <rect x="-13" y="16" width="26" height="3.5" rx="1.75" fill="#333"/>
            <rect x="-13" y="16" [attr.width]="26 * hpFraction(c)" height="3.5" rx="1.75"
                  [attr.fill]="hpFraction(c) > 0.5 ? '#66bb6a' : hpFraction(c) > 0.25 ? '#ffb300' : '#ef5350'"/>
            <text *ngIf="c.defeated" y="6" text-anchor="middle" font-size="17" fill="#ef5350">✕</text>
            <text class="token-name" y="-19" text-anchor="middle" font-size="9"
                  fill="#bdb2a0">{{ cn(c) }}</text>
          </g>

          <!-- Angriffs-/Zauber-Animationen -->
          <g *ngFor="let a of anims; trackBy: trackById">
            <g *ngIf="a.kind === 'melee'" [attr.transform]="'translate(' + a.x2 + ',' + a.y2 + ')'">
              <path class="anim-slash" d="M-14,-14 L14,14 M-6,-16 L16,6" stroke="#ffd54f"
                    stroke-width="3.5" stroke-linecap="round" fill="none"/>
            </g>
            <g *ngIf="a.kind === 'ranged'">
              <line class="anim-arrow" [attr.x1]="a.x1" [attr.y1]="a.y1" [attr.x2]="a.x2" [attr.y2]="a.y2"
                    stroke="#e0d5c0" stroke-width="2.5" stroke-linecap="round"
                    [attr.stroke-dasharray]="animLength(a)" [attr.stroke-dashoffset]="animLength(a)"/>
              <circle class="anim-hit" [attr.cx]="a.x2" [attr.cy]="a.y2" r="4" fill="#ffd54f"/>
            </g>
            <g *ngIf="a.kind === 'spell'">
              <circle class="anim-bolt" r="6" fill="#b39ddb" [attr.filter]="''">
                <animateMotion [attr.path]="'M' + a.x1 + ',' + a.y1 + ' L' + a.x2 + ',' + a.y2"
                               dur="0.4s" fill="freeze"/>
              </circle>
              <circle class="anim-burst" [attr.cx]="a.x2" [attr.cy]="a.y2" r="4"
                      fill="none" stroke="#b39ddb" stroke-width="3"/>
            </g>
          </g>
        </svg>
      </div>

      <!-- Fußzeile: Auswahl-Info -->
      <div class="map-footer" *ngIf="selected as sel">
        <span class="sel-name" [style.color]="sel.npc ? '#ef9a9a' : '#e8d5a0'">{{ cn(sel) }}</span>
        <span>Bewegung: {{ remainingMove(sel) }}/{{ effectiveMovement(sel) }} Felder</span>
        <span *ngIf="!canMove(sel)" class="sel-hint">— {{ moveBlockReason(sel) }}</span>
        <span *ngIf="canMove(sel)" class="sel-hint ok">— erreichbare Felder anklicken</span>
        <button mat-stroked-button class="sel-close" (click)="clearSelection()">
          <mat-icon>close</mat-icon> Abwählen
        </button>
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; height: 100vh; background: #17120d; color: #e0d5c0; overflow: hidden; }
    .map-shell { display: flex; flex-direction: column; height: 100%; }
    .map-header {
      display: flex; align-items: center; gap: 14px; padding: 8px 14px;
      background: #201a12; border-bottom: 1px solid #3a3028; flex-wrap: wrap;
    }
    .map-title { font-family: 'Cinzel', serif; font-weight: 700; color: #c9a84c; font-size: 1.05rem; }
    .map-round { color: #999; font-size: 0.85rem; }
    .map-turn {
      display: inline-flex; align-items: center; gap: 4px; color: #80cbc4; font-size: 0.85rem;
      mat-icon { font-size: 17px; height: 17px; width: 17px; }
    }
    .move-budget { color: #777; }
    .spacer { flex: 1; }
    .gm-badge {
      background: rgba(201,168,76,0.15); color: #c9a84c; border: 1px solid #4a3f2a;
      border-radius: 10px; padding: 1px 9px; font-size: 0.75rem; font-weight: 700;
    }
    .map-palette {
      display: flex; align-items: center; gap: 5px; flex-wrap: wrap;
      padding: 6px 14px; background: #1b1610; border-bottom: 1px solid #3a3028;
    }
    .pal-btn {
      display: inline-flex; align-items: center; gap: 4px;
      background: transparent; border: 1px solid #3a3028; border-radius: 6px;
      color: #aaa; padding: 4px 9px; font-size: 0.8rem; cursor: pointer; white-space: nowrap;
      mat-icon { font-size: 15px; height: 15px; width: 15px; }
    }
    .pal-btn:hover { border-color: #c9a84c; color: #c9a84c; }
    .pal-btn.active { border-color: #c9a84c; color: #c9a84c; background: rgba(201,168,76,0.12); }
    .pal-btn.erase.active { border-color: #ef5350; color: #ef5350; background: rgba(239,83,80,0.1); }
    .pal-sep { width: 1px; height: 20px; background: #3a3028; margin: 0 4px; }
    .map-scroll { flex: 1; overflow: auto; display: flex; }
    .hex-svg { margin: auto; }
    .hex {
      fill: #241e15; stroke: #3a3226; stroke-width: 1;
      cursor: pointer; transition: fill 0.12s;
    }
    .hex:hover { fill: #322a1c; }
    .hex.reachable { fill: rgba(128,203,196,0.16); stroke: #2f6f68; }
    .hex.reachable:hover { fill: rgba(128,203,196,0.32); }
    .obstacle { cursor: pointer; }
    .token { cursor: pointer; transition: transform 0.45s ease; }
    .token.defeated { opacity: 0.45; filter: grayscale(0.8); }
    .token.selected circle { stroke-width: 4; }
    .token.active-turn circle { filter: drop-shadow(0 0 6px rgba(201,168,76,0.9)); }
    .token-name { paint-order: stroke; stroke: #17120d; stroke-width: 3px; font-weight: 600; }
    .map-footer {
      display: flex; align-items: center; gap: 12px; padding: 8px 14px;
      background: #201a12; border-top: 1px solid #3a3028; font-size: 0.85rem; color: #999;
    }
    .sel-name { font-weight: 700; }
    .sel-hint { color: #ffb74d; }
    .sel-hint.ok { color: #80cbc4; }
    .sel-close { margin-left: auto; height: 30px; font-size: 0.8rem;
      mat-icon { font-size: 15px; height: 15px; width: 15px; } }

    /* Animationen */
    .anim-slash { animation: slash 0.5s ease-out forwards; }
    @keyframes slash {
      0% { opacity: 0; transform: scale(0.4) rotate(-25deg); }
      30% { opacity: 1; transform: scale(1.15) rotate(0deg); }
      100% { opacity: 0; transform: scale(1.25) rotate(8deg); }
    }
    .anim-arrow { animation: arrowfly 0.35s linear forwards; }
    @keyframes arrowfly { to { stroke-dashoffset: 0; } }
    .anim-hit { opacity: 0; animation: hitflash 0.3s ease-out 0.32s forwards; }
    @keyframes hitflash {
      0% { opacity: 1; r: 4; } 100% { opacity: 0; r: 13; }
    }
    .anim-burst { opacity: 0; animation: burst 0.45s ease-out 0.38s forwards; }
    @keyframes burst {
      0% { opacity: 1; r: 4; stroke-width: 4; } 100% { opacity: 0; r: 20; stroke-width: 0.5; }
    }
    .anim-bolt { animation: boltfade 0.5s linear forwards; }
    @keyframes boltfade { 0%, 85% { opacity: 1; } 100% { opacity: 0; } }
  `]
})
export class CombatMapComponent implements OnInit, OnDestroy {

  /** Hexgröße (Außenradius) und vertikale Stauchung für die Pseudo-Schrägsicht. */
  private static readonly SIZE = 27;
  private static readonly SQUASH = 0.82;

  session?: CombatSession;
  cells: HexCell[] = [];
  viewBox = '0 0 100 100';
  svgWidth = 800;
  svgHeight = 600;

  tool: MapTool = 'SELECT';
  placeCombatantId?: number;
  selected?: CombatantState;
  reachable = new Map<string, number>();

  anims: AttackAnim[] = [];
  private animSeq = 0;
  private lastLogId = -1;
  private logInitialized = false;

  private sessionId!: number;
  private wsSub?: Subscription;

  constructor(
    private route: ActivatedRoute,
    private combatService: CombatService,
    private ws: WebSocketService,
    private activeUserService: ActiveUserService,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.sessionId = Number(this.route.snapshot.paramMap.get('id'));
    this.combatService.findById(this.sessionId).subscribe(s => this.applySession(s));
    this.wsSub = this.ws.subscribeToSession(this.sessionId).subscribe(s => this.applySession(s));
  }

  ngOnDestroy(): void {
    this.wsSub?.unsubscribe();
    this.ws.unsubscribeFromSession(this.sessionId);
  }

  // --- Session-Update ---

  private applySession(s: CombatSession): void {
    const dimsChanged = !this.session
      || this.session.mapWidth !== s.mapWidth || this.session.mapHeight !== s.mapHeight;
    this.session = s;
    if (dimsChanged) this.buildGrid();
    // Auswahl auf frisches Objekt umbiegen (WebSocket liefert neue Instanzen)
    if (this.selected) {
      this.selected = s.combatants.find(c => c.id === this.selected!.id);
      this.recomputeReachable();
    }
    this.detectAttacks(s);
  }

  private buildGrid(): void {
    const s = this.session!;
    const W = s.mapWidth ?? 24, H = s.mapHeight ?? 16;
    const SIZE = CombatMapComponent.SIZE, SQ = CombatMapComponent.SQUASH;
    const hexW = Math.sqrt(3) * SIZE;
    const points = this.hexPoints();
    this.cells = [];
    for (let r = 0; r < H; r++) {
      for (let q = 0; q < W; q++) {
        this.cells.push({ q, r, cx: this.cx(q, r), cy: this.cy(q, r), points });
      }
    }
    const totalW = hexW * (W + 0.5) + 20;
    const totalH = (1.5 * SIZE * (H - 1) + 2 * SIZE) * SQ + 30;
    this.viewBox = `${-hexW / 2 - 10} ${-SIZE * SQ - 10} ${totalW} ${totalH}`;
    this.svgWidth = totalW;
    this.svgHeight = totalH;
  }

  private hexPoints(): string {
    const SIZE = CombatMapComponent.SIZE, SQ = CombatMapComponent.SQUASH;
    const pts: string[] = [];
    for (let i = 0; i < 6; i++) {
      const angle = Math.PI / 180 * (60 * i - 30);
      pts.push((SIZE * Math.cos(angle)).toFixed(2) + ',' + (SIZE * Math.sin(angle) * SQ).toFixed(2));
    }
    return pts.join(' ');
  }

  cx(q: number, r: number): number {
    return Math.sqrt(3) * CombatMapComponent.SIZE * (q + 0.5 * (r & 1));
  }

  cy(q: number, r: number): number {
    return 1.5 * CombatMapComponent.SIZE * r * CombatMapComponent.SQUASH;
  }

  // --- Abfragen ---

  isGm(): boolean {
    const u = this.activeUserService.activeUser;
    return !u || !!u.gamemaster;
  }

  cn(c: CombatantState): string {
    return c.displayName ?? c.character.name;
  }

  initialOf(c: CombatantState): string {
    return this.cn(c).substring(0, 2);
  }

  /** HP-Anteil für den Balken — gleiche Basis wie der Tracker (Bewusstlosigkeitsschwelle). */
  hpFraction(c: CombatantState): number {
    const max = Math.max(1, c.character.unconsciousnessRating ?? c.character.toughness * 2);
    return Math.max(0, Math.min(1, 1 - c.currentDamage / max));
  }

  placedCombatants(): CombatantState[] {
    return (this.session?.combatants ?? []).filter(c => c.mapQ != null && c.mapR != null);
  }

  unplacedCombatants(): CombatantState[] {
    return (this.session?.combatants ?? []).filter(c => (c.mapQ == null || c.mapR == null) && !c.defeated);
  }

  activeTurn(): CombatantState | undefined {
    const s = this.session;
    if (!s || s.status !== 'ACTIVE' || s.phase !== 'ACTION') return undefined;
    return [...s.combatants]
      .sort((a, b) => a.initiativeOrder - b.initiativeOrder)
      .find(c => !c.defeated && !c.hasActedThisRound);
  }

  isActiveTurn(c: CombatantState): boolean {
    return this.activeTurn()?.id === c.id;
  }

  /**
   * Effektive Bewegungsrate — Basis vom Charakterbogen, moduliert durch aktive Effekte
   * mit targetStat MOVEMENT_HEXES (Spiegel des ModifierAggregators: erst ADD, dann MULTIPLY).
   */
  effectiveMovement(c: CombatantState): number {
    let value = c.character.movementHexes ?? 8;
    const mods = (c.activeEffects ?? [])
      .flatMap(e => e.modifiers ?? [])
      .filter(m => m.targetStat === 'MOVEMENT_HEXES');
    for (const m of mods.filter(m => m.operation === 'ADD')) value += m.value;
    for (const m of mods.filter(m => m.operation === 'MULTIPLY')) value *= m.value;
    return Math.max(0, Math.floor(value));
  }

  remainingMove(c: CombatantState): number {
    return Math.max(0, this.effectiveMovement(c) - (c.movedHexesThisRound ?? 0));
  }

  canMove(c: CombatantState): boolean {
    if (this.isGm()) return true;
    return this.isActiveTurn(c) && !c.defeated && this.remainingMove(c) > 0;
  }

  moveBlockReason(c: CombatantState): string {
    if (c.defeated) return 'besiegt';
    if (!this.isActiveTurn(c)) return 'nicht am Zug (Initiative-Reihenfolge)';
    if (this.remainingMove(c) <= 0) return 'Bewegung dieser Runde aufgebraucht';
    return '';
  }

  // --- Interaktion ---

  setTool(t: MapTool): void {
    this.tool = t;
    this.placeCombatantId = undefined;
    this.clearSelection();
  }

  startPlacing(c: CombatantState): void {
    this.tool = 'PLACE';
    this.placeCombatantId = c.id;
    this.clearSelection();
  }

  tokenClick(c: CombatantState): void {
    if (this.tool === 'ERASE' && this.isGm()) {
      this.combatService.placeOnMap(this.sessionId, c.id, -1, -1).subscribe({ error: e => this.err(e) });
      return;
    }
    if (this.tool !== 'SELECT') return;
    if (this.selected?.id === c.id) {
      this.clearSelection();
      return;
    }
    this.selected = c;
    this.recomputeReachable();
  }

  hexClick(q: number, r: number): void {
    const s = this.session;
    if (!s) return;
    switch (this.tool) {
      case 'PLACE':
        if (this.placeCombatantId != null) {
          this.combatService.placeOnMap(this.sessionId, this.placeCombatantId, q, r).subscribe({
            next: () => { this.tool = 'SELECT'; this.placeCombatantId = undefined; },
            error: e => this.err(e)
          });
        }
        return;
      case 'WALL': case 'DOOR': case 'TREE': case 'ROCK': case 'FURNITURE':
        this.combatService.addObstacle(this.sessionId, this.tool, q, r).subscribe({ error: e => this.err(e) });
        return;
      case 'ERASE': {
        const o = (s.obstacles ?? []).find(x => x.q === q && x.r === r);
        if (o) this.combatService.removeObstacle(this.sessionId, o.id).subscribe({ error: e => this.err(e) });
        return;
      }
      case 'SELECT': {
        if (this.selected && this.reachable.has(q + ',' + r)) {
          const gmOverride = this.isGm() && !this.canMoveByRules(this.selected);
          this.combatService.moveOnMap(this.sessionId, this.selected.id, q, r, gmOverride).subscribe({
            error: e => this.err(e)
          });
        }
      }
    }
  }

  obstacleClick(o: MapObstacle): void {
    if (this.tool === 'ERASE' && this.isGm()) {
      this.combatService.removeObstacle(this.sessionId, o.id).subscribe({ error: e => this.err(e) });
      return;
    }
    if (this.tool === 'SELECT' && o.type === 'DOOR') {
      this.combatService.toggleDoor(this.sessionId, o.id).subscribe({ error: e => this.err(e) });
    }
  }

  clearSelection(): void {
    this.selected = undefined;
    this.reachable = new Map();
  }

  /** Darf sich der Kombattant nach den normalen Regeln bewegen (ohne GM-Privileg)? */
  private canMoveByRules(c: CombatantState): boolean {
    return this.session?.status === 'ACTIVE' && this.isActiveTurn(c) && !c.defeated;
  }

  private recomputeReachable(): void {
    const s = this.session;
    const c = this.selected;
    this.reachable = new Map();
    if (!s || !c || c.mapQ == null || c.mapR == null) return;
    if (!this.canMove(c)) return;
    const budget = this.isGm() && !this.canMoveByRules(c) ? 999 : this.remainingMove(c);
    const blocked = (q: number, r: number): boolean => {
      const o = (s.obstacles ?? []).find(x => x.q === q && x.r === r);
      if (o && (o.type !== 'DOOR' || !o.doorOpen)) return true;
      return (s.combatants ?? []).some(x => x.id !== c.id && x.mapQ === q && x.mapR === r);
    };
    this.reachable = reachableHexes(
      c.mapQ, c.mapR, Math.min(budget, 30),
      s.mapWidth ?? 24, s.mapHeight ?? 16, blocked);
  }

  // --- Angriffs-Animationen aus dem Kampflog ---

  private static readonly ANIM_TYPES: Record<string, 'melee' | 'ranged' | 'spell'> = {
    MELEE_ATTACK: 'melee', NACHTRETEN: 'melee', SCHWANZANGRIFF: 'melee',
    ZWEITE_WAFFE: 'melee', LUFTTANZ_ATTACK: 'melee',
    RANGED_ATTACK: 'ranged', BLATTSCHUSS_KARMA: 'ranged',
    SPELL_ATTACK: 'spell', SPELL_CAST: 'spell'
  };

  private detectAttacks(s: CombatSession): void {
    const log: CombatLog[] = s.log ?? [];
    if (log.length === 0) return;
    const newestId = log[0].id;
    if (!this.logInitialized) {
      // Beim ersten Laden nichts animieren — nur den Stand merken
      this.logInitialized = true;
      this.lastLogId = newestId;
      return;
    }
    const fresh = log.filter(e => e.id > this.lastLogId).reverse();
    this.lastLogId = Math.max(this.lastLogId, newestId);
    for (const e of fresh) {
      const kind = CombatMapComponent.ANIM_TYPES[e.actionType];
      if (!kind || !e.targetName || e.actorName === e.targetName) continue;
      const actor = s.combatants.find(c => this.cn(c) === e.actorName);
      const target = s.combatants.find(c => this.cn(c) === e.targetName);
      if (!actor || !target || actor.mapQ == null || target.mapQ == null) continue;
      const anim: AttackAnim = {
        id: ++this.animSeq, kind,
        x1: this.cx(actor.mapQ, actor.mapR!), y1: this.cy(actor.mapQ, actor.mapR!),
        x2: this.cx(target.mapQ, target.mapR!), y2: this.cy(target.mapQ, target.mapR!)
      };
      this.anims = [...this.anims, anim];
      setTimeout(() => this.anims = this.anims.filter(a => a.id !== anim.id), 1100);
    }
  }

  animLength(a: AttackAnim): number {
    return Math.hypot(a.x2 - a.x1, a.y2 - a.y1);
  }

  // --- Sonstiges ---

  trackById(_i: number, x: { id: number }): number {
    return x.id;
  }

  trackByCell(_i: number, h: HexCell): string {
    return h.q + ',' + h.r;
  }

  private err(e: any): void {
    const msg = e?.error?.message ?? e?.message ?? 'Unbekannter Fehler';
    this.snack.open('Karte: ' + msg, 'OK', { duration: 4000 });
  }
}
