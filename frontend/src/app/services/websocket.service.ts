import { Injectable, OnDestroy } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { CombatSession } from '../models/combat.model';

@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {
  private client!: Client;
  private connected = false;
  private connectPromise: Promise<void>;

  constructor() {
    this.connectPromise = new Promise((resolve) => {
      this.client = new Client({
        webSocketFactory: () => new (SockJS as any)('/ws'),
        reconnectDelay: 5000,
        onConnect: () => {
          this.connected = true;
          resolve();
        }
      });
      this.client.activate();
    });
  }

  subscribeToSession(sessionId: number): Observable<CombatSession> {
    const subject = new Subject<CombatSession>();
    this.connectPromise.then(() => {
      this.client.subscribe(`/topic/combat/${sessionId}`, (msg: IMessage) => {
        subject.next(JSON.parse(msg.body) as CombatSession);
      });
    });
    return subject.asObservable();
  }

  ngOnDestroy(): void {
    this.client?.deactivate();
  }
}
