import type { Role } from './auth-context'

const ROLES: readonly Role[] = ['MEI', 'ACCOUNTANT', 'ADMIN']

/**
 * Reads the role claim from the JWT payload to adapt the interface.
 * The signature is not verified here: the backend stays the authority and
 * rejects any request the role is not allowed to make.
 */
export function getTokenRole(token: string): Role | null {
  try {
    const payload = token.split('.')[1]
    if (!payload) return null
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const claims = JSON.parse(atob(base64)) as { role?: unknown }
    return ROLES.find((role) => role === claims.role) ?? null
  } catch {
    return null
  }
}

/** Epoch millis of the JWT `exp` claim, when the token can be read. */
export function getTokenExpiresAt(token: string): number | null {
  try {
    const payload = token.split('.')[1]
    if (!payload) return null
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const claims = JSON.parse(atob(base64)) as { exp?: unknown }
    return typeof claims.exp === 'number' ? claims.exp * 1000 : null
  } catch {
    return null
  }
}
