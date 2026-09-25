import { inject } from '@angular/core';
import { Router } from '@angular/router';

import { UsersService } from '../../api';
import { isApiProblem } from '../errors/api-problem';
import { SessionService } from './session.service';

/**
 * Runs once at startup: a stored session whose user was deleted meanwhile is dropped.
 * Does not block rendering; other errors (e.g. backend down) keep the session.
 */
export function verifyStoredSession(): void {
  const session = inject(SessionService);
  const users = inject(UsersService);
  const router = inject(Router);
  const user = session.user();
  if (!user) {
    return;
  }
  users.getById(user.id).subscribe({
    next: (current) => {
      if (current.id && current.nickname && current.role) {
        session.signIn({ id: current.id, nickname: current.nickname, role: current.role });
      }
    },
    error: (error: unknown) => {
      if (isApiProblem(error) && error.status === 404) {
        session.signOut();
        void router.navigateByUrl('/login');
      }
    },
  });
}
