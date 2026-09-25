import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';

import { problemBody, provideHttpTesting } from '../../../../testing/http-test-providers';
import { SessionService } from '../../../core/session/session.service';
import { NewProjectDialogComponent } from './new-project-dialog.component';

describe('NewProjectDialogComponent', () => {
  let fixture: ComponentFixture<NewProjectDialogComponent>;
  let backend: HttpTestingController;
  const close = vi.fn();

  beforeEach(async () => {
    localStorage.clear();
    close.mockReset();
    TestBed.configureTestingModule({
      imports: [NewProjectDialogComponent],
      providers: [...provideHttpTesting(), { provide: MatDialogRef, useValue: { close } }],
    });
    TestBed.inject(SessionService).signIn({ id: 'a1', nickname: 'alice', role: 'ML_ENGINEER' });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(NewProjectDialogComponent);
    await fixture.whenStable();
  });

  afterEach(() => backend.verify());

  async function submit(name: string, description = ''): Promise<void> {
    const element = fixture.nativeElement as HTMLElement;
    const nameInput = element.querySelector<HTMLInputElement>('input[formcontrolname="name"]');
    const descriptionInput = element.querySelector<HTMLTextAreaElement>('textarea[formcontrolname="description"]');
    if (!nameInput || !descriptionInput) {
      throw new Error('form fields not found');
    }
    nameInput.value = name;
    nameInput.dispatchEvent(new Event('input'));
    descriptionInput.value = description;
    descriptionInput.dispatchEvent(new Event('input'));
    element.querySelector<HTMLButtonElement>('button[type="submit"]')?.click();
    await fixture.whenStable();
  }

  it('requires a name', async () => {
    await submit('  ');

    backend.expectNone('/api/v1/projects');
    expect(close).not.toHaveBeenCalled();
  });

  it('creates the project owned by the signed-in user and closes with its id', async () => {
    await submit(' fraud ', ' Fraud models ');

    const request = backend.expectOne({ method: 'POST', url: '/api/v1/projects' });
    expect(request.request.body).toEqual({ name: 'fraud', description: 'Fraud models', ownerId: 'a1' });
    request.flush({ id: 'p1', name: 'fraud' }, { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    expect(close).toHaveBeenCalledWith('p1');
  });

  it('keeps the dialog open with a name error for 409', async () => {
    await submit('fraud');

    backend
      .expectOne('/api/v1/projects')
      .flush(problemBody(409, "Project name 'fraud' is already taken"), { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('A project with this name already exists');
    expect(close).not.toHaveBeenCalled();
  });
});
