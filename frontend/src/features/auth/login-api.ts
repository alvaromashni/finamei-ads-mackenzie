import { apiFetch } from '../../lib/http'
import type { AuthUser } from './auth-context'

/** Mirrors LoginRequest/LoginResponse from the backend (POST /auth/login). */
export type LoginRequest = {
  email: string
  password: string
}

export type LoginResponse = {
  token: string
  tokenType: string
  expiresInSeconds: number
  user?: AuthUser | null
  /** Acesso anterior a este login. Nulo no primeiro acesso. */
  lastAccessAt?: string | null
}

export const LOGIN_PATH = '/auth/login'

export function login(request: LoginRequest) {
  return apiFetch<LoginResponse>(LOGIN_PATH, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}
