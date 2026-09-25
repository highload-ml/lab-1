import { Injectable, computed, signal } from '@angular/core';

export type UserRole = 'ADMIN' | 'ML_ENGINEER' | 'REVIEWER';

/**
 * The signed-in user. There is no authentication in lab 1: the nickname only identifies the user,
 * nothing is sent to the backend as proof of identity.
 */
export interface SessionUser {
  id: string;
  nickname: string;
  role: UserRole;
}

const STORAGE_KEY = 'highload-ml.session';

@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly userState = signal<SessionUser | null>(readStoredUser());

  readonly user = this.userState.asReadonly();
  readonly signedIn = computed(() => this.userState() !== null);

  signIn(user: SessionUser): void {
    const session: SessionUser = { id: user.id, nickname: user.nickname, role: user.role };
    this.userState.set(session);
    writeStoredUser(session);
  }

  signOut(): void {
    this.userState.set(null);
    writeStoredUser(null);
  }

  /** Id of the signed-in user; callers must be behind {@link sessionGuard}. */
  requireUserId(): string {
    const user = this.userState();
    if (!user) {
      throw new Error('No signed-in user');
    }
    return user.id;
  }
}

// Storage can be unavailable (private mode, blocked site data): the app then keeps the session in memory only.
function readStoredUser(): SessionUser | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as Partial<SessionUser>;
    return parsed.id && parsed.nickname && parsed.role
      ? { id: parsed.id, nickname: parsed.nickname, role: parsed.role }
      : null;
  } catch {
    return null;
  }
}

function writeStoredUser(user: SessionUser | null): void {
  try {
    if (user) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(user));
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // ignore: see readStoredUser
  }
}
