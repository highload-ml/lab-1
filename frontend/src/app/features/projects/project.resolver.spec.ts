import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RedirectCommand, Router, RouterStateSnapshot, convertToParamMap, provideRouter } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';

import { problemBody, provideHttpTesting } from '../../../testing/http-test-providers';
import { SessionService } from '../../core/session/session.service';
import { ProjectContext, projectResolver } from './project.resolver';

describe('projectResolver', () => {
  let backend: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([]), ...provideHttpTesting()] });
    TestBed.inject(SessionService).signIn({ id: 'a1', nickname: 'alice', role: 'ML_ENGINEER' });
    backend = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => backend.verify());

  function resolve(projectId: string): Promise<ProjectContext | RedirectCommand> {
    const route = { paramMap: convertToParamMap({ projectId }) } as ActivatedRouteSnapshot;
    const result = TestBed.runInInjectionContext(() => projectResolver(route, {} as RouterStateSnapshot));
    return firstValueFrom(result as Observable<ProjectContext | RedirectCommand>);
  }

  it('resolves the project with the role of the signed-in user', async () => {
    const result = resolve('p1');

    backend.expectOne('/api/v1/projects/p1').flush({ id: 'p1', name: 'fraud', createdAt: '2026-09-25T09:00:00Z' });
    backend.expectOne('/api/v1/projects/p1/members/a1').flush({ role: 'OWNER' });

    expect(await result).toEqual({
      project: { id: 'p1', name: 'fraud', description: null, createdAt: '2026-09-25T09:00:00Z' },
      myRole: 'OWNER',
    });
  });

  it('shows the not-found page for an unknown project', async () => {
    const result = resolve('missing');

    const membership = backend.expectOne('/api/v1/projects/missing/members/a1');
    backend
      .expectOne('/api/v1/projects/missing')
      .flush(problemBody(404, 'Project missing not found'), { status: 404, statusText: 'Not Found' });
    // forkJoin drops the membership lookup once the project is known to be missing
    expect(membership.cancelled).toBe(true);

    const redirect = (await result) as RedirectCommand;
    expect(redirect).toBeInstanceOf(RedirectCommand);
    expect(router.serializeUrl(redirect.redirectTo)).toBe('/not-found');
  });
});
