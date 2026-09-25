import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { provideHttpTesting } from '../../../../testing/http-test-providers';
import { SessionService } from '../../../core/session/session.service';
import { MyProjectsService } from './my-projects.service';

describe('MyProjectsService', () => {
  let service: MyProjectsService;
  let backend: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: provideHttpTesting() });
    TestBed.inject(SessionService).signIn({ id: 'a1', nickname: 'alice', role: 'ML_ENGINEER' });
    service = TestBed.inject(MyProjectsService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('loads a page of the signed-in user projects with the total from X-Total-Count', async () => {
    const result = firstValueFrom(service.page(1, 20));

    const request = backend.expectOne((r) => r.url === '/api/v1/users/a1/projects');
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('size')).toBe('20');
    request.flush(
      [
        {
          projectId: 'p1',
          name: 'fraud',
          createdAt: '2026-09-25T09:00:00Z',
          role: 'OWNER',
          joinedAt: '2026-09-25T09:00:01Z',
        },
      ],
      { headers: { 'X-Total-Count': '21' } },
    );

    expect(await result).toEqual({
      total: 21,
      items: [
        {
          id: 'p1',
          name: 'fraud',
          description: null,
          createdAt: '2026-09-25T09:00:00Z',
          role: 'OWNER',
          joinedAt: '2026-09-25T09:00:01Z',
        },
      ],
    });
  });

  it('creates a project owned by the signed-in user', async () => {
    const result = firstValueFrom(service.create({ name: 'fraud', description: null }));

    const request = backend.expectOne({ method: 'POST', url: '/api/v1/projects' });
    expect(request.request.body).toEqual({ name: 'fraud', description: undefined, ownerId: 'a1' });
    request.flush({ id: 'p1', name: 'fraud' }, { status: 201, statusText: 'Created' });

    expect(await result).toBe('p1');
  });

  it('rejects an incomplete project from the backend', async () => {
    const result = firstValueFrom(service.page(0, 20)).catch((error: unknown) => error);

    backend.expectOne((r) => r.url === '/api/v1/users/a1/projects').flush([{ name: 'no id' }]);

    expect(await result).toBeInstanceOf(Error);
  });
});
