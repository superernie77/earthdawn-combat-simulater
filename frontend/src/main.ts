import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import { appConfig } from './app/app.config';

// SockJS erwartet Node.js-Global `global` — im Browser als Polyfill setzen
(window as any).global = window;

bootstrapApplication(AppComponent, appConfig).catch(err => console.error(err));
