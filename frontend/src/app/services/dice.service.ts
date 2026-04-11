import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { RollResult, ProbeRequest, ProbeResult } from '../models/dice.model';

@Injectable({ providedIn: 'root' })
export class DiceService {
  private readonly base = '/api/dice';

  constructor(private http: HttpClient) {}

  roll(step: number): Observable<RollResult> {
    return this.http.post<RollResult>(`${this.base}/roll`, { step });
  }

  probe(req: ProbeRequest): Observable<ProbeResult> {
    return this.http.post<ProbeResult>(`${this.base}/probe`, req);
  }
}
