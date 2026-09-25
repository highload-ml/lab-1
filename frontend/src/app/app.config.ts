import { ApplicationConfig, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { provideApi } from './api';
import { routes } from './app.routes';
import { problemInterceptor } from './core/errors/problem.interceptor';
import { verifyStoredSession } from './core/session/session-check';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([problemInterceptor])),
    // Relative URLs: `ng serve` proxies /api to the backend (proxy.conf.json), nginx does the same in Docker.
    provideApi(''),
    provideAppInitializer(verifyStoredSession),
  ],
};
