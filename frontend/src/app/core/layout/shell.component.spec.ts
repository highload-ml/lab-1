import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { SessionService } from '../session/session.service';
import { ShellComponent } from './shell.component';

describe('ShellComponent', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [ShellComponent],
      providers: [provideRouter([])],
    });
  });

  it('shows the signed-in user and signs out to /login', async () => {
    const session = TestBed.inject(SessionService);
    session.signIn({ id: 'a1', nickname: 'alice', role: 'REVIEWER' });
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    const fixture = TestBed.createComponent(ShellComponent);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('[data-testid="current-user"]')?.textContent).toContain('alice');
    expect(element.textContent).toContain('REVIEWER');

    const signOut = Array.from(element.querySelectorAll('button')).find((b) => b.textContent?.includes('Sign out'));
    signOut?.click();

    expect(session.signedIn()).toBe(false);
    expect(navigate).toHaveBeenCalledWith('/login');
  });
});
