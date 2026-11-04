import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { FormAlert } from '../../components/FormAlert'
import { TextField } from '../../components/TextField'
import { getApiErrorMessage } from '../../lib/api-error'
import { applyApiFieldErrors } from '../../lib/form-errors'
import { HttpError } from '../../lib/http'
import {
  REGISTER_FIELDS,
  registerSchema,
  type RegisterFormValues,
} from './register-schema'
import { useAuth } from './useAuth'
import { useRegisterMutation } from './useRegister'

const EMAIL_TAKEN_MESSAGE =
  'Este e-mail já tem uma conta no FinaMEI. Entre com ele ou use outro e-mail.'

/** Account creation (OF01). New accounts are MEI accounts. */
export function RegisterPage() {
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const mutation = useRegisterMutation()
  const [formError, setFormError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      name: '',
      email: '',
      password: '',
      passwordConfirmation: '',
    },
  })

  if (isAuthenticated) {
    // Signed in right after creating the account: go to the first screen.
    return <Navigate to="/" replace />
  }

  const onSubmit = handleSubmit(({ name, email, password }) => {
    setFormError(null)
    mutation.mutate(
      { name, email, password },
      {
        onSuccess: ({ session }) => {
          if (!session) {
            navigate('/login', {
              replace: true,
              state: { registeredEmail: email },
            })
          }
        },
        onError: (error) => {
          if (error instanceof HttpError && error.status === 409) {
            setError(
              'email',
              { message: EMAIL_TAKEN_MESSAGE },
              { shouldFocus: true },
            )
          } else if (!applyApiFieldErrors(error, REGISTER_FIELDS, setError)) {
            setFormError(getApiErrorMessage(error))
          }
        },
      },
    )
  })

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-md items-center px-4 py-10 sm:px-6">
      <div className="w-full">
        <header className="text-center">
          <span className="text-sm font-semibold uppercase tracking-wider text-emerald-700">
            FinaMEI
          </span>
          <h1 className="mt-2 text-2xl font-bold text-slate-900 sm:text-3xl">
            Criar conta
          </h1>
          <p className="mt-2 text-slate-600">
            Comece a organizar as finanças do seu MEI em poucos minutos.
          </p>
        </header>

        <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-8">
          <form noValidate onSubmit={onSubmit} className="space-y-5">
            {formError && <FormAlert tone="error">{formError}</FormAlert>}

            <TextField
              id="register-name"
              label="Nome"
              autoComplete="name"
              autoFocus
              error={errors.name?.message}
              {...register('name')}
            />
            <TextField
              id="register-email"
              label="E-mail"
              type="email"
              autoComplete="email"
              error={errors.email?.message}
              {...register('email')}
            />
            <TextField
              id="register-password"
              label="Senha"
              type="password"
              autoComplete="new-password"
              // The error already repeats the rule; avoid saying it twice.
              hint={
                errors.password
                  ? undefined
                  : 'No mínimo 8 caracteres, com pelo menos uma letra e um número.'
              }
              error={errors.password?.message}
              {...register('password')}
            />
            <TextField
              id="register-password-confirmation"
              label="Repita a senha"
              type="password"
              autoComplete="new-password"
              error={errors.passwordConfirmation?.message}
              {...register('passwordConfirmation')}
            />

            <button
              type="submit"
              disabled={mutation.isPending}
              className="w-full rounded-lg bg-emerald-700 px-4 py-2 font-semibold text-white hover:bg-emerald-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {mutation.isPending ? 'Criando conta…' : 'Criar conta'}
            </button>
          </form>
        </section>

        <p className="mt-6 text-center text-sm text-slate-600">
          Já tem conta?{' '}
          <Link
            to="/login"
            className="rounded font-semibold text-emerald-800 touch:inline-flex touch:min-h-11 touch:items-center touch:px-1 underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700"
          >
            Entrar
          </Link>
        </p>
      </div>
    </main>
  )
}
