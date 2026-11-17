import { useQueryClient } from '@tanstack/react-query'
import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { FormAlert } from '../../components/FormAlert'
import { SectionError } from '../../components/SectionError'
import { SelectField } from '../../components/SelectField'
import { TextField } from '../../components/TextField'
import { getApiErrorMessage } from '../../lib/api-error'
import { applyApiFieldErrors } from '../../lib/form-errors'
import { HttpError } from '../../lib/http'
import { useAuthorizedFetch } from '../auth/useAuthorizedFetch'
import { useCategoriesQuery } from '../category/useCategories'
import { fetchRevenueSummary } from '../dashboard/dashboard-api'
import { revenueAlertMessage } from '../revenue/revenue-message'
import type { Transaction, TransactionType } from './transaction-api'
import {
  createTransactionSchema,
  TRANSACTION_FIELDS,
  type TransactionFormOutput,
  type TransactionFormValues,
} from './transaction-schema'
import {
  useCreateTransactionMutation,
  useUpdateTransactionMutation,
} from './useTransactions'

const typeOptions: { value: TransactionType; label: string }[] = [
  { value: 'INCOME', label: 'Receita' },
  { value: 'EXPENSE', label: 'Despesa' },
]

const typeLabels: Record<TransactionType, string> = {
  INCOME: 'receita',
  EXPENSE: 'despesa',
}

const DELETED_MESSAGE =
  'Este lançamento não existe mais. Ele pode ter sido excluído em outra sessão.'

/** 1500.5 -> "1500,50", the format the amount field accepts. */
function toAmountInput(amount: number) {
  return amount.toFixed(2).replace('.', ',')
}

type TransactionFormProps = {
  /** Reference date (YYYY-MM-DD): the default date and the latest one allowed. */
  todayIso: string
  initialType: TransactionType
  /** When set, the form edits this transaction instead of creating one. */
  transaction?: Transaction
  /** Edit mode: called after saving, with the confirmation message. */
  onSaved?: (message: string) => void
  /** Edit mode: leaves the transaction as it was (UC 3a). */
  onCancel?: () => void
}

/**
 * Registers an income (OF05) or an expense (OF07), or edits one that was
 * already registered (OF06, OF08).
 */
