import { Component, OnInit } from '@angular/core';
import { MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { CommonModule } from '@angular/common';
import { ReferenceService } from '../../services/reference.service';
import { DisciplineDefinition, emptyCharacter } from '../../models/character.model';

@Component({
  selector: 'app-new-character-dialog',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatDialogModule,
    MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule
  ],
  template: `
    <h2 mat-dialog-title style="font-family:'Cinzel',serif;color:#c9a84c">Neuer Charakter</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="form-grid">
        <mat-form-field appearance="fill">
          <mat-label>Name des Charakters</mat-label>
          <input matInput formControlName="name" placeholder="z.B. Ragnar der Krieger">
        </mat-form-field>

        <mat-form-field appearance="fill">
          <mat-label>Spieler</mat-label>
          <input matInput formControlName="playerName">
        </mat-form-field>

        <mat-form-field appearance="fill">
          <mat-label>Disziplin *</mat-label>
          <mat-select formControlName="disciplineId">
            <mat-option *ngFor="let d of disciplines" [value]="d.id">
              {{ d.name }} (Karma d{{ d.karmaStep }})
            </mat-option>
          </mat-select>
          <mat-error *ngIf="form.get('disciplineId')?.hasError('required')">Disziplin ist erforderlich</mat-error>
        </mat-form-field>

        <mat-form-field appearance="fill">
          <mat-label>Kreis</mat-label>
          <input matInput type="number" formControlName="circle" min="1" max="15">
        </mat-form-field>

        <div class="attr-section">
          <div class="section-title">Attribute</div>
          <div class="attr-grid">
            <mat-form-field appearance="fill" *ngFor="let a of attributes">
              <mat-label>{{ a.label }}</mat-label>
              <input matInput type="number" [formControlName]="a.key" min="1" max="20">
            </mat-form-field>
          </div>
        </div>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Abbrechen</button>
      <button mat-raised-button color="primary" [disabled]="form.invalid" (click)="submit()">
        Erstellen
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .form-grid { display: flex; flex-direction: column; gap: 8px; min-width: 400px; }
    mat-form-field { width: 100%; }
    .attr-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 8px; }
  `]
})
export class NewCharacterDialogComponent implements OnInit {
  disciplines: DisciplineDefinition[] = [];
  form: FormGroup;

  attributes = [
    { key: 'dexterity', label: 'Geschicklichkeit' },
    { key: 'strength', label: 'Stärke' },
    { key: 'toughness', label: 'Zähigkeit' },
    { key: 'perception', label: 'Wahrnehmung' },
    { key: 'willpower', label: 'Willenskraft' },
    { key: 'charisma', label: 'Charisma' },
  ];

  constructor(
    private dialogRef: MatDialogRef<NewCharacterDialogComponent>,
    private fb: FormBuilder,
    private refService: ReferenceService
  ) {
    this.form = this.fb.group({
      name: ['', Validators.required],
      playerName: [''],
      disciplineId: [null, Validators.required],
      circle: [1, [Validators.min(1), Validators.max(15)]],
      dexterity: [10], strength: [10], toughness: [10],
      perception: [10], willpower: [10], charisma: [10]
    });
  }

  ngOnInit(): void {
    this.refService.getDisciplines().subscribe(d => this.disciplines = d);
  }

  submit(): void {
    const v = this.form.value;
    const character = {
      ...emptyCharacter(),
      name: v.name,
      playerName: v.playerName,
      circle: v.circle,
      dexterity: v.dexterity,
      strength: v.strength,
      toughness: v.toughness,
      perception: v.perception,
      willpower: v.willpower,
      charisma: v.charisma,
      discipline: v.disciplineId ? { id: v.disciplineId } : undefined
    };
    this.dialogRef.close(character);
  }
}
