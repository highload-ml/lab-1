import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { problemBody, provideHttpTesting } from '../../../../testing/http-test-providers';
import { ApiProblem } from '../../../core/errors/api-problem';
import { SessionService } from '../../../core/session/session.service';
import { ProjectService } from './project.service';

describe('ProjectService', () => {
  let service: ProjectService;
  let backend: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: provideHttpTesting() });
    TestBed.inject(SessionService).signIn({ id: 'a1', nickname: 'alice', role: 'ML_ENGINEER' });
    service = TestBed.inject(ProjectService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('loads project details', async () => {
    const result = firstValueFrom(service.get('p1'));

    backend.expectOne('/api/v1/projects/p1').flush({ id: 'p1', name: 'fraud', createdAt: '2026-09-25T09:00:00Z' });

    expect(await result).toEqual({ id: 'p1', name: 'fraud', description: null, createdAt: '2026-09-25T09:00:00Z' });
  });

  it('myRole returns the role of the signed-in user', async () => {
    const result = firstValueFrom(service.myRole('p1'));

    backend.expectOne('/api/v1/projects/p1/members/a1').flush({ userId: 'a1', nickname: 'alice', role: 'EDITOR' });

    expect(await result).toBe('EDITOR');
  });

  it('myRole returns null when the user is not a member', async () => {
    const result = firstValueFrom(service.myRole('p1'));

    backend
      .expectOne('/api/v1/projects/p1/members/a1')
      .flush(problemBody(404, 'Membership not found'), { status: 404, statusText: 'Not Found' });

    expect(await result).toBeNull();
  });

  it('myRole propagates other errors', async () => {
    const result = firstValueFrom(service.myRole('p1')).then(
      () => {
        throw new Error('expected an error');
      },
      (error: unknown) => error as ApiProblem,
    );

    backend
      .expectOne('/api/v1/projects/p1/members/a1')
      .flush(problemBody(500, 'boom'), { status: 500, statusText: 'Server Error' });

    expect((await result).status).toBe(500);
  });
});
