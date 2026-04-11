import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { CommonModule } from '@angular/common';
import { CharacterService } from '../../services/character.service';
import { ActiveCharacterService } from '../../services/active-character.service';
import { Character, emptyCharacter } from '../../models/character.model';
import { NewCharacterDialogComponent } from '../new-character-dialog/new-character-dialog.component';

@Component({
  selector: 'app-character-list',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatButtonModule, MatIconModule, MatDialogModule, MatSnackBarModule, MatTooltipModule],
  template: `
    <div class="page-container">
      <div class="page-header">
        <h1>Charaktere</h1>
        <button mat-raised-button color="primary" (click)="newCharacter()">
          <mat-icon>add</mat-icon> Neuer Charakter
        </button>
      </div>

      <!-- Aktiver Charakter Banner -->
      <div class="active-banner" *ngIf="activeChar">
        <mat-icon>radio_button_checked</mat-icon>
        <span><strong>{{ activeChar.name }}</strong> ist aktiv — Karma im Würfelscreen wird von diesem Charakter abgezogen.</span>
        <button mat-icon-button (click)="activeCharService.clear()" matTooltip="Deaktivieren"><mat-icon>close</mat-icon></button>
      </div>

      <div class="character-grid" *ngIf="characters.length > 0; else empty">
        <mat-card class="char-card" *ngFor="let c of characters" (click)="openCharacter(c.id!)" [class.active-card]="isActive(c)">
          <mat-card-header>
            <mat-card-title>{{ c.name }}</mat-card-title>
            <mat-card-subtitle>
              {{ c.playerName }} · {{ c.discipline?.name || 'Keine Disziplin' }} Kreis {{ c.circle }}
            </mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <div class="attr-row">
              <span class="attr-chip" title="Geschicklichkeit">GE {{ c.dexterity }}</span>
              <span class="attr-chip" title="Stärke">ST {{ c.strength }}</span>
              <span class="attr-chip" title="Zähigkeit">ZÄ {{ c.toughness }}</span>
              <span class="attr-chip" title="Wahrnehmung">WN {{ c.perception }}</span>
              <span class="attr-chip" title="Willenskraft">WK {{ c.willpower }}</span>
              <span class="attr-chip" title="Charisma">CH {{ c.charisma }}</span>
            </div>
            <div class="status-row">
              <span class="status-item">
                <mat-icon style="font-size:14px;height:14px;width:14px">favorite</mat-icon>
                {{ c.currentDamage }}/{{ unconsciousnessRating(c) }}
              </span>
              <span class="status-item" *ngIf="c.wounds > 0" style="color:#f44336">
                <mat-icon style="font-size:14px;height:14px;width:14px">warning</mat-icon>
                {{ c.wounds }} Wunde(n)
              </span>
              <span class="status-item" style="color:#c9a84c">
                <mat-icon style="font-size:14px;height:14px;width:14px">auto_awesome</mat-icon>
                {{ c.karmaCurrent }}/{{ c.karmaMax }} Karma
              </span>
            </div>
          </mat-card-content>
          <mat-card-actions>
            <button mat-button (click)="openCharacter(c.id!); $event.stopPropagation()">
              <mat-icon>edit</mat-icon> Bearbeiten
            </button>
            <button mat-button
              [color]="isActive(c) ? 'accent' : ''"
              (click)="activate(c); $event.stopPropagation()"
              [matTooltip]="isActive(c) ? 'Aktiver Charakter (klicken zum Deaktivieren)' : 'Als aktiven Charakter setzen'">
              <mat-icon>{{ isActive(c) ? 'radio_button_checked' : 'radio_button_unchecked' }}</mat-icon>
              {{ isActive(c) ? 'Aktiv' : 'Aktivieren' }}
            </button>
            <button mat-button color="warn" (click)="delete(c); $event.stopPropagation()">
              <mat-icon>delete</mat-icon>
            </button>
          </mat-card-actions>
        </mat-card>
      </div>

      <ng-template #empty>
        <div class="empty-state">
          <mat-icon>people_outline</mat-icon>
          <p>Noch keine Charaktere. Erstelle deinen ersten Helden!</p>
          <button mat-raised-button color="primary" (click)="newCharacter()">Charakter erstellen</button>
        </div>
      </ng-template>
    </div>
  `,
  styles: [`
    .page-container { padding: 24px; }
    .page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; }
    h1 { font-family: 'Cinzel', serif; color: #c9a84c; margin: 0; }

    .character-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      gap: 16px;
    }

    .active-banner {
      display: flex; align-items: center; gap: 10px;
      background: rgba(201,168,76,0.1); border: 1px solid #c9a84c;
      border-radius: 6px; padding: 8px 14px; margin-bottom: 16px;
      color: #c9a84c; font-size: 0.9rem;
      mat-icon { flex-shrink: 0; }
      span { flex: 1; }
    }

    .char-card {
      cursor: pointer;
      transition: transform 0.15s, border-color 0.15s;
      border: 1px solid #3a3028;
      &:hover { transform: translateY(-2px); border-color: #c9a84c; }
      &.active-card { border-color: #c9a84c; box-shadow: 0 0 10px rgba(201,168,76,0.3); }
    }

    .attr-row { display: flex; gap: 6px; flex-wrap: wrap; margin: 8px 0; }
    .attr-chip {
      padding: 2px 8px; border-radius: 10px;
      background: rgba(201,168,76,0.12); border: 1px solid #4a3a20;
      font-size: 12px; color: #c9a84c;
    }

    .status-row { display: flex; gap: 12px; flex-wrap: wrap; font-size: 12px; color: #999; }
    .status-item { display: flex; align-items: center; gap: 2px; }

    .empty-state {
      text-align: center; padding: 60px 20px; color: #666;
      mat-icon { font-size: 64px; height: 64px; width: 64px; opacity: 0.3; }
    }
  `]
})
export class CharacterListComponent implements OnInit {
  characters: Character[] = [];
  activeChar: Character | null = null;

