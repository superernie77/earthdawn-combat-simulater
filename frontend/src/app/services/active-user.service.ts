import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { UserAccount } from '../models/user-account.model';

const STORAGE_KEY = 'earthdawn_active_user';

@Injectable({ providedIn: 'root' })
export class ActiveUserService {

  private subject = new BehaviorSubject<UserAccount | null>(this.loadFromStorage());

  readonly activeUser$ = this.subject.asObservable();

  get activeUser(): UserAccount | null {
    return this.subject.value;
  }

  set(user: UserAccount): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(user));
    this.subject.next(user);
  }

  update(user: UserAccount): void {
    if (this.subject.value?.id === user.id) {
      this.set(user);
    }
  }

  clear(): void {
    localStorage.removeItem(STORAGE_KEY);
    this.subject.next(null);
  }

  private loadFromStorage(): UserAccount | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? JSON.parse(raw) as UserAccount : null;
    } catch {
      return null;
    }
  }
}
