import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { guestGuard, sessionGuard } from './session.guard';
import { SessionService } from './session.service';

describe('session guards', () => {
  let session: SessionService;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    session = TestBed.inject(SessionService);
    router = TestBed.inject(Router);
  });

  function run(guard: typeof sessionGuard, url = '/projects/p1'): boolean | UrlTree {
    return TestBed.runInInjectionContext(
      () => guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot) as boolean | UrlTree,
    );
  }

  it('sessionGuard sends a signed-out user to /login with the requested url', () => {
    const result = run(sessionGuard);

    expect(result instanceof UrlTree).toBe(true);
    expect(router.serializeUrl(result as UrlTree)).toBe('/login?returnUrl=%2Fprojects%2Fp1');
  });

  it('sessionGuard lets a signed-in user through', () => {
    session.signIn({ id: 'a1', nickname: 'alice', role: 'ADMIN' });

    expect(run(sessionGuard)).toBe(true);
  });

  it('guestGuard redirects a signed-in user to /projects', () => {
    session.signIn({ id: 'a1', nickname: 'alice', role: 'ADMIN' });

    expect(router.serializeUrl(run(guestGuard) as UrlTree)).toBe('/projects');
  });

  it('guestGuard lets a signed-out user open the public page', () => {
    expect(run(guestGuard)).toBe(true);
  });
});
