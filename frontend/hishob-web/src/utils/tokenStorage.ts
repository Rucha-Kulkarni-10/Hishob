/**
 * Centralized JWT token storage utility.
 *
 * All token read/write/delete operations go through this module.
 * No other file should access localStorage for the token directly.
 *
 * Security notes:
 *   - localStorage is accessible to JavaScript on the same origin.
 *     This is a standard trade-off for SPAs without a dedicated
 *     token-refresh / httpOnly-cookie backend flow.
 *   - Never log the token value.
 *   - Never store passwords here.
 *   - Never store internal service credentials here.
 *   - The HISHOB backend does not currently issue refresh tokens,
 *     so httpOnly cookie rotation is deferred to a future phase.
 */

const TOKEN_KEY = 'hishob_access_token';

/** Returns the stored JWT, or null if absent. */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

/** Persists the JWT. Call after a successful login or registration. */
export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

/** Removes the JWT. Call on logout or when a 401 is received. */
export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

/** Returns true if a token is currently stored (does not validate it). */
export function hasToken(): boolean {
  return getToken() !== null;
}
