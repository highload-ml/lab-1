import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { isApiProblem } from '../../../core/errors/api-problem';
import { applyServerErrors, setServerError } from '../../../core/errors/form-errors';
import { NotificationService } from '../../../core/notifications/notification.service';
import { notBlank } from '../../../shared/forms/validators';
import { MyProjectsService } from '../data-access/my-projects.service';

/** F4: creates a project owned by the signed-in user; closes with the new project id. */
@Component({
  selector: 'app-new-project-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>New project</h2>
    <form [formGroup]="form" (ngSubmit)="submit()">
      <mat-dialog-content class="fields">
        <mat-form-field appearance="outline">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" cdkFocusInitial />
          @if (form.controls.name.hasError('required')) {
            <mat-error>Name is required</mat-error>
          } @else if (form.controls.name.hasError('maxlength')) {
            <mat-error>At most 100 characters</mat-error>
          } @else if (form.controls.name.hasError('server')) {
            <mat-error>{{ form.controls.name.getError('server') }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Description</mat-label>
          <textarea matInput formControlName="description" rows="3"></textarea>
          @if (form.controls.description.hasError('maxlength')) {
            <mat-error>At most 1000 characters</mat-error>
          } @else if (form.controls.description.hasError('server')) {
            <mat-error>{{ form.controls.description.getError('server') }}</mat-error>
          }
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancel</button>
        <button mat-flat-button type="submit" [disabled]="submitting()">Create</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `.fields { display: flex; flex-direction: column; gap: 8px; min-width: min(420px, 80vw); }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NewProjectDialogComponent {
  private readonly projects = inject(MyProjectsService);
  private readonly dialogRef = inject<MatDialogRef<NewProjectDialogComponent, string>>(MatDialogRef);
  private readonly notifications = inject(NotificationService);

  protected readonly submitting = signal(false);
  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [notBlank, Validators.maxLength(100)]],
    description: ['', Validators.maxLength(1000)],
  });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    const { name, description } = this.form.getRawValue();
    this.projects.create({ name: name.trim(), description: description.trim() || null }).subscribe({
      next: (projectId) => this.dialogRef.close(projectId),
      error: (error: unknown) => {
        this.submitting.set(false);
        if (isApiProblem(error) && error.status === 409) {
          setServerError(this.form.controls.name, 'A project with this name already exists');
        } else if (!(isApiProblem(error) && applyServerErrors(this.form, error))) {
          this.notifications.problem(error);
        }
      },
    });
  }
}
