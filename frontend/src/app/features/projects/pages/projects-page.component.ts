import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';

import { NotificationService } from '../../../core/notifications/notification.service';
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS } from '../../../shared/pagination/page';
import { PROJECT_ROLE_LABELS } from '../../../shared/ui/labels';
import { MyProject, MyProjectsService } from '../data-access/my-projects.service';
import { NewProjectDialogComponent } from '../ui/new-project-dialog.component';

/** F3 "My projects" (classic pagination with X-Total-Count) and F4 "New project". */
@Component({
  selector: 'app-projects-page',
  imports: [DatePipe, RouterLink, MatButtonModule, MatChipsModule, MatIconModule, MatPaginatorModule, MatProgressBarModule, MatTableModule],
  template: `
    <header class="header">
      <h1>My projects</h1>
      <button mat-flat-button type="button" (click)="openNewProject()">
        <mat-icon>add</mat-icon> New project
      </button>
    </header>

    @if (loading()) {
      <mat-progress-bar mode="indeterminate" />
    }

    @if (!loading() && total() === 0 && !failed()) {
      <section class="empty" data-testid="empty-state">
        <p>You have no projects yet.</p>
        <button mat-stroked-button type="button" (click)="openNewProject()">Create your first project</button>
      </section>
    } @else {
      <table mat-table [dataSource]="projects()" class="table">
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Name</th>
          <td mat-cell *matCellDef="let project">
            <a class="project-link" [routerLink]="['/projects', project.id]">{{ project.name }}</a>
          </td>
        </ng-container>
        <ng-container matColumnDef="description">
          <th mat-header-cell *matHeaderCellDef>Description</th>
          <td mat-cell *matCellDef="let project" class="description">{{ project.description ?? '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="role">
          <th mat-header-cell *matHeaderCellDef>My role</th>
          <td mat-cell *matCellDef="let project">
            <mat-chip [class]="'role-' + project.role.toLowerCase()">{{ roleLabel(project) }}</mat-chip>
          </td>
        </ng-container>
        <ng-container matColumnDef="joinedAt">
          <th mat-header-cell *matHeaderCellDef>Joined</th>
          <td mat-cell *matCellDef="let project">{{ project.joinedAt | date: 'medium' }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns" data-testid="project-row"></tr>
      </table>
      <mat-paginator
        [length]="total()"
        [pageIndex]="pageIndex()"
        [pageSize]="pageSize()"
        [pageSizeOptions]="pageSizeOptions"
        (page)="changePage($event)"
        aria-label="Projects pages"
      />
    }
  `,
  styles: `
    .header { display: flex; align-items: center; justify-content: space-between; }
    .table { width: 100%; }
    .project-link { color: var(--mat-sys-primary); font-weight: 500; text-decoration: none; }
    .project-link:hover { text-decoration: underline; }
    .description { color: var(--mat-sys-on-surface-variant); max-width: 420px; }
    .empty { text-align: center; padding: 48px 0; }
    .role-owner { --mat-chip-elevated-container-color: var(--mat-sys-primary-container); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProjectsPageComponent implements OnInit {
  private readonly myProjects = inject(MyProjectsService);
  private readonly dialog = inject(MatDialog);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);

  protected readonly columns = ['name', 'description', 'role', 'joinedAt'];
  protected readonly pageSizeOptions = [...PAGE_SIZE_OPTIONS];

  protected readonly projects = signal<MyProject[]>([]);
  protected readonly total = signal(0);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal<number>(DEFAULT_PAGE_SIZE);
  protected readonly loading = signal(false);
  protected readonly failed = signal(false);

  ngOnInit(): void {
    this.load();
  }

  protected roleLabel(project: MyProject): string {
    return PROJECT_ROLE_LABELS[project.role];
  }

  protected changePage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }

  protected openNewProject(): void {
    this.dialog
      .open<NewProjectDialogComponent, void, string>(NewProjectDialogComponent)
      .afterClosed()
      .subscribe((projectId) => {
        if (projectId) {
          this.notifications.info('Project created');
          void this.router.navigate(['/projects', projectId]);
        }
      });
  }

  private load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.myProjects.page(this.pageIndex(), this.pageSize()).subscribe({
      next: (page) => {
        this.projects.set(page.items);
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