export function TransactionForm({
  todayIso,
  initialType,
  transaction,
  onSaved,
  onCancel,
}: TransactionFormProps) {
  const schema = useMemo(() => createTransactionSchema(todayIso), [todayIso])
  const createMutation = useCreateTransactionMutation()
  const updateMutation = useUpdateTransactionMutation()
  const isEditing = transaction !== undefined
  const isPending = createMutation.isPending || updateMutation.isPending
  const [formError, setFormError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [revenueAlert, setRevenueAlert] = useState<string | null>(null)
  const queryClient = useQueryClient()
  const authorizedFetch = useAuthorizedFetch()

  const {
    register,
    handleSubmit,
    reset,
    setError,
    setValue,
    setFocus,
    control,
    formState: { errors },
  } = useForm<TransactionFormValues, unknown, TransactionFormOutput>({
    resolver: zodResolver(schema),
    defaultValues: transaction
      ? {
          type: transaction.type,
          amount: toAmountInput(transaction.amount),
          date: transaction.date,
          categoryId: transaction.category.id,
          description: transaction.description,
        }
      : {
          type: initialType,
          amount: '',
          date: todayIso,
          categoryId: '',
          description: '',
        },
  })

  // Opening the edit form moves the focus to it, also on small screens where
  // it sits above the list (UC step 2).
  useEffect(() => {
    if (isEditing) setFocus('amount')
  }, [isEditing, setFocus])

  const type = useWatch({ control, name: 'type' })
  const categoriesQuery = useCategoriesQuery(type)
  // An inactive category no longer shows up for new transactions, but stays on
  // the ones that already use it (RN07), so keep it available while editing.
  const keptCategory =
    transaction &&
    transaction.type === type &&
    categoriesQuery.data &&
    !categoriesQuery.data.some((item) => item.id === transaction.category.id)
      ? transaction.category
      : null

  const clearFeedback = () => {
    setSuccessMessage(null)
    setRevenueAlert(null)
  }

  // Fields stay enabled while saving: a server field error must be able to
  // take the focus (ONF01). The submit button prevents double submissions.
  const handleError = (error: unknown) => {
    if (error instanceof HttpError && error.status === 404) {
      setFormError(DELETED_MESSAGE)
      return
    }
    if (!applyApiFieldErrors(error, TRANSACTION_FIELDS, setError)) {
      setFormError(getApiErrorMessage(error))
    }
  }

  const onSubmit = handleSubmit((values) => {
    setFormError(null)
    setSuccessMessage(null)

    if (transaction) {
      updateMutation.mutate(
        { id: transaction.id, request: values },
        {
          onSuccess: () => onSaved?.('Lançamento atualizado com sucesso.'),
          onError: handleError,
        },
      )
      return
    }

    createMutation.mutate(values, {
      onSuccess: () => {
        // Keep type and date: people often register several in a row.
        reset({
          type: values.type,
          amount: '',
          date: values.date,
          categoryId: '',
          description: '',
        })
        setSuccessMessage(
          values.type === 'INCOME'
            ? 'Receita registrada com sucesso.'
            : 'Despesa registrada com sucesso.',
        )
        if (values.type !== 'INCOME') {
          setRevenueAlert(null)
          return
        }
        const year = Number(values.date.slice(0, 4))
        void queryClient
          .fetchQuery({
            queryKey: ['dashboard', 'revenue', year],
            queryFn: () =>
              authorizedFetch((token) => fetchRevenueSummary(token, year)),
          })
          .then((summary) => setRevenueAlert(revenueAlertMessage(summary)))
          .catch(() => setRevenueAlert(null))
      },
      onError: handleError,
    })
  })

  return (
    <form noValidate onSubmit={onSubmit} className="mt-4 space-y-5">
      {formError && <FormAlert tone="error">{formError}</FormAlert>}
      {successMessage && <FormAlert tone="success">{successMessage}</FormAlert>}
      {revenueAlert && (
        <p
          role="alert"
          className="rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-950"
        >
          {revenueAlert}
        </p>
      )}

      <fieldset>
        <legend className="block text-sm font-medium text-slate-800">
          Tipo
        </legend>
        <div className="mt-1 grid grid-cols-2 gap-1 rounded-lg bg-slate-100 p-1">
          {typeOptions.map((option) => (
            <label
              key={option.value}
              className="cursor-pointer rounded-md px-3 py-2 text-center text-sm font-semibold text-slate-700 has-[:checked]:bg-white has-[:checked]:text-emerald-800 has-[:checked]:shadow-sm has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-emerald-700"
            >
              <input
                type="radio"
                value={option.value}
                className="sr-only"
                {...register('type', {
                  onChange: () => {
                    // Income and expense have different categories.
                    setValue('categoryId', '')
                    clearFeedback()
                  },
                })}
              />
              {option.label}
            </label>
          ))}
        </div>
      </fieldset>

      <TextField
        id="transaction-amount"
        label="Valor (R$)"
        inputMode="decimal"
        placeholder="0,00"
        hint="Use vírgula para os centavos."
        error={errors.amount?.message}
        {...register('amount', { onChange: clearFeedback })}
      />

      <TextField
        id="transaction-date"
        label="Data"
        type="date"
        max={todayIso}
        error={errors.date?.message}
        {...register('date', { onChange: clearFeedback })}
      />

      {categoriesQuery.isError ? (
        <SectionError
          message="Não foi possível carregar as categorias. Tente novamente."
          onRetry={() => categoriesQuery.refetch()}
        />
      ) : (
        <SelectField
          id="transaction-category"
          label="Categoria"
          error={errors.categoryId?.message}
          disabled={categoriesQuery.isPending}
          {...register('categoryId', { onChange: clearFeedback })}
        >
          <option value="">
            {categoriesQuery.isPending
              ? 'Carregando categorias…'
              : 'Selecione uma categoria'}
          </option>
          {categoriesQuery.data?.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
          {keptCategory && (
            <option value={keptCategory.id}>
              {keptCategory.name} (inativa)
            </option>
          )}
        </SelectField>
      )}

      <TextField
        id="transaction-description"
        label="Descrição"
        hint="Até 120 caracteres."
        error={errors.description?.message}
        {...register('description', { onChange: clearFeedback })}
      />

      <div className={isEditing ? 'flex flex-col gap-3 sm:flex-row' : ''}>
        <button
          type="submit"
          disabled={isPending}
          className="w-full rounded-lg bg-emerald-700 px-4 py-2 font-semibold text-white hover:bg-emerald-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {isEditing
            ? updateMutation.isPending
              ? 'Salvando…'
              : 'Salvar alterações'
            : createMutation.isPending
              ? 'Registrando…'
              : `Registrar ${typeLabels[type]}`}
        </button>
        {isEditing && (
          <button
            type="button"
            onClick={onCancel}
            disabled={isPending}
            className="w-full rounded-lg border border-slate-300 px-4 py-2 font-semibold text-slate-800 hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            Cancelar
          </button>
        )}
      </div>
    </form>
  )
}
