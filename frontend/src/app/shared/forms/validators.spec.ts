import { FormControl } from '@angular/forms';
import { notBlank } from './validators';

describe('notBlank', () => {
  it.each(['', '   ', null])('rejects %j', (value) => {
    expect(notBlank(new FormControl<string | null>(value))).toEqual({ required: true });
  });

  it('accepts text with content', () => {
    expect(notBlank(new FormControl(' alice '))).toBeNull();
  });
});
