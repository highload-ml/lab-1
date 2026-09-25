import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { ExperimentResponse, ExperimentsService as ExperimentsApi } from '../../../api';
import { Page, toPage } from '../../../shared/pagination/page';

export interface TagRef {
  id: string;
  name: string;
}

export interface Experiment {
  id: string;
  projectId: string;
  name: string;
  description: string | null;
  createdAt: string;
  /** Sorted by name (backend order). */
  tags: TagRef[];
}

export interface ExperimentDraft {
  name: string;
  description: string | null;
}

@Injectable({ providedIn: 'root' })
export class ExperimentsService {
  private readonly api = inject(ExperimentsApi);

  /** Classic page of the project's experiments, oldest first; `tagId` keeps only experiments with that tag. */
  page(projectId: string, page: number, size: number, tagId?: string): Observable<Page<Experiment>> {
    return this.api.findAll(projectId, tagId, page, size, 'response').pipe(
      map(toPage),
      map((result) => ({ total: result.total, items: result.items.map(toExperiment) })),
    );
  }

  get(projectId: string, experimentId: string): Observable<Experiment> {
    return this.api.getById(projectId, experimentId).pipe(map(toExperiment));
  }

  create(projectId: string, draft: ExperimentDraft): Observable<Experiment> {
    return this.api.create(projectId, toRequest(draft)).pipe(map(toExperiment));
  }

  update(projectId: string, experimentId: string, draft: ExperimentDraft): Observable<Experiment> {
    return this.api.update(projectId, experimentId, toRequest(draft)).pipe(map(toExperiment));
  }

  delete(projectId: string, experimentId: string): Observable<void> {
    return this.api._delete(projectId, experimentId).pipe(map(() => undefined));
  }

  /** Idempotent on the backend; returns the experiment with its updated tags. */
  attachTag(projectId: string, experimentId: string, tagId: string): Observable<Experiment> {
    return this.api.addTag(projectId, experimentId, tagId).pipe(map(toExperiment));
  }

  /** Idempotent on the backend (204 even if the tag was not attached). */
  detachTag(projectId: string, experimentId: string, tagId: string): Observable<void> {
    return this.api.removeTag(projectId, experimentId, tagId).pipe(map(() => undefined));
  }
}

function toRequest(draft: ExperimentDraft): { name: string; description?: string } {
  return { name: draft.name, description: draft.description ?? undefined };
}

export function toExperiment(response: ExperimentResponse): Experiment {
  if (!response.id || !response.projectId || !response.name || !response.createdAt) {
    throw new Error('Incomplete experiment in the backend response');
  }
  return {
    id: response.id,
    projectId: response.projectId,
    name: response.name,
    description: response.description ?? null,
    createdAt: response.createdAt,
    tags: (response.tags ?? []).flatMap((tag) => (tag.id && tag.name ? [{ id: tag.id, name: tag.name }] : [])),
  };
}
