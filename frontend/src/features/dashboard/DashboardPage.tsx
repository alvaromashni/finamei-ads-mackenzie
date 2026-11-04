import { Link } from 'react-router-dom'
import { formatMonthYear, toYearMonth } from '../../lib/format'
import { BalanceCards } from './BalanceCards'
import { RevenueCard } from './RevenueCard'

type DashboardPageProps = {
  /** Reference date; defaults to now. Useful in tests. */
  today?: Date
}

const actionBase =
  'rounded-lg px-4 py-2 text-center font-semibold touch:flex touch:min-h-11 touch:items-center touch:justify-center focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700'

export function DashboardPage({ today = new Date() }: DashboardPageProps) {
  return (
    <main className="mx-auto w-full max-w-5xl px-4 py-8 sm:px-6 sm:py-10">
      <header>
        <h1 className="text-2xl font-bold text-slate-900 sm:text-3xl">
          Painel
        </h1>
        <p className="mt-1 text-slate-600">
          Visão geral de {formatMonthYear(today)}
        </p>
      </header>

      <div className="mt-6 space-y-4">
        <BalanceCards month={toYearMonth(today)} />
        <RevenueCard year={today.getFullYear()} />
      </div>

      <div className="mt-6 flex flex-col gap-3 sm:flex-row">
        <Link
          to="/lancamentos?tipo=receita"
          className={`${actionBase} bg-emerald-700 text-white hover:bg-emerald-800`}
        >
          + Nova receita
        </Link>
        <Link
          to="/lancamentos?tipo=despesa"
          className={`${actionBase} border border-emerald-700 text-emerald-800 hover:bg-emerald-50`}
        >
          + Nova despesa
        </Link>
      </div>
    </main>
  )
}