  constructor(
    private characterService: CharacterService,
    public activeCharService: ActiveCharacterService,
    private router: Router,
    private dialog: MatDialog,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.loadCharacters();
    this.activeCharService.activeChar$.subscribe(c => this.activeChar = c);
  }

  loadCharacters(): void {
    this.characterService.findAll().subscribe(list => this.characters = list);
  }

  openCharacter(id: number): void {
    this.router.navigate(['/characters', id]);
  }

  newCharacter(): void {
    const ref = this.dialog.open(NewCharacterDialogComponent, { width: '500px' });
    ref.afterClosed().subscribe((result: Character | undefined) => {
      if (result) {
        this.characterService.create(result).subscribe(c => {
          this.snack.open(`${c.name} erstellt!`, 'OK', { duration: 2000 });
          this.router.navigate(['/characters', c.id]);
        });
      }
    });
  }

  activate(c: Character): void {
    if (this.isActive(c)) {
      this.activeCharService.clear();
      this.snack.open(`${c.name} deaktiviert.`, 'OK', { duration: 1500 });
    } else {
      this.activeCharService.set(c);
      this.snack.open(`${c.name} ist jetzt aktiv!`, 'OK', { duration: 1500 });
    }
  }

  isActive(c: Character): boolean {
    return this.activeChar?.id === c.id;
  }

  delete(c: Character): void {
    if (confirm(`${c.name} wirklich löschen?`)) {
      this.characterService.delete(c.id!).subscribe(() => {
        this.snack.open(`${c.name} gelöscht.`, 'OK', { duration: 2000 });
        this.loadCharacters();
      });
    }
  }

  unconsciousnessRating(c: Character): number {
    return c.unconsciousnessRating ?? c.toughness * 2;
  }
}
