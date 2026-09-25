import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { provideHttpTesting } from '../../../../testing/http-test-providers';
import { TagsService, toTag } from './tags.service';

function tags(from: number, count: number): { id: string; name: string }[] {
  return Array.from({ length: count }, (_, i) => ({ id: `t${from + i}`, name: `tag-${from + i}` }));
}

describe('TagsService', () => {
  let service: TagsService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideHttpTesting() });
    service = TestBed.inject(TagsService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('loads a page of tags with the total', async () => {
    const result = firstValueFrom(service.page(1, 20));

    const request = backend.expectOne((r) => r.url === '/api/v1/tags');
    expect(request.request.params.get('page')).toBe('1');
    request.flush(tags(20, 2), { headers: { 'X-Total-Count': '22' } });

    expect(await result).toEqual({ total: 22, items: tags(20, 2) });
  });

  it('all() loads pages of 50 until the total is reached', async () => {
    const result = firstValueFrom(service.all());

    const first = backend.expectOne((r) => r.url === '/api/v1/tags' && r.params.get('page') === '0');
    expect(first.request.params.get('size')).toBe('50');
    first.flush(tags(0, 50), { headers: { 'X-Total-Count': '120' } });
    backend.expectOne((r) => r.params.get('page') === '1').flush(tags(50, 50), { headers: { 'X-Total-Count': '120' } });
    backend.expectOne((r) => r.params.get('page') === '2').flush(tags(100, 20), { headers: { 'X-Total-Count': '120' } });

    const all = await result;
    expect(all.length).toBe(120);
    expect(all[119].name).toBe('tag-119');
  });

  it('all() makes a single request when everything fits in one page', async () => {
    const result = firstValueFrom(service.all());

    backend.expectOne((r) => r.url === '/api/v1/tags').flush(tags(0, 3), { headers: { 'X-Total-Count': '3' } });

    expect((await result).length).toBe(3);
  });

  it('creates a tag', async () => {
    const result = firstValueFrom(service.create('baseline'));

    const request = backend.expectOne({ method: 'POST', url: '/api/v1/tags' });
    expect(request.request.body).toEqual({ name: 'baseline' });
    request.flush({ id: 't1', name: 'baseline' }, { status: 201, statusText: 'Created' });

    expect(await result).toEqual({ id: 't1', name: 'baseline' });
  });

  it('rejects an incomplete tag', () => {
    expect(() => toTag({ name: 'no id' })).toThrow();
  });
});
