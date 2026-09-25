import { TestBed } from '@angular/core/testing';
import { SessionService, SessionUser } from './session.service';

const alice: SessionUser = { id: 'a1', nickname: 'alice', role: 'ML_ENGINEER' };
const STORAGE_KEY = 'highload-ml.session';

describe('SessionService', () => {
  beforeEach(() => localStorage.clear());

  function create(): SessionService {
    TestBed.resetTestingModule();
    return TestBed.inject(SessionService);
  }

  it('starts signed out when nothing is stored', () => {
    const session = create();

    expect(session.signedIn()).toBe(false);
    expect(session.user()).toBeNull();
  });

  it('signIn exposes the user and persists only id, nickname and role', () => {
    const session = create();

    session.signIn({ ...alice, createdAt: '2026-01-01' } as SessionUser);

    expect(session.user()).toEqual(alice);
    expect(session.requireUserId()).toBe('a1');
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}')).toEqual(alice);
  });

  it('restores the session from storage after a reload', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(alice));

    expect(create().user()).toEqual(alice);
  });

  it('ignores corrupted storage', () => {
    localStorage.setItem(STORAGE_KEY, '{not json');

    expect(create().signedIn()).toBe(false);
  });

  it('ignores a stored object without required fields', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ nickname: 'alice' }));

    expect(create().signedIn()).toBe(false);
  });

  it('signOut clears the user and the storage', () => {
    const session = create();
    session.signIn(alice);

    session.signOut();

    expect(session.signedIn()).toBe(false);
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
    expect(() => session.requireUserId()).toThrow();
  });
});
