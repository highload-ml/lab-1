import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { toApiProblem } from './api-problem';

/** Every failed backend call is rethrown as a typed {@link ApiProblem}, so features never parse error bodies. */
export const problemInterceptor: HttpInterceptorFn = (request, next) =>
  next(request).pipe(
    catchError((error: unknown) =>
      throwError(() => (error instanceof HttpErrorResponse ? toApiProblem(error) : error)),
    ),
  );
