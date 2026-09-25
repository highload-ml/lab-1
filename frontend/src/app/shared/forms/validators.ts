import { AbstractControl, ValidationErrors } from '@angular/forms';

/** Like Validators.required, but whitespace-only text also fails (mirrors the backend's @NotBlank). */
export function notBlank(control: AbstractControl<string | null>): ValidationErrors | null {
  return typeof control.value === 'string' && control.value.trim().length > 0 ? null : { required: true };
}
