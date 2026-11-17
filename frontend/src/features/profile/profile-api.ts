import { apiFetch } from '../../lib/http'
import type { AuthUser } from '../auth/auth-context'

/**
 * Contrato esperado do backend (OF04, módulo `user`):
 * - GET /users/me          -> 200 UserResponse
 * - PUT /users/me          -> 200 UserResponse
 *     400 VALIDATION_ERROR com fieldErrors
 *     409 EMAIL_ALREADY_REGISTERED quando o e-mail já pertence a outra conta
 */
export type Profile = AuthUser

export type UpdateProfileRequest = {
  name: string
  email: string
  meiOpeningDate?: string | null
  clearMeiOpeningDate?: boolean
}

export const PROFILE_PATH = '/users/me'

export function fetchProfile(token: string | null) {
  return apiFetch<Profile>(PROFILE_PATH, { token })
}

export function updateProfile(
  token: string | null,
  request: UpdateProfileRequest,
) {
  return apiFetch<Profile>(PROFILE_PATH, {
    token,
    method: 'PUT',
    body: JSON.stringify(request),
  })
}
