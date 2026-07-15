import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { UserAccountService } from '../../services/user-account.service';
import { ActiveUserService } from '../../services/active-user.service';
import { UserAccount } from '../../models/user-account.model';

@Component({
  selector: 'app-accounts',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatCardModule, MatButtonModule, MatIconModule,
    MatInputModule, MatFormFieldModule,
    MatSnackBarModule, MatTooltipModule
  ],
  template: `
    <div class="accounts-container">
      <div class="page-header">
        <mat-icon class="page-icon">manage_accounts</mat-icon>
        <div>
          <h1 class="page-title">Spielerkonten</h1>
          <p class="page-sub">Wähle deinen Spieler aus oder erstelle einen neuen Eintrag.</p>
        </div>
      </div>

      <!-- Active user banner -->
      <div class="active-banner" *ngIf="activeUser">
        <mat-icon>person</mat-icon>
        <span>Aktiver Spieler: <strong>{{ activeUser.username }}</strong></span>
        <span class="gm-badge" *ngIf="activeUser.gamemaster">Spielleiter</span>
        <button mat-stroked-button (click)="clearActive()" style="margin-left:auto">
          <mat-icon>logout</mat-icon> Abmelden
        </button>
      </div>

      <!-- Account cards -->
      <div class="accounts-grid">
        <div class="account-card" *ngFor="let u of accounts"
             [class.is-active]="isActive(u)">
          <div class="account-card__header">
            <mat-icon class="account-avatar" [class.gm-avatar]="u.gamemaster">
              {{ u.gamemaster ? 'admin_panel_settings' : 'person' }}
            </mat-icon>
            <div class="account-info">
              <span class="account-name">{{ u.username }}</span>
              <span class="gm-badge" *ngIf="u.gamemaster">Spielleiter</span>
            </div>
            <mat-icon class="active-check" *ngIf="isActive(u)" matTooltip="Aktiver Spieler">check_circle</mat-icon>
          </div>

          <div class="account-card__actions">
            <button mat-raised-button
              [color]="isActive(u) ? 'accent' : 'primary'"
              (click)="selectUser(u)"
              matTooltip="{{ isActive(u) ? 'Bereits ausgewählt' : 'Als aktiver Spieler wählen' }}">
              <mat-icon>{{ isActive(u) ? 'how_to_reg' : 'login' }}</mat-icon>
              {{ isActive(u) ? 'Aktiv' : 'Wählen' }}
            </button>

            <button mat-stroked-button
              (click)="toggleGm(u)"
              [matTooltip]="u.gamemaster ? 'Spielleiter-Status entfernen' : 'Zum Spielleiter machen'">
              <mat-icon>{{ u.gamemaster ? 'remove_moderator' : 'add_moderator' }}</mat-icon>
              {{ u.gamemaster ? 'Ist SL' : 'Zum SL' }}
            </button>

            <button mat-icon-button color="warn" (click)="deleteUser(u)"
              matTooltip="Konto löschen" [disabled]="isActive(u)">
              <mat-icon>delete</mat-icon>
            </button>
          </div>
        </div>

        <div class="accounts-empty" *ngIf="!accounts.length">
          <mat-icon>group_off</mat-icon>
          <p>Noch keine Konten vorhanden. Erstelle das erste Konto unten.</p>
        </div>
      </div>

      <!-- Add user form -->
      <div class="add-user-form">
        <h2 class="section-title">Neues Konto erstellen</h2>
        <div class="add-row">
          <mat-form-field appearance="fill" style="flex:1">
            <mat-label>Spielername</mat-label>
            <input matInput [(ngModel)]="newUsername" placeholder="z.B. Thorin"
              (keydown.enter)="addUser()" maxlength="60">
          </mat-form-field>
          <button mat-raised-button color="primary"
            [disabled]="!newUsername.trim()"
            (click)="addUser()">
            <mat-icon>person_add</mat-icon> Hinzufügen
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .accounts-container { padding: 24px; max-width: 900px; }

    .page-header {
      display: flex; align-items: center; gap: 16px; margin-bottom: 24px;
    }
    .page-icon { font-size: 2.5rem; height: 2.5rem; width: 2.5rem; color: #c9a84c; }
    .page-title { font-family: 'Cinzel', serif; color: #c9a84c; font-size: 1.6rem; margin: 0; }
    .page-sub { color: #777; font-size: 0.85rem; margin: 4px 0 0; }

    .active-banner {
      display: flex; align-items: center; gap: 10px;
      background: rgba(201,168,76,0.1); border: 1px solid rgba(201,168,76,0.3);
      border-radius: 8px; padding: 12px 16px; margin-bottom: 20px;
      color: #e0d5c0;
      mat-icon { color: #c9a84c; }
    }

    .accounts-grid {
      display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
      gap: 16px; margin-bottom: 32px;
    }

    .account-card {
      background: #2a2520; border: 1px solid #3a3028; border-radius: 10px;
      padding: 16px; transition: border-color 0.2s;
      &.is-active { border-color: #c9a84c; background: rgba(201,168,76,0.06); }
    }

    .account-card__header {
      display: flex; align-items: center; gap: 12px; margin-bottom: 14px;
    }
    .account-avatar {
      font-size: 2rem; height: 2rem; width: 2rem; color: #666;
      &.gm-avatar { color: #c9a84c; }
    }
    .account-info { flex: 1; display: flex; flex-direction: column; gap: 4px; }
    .account-name { font-size: 1rem; font-weight: 600; color: #e0d5c0; }
    .active-check { color: #c9a84c; }

    .gm-badge {
      background: rgba(201,168,76,0.2); color: #c9a84c;
      border-radius: 10px; padding: 1px 8px; font-size: 0.72rem; font-weight: 700;
      letter-spacing: 0.05em; width: fit-content;
    }

    .account-card__actions {
      display: flex; gap: 6px; flex-wrap: wrap; align-items: center;
    }

    .accounts-empty {
      grid-column: 1/-1; text-align: center; color: #555;
      padding: 40px; display: flex; flex-direction: column; align-items: center; gap: 8px;
      mat-icon { font-size: 2.5rem; height: 2.5rem; width: 2.5rem; }
    }

    .add-user-form {
      background: #2a2520; border: 1px solid #3a3028; border-radius: 10px; padding: 20px;
    }
    .section-title { font-family: 'Cinzel', serif; color: #9c7b3c; font-size: 0.95rem;
      margin: 0 0 12px; text-transform: uppercase; letter-spacing: 0.08em; }
    .add-row { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
  `]
})
export class AccountsComponent implements OnInit {
  accounts: UserAccount[] = [];
  newUsername = '';
  activeUser: UserAccount | null = null;

