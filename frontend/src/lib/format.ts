const currencyFormatter = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
})

export function formatCurrency(value: number) {
  return currencyFormatter.format(value)
}

const monthFormatter = new Intl.DateTimeFormat('pt-BR', {
  month: 'long',
  year: 'numeric',
})

/** "setembro de 2026" */
export function formatMonthYear(date: Date) {
  return monthFormatter.format(date)
}

/** "2026-09", the month format used by the API. */
export function toYearMonth(date: Date) {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  return `${date.getFullYear()}-${month}`
}

/** "2026-09-15", the date format used by the API (local time, no timezone shift). */
export function toIsoDate(date: Date) {
  const day = String(date.getDate()).padStart(2, '0')
  return `${toYearMonth(date)}-${day}`
}

const dateTimeFormatter = new Intl.DateTimeFormat('pt-BR', {
  dateStyle: 'short',
  timeStyle: 'short',
  timeZone: 'America/Sao_Paulo',
})

/** "17/11/2026 às 17:40" in Brasília. */
export function formatDateTime(isoDateTime: string) {
  const date = new Date(isoDateTime)
  if (Number.isNaN(date.getTime())) return isoDateTime
  return dateTimeFormatter.format(date).replace(',', ' às')
}

/** "2026-09-15" -> "15/09/2026", without going through Date to avoid timezone shifts. */
export function formatDate(isoDate: string) {
  const [year, month, day] = isoDate.split('-')
  return `${day}/${month}/${year}`
}
