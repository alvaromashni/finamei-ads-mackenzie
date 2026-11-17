import { apiFetch } from '../../lib/http'
import type { Page } from '../../lib/page'
import type { Role } from '../auth/auth-context'

export type AdminUser = {
  id: string
  name: string
  email: string
  role: Role
  active: boolean
  createdAt: string
  loginLocked: boolean
  loginLockedUntil: string | null
}

export function fetchAdminUsers(token: string | null) {
  return apiFetch<Page<AdminUser>>('/admin/users?page=0&size=100', { token })
}

export function updateAdminUserStatus(
  token: string | null,
  id: string,
  active: boolean,
) {
  return apiFetch<AdminUser>(`/admin/users/${encodeURIComponent(id)}/status`, {
    token,
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}
