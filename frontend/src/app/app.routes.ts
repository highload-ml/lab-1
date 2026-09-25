import { Routes } from '@angular/router';

import { guestGuard, sessionGuard } from './core/session/session.guard';
import { ShellComponent } from './core/layout/shell.component';

export const routes: Routes = [
  // Public pages (F1 register, F2 sign in)
  {
    path: 'login',
    canActivate: [guestGuard],
    title: 'Sign in',
    loadComponent: () => import('./features/auth/pages/login-page.component').then((m) => m.LoginPageComponent),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    title: 'Create an account',
    loadComponent: () =>
      import('./features/auth/pages/register-page.component').then((m) => m.RegisterPageComponent),
  },
  // Everything else needs a signed-in user and is rendered inside the toolbar shell.
  {
    path: '',
    component: ShellComponent,
    canActivate: [sessionGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'projects' },
      {
        path: 'projects',
        loadChildren: () => import('./features/projects/projects.routes').then((m) => m.PROJECTS_ROUTES),
      },
      {
        path: 'tags',
        title: 'Tags',
        loadComponent: () => import('./features/tags/pages/tags-page.component').then((m) => m.TagsPageComponent),
      },
    ],
  },
  {
    path: '**',
    title: 'Not found',
    loadComponent: () => import('./pages/not-found-page.component').then((m) => m.NotFoundPageComponent),
  },
];
