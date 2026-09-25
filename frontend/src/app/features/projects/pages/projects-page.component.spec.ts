import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { problemBody, provideHttpTesting } from '../../../../testing/http-test-providers';
import { SessionService } from '../../../core/session/session.service';
import { ProjectsPageComponent } from './projects-page.component';

const fraud = {
  projectId: 'p1',
  name: 'fraud',
  description: 'Fraud models',
  createdAt: '2026-09-25T09:00:00Z',
  role: 'OWNER',
  joinedAt: '2026-09-25T09:00:01Z',
};

describe('ProjectsPageComponent', () => {
  let fixture: ComponentFixture<ProjectsPageComponent>;
  let backend: HttpTestingController;

  beforeEach(async () => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [ProjectsPageComponent],
      providers: [provideRouter([]), ...provideHttpTesting()],
    });
    TestBed.inject(SessionService).signIn({ id: 'a1', nickname: 'alice', role: 'ML_ENGINEER' });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProjectsPageComponent);
    await fixture.whenStable();
  });

  afterEach(() => backend.verify());

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function expectProjectsRequest(page = '0'): TestRequest {
    const request = backend.expectOne((r) => r.url === '/api/v1/users/a1/projects');
    expect(request.request.params.get('page')).toBe(page);
    return request;
  }

  it('shows the user projects with their role and the total in the paginator', async () => {
    expectProjectsRequest().flush([fraud], { headers: { 'X-Total-Count': '1' } });
    await fixture.whenStable();

    const rows = element().querySelectorAll('[data-testid="project-row"]');
    expect(rows.length).toBe(1);
    expect(rows[0].textContent).toContain('fraud');
    expect(rows[0].textContent).toContain('Fraud models');
    expect(rows[0].textContent).toContain('Owner');
    expect(element().querySelector('.mat-mdc-paginator-range-label')?.textContent).toContain('1 – 1 of 1');
  });

  it('shows the empty state when the user has no projects', async () => {
    expectProjectsRequest().flush([], { headers: { 'X-Total-Count': '0' } });
    await fixture.whenStable();

    expect(element().querySelector('[data-testid="empty-state"]')?.textContent).toContain('You have no projects yet');
  });

  it('links each project to its page and opens a newly created project', async () => {
    expectProjectsRequest().flush([fraud], { headers: { 'X-Total-Count': '1' } });
    await fixture.whenStable();
    expect(element().querySelector('a.project-link')?.getAttribute('href')).toBe('/projects/p1');

    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const dialog = TestBed.inject(MatDialog);
    vi.spyOn(dialog, 'open').mockReturnValue({ afterClosed: () => of('p2') } as ReturnType<MatDialog['open']>);
    element().querySelector<HTMLButtonElement>('header button')?.click();
    await fixture.whenStable();

    expect(navigate).toHaveBeenCalledWith(['/projects', 'p2']);
  });

  it('keeps the page usable when loading fails', async () => {
    expectProjectsRequest().flush(problemBody(500, 'boom'), { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect(element().querySelector('[data-testid="empty-state"]')).toBeNull();
    expect(element().textContent).toContain('New project');
  });
});
