import { Injectable, inject } from '@angular/core';
import { Observable, map, tap } from 'rxjs';

import { UserResponse, UsersService } from '../../../api';
import { SessionService, SessionUser, UserRole } from '../../../core/session/session.service';

/** Registration and sign-in by nickname. Neither authenticates: the nickname only identifies the user. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly users = inject(UsersService);
  private readonly session = inject(SessionService);

  /** Creates the user without a password and signs them in. */
  register(nickname: string, role: UserRole): Observable<SessionUser> {
    return this.users.create({ nickname, role }).pipe(map(toSessionUser), tap((user) => this.session.signIn(user)));
  }

  /** Looks the user up by nickname and signs them in; fails with a 404 ApiProblem for an unknown nickname. */
  signIn(nickname: string): Observable<SessionUser> {
    return this.users.getByNickname(nickname).pipe(map(toSessionUser), tap((user) => this.session.signIn(user)));
  }
}

export function toSessionUser(response: UserResponse): SessionUser {
  if (!response.id || !response.nickname || !response.role) {
    throw new Error('Incomplete user in the backend response');
  }
  return { id: response.id, nickname: response.nickname, role: response.role };
}
