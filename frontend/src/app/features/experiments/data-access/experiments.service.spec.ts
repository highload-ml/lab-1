import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { provideHttpTesting } from '../../../../testing/http-test-providers';
import { ExperimentsService, toExperiment } from './experiments.service';

const EXPERIMENTS = '/api/v1/projects/p1/experiments';
const run1 = {
  id: 'e1',
  projectId: 'p1',
  name: 'run-1',
  createdAt: '2026-09-25T09:00:00Z',
  tags: [{ id: 't1', name: 'baseline' }],
};

describe('ExperimentsService', () => {
  let service: ExperimentsService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideHttpTesting() });
    service = TestBed.inject(ExperimentsService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('loads a page of experiments with the total, optionally filtered by tag', async () => {
    const result = firstValueFrom(service.page('p1', 2, 20, 't1'));

    const request = backend.expectOne((r) => r.url === EXPERIMENTS);
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('20');
    expect(request.request.params.get('tagId')).toBe('t1');
    request.flush([run1], { headers: { 'X-Total-Count': '41' } });

    const page = await result;
    expect(page.total).toBe(41);
    expect(page.items[0]).toEqual({ ...run1, description: null });
  });

  it('does not send tagId without a filter', async () => {
    const result = firstValueFrom(service.page('p1', 0, 20));

    const request = backend.expectOne((r) => r.url === EXPERIMENTS);
    expect(request.request.params.has('tagId')).toBe(false);
    request.flush([], { headers: { 'X-Total-Count': '0' } });
    expect((await result).items).toEqual([]);
  });

  it('creates, updates and deletes experiments of the project', async () => {
    const created = firstValueFrom(service.create('p1', { name: 'run-1', description: null }));
    const create = backend.expectOne({ method: 'POST', url: EXPERIMENTS });
    expect(create.request.body).toEqual({ name: 'run-1', description: undefined });
    create.flush(run1, { status: 201, statusText: 'Created' });
    expect((await created).id).toBe('e1');

    const updated = firstValueFrom(service.update('p1', 'e1', { name: 'run-2', description: 'tuned' }));
    const update = backend.expectOne({ method: 'PUT', url: `${EXPERIMENTS}/e1` });
    expect(update.request.body).toEqual({ name: 'run-2', description: 'tuned' });
    update.flush({ ...run1, name: 'run-2', description: 'tuned' });
    expect((await updated).name).toBe('run-2');

    const deleted = firstValueFrom(service.delete('p1', 'e1'));
    backend.expectOne({ method: 'DELETE', url: `${EXPERIMENTS}/e1` }).flush(null, { status: 204, statusText: 'No Content' });
    expect(await deleted).toBeUndefined();
  });

  it('gets one experiment and attaches / detaches tags', async () => {
    const loaded = firstValueFrom(service.get('p1', 'e1'));
    backend.expectOne({ method: 'GET', url: `${EXPERIMENTS}/e1` }).flush(run1);
    expect((await loaded).tags).toEqual([{ id: 't1', name: 'baseline' }]);

    const attached = firstValueFrom(service.attachTag('p1', 'e1', 't2'));
    backend
      .expectOne({ method: 'PUT', url: `${EXPERIMENTS}/e1/tags/t2` })
      .flush({ ...run1, tags: [...run1.tags, { id: 't2', name: 'xgboost' }] });
    expect((await attached).tags.map((tag) => tag.name)).toEqual(['baseline', 'xgboost']);

    const detached = firstValueFrom(service.detachTag('p1', 'e1', 't1'));
    backend.expectOne({ method: 'DELETE', url: `${EXPERIMENTS}/e1/tags/t1` }).flush(null, { status: 204, statusText: 'No Content' });
    expect(await detached).toBeUndefined();
  });

  it('drops incomplete tags and rejects an incomplete experiment', () => {
    expect(toExperiment({ ...run1, tags: [{ id: 't1' }, { id: 't2', name: 'x' }] }).tags).toEqual([{ id: 't2', name: 'x' }]);
    expect(() => toExperiment({ name: 'no id' })).toThrow();
  });
});
