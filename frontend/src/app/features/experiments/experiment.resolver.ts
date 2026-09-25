import { inject } from '@angular/core';
import { RedirectCommand, ResolveFn, Router } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';

import { isApiProblem } from '../../core/errors/api-problem';
import { NotificationService } from '../../core/notifications/notification.service';
import { ProjectRole } from '../projects/data-access/my-projects.service';
import { ProjectDetails, ProjectService } from '../projects/data-access/project.service';
import { Experiment, ExperimentsService } from './data-access/experiments.service';

export interface ExperimentContext {
  project: ProjectDetails;
  experiment: Experiment;
  myRole: ProjectRole | null;
}

/** Loads the experiment with its project before the page renders; unknown ids show the not-found page. */
export const experimentResolver: ResolveFn<ExperimentContext> = (route) => {
  const router = inject(Router);
  const notifications = inject(NotificationService);
  const projects = inject(ProjectService);
  const experiments = inject(ExperimentsService);
  const projectId = route.paramMap.get('projectId') ?? '';
  const experimentId = route.paramMap.get('experimentId') ?? '';

  return forkJoin({
    project: projects.get(projectId),
    experiment: experiments.get(projectId, experimentId),
    myRole: projects.myRole(projectId),
  }).pipe(
    catchError((error: unknown) => {
      const notFound = isApiProblem(error) && (error.status === 404 || error.status === 400);
      if (!notFound) {
        notifications.problem(error);
      }
      return of(
        new RedirectCommand(router.parseUrl(notFound ? '/not-found' : `/projects/${projectId}`), {
          skipLocationChange: notFound,
        }),
      );
    }),
  );
};
