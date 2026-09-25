import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { catchError, map, throwError } from 'rxjs';

import { isApiProblem } from '../../../core/errors/api-problem';
import { NotificationService } from '../../../core/notifications/notification.service';
import { Tag, TagsService } from '../data-access/tags.service';

const TAG_NAME_MAX_LENGTH = 100;
const MAX_OPTIONS = 20;

/** Autocomplete option: an existing tag or a request to create one with the typed name. */
type TagOption = { kind: 'existing'; tag: Tag } | { kind: 'create'; name: string };

/**
 * F7: picks an existing global tag or creates a new one inline, then emits it.
 * Tags listed in `excludeIds` (already attached) are not offered.
 */
@Component({
  selector: 'app-tag-picker',
  imports: [ReactiveFormsModule, MatAutocompleteModule, MatFormFieldModule, MatIconModule, MatInputModule],
  template: `
    <mat-form-field appearance="outline" class="field" subscriptSizing="dynamic">
      <mat-label>Add a tag</mat-label>
      <mat-icon matPrefix>sell</mat-icon>
      <input
        matInput
        [formControl]="query"
        [matAutocomplete]="auto"
        [readonly]="busy()"
        placeholder="Type to search or create"
        data-testid="tag-input"
      />
      <mat-autocomplete #auto="matAutocomplete" (optionSelected)="select($event)" [displayWith]="display">
        @for (option of options(); track option.tag.id) {
          <mat-option [value]="option">{{ option.tag.name }}</mat-option>
        }
        @if (createName(); as name) {
          <mat-option [value]="{ kind: 'create', name: name }" data-testid="create-tag-option">
            <mat-icon>add</mat-icon> Create tag «{{ name }}»
          </mat-option>
        }
      </mat-autocomplete>
      @if (tooLong()) {
        <mat-hint class="warn">Tag names are at most 100 characters</mat-hint>
      }
    </mat-form-field>
  `,
  styles: `
    .field { width: 100%; max-width: 360px; }
    .warn { color: var(--mat-sys-error); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TagPickerComponent implements OnInit {
  /** Ids of tags that must not be offered (already attached). */
  readonly excludeIds = input<string[]>([]);
  readonly picked = output<Tag>();

  private readonly tagsService = inject(TagsService);
  private readonly notifications = inject(NotificationService);

  protected readonly query = new FormControl<string | TagOption>('', { nonNullable: true });
  private readonly queryValue = toSignal(this.query.valueChanges, { initialValue: '' });
  private readonly tags = signal<Tag[]>([]);
  protected readonly busy = signal(false);

  private readonly text = computed(() => {
    const value = this.queryValue();
    return typeof value === 'string' ? value.trim() : '';
  });
  protected readonly options = computed(() => {
    const text = this.text().toLowerCase();
    const excluded = new Set(this.excludeIds());
    return this.tags()
      .filter((tag) => !excluded.has(tag.id) && tag.name.toLowerCase().includes(text))
      .slice(0, MAX_OPTIONS)
      .map((tag) => ({ kind: 'existing' as const, tag }));
  });
  protected readonly tooLong = computed(() => this.text().length > TAG_NAME_MAX_LENGTH);
  /** Offer creation only for a new, valid name (tag names are unique and case-sensitive on the backend). */
  protected readonly createName = computed(() => {
    const text = this.text();
    const exists = this.tags().some((tag) => tag.name === text);
    return text && !exists && !this.tooLong() ? text : null;
  });

  protected readonly display = (option: TagOption | string | null): string =>
    typeof option === 'string' || option === null ? (option ?? '') : option.kind === 'existing' ? option.tag.name : option.name;

  ngOnInit(): void {
    this.reloadTags();
  }

  protected select(event: MatAutocompleteSelectedEvent): void {
    const option = event.option.value as TagOption;
    if (option.kind === 'existing') {
      this.emit(option.tag);
      return;
    }
    this.busy.set(true);
    this.tagsService
      .create(option.name)
      .pipe(
        // Someone created the same tag meanwhile: use theirs.
        catchError((error: unknown) =>
          isApiProblem(error) && error.status === 409
            ? this.tagsService.all().pipe(
                map((tags) => {
                  this.tags.set(tags);
                  const existing = tags.find((tag) => tag.name === option.name);
                  if (!existing) {
                    throw error;
                  }
                  return existing;
                }),
              )
            : throwError(() => error),
        ),
      )
      .subscribe({
        next: (tag) => {
          this.busy.set(false);
          if (!this.tags().some((known) => known.id === tag.id)) {
            this.tags.update((tags) => [...tags, tag].sort((a, b) => a.name.localeCompare(b.name)));
          }
          this.emit(tag);
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.query.setValue('');
          this.notifications.problem(error);
        },
      });
  }

  private emit(tag: Tag): void {
    this.query.setValue('');
    this.picked.emit(tag);
  }

  private reloadTags(): void {
    this.tagsService.all().subscribe({
      next: (tags) => this.tags.set(tags),
      error: (error: unknown) => this.notifications.problem(error),
    });
  }
}
