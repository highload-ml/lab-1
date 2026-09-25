import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SessionService } from './session.service';

/** Lets signed-in users through; everyone else goes to /login and comes back after signing in. */
export const sessionGuard: CanActivateFn = (_route, state) => {
  if (inject(SessionService).signedIn()) {
    return true;
  }
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Keeps signed-in users away from /login and /register. */
export const guestGuard: CanActivateFn = () =>
  inject(SessionService).signedIn() ? inject(Router).createUrlTree(['/projects']) : true;
