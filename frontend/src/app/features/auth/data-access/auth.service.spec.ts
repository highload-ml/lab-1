import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { problemBody, provideHttpTesting } from '../../../../testing/http-test-providers';
import { ApiProblem } from '../../../core/errors/api-problem';
import { SessionService } from '../../../core/session/session.service';
import { AuthService, toSessionUser } from './auth.service';

const aliceResponse = { id: 'a1', nickname: 'alice', role: 'ML_ENGINEER', createdAt: '2026-09-25T09:00:00Z' };

describe('AuthService', () => {
  let auth: AuthService;
  let session: SessionService;
  let backend: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: provideHttpTesting() });
    auth = TestBed.inject(AuthService);
    session = TestBed.inject(SessionService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('register posts nickname and role without a password and signs the user in', async () => {
    const result = firstValueFrom(auth.register('alice', 'ML_ENGINEER'));

    const request = backend.expectOne({ method: 'POST', url: '/api/v1/users' });
    expect(request.request.body).toEqual({ nickname: 'alice', role: 'ML_ENGINEER' });
    request.flush(aliceResponse, { status: 201, statusText: 'Created' });

    expect(await result).toEqual({ id: 'a1', nickname: 'alice', role: 'ML_ENGINEER' });
    expect(session.user()?.nickname).toBe('alice');
  });

  it('signIn looks the user up by nickname and signs them in', async () => {
    const result = firstValueFrom(auth.signIn('alice'));

    backend.expectOne({ method: 'GET', url: '/api/v1/users/by-nickname/alice' }).flush(aliceResponse);

    expect((await result).id).toBe('a1');
    expect(session.signedIn()).toBe(true);
  });

  it('signIn with an unknown nickname fails with 404 and stays signed out', async () => {
    const result = firstValueFrom(auth.signIn('ghost')).then(
      () => {
        throw new Error('expected an error');
      },
      (error: unknown) => error as ApiProblem,
    );

    backend
      .expectOne('/api/v1/users/by-nickname/ghost')
      .flush(problemBody(404, "User with nickname 'ghost' not found"), { status: 404, statusText: 'Not Found' });

    expect((await result).status).toBe(404);
    expect(session.signedIn()).toBe(false);
  });

  it('toSessionUser rejects an incomplete response', () => {
    expect(() => toSessionUser({ nickname: 'alice' })).toThrow();
  });
});
