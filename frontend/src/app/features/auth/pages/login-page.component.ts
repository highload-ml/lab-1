import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { isApiProblem } from '../../../core/errors/api-problem';
import { setServerError } from '../../../core/errors/form-errors';
import { NotificationService } from '../../../core/notifications/notification.service';
import { notBlank } from '../../../shared/forms/validators';
import { AuthService } from '../data-access/auth.service';
import { safeReturnUrl } from './auth-form';

/** F2: sign in by nickname. Identifies the user, does not authenticate. */
@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule],
  template: `
    <mat-card class="card">
      <mat-card-header>
        <mat-card-title>Sign in</mat-card-title>
        <mat-card-subtitle>Enter your nickname</mat-card-subtitle>
      </mat-card-header>
      <form [formGroup]="form" (ngSubmit)="submit()">
        <mat-card-content>
          <mat-form-field appearance="outline">
            <mat-label>Nickname</mat-label>
            <input matInput formControlName="nickname" autocomplete="username" />
            @if (form.controls.nickname.hasError('required')) {
              <mat-error>Nickname is required</mat-error>
            } @else if (form.controls.nickname.hasError('server')) {
              <mat-error>{{ form.controls.nickname.getError('server') }}</mat-error>
            }
          </mat-form-field>
        </mat-card-content>
        <mat-card-actions class="actions">
          <a mat-button routerLink="/register" [queryParams]="{ returnUrl: returnUrl() }">Create an account</a>
          <button mat-flat-button type="submit" [disabled]="submitting()">Sign in</button>
        </mat-card-actions>
      </form>
    </mat-card>
  `,
  styleUrl: './auth-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPageComponent {
  /** Bound from the `returnUrl` query parameter set by sessionGuard. */
  readonly returnUrl = input<string>();

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  protected readonly submitting = signal(false);
  protected readonly form = inject(NonNullableFormBuilder).group({
    nickname: ['', notBlank],
  });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.auth.signIn(this.form.getRawValue().nickname.trim()).subscribe({
      next: () => void this.router.navigateByUrl(safeReturnUrl(this.returnUrl())),
      error: (error: unknown) => {
        this.submitting.set(false);
        if (isApiProblem(error) && error.status === 404) {
          setServerError(this.form.controls.nickname, 'No user with this nickname');
        } else {
          this.notifications.problem(error);
        }
      },
    });
  }
}
