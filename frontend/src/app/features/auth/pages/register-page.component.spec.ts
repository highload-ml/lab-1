import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { problemBody, provideHttpTesting } from '../../../../testing/http-test-providers';
import { SessionService } from '../../../core/session/session.service';
import { RegisterPageComponent } from './register-page.component';

describe('RegisterPageComponent', () => {
  let fixture: ComponentFixture<RegisterPageComponent>;
  let backend: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [RegisterPageComponent],
      providers: [provideRouter([]), ...provideHttpTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(RegisterPageComponent);
    await fixture.whenStable();
  });

  afterEach(() => backend.verify());

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  async function submitNickname(nickname: string): Promise<void> {
    const input = element().querySelector<HTMLInputElement>('input[formcontrolname="nickname"]');
    if (!input) {
      throw new Error('nickname input not found');
    }
    input.value = nickname;
    input.dispatchEvent(new Event('input'));
    element().querySelector<HTMLButtonElement>('button[type="submit"]')?.click();
    await fixture.whenStable();
  }

  it('does not call the backend for an invalid nickname', async () => {
    await submitNickname('a b');

    backend.expectNone('/api/v1/users');
    expect(element().textContent).toContain('Only latin letters, digits');
  });

  it('registers with the default ML engineer role, signs in and opens /projects', async () => {
    await submitNickname('alice');

    const request = backend.expectOne({ method: 'POST', url: '/api/v1/users' });
    expect(request.request.body).toEqual({ nickname: 'alice', role: 'ML_ENGINEER' });
    request.flush({ id: 'a1', nickname: 'alice', role: 'ML_ENGINEER' }, { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    expect(TestBed.inject(SessionService).user()?.id).toBe('a1');
    expect(navigate).toHaveBeenCalledWith('/projects');
  });

  it('shows "already taken" on the nickname field for 409', async () => {
    await submitNickname('alice');

    backend
      .expectOne('/api/v1/users')
      .flush(problemBody(409, "Nickname 'alice' is already taken"), { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();

    expect(element().textContent).toContain('Nickname is already taken');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('puts backend validation errors on the field', async () => {
    await submitNickname('alice');

    backend
      .expectOne('/api/v1/users')
      .flush(problemBody(400, 'Request validation failed', { nickname: ['rejected by server'] }), {
        status: 400,
        statusText: 'Bad Request',
      });
    await fixture.whenStable();

    expect(element().textContent).toContain('rejected by server');
  });
});
