import { HttpResponse } from '@angular/common/http';

/** Response header with the total number of elements for classic pagination (see backend `Pagination`). */
export const TOTAL_COUNT_HEADER = 'X-Total-Count';

/** The backend caps page size at 50; the UI never offers more. */
export const PAGE_SIZE_OPTIONS = [10, 20, 50] as const;
export const DEFAULT_PAGE_SIZE = 20;

export interface Page<T> {
  items: T[];
  total: number;
}

/** Classic page: items from the body, total from `X-Total-Count` (falls back to the item count if absent). */
export function toPage<T>(response: HttpResponse<T[]>): Page<T> {
  const items = response.body ?? [];
  const header = response.headers.get(TOTAL_COUNT_HEADER);
  const total = header === null ? Number.NaN : Number(header);
  return { items, total: Number.isFinite(total) ? total : items.length };
}
