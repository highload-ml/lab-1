import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Observable } from 'rxjs';

import { isApiProblem } from '../../../core/errors/api-problem';
import { applyServerErrors, setServerError } from '../../../core/errors/form-errors';
import { NotificationService } from '../../../core/notifications/notification.service';
import { notBlank } from '../../../shared/forms/validators';
import { Experiment, ExperimentsService } from '../data-access/experiments.service';

export interface ExperimentDialogData {
  projectId: string;
  /** Edit this experiment; create a new one when absent. */
  experiment?: Experiment;
}

/** F5: create or edit an experiment of a project; closes with the saved experiment. */
@Component({
  selector: 'app-experiment-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>{{ editing ? 'Edit experiment' : 'New experiment' }}</h2>
    <form [formGroup]="form" (ngSubmit)="submit()">
      <mat-dialog-content class="fields">
        <mat-form-field appearance="outline">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" cdkFocusInitial />
          <mat-hint>Unique within the project</mat-hint>
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
        <button mat-flat-button type="submit" [disabled]="submitting()">{{ editing ? 'Save' : 'Create' }}</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `.fields { display: flex; flex-direction: column; gap: 8px; min-width: min(420px, 80vw); }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExperimentDialogComponent {
  private readonly data = inject<ExperimentDialogData>(MAT_DIALOG_DATA);
  private readonly experiments = inject(ExperimentsService);
  private readonly dialogRef = inject<MatDialogRef<ExperimentDialogComponent, Experiment>>(MatDialogRef);
  private readonly notifications = inject(NotificationService);

  protected readonly editing = this.data.experiment !== undefined;
  protected readonly submitting = signal(false);
  protected readonly form = inject(NonNullableFormBuilder).group({
    name: [this.data.experiment?.name ?? '', [notBlank, Validators.maxLength(100)]],
    description: [this.data.experiment?.description ?? '', Validators.maxLength(1000)],
  });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    const { name, description } = this.form.getRawValue();
    const draft = { name: name.trim(), description: description.trim() || null };
    const request: Observable<Experiment> = this.data.experiment
      ? this.experiments.update(this.data.projectId, this.data.experiment.id, draft)
      : this.experiments.create(this.data.projectId, draft);
    request.subscribe({
      next: (experiment) => this.dialogRef.close(experiment),
      error: (error: unknown) => {
        this.submitting.set(false);
        if (isApiProblem(error) && error.status === 409) {
          setServerError(this.form.controls.name, 'An experiment with this name already exists in the project');
        } else if (!(isApiProblem(error) && applyServerErrors(this.form, error))) {
          this.notifications.problem(error);
        }
      },
    });
  }
}
