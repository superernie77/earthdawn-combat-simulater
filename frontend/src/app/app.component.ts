import { Component } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive,
    MatToolbarModule, MatButtonModule, MatIconModule,
    MatSidenavModule, MatListModule
  ],
  template: `
    <mat-sidenav-container class="app-container">
      <mat-sidenav mode="side" opened class="sidenav">
        <div class="sidenav-header">
          <span class="brand">Earthdawn</span>
          <span class="brand-sub">Combat Simulator</span>
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
        </mat-nav-list>
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
    }

    .sidenav-header {
      padding: 20px 16px 12px;
      border-bottom: 1px solid #3a3028;
      display: flex;
      flex-direction: column;
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
  `]
})
export class AppComponent {}
