import { HttpErrorResponse } from '@angular/common/http';

/**
 * RFC 7807 Problem Details as produced by the backend's ApiExceptionHandler.
 * `status` 0 means the request never reached the backend (network error, proxy down).
 */
export interface ApiProblem {
  status: number;
  title: string;
  detail: string;
  validationErrors: Record<string, string[]>;
}

export function isApiProblem(value: unknown): value is ApiProblem {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as ApiProblem).status === 'number' &&
    typeof (value as ApiProblem).detail === 'string' &&
    typeof (value as ApiProblem).validationErrors === 'object'
  );
}

export function toApiProblem(error: HttpErrorResponse): ApiProblem {
  const body: unknown = error.error;
  const problem = typeof body === 'object' && body !== null ? (body as Record<string, unknown>) : {};
  return {
    status: error.status,
    title: typeof problem['title'] === 'string' ? problem['title'] : defaultTitle(error.status),
    detail: typeof problem['detail'] === 'string' ? problem['detail'] : defaultDetail(error.status),
    validationErrors: readValidationErrors(problem['validationErrors']),
  };
}

function readValidationErrors(value: unknown): Record<string, string[]> {
  if (typeof value !== 'object' || value === null) {
    return {};
  }
  const result: Record<string, string[]> = {};
  for (const [field, messages] of Object.entries(value)) {
    if (Array.isArray(messages)) {
      result[field] = messages.filter((m): m is string => typeof m === 'string');
    }
  }
  return result;
}

function defaultTitle(status: number): string {
  return status === 0 ? 'Network error' : `HTTP ${status}`;
}

// Never surface raw server output: unknown failures get a generic message.
function defaultDetail(status: number): string {
  if (status === 0) {
    return 'The server is unreachable. Check that the backend is running.';
  }
  return status >= 500 ? 'Something went wrong on the server. Please try again.' : 'The request failed.';
}
