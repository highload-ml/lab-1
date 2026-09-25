import { Injectable, inject } from '@angular/core';
import { EMPTY, Observable, expand, map, reduce } from 'rxjs';

import { TagResponse, TagsService as TagsApi } from '../../../api';
import { Page, toPage } from '../../../shared/pagination/page';

export interface Tag {
  id: string;
  name: string;
}

/** Backend page size limit; used to load the whole tag dictionary in as few requests as possible. */
const MAX_PAGE_SIZE = 50;
/** Safety limit for {@link TagsService.all}: 20 pages of 50. */
const MAX_TAGS = 1000;

@Injectable({ providedIn: 'root' })
export class TagsService {
  private readonly api = inject(TagsApi);

  /** Classic page of global tags sorted by name. */
  page(page: number, size: number): Observable<Page<Tag>> {
    return this.api.findAll(page, size, 'response').pipe(
      map(toPage),
      map((result) => ({ total: result.total, items: result.items.map(toTag) })),
    );
  }

  /**
   * All tags sorted by name, for pickers and filters. The backend has no search endpoint, so pages of 50
   * are loaded one after another until the total from X-Total-Count is reached (at most {@link MAX_TAGS}).
   */
  all(): Observable<Tag[]> {
    return this.page(0, MAX_PAGE_SIZE).pipe(
      expand((current, index) => {
        const loaded = (index + 1) * MAX_PAGE_SIZE;
        return loaded < current.total && loaded < MAX_TAGS ? this.page(index + 1, MAX_PAGE_SIZE) : EMPTY;
      }),
      reduce((tags, page) => tags.concat(page.items), [] as Tag[]),
    );
  }

  create(name: string): Observable<Tag> {
    return this.api.create({ name }).pipe(map(toTag));
  }
}

export function toTag(response: TagResponse): Tag {
  if (!response.id || !response.name) {
    throw new Error('Incomplete tag in the backend response');
  }
  return { id: response.id, name: response.name };
}
