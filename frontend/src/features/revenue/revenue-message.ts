import { formatCurrency } from '../../lib/format'
import type { RevenueSummary } from './revenue-types'

/** Same sentences shown on the panel (RN02), reused when a revenue is registered. */
export function revenueAlertMessage(summary: RevenueSummary): string | null {
  if (summary.band === 'NORMAL') return null
  const { accumulated, limit, band } = summary
  const percentage = limit > 0 ? Math.floor((accumulated / limit) * 100) : 0
  const remaining = Math.max(limit - accumulated, 0)
  const excess = Math.max(accumulated - limit, 0)

  switch (band) {
    case 'ATTENTION':
      return `Atenção: você já atingiu ${percentage}% do limite anual. Margem restante: ${formatCurrency(remaining)}.`
    case 'CRITICAL':
      return `Faixa crítica: você já atingiu ${percentage}% do limite anual. Margem restante: ${formatCurrency(remaining)}. Acompanhe de perto os próximos recebimentos.`
    case 'EXCEEDED':
      if (excess === 0) {
        return `Limite atingido: você atingiu 100% do limite anual e não há margem restante. Novos recebimentos passam do limite. Procure seu contador para avaliar o desenquadramento do MEI.`
      }
      return `Limite excedido: seu faturamento passou o limite em ${formatCurrency(excess)}. Procure seu contador para avaliar o desenquadramento do MEI.`
  }
}
