import { ChangeDetectionStrategy, Component, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';

import { NotificationService } from '../../../core/notifications/notification.service';
import { Tag } from '../../tags/data-access/tags.service';
import { TagPickerComponent } from '../../tags/ui/tag-picker.component';
import { Experiment, ExperimentsService, TagRef } from '../data-access/experiments.service';
import { ExperimentContext } from '../experiment.resolver';

/** F7: experiment details with its tags (attach / detach). Runs and metrics (F8) are added by developer 2. */
@Component({
  selector: 'app-experiment-page',
  imports: [DatePipe, RouterLink, MatButtonModule, MatChipsModule, MatIconModule, TagPickerComponent],
  template: `
    <a mat-button [routerLink]="['/projects', context().project.id]" class="back">
      <mat-icon>arrow_back</mat-icon> {{ context().project.name }}
    </a>

    <header>
      <h1>{{ experiment().name }}</h1>
      <p class="description">{{ experiment().description ?? 'No description' }}</p>
      <p class="meta">Created {{ experiment().createdAt | date: 'medium' }}</p>
    </header>

    <section class="tags">
      <h2>Tags</h2>
      @if (experiment().tags.length) {
        <mat-chip-set aria-label="Experiment tags">
          @for (tag of experiment().tags; track tag.id) {
            <mat-chip [removable]="canEdit()" (removed)="detach(tag)" [disabled]="pending()" data-testid="tag-chip">
              {{ tag.name }}
              @if (canEdit()) {
                <button matChipRemove [attr.aria-label]="'Remove tag ' + tag.name"><mat-icon>cancel</mat-icon></button>
              }
            </mat-chip>
          }
        </mat-chip-set>
      } @else {
        <p class="meta" data-testid="no-tags">No tags yet.</p>
      }
      @if (canEdit()) {
        <app-tag-picker [excludeIds]="tagIds()" (picked)="attach($event)" />
      }
    </section>

    <section class="runs">
      <h2>Runs and metrics</h2>
      <p class="meta">Runs of this experiment and their metric charts will appear here.</p>
    </section>
  `,
  styles: `
    .back { margin-left: -12px; }
    header h1 { margin-bottom: 4px; }
    .description { color: var(--mat-sys-on-surface-variant); margin: 0; }
    .meta { color: var(--mat-sys-on-surface-variant); font-size: 13px; }
    .tags { display: flex; flex-direction: column; gap: 12px; margin-top: 24px; }
    .tags h2, .runs h2 { margin: 0; }
    .runs { margin-top: 32px; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExperimentPageComponent {
  /** Resolved by experimentResolver, bound through withComponentInputBinding. */
  readonly context = input.required<ExperimentContext>();

  private readonly experiments = inject(ExperimentsService);
  private readonly notifications = inject(NotificationService);

  /** Starts from the resolved experiment, then follows attach / detach without re-running the resolver. */
  protected readonly experiment = linkedSignal<Experiment>(() => this.context().experiment);
  protected readonly pending = signal(false);
  protected readonly tagIds = computed(() => this.experiment().tags.map((tag) => tag.id));
  /** UI hint only: there is no authorization in the backend. */
  protected readonly canEdit = computed(() => {
    const role = this.context().myRole;
    return role === 'OWNER' || role === 'EDITOR';
  });

  protected attach(tag: Tag): void {
    this.pending.set(true);
    const { projectId, id } = this.experiment();
    this.experiments.attachTag(projectId, id, tag.id).subscribe({
      next: (updated) => {
        this.pending.set(false);
        this.experiment.set(updated);
      },
      error: (error: unknown) => {
        this.pending.set(false);
        this.notifications.problem(error);
        this.refresh();
      },
    });
  }

  protected detach(tag: TagRef): void {
    this.pending.set(true);
    const { projectId, id } = this.experiment();
    this.experiments.detachTag(projectId, id, tag.id).subscribe({
      next: () => {
        this.pending.set(false);
        this.experiment.update((experiment) => ({
          ...experiment,
          tags: experiment.tags.filter((attached) => attached.id !== tag.id),
        }));
      },
      error: (error: unknown) => {
        this.pending.set(false);
        this.notifications.problem(error);
        this.refresh();
      },
    });
  }

  /** After a failed change (e.g. the tag was deleted meanwhile) show the backend state again. */
  private refresh(): void {
    const { projectId, id } = this.experiment();
    this.experiments.get(projectId, id).subscribe({
      next: (experiment) => this.experiment.set(experiment),
      error: (error: unknown) => this.notifications.problem(error),
    });
  }
}
