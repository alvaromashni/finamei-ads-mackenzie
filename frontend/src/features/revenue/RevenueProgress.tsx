import { formatCurrency } from '../../lib/format'
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

function getAlertMessage(
  band: Exclude<RevenueBand, 'NORMAL'>,
  percentage: number,
  remaining: number,
  excess: number,
) {
  switch (band) {
    case 'ATTENTION':
      return `Atenção: você já atingiu ${percentage}% do limite anual. Margem restante: ${formatCurrency(remaining)}.`
    case 'CRITICAL':
      return `Faixa crítica: você já atingiu ${percentage}% do limite anual. Margem restante: ${formatCurrency(remaining)}. Acompanhe de perto os próximos recebimentos.`
    case 'EXCEEDED':
      // RN02 classifies >= 100% as exceeded, so the amount can be exactly the limit.
      if (excess === 0) {
        return `Limite atingido: você atingiu 100% do limite anual e não há margem restante. Novos recebimentos passam do limite. Procure seu contador para avaliar o desenquadramento do MEI.`
      }
      return `Limite excedido: seu faturamento passou o limite em ${formatCurrency(excess)}. Procure seu contador para avaliar o desenquadramento do MEI.`
  }
}

export function RevenueProgress({ summary }: { summary: RevenueSummary }) {
  const { accumulated, limit, band } = summary
  // Floor so the number shown never looks like the next band (89.99% -> 89%).
  const percentage = limit > 0 ? Math.floor((accumulated / limit) * 100) : 0
  const remaining = Math.max(limit - accumulated, 0)
  const excess = Math.max(accumulated - limit, 0)

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
          {getAlertMessage(band, percentage, remaining, excess)}
        </p>
      )}
    </>
  )
}
