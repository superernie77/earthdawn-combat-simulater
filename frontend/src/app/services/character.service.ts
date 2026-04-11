import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Character, DerivedStats, Equipment } from '../models/character.model';

@Injectable({ providedIn: 'root' })
export class CharacterService {
  private readonly base = '/api/characters';

  constructor(private http: HttpClient) {}

  findAll(): Observable<Character[]> {
    return this.http.get<Character[]>(this.base);
  }

  findById(id: number): Observable<Character> {
    return this.http.get<Character>(`${this.base}/${id}`);
  }

  create(character: Character): Observable<Character> {
    return this.http.post<Character>(this.base, character);
  }

  update(id: number, character: Character): Observable<Character> {
    return this.http.put<Character>(`${this.base}/${id}`, character);
  }

  updateField(id: number, field: string, delta: number): Observable<Character> {
    return this.http.patch<Character>(`${this.base}/${id}/field`, { field, delta });
  }

  setField(id: number, field: string, absoluteValue: number): Observable<Character> {
    return this.http.patch<Character>(`${this.base}/${id}/field`, { field, absoluteValue });
  }

  updateNotes(id: number, notes: string): Observable<Character> {
    return this.http.patch<Character>(`${this.base}/${id}/notes`, { notes });
  }

  getDerived(id: number): Observable<DerivedStats> {
    return this.http.get<DerivedStats>(`${this.base}/${id}/derived`);
  }

  recalculate(id: number): Observable<Character> {
    return this.http.post<Character>(`${this.base}/${id}/recalculate`, {});
  }

  addTalent(characterId: number, talentDefinitionId: number, rank = 1): Observable<Character> {
    return this.http.post<Character>(
      `${this.base}/${characterId}/talents`,
      null,
      { params: { talentDefinitionId, rank } }
    );
  }

  updateTalentRank(characterId: number, talentId: number, rank: number): Observable<void> {
    return this.http.patch<void>(
      `${this.base}/${characterId}/talents/${talentId}`,
      null,
      { params: { rank } }
    );
  }

  removeTalent(characterId: number, talentId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${characterId}/talents/${talentId}`);
  }

  addSkill(characterId: number, skillDefinitionId: number, rank = 1): Observable<Character> {
    return this.http.post<Character>(
      `${this.base}/${characterId}/skills`,
      null,
      { params: { skillDefinitionId, rank } }
    );
  }

  updateSkillRank(characterId: number, skillId: number, rank: number): Observable<void> {
    return this.http.patch<void>(
      `${this.base}/${characterId}/skills/${skillId}`,
      null,
      { params: { rank } }
    );
  }

  removeSkill(characterId: number, skillId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${characterId}/skills/${skillId}`);
  }

  addEquipment(characterId: number, equipment: Equipment): Observable<Character> {
    return this.http.post<Character>(`${this.base}/${characterId}/equipment`, equipment);
  }

  removeEquipment(characterId: number, equipmentId: number): Observable<Character> {
    return this.http.delete<Character>(`${this.base}/${characterId}/equipment/${equipmentId}`);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
