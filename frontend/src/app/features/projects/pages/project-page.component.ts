import { ChangeDetectionStrategy, Component, OnInit, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { filter, switchMap } from 'rxjs';

import { NotificationService } from '../../../core/notifications/notification.service';
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS } from '../../../shared/pagination/page';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../../shared/ui/confirm-dialog.component';
import { PROJECT_ROLE_LABELS } from '../../../shared/ui/labels';
import { Experiment, ExperimentsService } from '../../experiments/data-access/experiments.service';
import {
  ExperimentDialogComponent,
  ExperimentDialogData,
} from '../../experiments/ui/experiment-dialog.component';
import { Tag, TagsService } from '../../tags/data-access/tags.service';
import { ProjectContext } from '../project.resolver';

/** F5: project header and its experiments (classic pagination, create / edit / delete). */
@Component({
  selector: 'app-project-page',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatChipsModule,
    MatFormFieldModule,
    MatIconModule,
    MatSelectModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatTableModule,
    MatTooltipModule,
  ],
  template: `
    <a mat-button routerLink="/projects" class="back"><mat-icon>arrow_back</mat-icon> My projects</a>

    <header class="project">
      <div>
        <h1>{{ context().project.name }}</h1>
        <p class="description">{{ context().project.description ?? 'No description' }}</p>
        <p class="meta">Created {{ context().project.createdAt | date: 'mediumDate' }}</p>
      </div>
      @if (roleLabel(); as label) {
        <mat-chip-set><mat-chip data-testid="my-role">{{ label }}</mat-chip></mat-chip-set>
      } @else {
        <span class="meta" data-testid="not-member">You are not a member of this project</span>
      }
    </header>

    <section>
      <div class="section-header">
        <h2>Experiments</h2>
        @if (canEdit()) {
          <button mat-flat-button type="button" (click)="openCreate()"><mat-icon>add</mat-icon> New experiment</button>
        }
      </div>

      <mat-form-field appearance="outline" class="filter" subscriptSizing="dynamic">
        <mat-label>Filter by tag</mat-label>
        <mat-select [value]="tagId() ?? ''" (valueChange)="filterByTag($event)" data-testid="tag-filter">
          <mat-option value="">All experiments</mat-option>
          @for (tag of allTags(); track tag.id) {
            <mat-option [value]="tag.id">{{ tag.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>

      @if (loading()) {
        <mat-progress-bar mode="indeterminate" />
      }

      @if (!loading() && total() === 0 && !failed()) {
        <p class="empty" data-testid="empty-state">{{ tagId() ? 'No experiments with this tag.' : 'No experiments yet.' }}</p>
      } @else {
        <table mat-table [dataSource]="experiments()" class="table">
          <ng-container matColumnDef="name">
            <th mat-header-cell *matHeaderCellDef>Name</th>
            <td mat-cell *matCellDef="let experiment">
              <a class="experiment-link" [routerLink]="['experiments', experiment.id]">{{ experiment.name }}</a>
              @if (experiment.description) {
                <div class="description">{{ experiment.description }}</div>
              }
            </td>
          </ng-container>
          <ng-container matColumnDef="tags">
            <th mat-header-cell *matHeaderCellDef>Tags</th>
            <td mat-cell *matCellDef="let experiment">
              @if (tagsOf(experiment).length) {
                <mat-chip-set aria-label="Experiment tags">
                  @for (tag of tagsOf(experiment); track tag.id) {
                    <mat-chip>{{ tag.name }}</mat-chip>
                  }
                </mat-chip-set>
              } @else {
                <span class="meta">—</span>
              }
            </td>
          </ng-container>
          <ng-container matColumnDef="createdAt">
            <th mat-header-cell *matHeaderCellDef>Created</th>
            <td mat-cell *matCellDef="let experiment">{{ experiment.createdAt | date: 'medium' }}</td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef><span class="visually-hidden">Actions</span></th>
            <td mat-cell *matCellDef="let experiment" class="actions">
              <button mat-icon-button type="button" matTooltip="Edit" aria-label="Edit experiment" (click)="openEdit(experiment)">
                <mat-icon>edit</mat-icon>
              </button>
              <button mat-icon-button type="button" matTooltip="Delete" aria-label="Delete experiment" (click)="confirmDelete(experiment)">
                <mat-icon>delete</mat-icon>
              </button>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns()"></tr>
          <tr mat-row *matRowDef="let row; columns: columns()" data-testid="experiment-row"></tr>
        </table>
        <mat-paginator
          [length]="total()"
          [pageIndex]="pageIndex()"
          [pageSize]="pageSize()"
          [pageSizeOptions]="pageSizeOptions"
          (page)="changePage($event)"
          aria-label="Experiments pages"
        />
      }
    </section>
  `,
  styles: `
    .back { margin-left: -12px; }
    .project { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 24px; }
    .project h1 { margin-bottom: 4px; }
    .description { color: var(--mat-sys-on-surface-variant); margin: 0; }
    .meta { color: var(--mat-sys-on-surface-variant); font-size: 13px; }
    .section-header { display: flex; align-items: center; justify-content: space-between; }
    .table { width: 100%; }
    .filter { margin: 8px 0 16px; min-width: 240px; }
    .experiment-link { color: var(--mat-sys-primary); font-weight: 500; text-decoration: none; }
    .experiment-link:hover { text-decoration: underline; }
    .actions { text-align: right; white-space: nowrap; }
    .empty { padding: 32px 0; text-align: center; color: var(--mat-sys-on-surface-variant); }
    .visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProjectPageComponent implements OnInit {
  /** Resolved by projectResolver, bound through withComponentInputBinding. */
  readonly context = input.required<ProjectContext>();
  /** `?tagId=` query parameter: show only experiments with this tag. */
  readonly tagId = input<string>();

  private readonly experimentsService = inject(ExperimentsService);
  private readonly dialog = inject(MatDialog);
  private readonly notifications = inject(NotificationService);
  private readonly tagsService = inject(TagsService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly pageSizeOptions = [...PAGE_SIZE_OPTIONS];
  protected readonly allTags = signal<Tag[]>([]);
  protected readonly experiments = signal<Experiment[]>([]);
  protected readonly total = signal(0);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal<number>(DEFAULT_PAGE_SIZE);
  protected readonly loading = signal(false);
  protected readonly failed = signal(false);

  protected readonly roleLabel = computed(() => {
    const role = this.context().myRole;
    return role ? PROJECT_ROLE_LABELS[role] : null;
  });
  /** UI hint only: there is no authorization in the backend. Viewers and non-members get a read-only view. */
  protected readonly canEdit = computed(() => {
    const role = this.context().myRole;
    return role === 'OWNER' || role === 'EDITOR';
  });
  protected readonly columns = computed(() =>
    this.canEdit() ? ['name', 'tags', 'createdAt', 'actions'] : ['name', 'tags', 'createdAt'],
  );

  constructor() {
    // Reload when the project or the tag filter changes (the component is reused for both).
    effect(() => {
      this.context();
      this.tagId();
      untracked(() => {
        this.pageIndex.set(0);
        this.load();
      });
    });
  }

  ngOnInit(): void {
    this.tagsService.all().subscribe({
      next: (tags) => this.allTags.set(tags),
      error: (error: unknown) => this.notifications.problem(error),
    });
  }

  protected filterByTag(tagId: string): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tagId: tagId || null },
      queryParamsHandling: 'merge',
    });
  }

  protected tagsOf(experiment: Experiment): Experiment['tags'] {
    return experiment.tags;
  }

  protected changePage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }

  protected openCreate(): void {
    this.openDialog({ projectId: this.projectId() }).subscribe(() => {
      this.notifications.info('Experiment created');
      this.load();
    });
  }

  protected openEdit(experiment: Experiment): void {
    this.openDialog({ projectId: this.projectId(), experiment }).subscribe(() => {
      this.notifications.info('Experiment saved');
      this.load();
    });
  }

  protected confirmDelete(experiment: Experiment): void {
    this.dialog
      .open<ConfirmDialogComponent, ConfirmDialogData, boolean>(ConfirmDialogComponent, {
        data: {
          title: 'Delete experiment?',
          message: `"${experiment.name}" and its tag assignments will be deleted.`,
          confirmLabel: 'Delete',
        },
      })
      .afterClosed()
      .pipe(
        filter((confirmed) => confirmed === true),
        switchMap(() => this.experimentsService.delete(this.projectId(), experiment.id)),
      )
      .subscribe({
        next: () => {
          this.notifications.info('Experiment deleted');
          // Step back if the deleted row was the only one on the last page.
          if (this.experiments().length === 1 && this.pageIndex() > 0) {
            this.pageIndex.update((page) => page - 1);
          }
          this.load();
        },
        error: (error: unknown) => {
          this.notifications.problem(error);
          this.load();
        },
      });
  }

  private openDialog(data: ExperimentDialogData) {
    return this.dialog
      .open<ExperimentDialogComponent, ExperimentDialogData, Experiment>(ExperimentDialogComponent, { data })
      .afterClosed()
      .pipe(filter((experiment): experiment is Experiment => experiment !== undefined));
  }

  private projectId(): string {
    return this.context().project.id;
  }

  private load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.experimentsService.page(this.projectId(), this.pageIndex(), this.pageSize(), this.tagId() || undefined).subscribe({
      next: (page) => {
        this.experiments.set(page.items);
        this.total.set(page.total);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.failed.set(true);
        this.notifications.problem(error);
      },
    });
  }
}
