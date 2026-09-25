import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { PAGE_SIZE_OPTIONS, toPage } from './page';

describe('toPage', () => {
  it('reads items from the body and the total from X-Total-Count', () => {
    const response = new HttpResponse({ body: ['a', 'b'], headers: new HttpHeaders({ 'X-Total-Count': '137' }) });

    expect(toPage(response)).toEqual({ items: ['a', 'b'], total: 137 });
  });

  it('falls back to the item count without the header', () => {
    expect(toPage(new HttpResponse({ body: ['a'] }))).toEqual({ items: ['a'], total: 1 });
  });

  it('falls back to the item count for a malformed header', () => {
    const response = new HttpResponse({ body: ['a'], headers: new HttpHeaders({ 'X-Total-Count': 'many' }) });

    expect(toPage(response).total).toBe(1);
  });

  it('treats an empty body as an empty page', () => {
    expect(toPage(new HttpResponse<string[]>({ body: null }))).toEqual({ items: [], total: 0 });
  });

  it('never offers a page size above the backend limit of 50', () => {
    expect(Math.max(...PAGE_SIZE_OPTIONS)).toBe(50);
  });
});
