import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiProblem, isApiProblem } from '../errors/api-problem';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly snackBar = inject(MatSnackBar);

  info(message: string): void {
    this.snackBar.open(message, 'OK', { duration: 3000 });
  }

  error(message: string): void {
    this.snackBar.open(message, 'Close', { duration: 6000, panelClass: 'snack-error' });
  }

  /** Shows the backend's `detail`; anything that is not an {@link ApiProblem} gets a generic message. */
  problem(error: unknown): void {
    this.error(isApiProblem(error) ? (error as ApiProblem).detail : 'Unexpected error. Please try again.');
  }
}
