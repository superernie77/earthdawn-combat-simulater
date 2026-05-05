import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { UserAccount } from '../models/user-account.model';

@Injectable({ providedIn: 'root' })
export class UserAccountService {
  private readonly base = '/api/accounts';

  constructor(private http: HttpClient) {}

  findAll(): Observable<UserAccount[]> {
    return this.http.get<UserAccount[]>(this.base);
  }

  create(username: string): Observable<UserAccount> {
    return this.http.post<UserAccount>(this.base, { username });
  }

  setGamemaster(id: number, value: boolean): Observable<UserAccount> {
    return this.http.patch<UserAccount>(`${this.base}/${id}/gamemaster`, null, { params: { value } });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
