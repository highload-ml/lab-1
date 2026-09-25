import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RedirectCommand, Router, RouterStateSnapshot, convertToParamMap, provideRouter } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';

import { problemBody, provideHttpTesting } from '../../../testing/http-test-providers';
import { SessionService } from '../../core/session/session.service';
import { ExperimentContext, experimentResolver } from './experiment.resolver';

describe('experimentResolver', () => {
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

  function resolve(experimentId: string): Promise<ExperimentContext | RedirectCommand> {
    const route = { paramMap: convertToParamMap({ projectId: 'p1', experimentId }) } as ActivatedRouteSnapshot;
    const result = TestBed.runInInjectionContext(() => experimentResolver(route, {} as RouterStateSnapshot));
    return firstValueFrom(result as Observable<ExperimentContext | RedirectCommand>);
  }

  it('resolves the experiment with its project and the user role', async () => {
    const result = resolve('e1');

    backend.expectOne('/api/v1/projects/p1').flush({ id: 'p1', name: 'fraud', createdAt: '2026-09-25T08:00:00Z' });
    backend
      .expectOne('/api/v1/projects/p1/experiments/e1')
      .flush({ id: 'e1', projectId: 'p1', name: 'run-1', createdAt: '2026-09-25T09:00:00Z', tags: [] });
    backend.expectOne('/api/v1/projects/p1/members/a1').flush({ role: 'VIEWER' });

    const context = (await result) as ExperimentContext;
    expect(context.project.name).toBe('fraud');
    expect(context.experiment.name).toBe('run-1');
    expect(context.myRole).toBe('VIEWER');
  });

  it('shows the not-found page for an unknown experiment', async () => {
    const result = resolve('missing');

    backend.expectOne('/api/v1/projects/p1').flush({ id: 'p1', name: 'fraud', createdAt: '2026-09-25T08:00:00Z' });
    const membership = backend.expectOne('/api/v1/projects/p1/members/a1');
    backend
      .expectOne('/api/v1/projects/p1/experiments/missing')
      .flush(problemBody(404, 'Experiment missing not found'), { status: 404, statusText: 'Not Found' });
    expect(membership.cancelled).toBe(true);

    const redirect = (await result) as RedirectCommand;
    expect(router.serializeUrl(redirect.redirectTo)).toBe('/not-found');
  });

  it('goes back to the project page on other errors', async () => {
    const result = resolve('e1');

    backend.expectOne('/api/v1/projects/p1').flush(problemBody(500, 'boom'), { status: 500, statusText: 'Server Error' });
    backend.match(() => true).forEach((request) => expect(request.cancelled).toBe(true));

    const redirect = (await result) as RedirectCommand;
    expect(router.serializeUrl(redirect.redirectTo)).toBe('/projects/p1');
  });
});
