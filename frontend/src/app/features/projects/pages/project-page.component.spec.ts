import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { provideHttpTesting } from '../../../../testing/http-test-providers';
import { ProjectRole } from '../data-access/my-projects.service';
import { ProjectPageComponent } from './project-page.component';

const EXPERIMENTS = '/api/v1/projects/p1/experiments';
const run1 = { id: 'e1', projectId: 'p1', name: 'run-1', createdAt: '2026-09-25T09:00:00Z', tags: [{ id: 't1', name: 'baseline' }] };
const run2 = { id: 'e2', projectId: 'p1', name: 'run-2', createdAt: '2026-09-25T10:00:00Z', tags: [] };

describe('ProjectPageComponent', () => {
  let fixture: ComponentFixture<ProjectPageComponent>;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ProjectPageComponent],
      providers: [provideRouter([]), ...provideHttpTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  async function render(myRole: ProjectRole | null, tagId?: string): Promise<HTMLElement> {
    fixture = TestBed.createComponent(ProjectPageComponent);
    fixture.componentRef.setInput('context', {
      project: { id: 'p1', name: 'fraud', description: 'Fraud models', createdAt: '2026-09-25T08:00:00Z' },
      myRole,
    });
    if (tagId) {
      fixture.componentRef.setInput('tagId', tagId);
    }
    await fixture.whenStable();
    // tag filter options
    backend.expectOne((r) => r.url === '/api/v1/tags').flush([{ id: 't1', name: 'baseline' }], {
      headers: { 'X-Total-Count': '1' },
    });
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  function expectExperiments(page = '0', tagId: string | null = null): TestRequest {
    const request = backend.expectOne((r) => r.url === EXPERIMENTS);
    expect(request.request.params.get('page')).toBe(page);
    expect(request.request.params.get('tagId')).toBe(tagId);
    return request;
  }

  it('shows the project header, the user role and the experiments with tags', async () => {
    const element = await render('OWNER');
    expectExperiments().flush([run1, run2], { headers: { 'X-Total-Count': '2' } });
    await fixture.whenStable();

    expect(element.querySelector('h1')?.textContent).toContain('fraud');
    expect(element.textContent).toContain('Fraud models');
    expect(element.querySelector('[data-testid="my-role"]')?.textContent).toContain('Owner');
    const rows = element.querySelectorAll('[data-testid="experiment-row"]');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('baseline');
    expect(element.textContent).toContain('New experiment');
    expect(element.querySelectorAll('button[aria-label="Delete experiment"]').length).toBe(2);
  });

  it('links experiment names to the experiment page', async () => {
    const element = await render('OWNER');
    expectExperiments().flush([run1], { headers: { 'X-Total-Count': '1' } });
    await fixture.whenStable();

    expect(element.querySelector('a.experiment-link')?.getAttribute('href')).toBe('/experiments/e1');
  });

  it('filters by the tag from the query parameter', async () => {
    const element = await render('OWNER', 't1');
    expectExperiments('0', 't1').flush([], { headers: { 'X-Total-Count': '0' } });
    await fixture.whenStable();

    expect(element.querySelector('[data-testid="empty-state"]')?.textContent).toContain('No experiments with this tag');
  });

  it('puts the selected tag into the URL and reloads when it changes', async () => {
    await render('OWNER');
    expectExperiments().flush([run1], { headers: { 'X-Total-Count': '1' } });
    await fixture.whenStable();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    (fixture.componentInstance as unknown as { filterByTag(tagId: string): void }).filterByTag('t1');
    expect(navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { tagId: 't1' } }));

    fixture.componentRef.setInput('tagId', 't1');
    await fixture.whenStable();
    expectExperiments('0', 't1').flush([run1], { headers: { 'X-Total-Count': '1' } });
  });

  it('is read-only for viewers', async () => {
    const element = await render('VIEWER');
    expectExperiments().flush([run1], { headers: { 'X-Total-Count': '1' } });
    await fixture.whenStable();

    expect(element.textContent).not.toContain('New experiment');
    expect(element.querySelector('button[aria-label="Edit experiment"]')).toBeNull();
  });

  it('tells a non-member they are not in the project', async () => {
    const element = await render(null);
    expectExperiments().flush([], { headers: { 'X-Total-Count': '0' } });
    await fixture.whenStable();

    expect(element.querySelector('[data-testid="not-member"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="empty-state"]')?.textContent).toContain('No experiments yet');
  });

  it('reloads after creating an experiment', async () => {
    const element = await render('EDITOR');
    expectExperiments().flush([], { headers: { 'X-Total-Count': '0' } });
    await fixture.whenStable();
    vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of({ ...run1, description: null }),
    } as ReturnType<MatDialog['open']>);

    Array.from(element.querySelectorAll('button')).find((b) => b.textContent?.includes('New experiment'))?.click();
    await fixture.whenStable();

    expectExperiments().flush([run1], { headers: { 'X-Total-Count': '1' } });
    await fixture.whenStable();
    expect(element.querySelectorAll('[data-testid="experiment-row"]').length).toBe(1);
  });

  it('deletes an experiment only after confirmation', async () => {
    const element = await render('OWNER');
    expectExperiments().flush([run1], { headers: { 'X-Total-Count': '1' } });
    await fixture.whenStable();
    const open = vi.spyOn(TestBed.inject(MatDialog), 'open');

    open.mockReturnValueOnce({ afterClosed: () => of(false) } as ReturnType<MatDialog['open']>);
    element.querySelector<HTMLButtonElement>('button[aria-label="Delete experiment"]')?.click();
    await fixture.whenStable();
    backend.expectNone(`${EXPERIMENTS}/e1`);

    open.mockReturnValueOnce({ afterClosed: () => of(true) } as ReturnType<MatDialog['open']>);
    element.querySelector<HTMLButtonElement>('button[aria-label="Delete experiment"]')?.click();
    await fixture.whenStable();
    backend.expectOne({ method: 'DELETE', url: `${EXPERIMENTS}/e1` }).flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();

    expectExperiments().flush([], { headers: { 'X-Total-Count': '0' } });
    await fixture.whenStable();
    expect(element.querySelector('[data-testid="empty-state"]')).not.toBeNull();
  });
});
