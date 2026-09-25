import { inject } from '@angular/core';
import { RedirectCommand, ResolveFn, Router } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';

import { isApiProblem } from '../../core/errors/api-problem';
import { NotificationService } from '../../core/notifications/notification.service';
import { ProjectRole } from './data-access/my-projects.service';
import { ProjectDetails, ProjectService } from './data-access/project.service';

export interface ProjectContext {
  project: ProjectDetails;
  /** null when the signed-in user is not a member (there is no authorization, so the page still opens). */
  myRole: ProjectRole | null;
}

/** Loads the project before the page renders; an unknown project shows the not-found page. */
export const projectResolver: ResolveFn<ProjectContext> = (route) => {
  const router = inject(Router);
  const notifications = inject(NotificationService);
  const projects = inject(ProjectService);
  const projectId = route.paramMap.get('projectId') ?? '';

  return forkJoin({ project: projects.get(projectId), myRole: projects.myRole(projectId) }).pipe(
    catchError((error: unknown) => {
      const notFound = isApiProblem(error) && (error.status === 404 || error.status === 400);
      if (!notFound) {
        notifications.problem(error);
      }
      return of(new RedirectCommand(router.parseUrl(notFound ? '/not-found' : '/projects'), { skipLocationChange: notFound }));
    }),
  );
};
