import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { isApiProblem } from '../../../core/errors/api-problem';
import { applyServerErrors, setServerError } from '../../../core/errors/form-errors';
import { NotificationService } from '../../../core/notifications/notification.service';
import { notBlank } from '../../../shared/forms/validators';
import { UserRole } from '../../../core/session/session.service';
import { USER_ROLE_LABELS } from '../../../shared/ui/labels';
import { AuthService } from '../data-access/auth.service';
import { NICKNAME_PATTERN, safeReturnUrl } from './auth-form';

/** F1: registration by nickname only. */
@Component({
  selector: 'app-register-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  template: `
    <mat-card class="card">
      <mat-card-header>
        <mat-card-title>Create an account</mat-card-title>
        <mat-card-subtitle>Only a nickname is needed</mat-card-subtitle>
      </mat-card-header>
      <form [formGroup]="form" (ngSubmit)="submit()">
        <mat-card-content>
          <mat-form-field appearance="outline">
            <mat-label>Nickname</mat-label>
            <input matInput formControlName="nickname" autocomplete="username" />
            <mat-hint>3–50 characters: latin letters, digits, _ . -</mat-hint>
            @if (form.controls.nickname.hasError('required')) {
              <mat-error>Nickname is required</mat-error>
            } @else if (form.controls.nickname.hasError('minlength') || form.controls.nickname.hasError('maxlength')) {
              <mat-error>Nickname must be 3–50 characters</mat-error>
            } @else if (form.controls.nickname.hasError('pattern')) {
              <mat-error>Only latin letters, digits, _ . - are allowed</mat-error>
            } @else if (form.controls.nickname.hasError('server')) {
              <mat-error>{{ form.controls.nickname.getError('server') }}</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Role</mat-label>
            <mat-select formControlName="role">
              @for (role of roles; track role) {
                <mat-option [value]="role">{{ roleLabels[role] }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        </mat-card-content>
        <mat-card-actions class="actions">
          <a mat-button routerLink="/login">I already have an account</a>
          <button mat-flat-button type="submit" [disabled]="submitting()">Register</button>
        </mat-card-actions>
      </form>
    </mat-card>
  `,
  styleUrl: './auth-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterPageComponent {
  /** Bound from the `returnUrl` query parameter set by sessionGuard. */
  readonly returnUrl = input<string>();

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  protected readonly roles: UserRole[] = ['ML_ENGINEER', 'REVIEWER', 'ADMIN'];
  protected readonly roleLabels = USER_ROLE_LABELS;
  protected readonly submitting = signal(false);
  protected readonly form = inject(NonNullableFormBuilder).group({
    nickname: ['', [notBlank, Validators.minLength(3), Validators.maxLength(50), Validators.pattern(NICKNAME_PATTERN)]],
    role: ['ML_ENGINEER' as UserRole, Validators.required],
  });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    const { nickname, role } = this.form.getRawValue();
    this.auth.register(nickname.trim(), role).subscribe({
      next: () => void this.router.navigateByUrl(safeReturnUrl(this.returnUrl())),
      error: (error: unknown) => {
        this.submitting.set(false);
        if (isApiProblem(error) && error.status === 409) {
          setServerError(this.form.controls.nickname, 'Nickname is already taken');
        } else if (!(isApiProblem(error) && applyServerErrors(this.form, error))) {
          this.notifications.problem(error);
        }
      },
    });
  }
}
