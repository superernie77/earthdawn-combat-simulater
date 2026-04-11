import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DisciplineDefinition, TalentDefinition, SkillDefinition } from '../models/character.model';

@Injectable({ providedIn: 'root' })
export class ReferenceService {
  private readonly base = '/api/reference';

  constructor(private http: HttpClient) {}

  getDisciplines(): Observable<DisciplineDefinition[]> {
    return this.http.get<DisciplineDefinition[]>(`${this.base}/disciplines`);
  }

  getTalents(): Observable<TalentDefinition[]> {
    return this.http.get<TalentDefinition[]>(`${this.base}/talents`);
  }

  getSkills(): Observable<SkillDefinition[]> {
    return this.http.get<SkillDefinition[]>(`${this.base}/skills`);
  }
}
