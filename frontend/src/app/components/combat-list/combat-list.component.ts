import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { CombatService } from '../../services/combat.service';
import { CombatSession } from '../../models/combat.model';

@Component({
  selector: 'app-combat-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatCardModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatSnackBarModule
  ],
  template: `
    <div class="page-container">
      <div class="page-header">
        <h1>Kampfsessions</h1>
        <div class="new-session">
          <mat-form-field appearance="fill" style="width:240px">
            <mat-label>Session-Name</mat-label>
            <input matInput [(ngModel)]="newName" (keydown.enter)="create()" placeholder="z.B. Goblin-Höhle">
          </mat-form-field>
          <button mat-raised-button color="primary" [disabled]="!newName.trim()" (click)="create()">
            <mat-icon>add</mat-icon> Erstellen
          </button>
        </div>
      </div>

      <div class="sessions-list" *ngIf="sessions.length > 0; else empty">
        <mat-card class="session-card" *ngFor="let s of sessions" (click)="open(s.id)">
          <mat-card-header>
            <mat-card-title>{{ s.name }}</mat-card-title>
            <mat-card-subtitle>
              <span [class]="'status-badge ' + s.status.toLowerCase()">{{ statusLabel(s.status) }}</span>
              · Runde {{ s.round }} · {{ s.combatants.length }} Kombattanten
            </mat-card-subtitle>
          </mat-card-header>
          <mat-card-actions>
            <button mat-button color="primary" (click)="open(s.id); $event.stopPropagation()">
              <mat-icon>open_in_new</mat-icon> Öffnen
            </button>
            <button mat-button color="warn" (click)="delete(s.id); $event.stopPropagation()">
              <mat-icon>delete</mat-icon> Löschen
            </button>
          </mat-card-actions>
        </mat-card>
      </div>

      <ng-template #empty>
        <div class="empty-state">
          <mat-icon>shield_outlined</mat-icon>
          <p>Noch keine Kampfsessions. Starte einen Kampf!</p>
        </div>
      </ng-template>
    </div>
  `,
  styles: [`
    .page-container { padding: 24px; }
    .page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; }
    h1 { font-family: 'Cinzel', serif; color: #c9a84c; margin: 0; }
    .new-session { display: flex; align-items: center; gap: 12px; }
    .sessions-list { display: flex; flex-direction: column; gap: 12px; max-width: 600px; }
    .session-card { cursor: pointer; border: 1px solid #3a3028; &:hover { border-color: #c9a84c; } }
    .status-badge {
      padding: 2px 8px; border-radius: 10px; font-size: 11px; font-weight: 600;
      &.setup   { background: rgba(158,158,158,0.2); color: #9e9e9e; }
      &.active  { background: rgba(76,175,80,0.2); color: #4caf50; }
      &.finished{ background: rgba(244,67,54,0.2); color: #f44336; }
    }
    .empty-state { text-align: center; padding: 60px 20px; color: #666;
      mat-icon { font-size: 64px; height: 64px; width: 64px; opacity: 0.3; }
    }
  `]
})
export class CombatListComponent implements OnInit {
  sessions: CombatSession[] = [];
  newName = '';

  constructor(
    private combatService: CombatService,
    private router: Router,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.combatService.findAll().subscribe(s => this.sessions = s);
  }

  create(): void {
    if (!this.newName.trim()) return;
    this.combatService.create(this.newName.trim()).subscribe(s => {
      this.newName = '';
      this.snack.open(`Session "${s.name}" erstellt.`, 'OK', { duration: 2000 });
      this.router.navigate(['/combat', s.id]);
    });
  }

  open(id: number): void {
    this.router.navigate(['/combat', id]);
  }

  delete(id: number): void {
    this.combatService.delete(id).subscribe(() => {
      this.sessions = this.sessions.filter(s => s.id !== id);
    });
  }

  statusLabel(status: string): string {
    const labels: Record<string, string> = { SETUP: 'Vorbereitung', ACTIVE: 'Aktiv', FINISHED: 'Beendet' };
    return labels[status] ?? status;
  }
}
