import { useEffect, useRef } from 'react'
import { apiFetch } from '../../lib/http'
import { useAuthorizedFetch } from './useAuthorizedFetch'
import { useAuth } from './useAuth'
import { getTokenExpiresAt } from './token-claims'

const WINDOW_MS = 30 * 60 * 1000
const RENEW_AFTER_MS = 60_000

/**
 * Avisa a API quando a pessoa continua usando o sistema, para a sessão
 * valer 30 minutos a partir da última atividade (ONF04), não do login.
 */
export function useSessionKeepAlive() {
  const { token, isAuthenticated } = useAuth()
  const authorizedFetch = useAuthorizedFetch()
  const lastTouch = useRef(0)

  useEffect(() => {
    if (!isAuthenticated || !token) return

    const touch = () => {
      const expiresAt = getTokenExpiresAt(token)
      if (expiresAt == null) return
      const age = WINDOW_MS - (expiresAt - Date.now())
      if (age < RENEW_AFTER_MS) return
      const now = Date.now()
      if (now - lastTouch.current < RENEW_AFTER_MS) return
      lastTouch.current = now
      void authorizedFetch((current) =>
        apiFetch<void>('/auth/session', {
          token: current,
          method: 'POST',
          body: '{}',
        }),
      ).catch(() => {})
    }

    window.addEventListener('pointerdown', touch)
    window.addEventListener('keydown', touch)
    return () => {
      window.removeEventListener('pointerdown', touch)
      window.removeEventListener('keydown', touch)
    }
  }, [authorizedFetch, isAuthenticated, token])
}
