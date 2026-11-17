import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router-dom'
import { FormAlert } from '../../components/FormAlert'
import { TextField } from '../../components/TextField'
import { getApiErrorMessage } from '../../lib/api-error'
import { requestPasswordReset } from './password-reset-api'
import {
  requestResetSchema,
  type RequestResetValues,
} from './password-reset-schema'

/** UC-002: pede o link de redefinição sem revelar se o e-mail existe. */
export function RequestPasswordResetPage() {
  const [message, setMessage] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RequestResetValues>({
    resolver: zodResolver(requestResetSchema),
    defaultValues: { email: '' },
  })

  const onSubmit = handleSubmit(async ({ email }) => {
    setFormError(null)
    setMessage(null)
    setPending(true)
    try {
      const response = await requestPasswordReset(email)
      setMessage(response.message)
    } catch (error) {
      setFormError(getApiErrorMessage(error))
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
            Esqueci minha senha
          </h1>
          <p className="mt-2 text-slate-600">
            Informe o e-mail da conta. Se ele estiver cadastrado, enviamos um
            link para criar uma nova senha. O link vale por 30 minutos.
          </p>
        </header>

        <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-8">
          <form noValidate onSubmit={onSubmit} className="space-y-5">
            {formError && <FormAlert tone="error">{formError}</FormAlert>}
            {message && <FormAlert tone="success">{message}</FormAlert>}
            <TextField
              id="reset-email"
              label="E-mail"
              type="email"
              autoComplete="email"
              autoFocus
              error={errors.email?.message}
              disabled={pending}
              {...register('email')}
            />
            <button
              type="submit"
              disabled={pending}
              className="w-full rounded-lg bg-emerald-700 px-4 py-2 font-semibold text-white hover:bg-emerald-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {pending ? 'Enviando…' : 'Enviar link'}
            </button>
          </form>
        </section>

        <p className="mt-6 text-center text-sm text-slate-600">
          <Link
            to="/login"
            className="rounded font-semibold text-emerald-800 underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700"
          >
            Voltar para o login
          </Link>
        </p>
      </div>
    </main>
  )
}
