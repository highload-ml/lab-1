import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';

import { problemBody, provideHttpTesting } from '../../../../testing/http-test-providers';
import { Tag } from '../data-access/tags.service';
import { TagPickerComponent } from './tag-picker.component';

const known: Tag[] = [
  { id: 't1', name: 'baseline' },
  { id: 't2', name: 'xgboost' },
  { id: 't3', name: 'base-v2' },
];

/** Access to the protected members the template uses. */
interface PickerInternals {
  query: { setValue(value: string): void };
  options(): { tag: Tag }[];
  createName(): string | null;
  select(event: MatAutocompleteSelectedEvent): void;
}

describe('TagPickerComponent', () => {
  let fixture: ComponentFixture<TagPickerComponent>;
  let backend: HttpTestingController;
  let picker: PickerInternals;
  let picked: Tag[];

  beforeEach(async () => {
    TestBed.configureTestingModule({ imports: [TagPickerComponent], providers: provideHttpTesting() });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TagPickerComponent);
    fixture.componentRef.setInput('excludeIds', ['t2']);
    picked = [];
    fixture.componentInstance.picked.subscribe((tag) => picked.push(tag));
    picker = fixture.componentInstance as unknown as PickerInternals;
    await fixture.whenStable();
    backend.expectOne((r) => r.url === '/api/v1/tags').flush(known, { headers: { 'X-Total-Count': '3' } });
    await fixture.whenStable();
  });

  afterEach(() => backend.verify());

  function choose(value: unknown): void {
    picker.select({ option: { value } } as MatAutocompleteSelectedEvent);
  }

  it('offers matching tags except the ones already attached', () => {
    picker.query.setValue('BASE');

    expect(picker.options().map((option) => option.tag.name)).toEqual(['baseline', 'base-v2']);
    picker.query.setValue('xgb');
    expect(picker.options()).toEqual([]);
  });

  it('offers creation only for a new name', () => {
    picker.query.setValue('baseline');
    expect(picker.createName()).toBeNull();

    picker.query.setValue(' lightgbm ');
    expect(picker.createName()).toBe('lightgbm');

    picker.query.setValue('x'.repeat(101));
    expect(picker.createName()).toBeNull();
  });

  it('emits an existing tag', () => {
    choose({ kind: 'existing', tag: known[0] });

    expect(picked).toEqual([known[0]]);
  });

  it('creates a new tag and emits it', async () => {
    choose({ kind: 'create', name: 'lightgbm' });

    const request = backend.expectOne({ method: 'POST', url: '/api/v1/tags' });
    expect(request.request.body).toEqual({ name: 'lightgbm' });
    request.flush({ id: 't4', name: 'lightgbm' }, { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    expect(picked).toEqual([{ id: 't4', name: 'lightgbm' }]);
    picker.query.setValue('light');
    expect(picker.options().map((option) => option.tag.id)).toEqual(['t4']);
  });

  it('uses the existing tag when someone created the same name meanwhile', async () => {
    choose({ kind: 'create', name: 'lightgbm' });

    backend
      .expectOne({ method: 'POST', url: '/api/v1/tags' })
      .flush(problemBody(409, "Tag 'lightgbm' already exists"), { status: 409, statusText: 'Conflict' });
    backend
      .expectOne((r) => r.method === 'GET' && r.url === '/api/v1/tags')
      .flush([...known, { id: 't9', name: 'lightgbm' }], { headers: { 'X-Total-Count': '4' } });
    await fixture.whenStable();

    expect(picked).toEqual([{ id: 't9', name: 'lightgbm' }]);
  });
});
