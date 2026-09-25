import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';

import { problemBody, provideHttpTesting } from '../../../../testing/http-test-providers';
import { ProjectRole } from '../../projects/data-access/my-projects.service';
import { TagPickerComponent } from '../../tags/ui/tag-picker.component';
import { Experiment } from '../data-access/experiments.service';
import { ExperimentPageComponent } from './experiment-page.component';

const EXPERIMENT = '/api/v1/projects/p1/experiments/e1';
const run1: Experiment = {
  id: 'e1',
  projectId: 'p1',
  name: 'run-1',
  description: 'first run',
  createdAt: '2026-09-25T09:00:00Z',
  tags: [{ id: 't1', name: 'baseline' }],
};

describe('ExperimentPageComponent', () => {
  let fixture: ComponentFixture<ExperimentPageComponent>;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ExperimentPageComponent],
      providers: [provideRouter([]), ...provideHttpTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  async function render(myRole: ProjectRole | null): Promise<HTMLElement> {
    fixture = TestBed.createComponent(ExperimentPageComponent);
    fixture.componentRef.setInput('context', {
      project: { id: 'p1', name: 'fraud', description: null, createdAt: '2026-09-25T08:00:00Z' },
      experiment: run1,
      myRole,
    });
    await fixture.whenStable();
    if (myRole === 'OWNER' || myRole === 'EDITOR') {
      backend.expectOne((r) => r.url === '/api/v1/tags').flush([], { headers: { 'X-Total-Count': '0' } });
      await fixture.whenStable();
    }
    return fixture.nativeElement as HTMLElement;
  }

  function chipNames(element: HTMLElement): string[] {
    return Array.from(element.querySelectorAll('[data-testid="tag-chip"]')).map((chip) => chip.textContent?.replace('cancel', '').trim() ?? '');
  }

  it('shows the experiment, a link back to its project and its tags', async () => {
    const element = await render('EDITOR');

    expect(element.querySelector('h1')?.textContent).toContain('run-1');
    expect(element.textContent).toContain('first run');
    expect(element.querySelector('a.back')?.getAttribute('href')).toBe('/projects/p1');
    expect(chipNames(element)).toEqual(['baseline']);
    expect(element.querySelector('app-tag-picker')).not.toBeNull();
  });

  it('attaches a picked tag and shows the updated tags', async () => {
    const element = await render('OWNER');

    const picker = fixture.debugElement.query(By.directive(TagPickerComponent)).componentInstance as TagPickerComponent;
    picker.picked.emit({ id: 't2', name: 'xgboost' });
    backend
      .expectOne({ method: 'PUT', url: `${EXPERIMENT}/tags/t2` })
      .flush({ ...run1, tags: [...run1.tags, { id: 't2', name: 'xgboost' }] });
    await fixture.whenStable();

    expect(chipNames(element)).toEqual(['baseline', 'xgboost']);
  });

  it('detaches a tag with the chip remove button', async () => {
    const element = await render('OWNER');

    element.querySelector<HTMLButtonElement>('button[aria-label="Remove tag baseline"]')?.click();
    backend.expectOne({ method: 'DELETE', url: `${EXPERIMENT}/tags/t1` }).flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();

    expect(chipNames(element)).toEqual([]);
    expect(element.querySelector('[data-testid="no-tags"]')).not.toBeNull();
  });

  it('reloads the experiment when a tag change fails', async () => {
    const element = await render('OWNER');

    element.querySelector<HTMLButtonElement>('button[aria-label="Remove tag baseline"]')?.click();
    backend
      .expectOne({ method: 'DELETE', url: `${EXPERIMENT}/tags/t1` })
      .flush(problemBody(404, 'Tag t1 not found'), { status: 404, statusText: 'Not Found' });
    backend.expectOne({ method: 'GET', url: EXPERIMENT }).flush({ ...run1, tags: [] });
    await fixture.whenStable();

    expect(chipNames(element)).toEqual([]);
  });

  it('is read-only for viewers', async () => {
    const element = await render('VIEWER');

    expect(chipNames(element)).toEqual(['baseline']);
    expect(element.querySelector('button[aria-label="Remove tag baseline"]')).toBeNull();
    expect(element.querySelector('app-tag-picker')).toBeNull();
  });
});
