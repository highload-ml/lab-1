import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ApiProblem } from './api-problem';
import { applyServerErrors, setServerError } from './form-errors';

function problem(validationErrors: Record<string, string[]>): ApiProblem {
  return { status: 400, title: 'Bad Request', detail: 'Request validation failed', validationErrors };
}

describe('form errors', () => {
  function form(): FormGroup {
    return new FormGroup({
      nickname: new FormControl('al', Validators.minLength(1)),
      role: new FormControl('ADMIN'),
    });
  }

  it('attaches server messages to matching controls', () => {
    const f = form();

    const applied = applyServerErrors(f, problem({ nickname: ['too short', 'invalid characters'] }));

    expect(applied).toBe(true);
    expect(f.get('nickname')?.errors).toEqual({ server: 'too short; invalid characters' });
    expect(f.get('nickname')?.touched).toBe(true);
    expect(f.get('role')?.errors).toBeNull();
  });

  it('reports false when no field matches, so the caller shows the detail instead', () => {
    expect(applyServerErrors(form(), problem({ unknownField: ['x'] }))).toBe(false);
    expect(applyServerErrors(form(), problem({}))).toBe(false);
  });

  it('setServerError keeps existing client-side errors', () => {
    const control = new FormControl('', Validators.required);

    setServerError(control, 'Nickname is already taken');

    expect(control.errors).toEqual({ required: true, server: 'Nickname is already taken' });
  });
});
