import { zodResolver } from '@hookform/resolvers/zod'
import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { FormAlert } from '../../components/FormAlert'
import { TextField } from '../../components/TextField'
import { getApiErrorBody, getApiErrorMessage } from '../../lib/api-error'
import { applyApiFieldErrors } from '../../lib/form-errors'
import { formatCurrency, formatDate, formatMonthYear } from '../../lib/format'
import { HttpError } from '../../lib/http'
import type { DasGuide, DasStatus } from './das-api'
import {
  createDasPaymentSchema,
  DAS_PAYMENT_FIELDS,
  type DasPaymentFormValues,
} from './das-schema'
import { usePayDasMutation, useRefreshDasGuides } from './useDas'

const ALREADY_PAID_MESSAGE = 'Esta guia já estava registrada como paga.'

const statusStyle: Record<DasStatus, string> = {
  PAID: 'bg-emerald-50 text-emerald-800',
  PENDING: 'bg-slate-100 text-slate-700',
  OVERDUE: 'bg-red-50 text-red-800',
}

const buttonBase =
  'rounded-lg px-4 py-2 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:cursor-not-allowed disabled:opacity-50'

function describeStatus(guide: DasGuide) {
  if (guide.status === 'PAID') {
    return guide.paidAt ? `Paga em ${formatDate(guide.paidAt)}` : 'Paga'
  }
  return guide.status === 'OVERDUE' ? 'Vencida' : 'Pendente'
}

/** "2026-01" -> "janeiro de 2026" */
function competenceLabel(competence: string) {
  const [year, month] = competence.split('-').map(Number)
  return formatMonthYear(new Date(year, month - 1, 1))
}

type DasGuideItemProps = {
  guide: DasGuide
  year: number
  /** Reference date (YYYY-MM-DD): the default payment date and the latest allowed. */
  todayIso: string
}

/** One monthly DAS guide, with the action to register its payment (OF14). */
export function DasGuideItem({ guide, year, todayIso }: DasGuideItemProps) {
  const schema = useMemo(() => createDasPaymentSchema(todayIso), [todayIso])
  const mutation = usePayDasMutation(year)
  const refreshGuides = useRefreshDasGuides(year)
  const [isPaying, setIsPaying] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const label = competenceLabel(guide.competence)
  const title = label[0].toUpperCase() + label.slice(1)
  const fieldId = `das-paid-at-${guide.id}`

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<DasPaymentFormValues>({
    resolver: zodResolver(schema),
    defaultValues: { paidAt: todayIso },
  })

  const onSubmit = handleSubmit((values) => {
    setFormError(null)

    mutation.mutate(
      { id: guide.id, request: values },
      {
        onSuccess: () => {
          setIsPaying(false)
          setSuccessMessage(`Pagamento de ${label} registrado.`)
        },
        onError: (error) => {
          if (error instanceof HttpError && error.status === 409) {
            setIsPaying(false)
            setFormError(
              getApiErrorBody(error)?.message ?? ALREADY_PAID_MESSAGE,
            )
            void refreshGuides()
            return
          }
          if (!applyApiFieldErrors(error, DAS_PAYMENT_FIELDS, setError)) {
            setFormError(getApiErrorMessage(error))
          }
        },
      },
    )
  })

  return (
    <li className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="font-semibold text-slate-900">{title}</h3>
          <p className="text-sm text-slate-600">
            Vencimento: {formatDate(guide.dueDate)} ·{' '}
            {formatCurrency(guide.amount)}
          </p>
        </div>
        <span
          className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${statusStyle[guide.status]}`}
        >
          {describeStatus(guide)}
        </span>
      </div>

      {(formError || successMessage) && !isPaying && (
        <div className="mt-3">
          {formError ? (
            <FormAlert tone="error">{formError}</FormAlert>
          ) : (
            <FormAlert tone="success">{successMessage}</FormAlert>
          )}
        </div>
      )}

      {guide.status !== 'PAID' && !isPaying && (
        <button
          type="button"
          aria-label={`Marcar ${label} como paga`}
          onClick={() => {
            setFormError(null)
            setSuccessMessage(null)
            setIsPaying(true)
          }}
          className={`${buttonBase} mt-3 border border-emerald-700 text-emerald-800 hover:bg-emerald-50`}
        >
          Marcar como paga
        </button>
      )}

      {isPaying && (
        <form
          noValidate
          onSubmit={onSubmit}
          aria-label={`Pagamento de ${label}`}
          className="mt-3 space-y-3"
        >
          {formError && <FormAlert tone="error">{formError}</FormAlert>}
          <TextField
            id={fieldId}
            label="Data do pagamento"
            type="date"
            max={todayIso}
            error={errors.paidAt?.message}
            {...register('paidAt')}
          />
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <button
              type="button"
              onClick={() => {
                setIsPaying(false)
                setFormError(null)
              }}
              disabled={mutation.isPending}
              className={`${buttonBase} border border-slate-300 text-slate-800 hover:bg-slate-50`}
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={mutation.isPending}
              className={`${buttonBase} bg-emerald-700 text-white hover:bg-emerald-800`}
            >
              {mutation.isPending ? 'Registrando…' : 'Confirmar pagamento'}
            </button>
          </div>
        </form>
      )}
    </li>
  )
}
