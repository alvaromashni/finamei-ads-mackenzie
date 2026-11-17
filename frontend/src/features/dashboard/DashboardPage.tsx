import { Link } from 'react-router-dom'
import { SectionError } from '../../components/SectionError'
import { formatDateTime, formatMonthYear, toYearMonth } from '../../lib/format'
import { useAuth } from '../auth/useAuth'
import { useDasGuidesQuery } from '../das/useDas'
import { BalanceCards } from './BalanceCards'
import { RevenueCard } from './RevenueCard'

type DashboardPageProps = {
  /** Reference date; defaults to now. Useful in tests. */
  today?: Date
}

const actionBase =
  'rounded-lg px-4 py-2 text-center font-semibold touch:flex touch:min-h-11 touch:items-center touch:justify-center focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700'

function DasPendingIndicator({ year }: { year: number }) {
  const query = useDasGuidesQuery(year)
  const guides = query.data ?? []
  const pending = guides.filter((guide) => guide.status !== 'PAID')
  const overdue = guides.filter((guide) => guide.status === 'OVERDUE')

  return (
    <section
      aria-label="Pendências do DAS"
      className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm sm:p-5"
    >
      <h2 className="font-semibold text-slate-900">Pendências do DAS</h2>
      {query.isPending && (
        <p role="status" className="mt-2 text-sm text-slate-600">
          Carregando pendências do DAS…
        </p>
      )}
      {query.isError && (
        <div className="mt-2">
          <SectionError
            message="Não foi possível carregar as pendências do DAS."
            onRetry={() => query.refetch()}
          />
        </div>
      )}
      {query.data && pending.length === 0 && (
        <p className="mt-2 text-sm text-slate-700">Nenhuma pendência do DAS.</p>
      )}
      {query.data && pending.length > 0 && (
        <p role="status" className="mt-2 text-sm text-slate-800">
          Você tem {pending.length}{' '}
          {pending.length === 1 ? 'guia pendente' : 'guias pendentes'} do DAS.
          {overdue.length > 0 &&
            (overdue.length === 1
              ? ' Você tem 1 guia vencida.'
              : ` Você tem ${overdue.length} guias vencidas.`)}
        </p>
      )}
      <Link
        to="/das"
        className="mt-3 inline-block rounded text-sm font-semibold text-emerald-800 underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700"
      >
        Ver guias do DAS
      </Link>
    </section>
  )
}

export function DashboardPage({ today = new Date() }: DashboardPageProps) {
  const { user, lastAccessAt } = useAuth()

  return (
    <main className="mx-auto w-full max-w-5xl px-4 py-8 sm:px-6 sm:py-10">
      <header>
        <h1 className="text-2xl font-bold text-slate-900 sm:text-3xl">
          Painel
        </h1>
        {user?.name && (
          <p className="mt-2 text-lg font-semibold text-slate-900">
            Olá, {user.name}
          </p>
        )}
        {lastAccessAt && (
          <p className="mt-1 text-sm text-slate-600">
            Último acesso: {formatDateTime(lastAccessAt)}
          </p>
        )}
        <p className="mt-1 text-slate-600">
          Visão geral de {formatMonthYear(today)}
        </p>
      </header>

      <div className="mt-6 space-y-4">
        <BalanceCards month={toYearMonth(today)} />
        <RevenueCard year={today.getFullYear()} />
        <DasPendingIndicator year={today.getFullYear()} />
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
