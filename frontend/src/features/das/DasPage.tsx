import { useState } from 'react'
import { SectionError } from '../../components/SectionError'
import { toIsoDate } from '../../lib/format'
import type { DasGuide } from './das-api'
import { DasGuideItem } from './DasGuideItem'
import { useDasGuidesQuery } from './useDas'

const YEARS_AVAILABLE = 5

const selectClass =
  'mt-1 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-slate-900 shadow-sm outline-none focus-visible:ring-2 focus-visible:ring-emerald-600'

function DasSummary({ guides }: { guides: DasGuide[] }) {
  const paid = guides.filter((guide) => guide.status === 'PAID').length
  const overdue = guides.filter((guide) => guide.status === 'OVERDUE').length

  return (
    <div className="space-y-3">
      <p className="text-slate-700">
        {paid} de {guides.length}{' '}
        {guides.length === 1 ? 'guia paga' : 'guias pagas'}
      </p>
      {overdue > 0 && (
        <p
          role="alert"
          className="rounded-lg border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-900"
        >
          {overdue === 1
            ? 'Você tem 1 guia vencida.'
            : `Você tem ${overdue} guias vencidas.`}{' '}
          Pague o quanto antes para evitar multa e juros, e registre o pagamento
          aqui.
        </p>
      )}
    </div>
  )
}

type DasPageProps = {
  /** Reference date; defaults to now. Useful in tests. */
  today?: Date
}

/** Monthly DAS guides of the year and their payment status (OF14, RN05). */
export function DasPage({ today = new Date() }: DasPageProps) {
  const currentYear = today.getFullYear()
  const [year, setYear] = useState(currentYear)
  const query = useDasGuidesQuery(year)
  const todayIso = toIsoDate(today)

  const years = Array.from(
    { length: YEARS_AVAILABLE },
    (_, index) => currentYear - index,
  )

  return (
    <main className="mx-auto w-full max-w-5xl px-4 py-8 sm:px-6 sm:py-10">
      <header>
        <h1 className="text-2xl font-bold text-slate-900 sm:text-3xl">
          Controle do DAS
        </h1>
        <p className="mt-1 text-slate-600">
          Consulte as guias mensais e registre os pagamentos para manter o MEI
          em dia.
        </p>
      </header>

      <form
        aria-label="Filtro das guias"
        onSubmit={(event) => event.preventDefault()}
        className="mt-6 grid gap-3 sm:grid-cols-3"
      >
        <div>
          <label
            htmlFor="das-year"
            className="block text-sm font-medium text-slate-800"
          >
            Ano
          </label>
          <select
            id="das-year"
            className={selectClass}
            value={year}
            onChange={(event) => setYear(Number(event.target.value))}
          >
            {years.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </div>
      </form>

      <section aria-labelledby="das-title" className="mt-6">
        <h2 id="das-title" className="text-lg font-semibold text-slate-900">
          Guias de {year}
        </h2>

        <div className="mt-3">
          {query.isPending && (
            <p role="status" className="text-slate-600">
              Carregando guias…
            </p>
          )}

          {query.isError && (
            <SectionError
              message="Não foi possível carregar as guias do DAS agora. Tente novamente."
              onRetry={() => query.refetch()}
            />
          )}

          {query.data?.length === 0 && (
            <p className="rounded-lg bg-slate-100 px-4 py-3 text-sm text-slate-700">
              Nenhuma guia encontrada para {year}.
            </p>
          )}

          {query.data && query.data.length > 0 && (
            <div className="space-y-4">
              <DasSummary guides={query.data} />
              <ul
                aria-label={`Guias do DAS de ${year}`}
                className="grid gap-3 md:grid-cols-2"
              >
                {query.data.map((guide) => (
                  <DasGuideItem
                    key={guide.id}
                    guide={guide}
                    year={year}
                    todayIso={todayIso}
                  />
                ))}
              </ul>
            </div>
          )}
        </div>
      </section>
    </main>
  )
}
