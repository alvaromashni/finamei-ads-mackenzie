import { formatCurrency } from '../../lib/format'
import { revenueAlertMessage } from './revenue-message'
import type { RevenueBand, RevenueSummary } from './revenue-types'

const barColor: Record<RevenueBand, string> = {
  NORMAL: 'bg-emerald-600',
  ATTENTION: 'bg-amber-500',
  CRITICAL: 'bg-orange-600',
  EXCEEDED: 'bg-red-600',
}

const alertStyle: Record<Exclude<RevenueBand, 'NORMAL'>, string> = {
  ATTENTION: 'border-amber-300 bg-amber-50 text-amber-900',
  CRITICAL: 'border-orange-300 bg-orange-50 text-orange-900',
  EXCEEDED: 'border-red-300 bg-red-50 text-red-900',
}

export function RevenueProgress({ summary }: { summary: RevenueSummary }) {
  const { accumulated, limit, band } = summary
  // Floor so the number shown never looks like the next band (89.99% -> 89%).
  const percentage = limit > 0 ? Math.floor((accumulated / limit) * 100) : 0
  const remaining = Math.max(limit - accumulated, 0)

  return (
    <>
      <div
        role="progressbar"
        aria-label="Faturamento acumulado em relação ao limite anual"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.min(percentage, 100)}
        aria-valuetext={`${percentage}% do limite`}
        className="mt-3 h-3 w-full overflow-hidden rounded-full bg-slate-200"
      >
        <div
          className={`h-full rounded-full ${barColor[band]}`}
          style={{ width: `${Math.min(percentage, 100)}%` }}
        />
      </div>

      <p className="mt-2 text-sm text-slate-700">
        {formatCurrency(accumulated)} de {formatCurrency(limit)} ({percentage}%
        do limite anual do MEI)
      </p>

      {summary.proportionalLimit && summary.activeMonths !== null && (
        <p className="mt-1 text-sm text-slate-600">
          Limite proporcional a {summary.activeMonths}{' '}
          {summary.activeMonths === 1 ? 'mês' : 'meses'} de atividade neste ano.
        </p>
      )}

      {band === 'NORMAL' ? (
        <p className="mt-3 text-sm text-emerald-800">
          Dentro do limite. Margem restante: {formatCurrency(remaining)}.
        </p>
      ) : (
        <p
          role="alert"
          className={`mt-3 rounded-lg border px-4 py-3 text-sm ${alertStyle[band]}`}
        >
          {revenueAlertMessage(summary)}
        </p>
      )}
    </>
  )
}
