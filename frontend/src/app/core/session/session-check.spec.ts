import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { problemBody, provideHttpTesting } from '../../../testing/http-test-providers';
import { verifyStoredSession } from './session-check';
import { SessionService } from './session.service';

describe('verifyStoredSession', () => {
  let session: SessionService;
  let backend: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([]), ...provideHttpTesting()] });
    session = TestBed.inject(SessionService);
    backend = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });

  afterEach(() => backend.verify());

  it('does nothing without a stored session', () => {
    TestBed.runInInjectionContext(verifyStoredSession);

    backend.expectNone(() => true);
  });

  it('refreshes the stored user, e.g. after a role change', () => {
    session.signIn({ id: 'a1', nickname: 'alice', role: 'REVIEWER' });

    TestBed.runInInjectionContext(verifyStoredSession);
    backend.expectOne('/api/v1/users/a1').flush({ id: 'a1', nickname: 'alice', role: 'ADMIN' });

    expect(session.user()?.role).toBe('ADMIN');
  });

  it('signs out a user that was deleted', () => {
    session.signIn({ id: 'a1', nickname: 'alice', role: 'REVIEWER' });

    TestBed.runInInjectionContext(verifyStoredSession);
    backend.expectOne('/api/v1/users/a1').flush(problemBody(404, 'User a1 not found'), { status: 404, statusText: 'Not Found' });

    expect(session.signedIn()).toBe(false);
    expect(navigate).toHaveBeenCalledWith('/login');
  });

  it('keeps the session when the backend is unreachable', () => {
    session.signIn({ id: 'a1', nickname: 'alice', role: 'REVIEWER' });

    TestBed.runInInjectionContext(verifyStoredSession);
    backend.expectOne('/api/v1/users/a1').error(new ProgressEvent('error'), { status: 0, statusText: '' });

    expect(session.signedIn()).toBe(true);
  });
});
