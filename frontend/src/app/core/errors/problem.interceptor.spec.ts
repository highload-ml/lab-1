import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { ApiProblem, isApiProblem } from './api-problem';
import { problemInterceptor } from './problem.interceptor';

describe('problemInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([problemInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  async function failWith(body: string | object, status: number, statusText = 'Error'): Promise<ApiProblem> {
    const result = firstValueFrom(http.get('/api/v1/users')).then(
      () => {
        throw new Error('expected an error');
      },
      (error: unknown) => error as ApiProblem,
    );
    backend.expectOne('/api/v1/users').flush(body, { status, statusText });
    return result;
  }

  it('maps a ProblemDetail with validation errors', async () => {
    const problem = await failWith(
      {
        title: 'Bad Request',
        status: 400,
        detail: 'Request validation failed',
        validationErrors: { nickname: ['size must be between 3 and 50'], ignored: 'not a list' },
      },
      400,
    );

    expect(isApiProblem(problem)).toBe(true);
    expect(problem).toEqual({
      status: 400,
      title: 'Bad Request',
      detail: 'Request validation failed',
      validationErrors: { nickname: ['size must be between 3 and 50'] },
    });
  });

  it('keeps the detail of a conflict', async () => {
    const problem = await failWith({ title: 'Conflict', detail: "Nickname 'alice' is already taken" }, 409);

    expect(problem.status).toBe(409);
    expect(problem.detail).toBe("Nickname 'alice' is already taken");
    expect(problem.validationErrors).toEqual({});
  });

  it('never exposes a non-JSON server error body', async () => {
    const problem = await failWith('<html>stack trace</html>', 500);

    expect(problem.detail).toBe('Something went wrong on the server. Please try again.');
    expect(problem.title).toBe('HTTP 500');
  });

  it('reports an unreachable backend as a network problem', async () => {
    const result = firstValueFrom(http.get('/api/v1/users')).then(
      () => {
        throw new Error('expected an error');
      },
      (error: unknown) => error as ApiProblem,
    );
    backend.expectOne('/api/v1/users').error(new ProgressEvent('error'), { status: 0, statusText: '' });
    const problem = await result;

    expect(problem.status).toBe(0);
    expect(problem.title).toBe('Network error');
  });
});
