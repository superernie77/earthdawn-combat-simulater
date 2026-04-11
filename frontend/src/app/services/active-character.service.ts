import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { Character } from '../models/character.model';

@Injectable({ providedIn: 'root' })
export class ActiveCharacterService {
  private _activeChar = new BehaviorSubject<Character | null>(null);
  activeChar$ = this._activeChar.asObservable();

  get activeChar(): Character | null {
    return this._activeChar.value;
  }

  set(character: Character): void {
    this._activeChar.next(character);
  }

  clear(): void {
    this._activeChar.next(null);
  }

  update(character: Character): void {
    if (this._activeChar.value?.id === character.id) {
      this._activeChar.next(character);
    }
  }
}
