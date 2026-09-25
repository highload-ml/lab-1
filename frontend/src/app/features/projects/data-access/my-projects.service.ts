import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { ProjectsService, UserProjectResponse, UsersService } from '../../../api';
import { SessionService } from '../../../core/session/session.service';
import { Page, toPage } from '../../../shared/pagination/page';

export type ProjectRole = 'OWNER' | 'EDITOR' | 'VIEWER';

/** A project as the signed-in user sees it in "My projects". */
export interface MyProject {
  id: string;
  name: string;
  description: string | null;
  createdAt: string;
  role: ProjectRole;
  joinedAt: string;
}

export interface NewProject {
  name: string;
  description: string | null;
}

@Injectable({ providedIn: 'root' })
export class MyProjectsService {
  private readonly users = inject(UsersService);
  private readonly projects = inject(ProjectsService);
  private readonly session = inject(SessionService);

  /** Classic page of the signed-in user's projects, newest membership first (sorted by the backend). */
  page(page: number, size: number): Observable<Page<MyProject>> {
    return this.users.findProjects(this.session.requireUserId(), page, size, 'response').pipe(
      map(toPage),
      map((result) => ({ total: result.total, items: result.items.map(toMyProject) })),
    );
  }

  /** Creates a project owned by the signed-in user; returns the new project id. */
  create(project: NewProject): Observable<string> {
    return this.projects
      .create({
        name: project.name,
        description: project.description ?? undefined,
        ownerId: this.session.requireUserId(),
      })
      .pipe(
        map((created) => {
          if (!created.id) {
            throw new Error('Incomplete project in the backend response');
          }
          return created.id;
        }),
      );
  }
}

function toMyProject(response: UserProjectResponse): MyProject {
  if (!response.projectId || !response.name || !response.role || !response.createdAt || !response.joinedAt) {
    throw new Error('Incomplete project in the backend response');
  }
  return {
    id: response.projectId,
    name: response.name,
    description: response.description ?? null,
    createdAt: response.createdAt,
    role: response.role,
    joinedAt: response.joinedAt,
  };
}
