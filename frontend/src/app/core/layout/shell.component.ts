import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';

import { SessionService } from '../session/session.service';

/** Layout for signed-in pages: toolbar with navigation, the current user and sign out. */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatToolbarModule, MatButtonModule, MatIconModule],
  template: `
    <mat-toolbar class="toolbar">
      <a class="brand" routerLink="/projects">Experiment Tracking</a>
      <nav class="nav">
        <a mat-button routerLink="/projects" routerLinkActive="active">My projects</a>
        <a mat-button routerLink="/tags" routerLinkActive="active">Tags</a>
      </nav>
      <span class="spacer"></span>
      @if (session.user(); as user) {
        <span class="user" data-testid="current-user">
          <mat-icon>person</mat-icon>{{ user.nickname }}
          <span class="role">{{ user.role }}</span>
        </span>
        <button mat-button type="button" (click)="signOut()">Sign out</button>
      }
    </mat-toolbar>
    <main class="content">
      <router-outlet />
    </main>
  `,
  styles: `
    .toolbar { gap: 16px; }
    .brand { color: inherit; text-decoration: none; font-weight: 500; }
    .nav .active { text-decoration: underline; }
    .spacer { flex: 1; }
    .user { display: inline-flex; align-items: center; gap: 6px; font-size: 14px; }
    .role { font-size: 11px; padding: 2px 6px; border-radius: 8px; background: var(--mat-sys-secondary-container); color: var(--mat-sys-on-secondary-container); }
    .content { max-width: 1100px; margin: 0 auto; padding: 24px 16px; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShellComponent {
  protected readonly session = inject(SessionService);
  private readonly router = inject(Router);

  protected signOut(): void {
    this.session.signOut();
    void this.router.navigateByUrl('/login');
  }
}
