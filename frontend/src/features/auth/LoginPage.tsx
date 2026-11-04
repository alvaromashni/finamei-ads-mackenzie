import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useLocation, type Location } from 'react-router-dom'
import { FormAlert } from '../../components/FormAlert'
import { TextField } from '../../components/TextField'
import { getApiErrorBody, getApiErrorMessage } from '../../lib/api-error'
import { applyApiFieldErrors } from '../../lib/form-errors'
import { HttpError } from '../../lib/http'
import { LOGIN_FIELDS, loginSchema, type LoginFormValues } from './login-schema'
import { useAuth } from './useAuth'
import { useLoginMutation } from './useLogin'

const INVALID_CREDENTIALS_MESSAGE = 'E-mail ou senha inválidos.'
// The home route sends each role to its own first screen.
const DEFAULT_REDIRECT = '/'

/** E-mail of an account just created whose automatic sign-in failed. */
function getRegisteredEmail(state: unknown) {
  return (state as { registeredEmail?: string } | null)?.registeredEmail
}

function getRedirectPath(state: unknown) {
  const from = (state as { from?: Location } | null)?.from
  if (!from || from.pathname === '/login') return DEFAULT_REDIRECT
  return `${from.pathname}${from.search}${from.hash}`
}

export function LoginPage() {
  const { isAuthenticated, sessionExpired } = useAuth()
  const location = useLocation()
  const mutation = useLoginMutation()
  const [formError, setFormError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    setError,
    resetField,
    formState: { errors },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: getRegisteredEmail(location.state) ?? '',
      password: '',
    },
  })

  if (isAuthenticated) {
    return <Navigate to={getRedirectPath(location.state)} replace />
  }

  const onSubmit = handleSubmit((values) => {
    setFormError(null)

    mutation.mutate(values, {
      onError: (error) => {
        if (error instanceof HttpError && error.status === 401) {
          resetField('password')
          setFormError(
            getApiErrorBody(error)?.message ?? INVALID_CREDENTIALS_MESSAGE,
          )
          return
        }

        if (!applyApiFieldErrors(error, LOGIN_FIELDS, setError)) {
          setFormError(getApiErrorMessage(error))
        }
      },
    })
  })

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-md items-center px-4 py-10 sm:px-6">
      <div className="w-full">
        <header className="text-center">
          <span className="text-sm font-semibold uppercase tracking-wider text-emerald-700">
            FinaMEI
          </span>
          <h1 className="mt-2 text-2xl font-bold text-slate-900 sm:text-3xl">
            Entrar no FinaMEI
          </h1>
          <p className="mt-2 text-slate-600">
            Acesse sua conta para acompanhar as finanças do seu negócio.
          </p>
        </header>

        <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-8">
          {sessionExpired && (
            <p
              role="status"
              className="mb-5 rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900"
            >
              Sua sessão expirou por segurança. Entre novamente para continuar
              de onde parou.
            </p>
          )}
          <form noValidate onSubmit={onSubmit} className="space-y-5">
            {formError && <FormAlert tone="error">{formError}</FormAlert>}
            {!formError && getRegisteredEmail(location.state) && (
              <FormAlert tone="success">
                Conta criada! Entre com seu e-mail e sua senha.
              </FormAlert>
            )}

            <TextField
              id="login-email"
              label="E-mail"
              type="email"
              autoComplete="email"
              autoFocus
              error={errors.email?.message}
              disabled={mutation.isPending}
              {...register('email')}
            />

            <TextField
              id="login-password"
              label="Senha"
              type="password"
              autoComplete="current-password"
              error={errors.password?.message}
              disabled={mutation.isPending}
              {...register('password')}
            />

            <button
              type="submit"
              disabled={mutation.isPending}
              className="w-full rounded-lg bg-emerald-700 px-4 py-2 font-semibold text-white hover:bg-emerald-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {mutation.isPending ? 'Entrando…' : 'Entrar'}
            </button>
          </form>
        </section>

        <p className="mt-6 text-center text-sm text-slate-600">
          Ainda não tem conta?{' '}
          <Link
            to="/cadastro"
            className="rounded font-semibold text-emerald-800 touch:inline-flex touch:min-h-11 touch:items-center touch:px-1 underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700"
          >
            Criar conta
          </Link>
        </p>
      </div>
    </main>
  )
}
