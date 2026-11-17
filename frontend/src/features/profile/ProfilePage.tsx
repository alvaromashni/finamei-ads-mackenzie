import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { FormAlert } from '../../components/FormAlert'
import { TextField } from '../../components/TextField'
import { getApiErrorMessage } from '../../lib/api-error'
import { applyApiFieldErrors } from '../../lib/form-errors'
import { HttpError } from '../../lib/http'
import { useAuth } from '../auth/useAuth'
import {
  PROFILE_FIELDS,
  profileSchema,
  type ProfileFormValues,
} from './profile-schema'
import { useProfileQuery, useUpdateProfileMutation } from './useProfile'

const EMAIL_CONFLICT_MESSAGE =
  'Este e-mail já está em uso por outra conta. Informe um e-mail diferente.'

export function ProfilePage() {
  const { signOut } = useAuth()
  const profileQuery = useProfileQuery()
  const mutation = useUpdateProfileMutation()
  const [formError, setFormError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const profile = profileQuery.data

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isDirty },
  } = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    // Preenche com os dados do servidor e mantém o que o usuário já editou
    // caso a consulta seja atualizada em segundo plano.
    values: profile
      ? {
          name: profile.name,
          email: profile.email,
          meiOpeningDate: profile.meiOpeningDate ?? '',
        }
      : undefined,
    resetOptions: { keepDirtyValues: true },
  })

  const onSubmit = handleSubmit((values) => {
    setFormError(null)
    setSuccessMessage(null)

    mutation.mutate(
      {
        name: values.name,
        email: values.email,
        meiOpeningDate: values.meiOpeningDate || null,
        clearMeiOpeningDate: values.meiOpeningDate === '',
      },
      {
        onSuccess: (updated) => {
          reset({
            name: updated.name,
            email: updated.email,
            meiOpeningDate: updated.meiOpeningDate ?? '',
          })
          setSuccessMessage('Perfil atualizado com sucesso.')
        },
        onError: (error) => {
          if (error instanceof HttpError && error.status === 409) {
            setError(
              'email',
              { message: EMAIL_CONFLICT_MESSAGE },
              { shouldFocus: true },
            )
            return
          }

          if (!applyApiFieldErrors(error, PROFILE_FIELDS, setError)) {
            setFormError(getApiErrorMessage(error))
          }
        },
      },
    )
  })

  return (
    <main className="mx-auto w-full max-w-2xl px-4 py-8 sm:px-6 sm:py-12">
      <header>
        <h1 className="text-2xl font-bold text-slate-900 sm:text-3xl">
          Meu perfil
        </h1>
        <p className="mt-2 text-slate-600">
          Consulte e atualize seus dados de cadastro.
        </p>
      </header>

      <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-8">
        {profileQuery.isPending && (
          <p role="status" className="text-slate-600">
            Carregando seus dados…
          </p>
        )}

        {profileQuery.isError && (
          <div role="alert" className="space-y-4">
            <p className="text-red-700">
              {profileQuery.error instanceof HttpError &&
              profileQuery.error.status === 401
                ? getApiErrorMessage(profileQuery.error)
                : 'Não foi possível carregar seu perfil. Tente novamente.'}
            </p>
            {profileQuery.error instanceof HttpError &&
            profileQuery.error.status === 401 ? (
              <button
                type="button"
                onClick={() => signOut('expired')}
                className="rounded-lg bg-emerald-700 px-4 py-2 font-semibold text-white hover:bg-emerald-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700"
              >
                Entrar novamente
              </button>
            ) : (
              <button
                type="button"
                onClick={() => profileQuery.refetch()}
                className="rounded-lg border border-slate-300 px-4 py-2 font-semibold text-slate-800 hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700"
              >
                Tentar novamente
              </button>
            )}
          </div>
        )}

        {profile && (
          <form noValidate onSubmit={onSubmit} className="space-y-5">
            {formError && <FormAlert tone="error">{formError}</FormAlert>}
            {successMessage && (
              <FormAlert tone="success">{successMessage}</FormAlert>
            )}

            <TextField
              id="profile-name"
              label="Nome"
              type="text"
              autoComplete="name"
              error={errors.name?.message}
              disabled={mutation.isPending}
              {...register('name', {
                onChange: () => setSuccessMessage(null),
              })}
            />

            <TextField
              id="profile-email"
              label="E-mail"
              type="email"
              autoComplete="email"
              error={errors.email?.message}
              disabled={mutation.isPending}
              {...register('email', {
                onChange: () => setSuccessMessage(null),
              })}
            />

            <TextField
              id="profile-opening-date"
              label="Data de abertura do MEI"
              type="date"
              hint="Usada para calcular o limite proporcional no ano de abertura. Deixe em branco se o MEI foi aberto antes deste ano."
              error={errors.meiOpeningDate?.message}
              disabled={mutation.isPending}
              {...register('meiOpeningDate', {
                onChange: () => setSuccessMessage(null),
              })}
            />

            <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
              <button
                type="button"
                onClick={() => {
                  reset({
                    name: profile.name,
                    email: profile.email,
                    meiOpeningDate: profile.meiOpeningDate ?? '',
                  })
                  setFormError(null)
                }}
                disabled={!isDirty || mutation.isPending}
                className="rounded-lg border border-slate-300 px-4 py-2 font-semibold text-slate-800 hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:cursor-not-allowed disabled:opacity-50"
              >
                Descartar alterações
              </button>
              <button
                type="submit"
                disabled={!isDirty || mutation.isPending}
                className="rounded-lg bg-emerald-700 px-4 py-2 font-semibold text-white hover:bg-emerald-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {mutation.isPending ? 'Salvando…' : 'Salvar alterações'}
              </button>
            </div>
          </form>
        )}
      </section>
    </main>
  )
}
