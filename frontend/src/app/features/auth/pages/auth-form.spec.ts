import { NICKNAME_PATTERN, safeReturnUrl } from './auth-form';

describe('auth form helpers', () => {
  it('safeReturnUrl keeps in-app paths', () => {
    expect(safeReturnUrl('/projects/p1?tab=experiments')).toBe('/projects/p1?tab=experiments');
  });

  it('safeReturnUrl falls back to /projects for missing or external targets', () => {
    expect(safeReturnUrl(undefined)).toBe('/projects');
    expect(safeReturnUrl('')).toBe('/projects');
    expect(safeReturnUrl('https://evil.example')).toBe('/projects');
    expect(safeReturnUrl('//evil.example')).toBe('/projects');
  });

  it('NICKNAME_PATTERN matches the backend rule', () => {
    expect(NICKNAME_PATTERN.test('alice_ml.v-2')).toBe(true);
    expect(NICKNAME_PATTERN.test('a b')).toBe(false);
    expect(NICKNAME_PATTERN.test('алиса')).toBe(false);
  });
});
