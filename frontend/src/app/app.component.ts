import { Component, OnInit } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActiveUserService } from './services/active-user.service';
import { UserAccount } from './models/user-account.model';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule, RouterOutlet, RouterLink, RouterLinkActive,
    MatToolbarModule, MatButtonModule, MatIconModule,
    MatSidenavModule, MatListModule, MatTooltipModule
  ],
  template: `
    <mat-sidenav-container class="app-container">
      <mat-sidenav mode="side" opened class="sidenav">
        <div class="sidenav-header">
          <!-- Dasselbe SVG wie im Browser-Tab — eine Quelle für Favicon und Logo -->
          <img src="favicon.svg" alt="" class="brand-logo" width="38" height="38">
          <div class="brand-text">
            <span class="brand">Earthdawn</span>
            <span class="brand-sub">Combat Simulator</span>
          </div>
        </div>
        <mat-nav-list>
          <a mat-list-item routerLink="/characters" routerLinkActive="active-link">
            <mat-icon matListItemIcon>people</mat-icon>
            <span matListItemTitle>Charaktere</span>
          </a>
          <a mat-list-item routerLink="/combat" routerLinkActive="active-link">
            <mat-icon matListItemIcon>shield</mat-icon>
            <span matListItemTitle>Kampf</span>
          </a>
          <a mat-list-item routerLink="/dice" routerLinkActive="active-link">
            <mat-icon matListItemIcon>casino</mat-icon>
            <span matListItemTitle>Würfelwurf</span>
          </a>
          <a mat-list-item routerLink="/accounts" routerLinkActive="active-link">
            <mat-icon matListItemIcon>manage_accounts</mat-icon>
            <span matListItemTitle>Konten</span>
          </a>
        </mat-nav-list>

        <!-- Active user footer -->
        <div class="user-footer" (click)="router.navigate(['/accounts'])"
             matTooltip="Spieler wechseln">
          <mat-icon class="user-icon" [class.gm-icon]="activeUser?.gamemaster">
            {{ activeUser?.gamemaster ? 'admin_panel_settings' : 'person' }}
          </mat-icon>
          <div class="user-info">
            <span class="user-name">{{ activeUser?.username ?? 'Kein Spieler' }}</span>
            <span class="user-role" *ngIf="activeUser?.gamemaster">Spielleiter</span>
            <span class="user-role muted" *ngIf="!activeUser">Klicken zum Anmelden</span>
          </div>
        </div>
      </mat-sidenav>

      <mat-sidenav-content class="main-content">
        <router-outlet />
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: [`
    .app-container { height: 100vh; }

    .sidenav {
      width: 200px;
      background: #1e1a16;
      border-right: 1px solid #3a3028;
      display: flex;
      flex-direction: column;
    }

    .sidenav-header {
      padding: 20px 16px 12px;
      border-bottom: 1px solid #3a3028;
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .brand-logo {
      width: 38px;
      height: 38px;
      flex-shrink: 0;
    }

    /* Schriftzug bleibt gestapelt — die Kopfzeile selbst liegt jetzt nebeneinander */
    .brand-text {
      display: flex;
      flex-direction: column;
      min-width: 0;
    }

    .brand {
      font-family: 'Cinzel', serif;
      font-size: 1.3rem;
      font-weight: 700;
      color: #c9a84c;
      letter-spacing: 0.05em;
    }

    .brand-sub {
      font-size: 0.7rem;
      color: #777;
      letter-spacing: 0.1em;
      text-transform: uppercase;
    }

    mat-nav-list { flex: 1; }

    .main-content {
      background: #1a1a1a;
      overflow-y: auto;
    }

    ::ng-deep .active-link {
      background: rgba(201, 168, 76, 0.12) !important;
      color: #c9a84c !important;
    }

    ::ng-deep .mat-mdc-nav-list .mat-mdc-list-item {
      color: #c0b090;
    }

    .user-footer {
      margin-top: auto;
      padding: 12px 14px;
      border-top: 1px solid #3a3028;
      display: flex;
      align-items: center;
      gap: 10px;
      cursor: pointer;
      transition: background 0.15s;
      &:hover { background: rgba(201,168,76,0.08); }
    }
    .user-icon { color: #555; font-size: 1.6rem; height: 1.6rem; width: 1.6rem; }
    .gm-icon { color: #c9a84c; }
    .user-info { display: flex; flex-direction: column; overflow: hidden; }
    .user-name { font-size: 0.88rem; color: #c0b090; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .user-role { font-size: 0.7rem; color: #c9a84c; }
    .user-role.muted { color: #555; }
  `]
})
export class AppComponent implements OnInit {
  activeUser: UserAccount | null = null;

  constructor(
    private activeUserService: ActiveUserService,
    public router: Router
  ) {}

  ngOnInit(): void {
    this.activeUserService.activeUser$.subscribe(u => this.activeUser = u);
  }
}
