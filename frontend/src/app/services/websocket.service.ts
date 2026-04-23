import { Injectable, OnDestroy } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { CombatSession } from '../models/combat.model';

@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {
  private client!: Client;
  private connectPromise: Promise<void>;
  private sessionSubjects = new Map<number, Subject<CombatSession>>();
  private stompSubs = new Map<number, StompSubscription>();

  constructor() {
    this.connectPromise = new Promise((resolve) => {
      this.client = new Client({
        webSocketFactory: () => new (SockJS as any)('/ws'),
        reconnectDelay: 5000,
        onConnect: () => resolve()
      });
      this.client.activate();
    });
  }

  subscribeToSession(sessionId: number): Observable<CombatSession> {
    if (this.sessionSubjects.has(sessionId)) {
      return this.sessionSubjects.get(sessionId)!.asObservable();
    }
    const subject = new Subject<CombatSession>();
    this.sessionSubjects.set(sessionId, subject);
    this.connectPromise.then(() => {
      const stompSub = this.client.subscribe(`/topic/combat/${sessionId}`, (msg: IMessage) => {
        subject.next(JSON.parse(msg.body) as CombatSession);
      });
      this.stompSubs.set(sessionId, stompSub);
    });
    return subject.asObservable();
  }

  unsubscribeFromSession(sessionId: number): void {
    this.stompSubs.get(sessionId)?.unsubscribe();
    this.stompSubs.delete(sessionId);
    this.sessionSubjects.get(sessionId)?.complete();
    this.sessionSubjects.delete(sessionId);
  }

  ngOnDestroy(): void {
    this.stompSubs.forEach(sub => sub.unsubscribe());
    this.client?.deactivate();
  }
}