  constructor(
    private accountService: UserAccountService,
    private activeUserService: ActiveUserService,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.activeUserService.activeUser$.subscribe(u => this.activeUser = u);
    this.load();
  }

  load(): void {
    this.accountService.findAll().subscribe(list => this.accounts = list);
  }

  isActive(u: UserAccount): boolean {
    return this.activeUser?.id === u.id;
  }

  selectUser(u: UserAccount): void {
    this.activeUserService.set(u);
    this.snack.open(`Aktiver Spieler: ${u.username}`, 'OK', { duration: 1500 });
  }

  clearActive(): void {
    this.activeUserService.clear();
    this.snack.open('Abgemeldet', 'OK', { duration: 1500 });
  }

  toggleGm(u: UserAccount): void {
    this.accountService.setGamemaster(u.id, !u.gamemaster).subscribe(updated => {
      this.accounts = this.accounts.map(a => a.id === updated.id ? updated : a);
      this.activeUserService.update(updated);
    });
  }

  addUser(): void {
    const name = this.newUsername.trim();
    if (!name) return;
    this.accountService.create(name).subscribe({
      next: u => {
        this.accounts = [...this.accounts, u].sort((a, b) => a.username.localeCompare(b.username));
        this.newUsername = '';
        this.snack.open(`Konto "${u.username}" erstellt`, 'OK', { duration: 1500 });
      },
      error: err => {
        const msg = err.error?.message ?? 'Fehler beim Erstellen';
        this.snack.open(msg, 'OK', { duration: 3000 });
      }
    });
  }

  deleteUser(u: UserAccount): void {
    if (!confirm(`Konto "${u.username}" wirklich löschen?`)) return;
    this.accountService.delete(u.id).subscribe(() => {
      this.accounts = this.accounts.filter(a => a.id !== u.id);
      this.snack.open(`Konto gelöscht`, 'OK', { duration: 1500 });
    });
  }
}
