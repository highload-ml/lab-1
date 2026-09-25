import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { problemBody, provideHttpTesting } from '../../../../testing/http-test-providers';
import { SessionService } from '../../../core/session/session.service';
import { LoginPageComponent } from './login-page.component';

describe('LoginPageComponent', () => {
  let fixture: ComponentFixture<LoginPageComponent>;
  let backend: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [LoginPageComponent],
      providers: [provideRouter([]), ...provideHttpTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });

  afterEach(() => backend.verify());

  async function render(returnUrl?: string): Promise<HTMLElement> {
    fixture = TestBed.createComponent(LoginPageComponent);
    if (returnUrl) {
      fixture.componentRef.setInput('returnUrl', returnUrl);
    }
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  async function submit(element: HTMLElement, nickname: string): Promise<void> {
    const input = element.querySelector<HTMLInputElement>('input[formcontrolname="nickname"]');
    if (!input) {
      throw new Error('nickname input not found');
    }
    input.value = nickname;
    input.dispatchEvent(new Event('input'));
    element.querySelector<HTMLButtonElement>('button[type="submit"]')?.click();
    await fixture.whenStable();
  }

  it('requires a nickname', async () => {
    const element = await render();

    await submit(element, '');

    expect(element.textContent).toContain('Nickname is required');
  });

  it('signs in and returns to the page the user wanted', async () => {
    const element = await render('/projects?page=2');

    await submit(element, ' alice ');
    backend
      .expectOne('/api/v1/users/by-nickname/alice')
      .flush({ id: 'a1', nickname: 'alice', role: 'REVIEWER' });
    await fixture.whenStable();

    expect(TestBed.inject(SessionService).user()?.role).toBe('REVIEWER');
    expect(navigate).toHaveBeenCalledWith('/projects?page=2');
  });

  it('shows "No user with this nickname" for 404', async () => {
    const element = await render();

    await submit(element, 'ghost');
    backend
      .expectOne('/api/v1/users/by-nickname/ghost')
      .flush(problemBody(404, "User with nickname 'ghost' not found"), { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(element.textContent).toContain('No user with this nickname');
    expect(TestBed.inject(SessionService).signedIn()).toBe(false);
    expect(navigate).not.toHaveBeenCalled();
  });
});
