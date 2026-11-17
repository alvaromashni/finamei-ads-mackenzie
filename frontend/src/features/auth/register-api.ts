import { apiFetch } from '../../lib/http'
import type { AuthUser } from './auth-context'

/** Mirrors RegisterUserRequest from the backend (POST /auth/register, OF01). */
export type RegisterRequest = {
  name: string
  email: string
  password: string
  /** MEI por padrão. ACCOUNTANT cria a conta de contador usada no UC-008. */
  role?: 'MEI' | 'ACCOUNTANT'
}

export const REGISTER_PATH = '/auth/register'

/**
 * POST /auth/register -> 201 UserResponse
 *   400 VALIDATION_ERROR with fieldErrors (name, email, password)
 *   409 EMAIL_ALREADY_REGISTERED
 */
export function registerUser(request: RegisterRequest) {
  return apiFetch<AuthUser>(REGISTER_PATH, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}
