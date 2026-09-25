import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { problemBody, provideHttpTesting } from '../../../../testing/http-test-providers';
import { Experiment } from '../data-access/experiments.service';
import { ExperimentDialogComponent, ExperimentDialogData } from './experiment-dialog.component';

const existing: Experiment = {
  id: 'e1',
  projectId: 'p1',
  name: 'run-1',
  description: 'first',
  createdAt: '2026-09-25T09:00:00Z',
  tags: [],
};

describe('ExperimentDialogComponent', () => {
  let fixture: ComponentFixture<ExperimentDialogComponent>;
  let backend: HttpTestingController;
  const close = vi.fn();

  async function open(data: ExperimentDialogData): Promise<HTMLElement> {
    close.mockReset();
    TestBed.configureTestingModule({
      imports: [ExperimentDialogComponent],
      providers: [
        ...provideHttpTesting(),
        { provide: MatDialogRef, useValue: { close } },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ExperimentDialogComponent);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  afterEach(() => backend.verify());

  async function fillAndSubmit(element: HTMLElement, name: string): Promise<void> {
    const input = element.querySelector<HTMLInputElement>('input[formcontrolname="name"]');
    if (!input) {
      throw new Error('name input not found');
    }
    input.value = name;
    input.dispatchEvent(new Event('input'));
    element.querySelector<HTMLButtonElement>('button[type="submit"]')?.click();
    await fixture.whenStable();
  }

  it('creates an experiment in the project', async () => {
    const element = await open({ projectId: 'p1' });
    expect(element.textContent).toContain('New experiment');

    await fillAndSubmit(element, ' run-1 ');
    const request = backend.expectOne({ method: 'POST', url: '/api/v1/projects/p1/experiments' });
    expect(request.request.body).toEqual({ name: 'run-1', description: undefined });
    request.flush({ ...existing, description: undefined }, { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    expect(close).toHaveBeenCalledWith(expect.objectContaining({ id: 'e1', name: 'run-1' }));
  });

  it('edits an existing experiment, prefilled', async () => {
    const element = await open({ projectId: 'p1', experiment: existing });
    expect(element.textContent).toContain('Edit experiment');
    expect(element.querySelector<HTMLTextAreaElement>('textarea')?.value).toBe('first');

    await fillAndSubmit(element, 'run-2');
    const request = backend.expectOne({ method: 'PUT', url: '/api/v1/projects/p1/experiments/e1' });
    expect(request.request.body).toEqual({ name: 'run-2', description: 'first' });
    request.flush({ ...existing, name: 'run-2' });
    await fixture.whenStable();

    expect(close).toHaveBeenCalledWith(expect.objectContaining({ name: 'run-2' }));
  });

  it('shows a name conflict within the project and stays open', async () => {
    const element = await open({ projectId: 'p1' });

    await fillAndSubmit(element, 'run-1');
    backend
      .expectOne('/api/v1/projects/p1/experiments')
      .flush(problemBody(409, "Experiment 'run-1' already exists"), { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();

    expect(element.textContent).toContain('An experiment with this name already exists in the project');
    expect(close).not.toHaveBeenCalled();
  });

  it('does not submit a blank name', async () => {
    const element = await open({ projectId: 'p1' });

    await fillAndSubmit(element, '   ');

    backend.expectNone('/api/v1/projects/p1/experiments');
    expect(element.textContent).toContain('Name is required');
  });
});
