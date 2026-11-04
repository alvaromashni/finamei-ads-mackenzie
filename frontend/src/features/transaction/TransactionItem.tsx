import { useState } from 'react'
import { FormAlert } from '../../components/FormAlert'
import { getApiErrorMessage } from '../../lib/api-error'
import { formatCurrency, formatDate } from '../../lib/format'
import { HttpError } from '../../lib/http'
import type { Transaction } from './transaction-api'
import { useDeleteTransactionMutation } from './useTransactions'

const actionButton =
  'rounded-lg px-3 py-1.5 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:cursor-not-allowed disabled:opacity-50'

type TransactionItemProps = {
  transaction: Transaction
  isEditing: boolean
  onEdit: () => void
  /** Called after the deletion, or when it turns out to be already deleted. */
  onDeleted: (message: string) => void
}

/** One transaction of the list, with the edit and delete actions (OF06, OF08). */
export function TransactionItem({
  transaction,
  isEditing,
  onEdit,
  onDeleted,
}: TransactionItemProps) {
  const [confirming, setConfirming] = useState(false)
  const mutation = useDeleteTransactionMutation()
  const isIncome = transaction.type === 'INCOME'
  const label = transaction.description

  const confirmDelete = () =>
    mutation.mutate(transaction.id, {
      onSuccess: () => onDeleted('Lançamento excluído.'),
      onError: (error) => {
        // Deleted in another session: the list is refreshed anyway (UC 4c).
        if (error instanceof HttpError && error.status === 404) {
          onDeleted('Este lançamento já tinha sido excluído.')
        }
      },
    })

  return (
    <li className={`py-3 ${isEditing ? 'bg-emerald-50/60' : ''}`}>
      <div className="flex flex-col gap-1 sm:flex-row sm:items-start sm:justify-between sm:gap-3">
        <div className="min-w-0">
          <p className="break-words font-medium text-slate-900">
            {transaction.description}
          </p>
          <p className="text-sm text-slate-600">
            {transaction.category.name} · {formatDate(transaction.date)}
          </p>
        </div>
        <p
          className={`shrink-0 font-semibold ${
            isIncome ? 'text-emerald-700' : 'text-red-700'
          }`}
        >
          <span className="sr-only">
            {isIncome ? 'Receita: ' : 'Despesa: '}
          </span>
          <span aria-hidden="true">{isIncome ? '+ ' : '− '}</span>
          {formatCurrency(transaction.amount)}
        </p>
      </div>

      {!confirming && (
        <div className="mt-2 flex gap-2">
          <button
            type="button"
            onClick={onEdit}
            disabled={isEditing}
            aria-label={`Editar ${label}`}
            className={`${actionButton} border border-slate-300 text-slate-800 hover:bg-slate-50`}
          >
            {isEditing ? 'Editando' : 'Editar'}
          </button>
          <button
            type="button"
            onClick={() => setConfirming(true)}
            aria-label={`Excluir ${label}`}
            className={`${actionButton} text-red-700 hover:bg-red-50`}
          >
            Excluir
          </button>
        </div>
      )}

      {confirming && (
        <div
          role="group"
          aria-label={`Confirmar exclusão de ${label}`}
          className="mt-2 space-y-2 rounded-lg bg-red-50 p-3 text-red-900"
        >
          {mutation.isError && (
            <FormAlert tone="error">
              {getApiErrorMessage(mutation.error)}
            </FormAlert>
          )}
          <p className="text-sm">
            Excluir este lançamento? O saldo e o faturamento serão recalculados.
          </p>
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              autoFocus
              onClick={confirmDelete}
              disabled={mutation.isPending}
              className={`${actionButton} bg-red-700 text-white hover:bg-red-800`}
            >
              {mutation.isPending ? 'Excluindo…' : 'Sim, excluir'}
            </button>
            <button
              type="button"
              onClick={() => {
                mutation.reset()
                setConfirming(false)
              }}
              disabled={mutation.isPending}
              className={`${actionButton} border border-slate-300 bg-white text-slate-800 hover:bg-slate-50`}
            >
              Cancelar
            </button>
          </div>
        </div>
      )}
    </li>
  )
}
