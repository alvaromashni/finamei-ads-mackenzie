import { apiFetch } from '../../lib/http'

export type PasswordResetMessage = {
  message: string
}

export const REQUEST_RESET_PATH = '/auth/password-resets'
export const CONFIRM_RESET_PATH = '/auth/password-resets/confirm'

export function requestPasswordReset(email: string) {
  return apiFetch<PasswordResetMessage>(REQUEST_RESET_PATH, {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
}

export function confirmPasswordReset(token: string, password: string) {
  return apiFetch<PasswordResetMessage>(CONFIRM_RESET_PATH, {
    method: 'POST',
    body: JSON.stringify({ token, password }),
  })
}
