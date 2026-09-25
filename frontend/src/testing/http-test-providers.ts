import { EnvironmentProviders, Provider } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { provideApi } from '../app/api';
import { problemInterceptor } from '../app/core/errors/problem.interceptor';

/** Same HTTP setup as the app (relative URLs, ApiProblem errors) with a mocked backend. */
export function provideHttpTesting(): (Provider | EnvironmentProviders)[] {
  return [provideHttpClient(withInterceptors([problemInterceptor])), provideHttpClientTesting(), provideApi('')];
}

export function problemBody(status: number, detail: string, validationErrors?: Record<string, string[]>): object {
  return { title: `HTTP ${status}`, status, detail, ...(validationErrors ? { validationErrors } : {}) };
}
