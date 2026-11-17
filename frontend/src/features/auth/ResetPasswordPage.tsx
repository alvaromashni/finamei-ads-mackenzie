import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useSearchParams } from 'react-router-dom'
import { FormAlert } from '../../components/FormAlert'
import { TextField } from '../../components/TextField'
import { getApiErrorMessage } from '../../lib/api-error'
import { applyApiFieldErrors } from '../../lib/form-errors'
import { confirmPasswordReset } from './password-reset-api'
import {
  CONFIRM_RESET_FIELDS,
  confirmResetSchema,
  type ConfirmResetValues,
} from './password-reset-schema'

const INVALID_LINK =
  'Este link de redefinição é inválido. Solicite um novo link.'

/** UC-002: define a nova senha a partir do link recebido. */
export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const [message, setMessage] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<ConfirmResetValues>({
    resolver: zodResolver(confirmResetSchema),
    defaultValues: { password: '', passwordConfirmation: '' },
  })

  const onSubmit = handleSubmit(async ({ password }) => {
    setFormError(null)
    setPending(true)
    try {
      const response = await confirmPasswordReset(token, password)
      setMessage(response.message)
    } catch (error) {
      if (!applyApiFieldErrors(error, CONFIRM_RESET_FIELDS, setError)) {
        setFormError(getApiErrorMessage(error))
      }
    } finally {
      setPending(false)
    }
  })

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-md items-center px-4 py-10 sm:px-6">
      <div className="w-full">
        <header className="text-center">
          <span className="text-sm font-semibold uppercase tracking-wider text-emerald-700">
            FinaMEI
          </span>
          <h1 className="mt-2 text-2xl font-bold text-slate-900 sm:text-3xl">
            Redefinir senha
          </h1>
          <p className="mt-2 text-slate-600">
            Escolha uma nova senha com no mínimo 8 caracteres, pelo menos uma
            letra e um número.
          </p>
        </header>

        <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-8">
          {!token && (
            <FormAlert tone="error">
              {INVALID_LINK}{' '}
              <Link to="/recuperar-senha" className="font-semibold underline">
                Solicitar novo link
              </Link>
            </FormAlert>
          )}

          {token && !message && (
            <form noValidate onSubmit={onSubmit} className="space-y-5">
              {formError && (
                <FormAlert tone="error">
                  {formError}{' '}
                  <Link
                    to="/recuperar-senha"
                    className="font-semibold underline"
                  >
                    Solicitar novo link
                  </Link>
                </FormAlert>
              )}
              <TextField
                id="new-password"
                label="Nova senha"
                type="password"
                autoComplete="new-password"
                autoFocus
                error={errors.password?.message}
                disabled={pending}
                {...register('password')}
              />
              <TextField
                id="new-password-confirmation"
                label="Repita a senha"
                type="password"
                autoComplete="new-password"
                error={errors.passwordConfirmation?.message}
                disabled={pending}
                {...register('passwordConfirmation')}
              />
              <button
                type="submit"
                disabled={pending}
                className="w-full rounded-lg bg-emerald-700 px-4 py-2 font-semibold text-white hover:bg-emerald-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {pending ? 'Salvando…' : 'Redefinir senha'}
              </button>
            </form>
          )}

          {message && <FormAlert tone="success">{message}</FormAlert>}
        </section>

        <p className="mt-6 text-center text-sm text-slate-600">
          <Link
            to="/login"
            className="rounded font-semibold text-emerald-800 underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700"
          >
            Ir para o login
          </Link>
        </p>
      </div>
    </main>
  )
}
