import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: '/characters', pathMatch: 'full' },
  {
    path: 'characters',
    loadComponent: () => import('./components/character-list/character-list.component')
      .then(m => m.CharacterListComponent)
  },
  {
    path: 'characters/:id',
    loadComponent: () => import('./components/character-sheet/character-sheet.component')
      .then(m => m.CharacterSheetComponent)
  },
  {
    path: 'combat',
    loadComponent: () => import('./components/combat-list/combat-list.component')
      .then(m => m.CombatListComponent)
  },
  {
    path: 'combat/:id',
    loadComponent: () => import('./components/combat-tracker/combat-tracker.component')
      .then(m => m.CombatTrackerComponent)
  },
  {
    path: 'dice',
    loadComponent: () => import('./components/dice-roller/dice-roller.component')
      .then(m => m.DiceRollerComponent)
  }
];
