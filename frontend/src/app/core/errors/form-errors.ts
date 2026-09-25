import { AbstractControl, FormGroup } from '@angular/forms';
import { ApiProblem } from './api-problem';

/**
 * Puts the backend's `validationErrors` on the matching form controls as `{ server: message }`.
 * @returns true if at least one error was attached to a control, false if the caller must show it elsewhere
 */
export function applyServerErrors(form: FormGroup, problem: ApiProblem): boolean {
  let applied = false;
  for (const [field, messages] of Object.entries(problem.validationErrors)) {
    const control = form.get(field);
    if (control && messages.length > 0) {
      setServerError(control, messages.join('; '));
      applied = true;
    }
  }
  return applied;
}

/** Marks a single control, e.g. a 409 "name already taken" on the name field. */
export function setServerError(control: AbstractControl, message: string): void {
  control.setErrors({ ...(control.errors ?? {}), server: message });
  control.markAsTouched();
}
