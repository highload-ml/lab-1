import { Routes } from '@angular/router';

import { experimentResolver } from '../experiments/experiment.resolver';
import { projectResolver } from './project.resolver';

/** Lazy-loaded under /projects. */
export const PROJECTS_ROUTES: Routes = [
  {
    path: '',
    title: 'My projects',
    loadComponent: () => import('./pages/projects-page.component').then((m) => m.ProjectsPageComponent),
  },
  {
    path: ':projectId',
    title: 'Project',
    resolve: { context: projectResolver },
    // Keep the resolved project when only query params change (e.g. a tag filter).
    runGuardsAndResolvers: 'pathParamsChange',
    loadComponent: () => import('./pages/project-page.component').then((m) => m.ProjectPageComponent),
  },
  {
    path: ':projectId/experiments/:experimentId',
    title: 'Experiment',
    resolve: { context: experimentResolver },
    loadComponent: () =>
      import('../experiments/pages/experiment-page.component').then((m) => m.ExperimentPageComponent),
  },
];
