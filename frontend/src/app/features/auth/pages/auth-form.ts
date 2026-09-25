/** Same rule as the backend's UserValidation.NICKNAME_PATTERN. */
export const NICKNAME_PATTERN = /^[A-Za-z0-9_.-]+$/;

/** Only in-app paths are accepted as a redirect target, never an external URL. */
export function safeReturnUrl(returnUrl: string | undefined): string {
  return returnUrl && returnUrl.startsWith('/') && !returnUrl.startsWith('//') ? returnUrl : '/projects';
}
