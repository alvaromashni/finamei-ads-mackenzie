import { useMutation } from '@tanstack/react-query'
import { login } from './login-api'
import { registerUser, type RegisterRequest } from './register-api'
import { useAuth } from './useAuth'

/**
 * Creates the account and signs in right away, so the new user lands inside
 * the app. If the automatic sign-in fails, the account still exists:
 * `signedIn` is false and the screen sends the user to the login page.
 */
export function useRegisterMutation() {
  const { signIn } = useAuth()

  return useMutation({
    mutationFn: async (request: RegisterRequest) => {
      const user = await registerUser(request)
      try {
        const session = await login({
          email: request.email,
          password: request.password,
        })
        return { user, session }
      } catch {
        return { user, session: null }
      }
    },
    onSuccess: ({ user, session }) => {
      if (!session) return
      signIn({
        token: session.token,
        user: session.user ?? user,
        expiresAt: Date.now() + session.expiresInSeconds * 1000,
        lastAccessAt: session.lastAccessAt ?? new Date().toISOString(),
      })
    },
  })
}
