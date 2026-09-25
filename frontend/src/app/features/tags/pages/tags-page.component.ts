import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';

import { isApiProblem } from '../../../core/errors/api-problem';
import { applyServerErrors, setServerError } from '../../../core/errors/form-errors';
import { NotificationService } from '../../../core/notifications/notification.service';
import { notBlank } from '../../../shared/forms/validators';
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS } from '../../../shared/pagination/page';
import { Tag, TagsService } from '../data-access/tags.service';

/** F6: global tag dictionary (classic pagination, sorted by name) with inline creation. */
@Component({
  selector: 'app-tags-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatTableModule,
  ],
  template: `
    <h1>Tags</h1>
    <p class="meta">Tags are shared by all projects. Attach them to experiments on the experiment page.</p>

    <form [formGroup]="form" (ngSubmit)="create()" class="create">
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>New tag</mat-label>
        <input matInput formControlName="name" />
        @if (form.controls.name.hasError('maxlength')) {
          <mat-error>At most 100 characters</mat-error>
        } @else if (form.controls.name.hasError('server')) {
          <mat-error>{{ form.controls.name.getError('server') }}</mat-error>
        }
      </mat-form-field>
      <button mat-flat-button type="submit" [disabled]="submitting()"><mat-icon>add</mat-icon> Create</button>
    </form>

    @if (loading()) {
      <mat-progress-bar mode="indeterminate" />
    }

    @if (!loading() && total() === 0 && !failed()) {
      <p class="empty" data-testid="empty-state">No tags yet.</p>
    } @else {
      <table mat-table [dataSource]="tags()" class="table">
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Name</th>
          <td mat-cell *matCellDef="let tag">{{ tag.name }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns" data-testid="tag-row"></tr>
      </table>
      <mat-paginator
        [length]="total()"
        [pageIndex]="pageIndex()"
        [pageSize]="pageSize()"
        [pageSizeOptions]="pageSizeOptions"
        (page)="changePage($event)"
        aria-label="Tags pages"
      />
    }
  `,
  styles: `
    .meta { color: var(--mat-sys-on-surface-variant); }
    .create { display: flex; align-items: flex-start; gap: 12px; margin: 16px 0; }
    .create button { margin-top: 8px; }
    .table { width: 100%; }
    .empty { padding: 32px 0; text-align: center; color: var(--mat-sys-on-surface-variant); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TagsPageComponent implements OnInit {
  private readonly tagsService = inject(TagsService);
  private readonly notifications = inject(NotificationService);

  protected readonly columns = ['name'];
  protected readonly pageSizeOptions = [...PAGE_SIZE_OPTIONS];
  protected readonly tags = signal<Tag[]>([]);
  protected readonly total = signal(0);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal<number>(DEFAULT_PAGE_SIZE);
  protected readonly loading = signal(false);
  protected readonly failed = signal(false);
  protected readonly submitting = signal(false);
  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [notBlank, Validators.maxLength(100)]],
  });

  ngOnInit(): void {
    this.load();
  }

  protected changePage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }

  protected create(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    const name = this.form.getRawValue().name.trim();
    this.tagsService.create(name).subscribe({
      next: (tag) => {
        this.submitting.set(false);
        this.form.reset();
        this.notifications.info(`Tag «${tag.name}» created`);
        this.load();
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        if (isApiProblem(error) && error.status === 409) {
          setServerError(this.form.controls.name, 'This tag already exists');
        } else if (!(isApiProblem(error) && applyServerErrors(this.form, error))) {
          this.notifications.problem(error);
        }
      },
    });
  }

  private load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.tagsService.page(this.pageIndex(), this.pageSize()).subscribe({
      next: (page) => {
        this.tags.set(page.items);
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
