import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of, throwError } from 'rxjs';

import { ProjectMembersService, ProjectsService } from '../../../api';
import { isApiProblem } from '../../../core/errors/api-problem';
import { SessionService } from '../../../core/session/session.service';
import { ProjectRole } from './my-projects.service';

export interface ProjectDetails {
  id: string;
  name: string;
  description: string | null;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class ProjectService {
  private readonly projects = inject(ProjectsService);
  private readonly members = inject(ProjectMembersService);
  private readonly session = inject(SessionService);

  get(projectId: string): Observable<ProjectDetails> {
    return this.projects.getById(projectId).pipe(
      map((project) => {
        if (!project.id || !project.name || !project.createdAt) {
          throw new Error('Incomplete project in the backend response');
        }
        return {
          id: project.id,
          name: project.name,
          description: project.description ?? null,
          createdAt: project.createdAt,
        };
      }),
    );
  }

  /** Role of the signed-in user in the project, or null if they are not a member. */
  myRole(projectId: string): Observable<ProjectRole | null> {
    return this.members.getMember(projectId, this.session.requireUserId()).pipe(
      map((member) => member.role ?? null),
      catchError((error: unknown) => (isApiProblem(error) && error.status === 404 ? of(null) : throwError(() => error))),
    );
  }
}
