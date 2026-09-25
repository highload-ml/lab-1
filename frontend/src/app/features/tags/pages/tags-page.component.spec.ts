import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { problemBody, provideHttpTesting } from '../../../../testing/http-test-providers';
import { TagsPageComponent } from './tags-page.component';

describe('TagsPageComponent', () => {
  let fixture: ComponentFixture<TagsPageComponent>;
  let backend: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({ imports: [TagsPageComponent], providers: provideHttpTesting() });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TagsPageComponent);
    await fixture.whenStable();
  });

  afterEach(() => backend.verify());

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function expectPage(): TestRequest {
    return backend.expectOne((r) => r.method === 'GET' && r.url === '/api/v1/tags');
  }

  async function create(name: string): Promise<void> {
    const input = element().querySelector<HTMLInputElement>('input[formcontrolname="name"]');
    if (!input) {
      throw new Error('name input not found');
    }
    input.value = name;
    input.dispatchEvent(new Event('input'));
    element().querySelector<HTMLButtonElement>('button[type="submit"]')?.click();
    await fixture.whenStable();
  }

  it('lists tags with the total in the paginator', async () => {
    expectPage().flush([{ id: 't1', name: 'baseline' }], { headers: { 'X-Total-Count': '1' } });
    await fixture.whenStable();

    expect(element().querySelectorAll('[data-testid="tag-row"]').length).toBe(1);
    expect(element().textContent).toContain('baseline');
  });

  it('creates a tag and reloads the list', async () => {
    expectPage().flush([], { headers: { 'X-Total-Count': '0' } });
    await fixture.whenStable();
    expect(element().querySelector('[data-testid="empty-state"]')).not.toBeNull();

    await create(' baseline ');
    const request = backend.expectOne({ method: 'POST', url: '/api/v1/tags' });
    expect(request.request.body).toEqual({ name: 'baseline' });
    request.flush({ id: 't1', name: 'baseline' }, { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    expectPage().flush([{ id: 't1', name: 'baseline' }], { headers: { 'X-Total-Count': '1' } });
    await fixture.whenStable();
    expect(element().querySelectorAll('[data-testid="tag-row"]').length).toBe(1);
  });

  it('shows "already exists" for 409 and does not send blank names', async () => {
    expectPage().flush([], { headers: { 'X-Total-Count': '0' } });
    await fixture.whenStable();

    await create('   ');
    backend.expectNone({ method: 'POST', url: '/api/v1/tags' });

    await create('baseline');
    backend
      .expectOne({ method: 'POST', url: '/api/v1/tags' })
      .flush(problemBody(409, "Tag 'baseline' already exists"), { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();
    expect(element().textContent).toContain('This tag already exists');
  });
});
