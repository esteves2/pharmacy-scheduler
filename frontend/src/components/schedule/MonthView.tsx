import type { WeekSummaryResponse, WeekStatus } from '../../api/types'

const MONTH_NAMES = [
  'Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
  'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro',
]

function StatusBadge({ status }: { status: WeekStatus | null }) {
  if (!status) {
    return <span className="text-xs text-gray-400">Não gerada</span>
  }
  if (status === 'DRAFT') {
    return (
      <span className="text-xs bg-yellow-100 text-yellow-800 px-2 py-0.5 rounded-full font-medium">
        Rascunho
      </span>
    )
  }
  return (
    <span className="text-xs bg-green-100 text-green-800 px-2 py-0.5 rounded-full font-medium">
      Publicada
    </span>
  )
}

function formatDate(dateStr: string) {
  return new Date(dateStr + 'T00:00:00').toLocaleDateString('pt-PT', {
    day: 'numeric',
    month: 'short',
  })
}

interface Props {
  year: number
  month: number
  weekSummaries: WeekSummaryResponse[]
  loading: boolean
  error: string | null
  onMonthChange: (year: number, month: number) => void
  onWeekClick: (isoYear: number, isoWeek: number) => void
}

export default function MonthView({
  year, month, weekSummaries, loading, error, onMonthChange, onWeekClick,
}: Props) {
  const prev = () => month === 1 ? onMonthChange(year - 1, 12) : onMonthChange(year, month - 1)
  const next = () => month === 12 ? onMonthChange(year + 1, 1) : onMonthChange(year, month + 1)

  return (
    <div className="p-8 max-w-xl">
      <div className="flex items-center gap-3 mb-6">
        <button
          onClick={prev}
          className="w-8 h-8 flex items-center justify-center rounded hover:bg-gray-100 text-gray-500"
        >
          ‹
        </button>
        <h1 className="text-lg font-semibold w-44 text-center">
          {MONTH_NAMES[month - 1]} {year}
        </h1>
        <button
          onClick={next}
          className="w-8 h-8 flex items-center justify-center rounded hover:bg-gray-100 text-gray-500"
        >
          ›
        </button>
      </div>

      {error && (
        <p className="text-sm text-red-600 mb-4 bg-red-50 border border-red-200 rounded px-3 py-2">
          {error}
        </p>
      )}

      {loading ? (
        <p className="text-sm text-gray-400">A carregar...</p>
      ) : (
        <div className="flex flex-col gap-2">
          {weekSummaries.map(w => (
            <button
              key={`${w.isoYear}-${w.isoWeek}`}
              onClick={() => onWeekClick(w.isoYear, w.isoWeek)}
              className="flex items-center justify-between bg-white border border-gray-200 rounded-lg px-4 py-3 hover:border-blue-300 hover:bg-blue-50 transition-colors text-left w-full"
            >
              <div className="flex items-baseline gap-3">
                <span className="text-sm font-medium text-gray-800">
                  Semana {w.isoWeek}
                </span>
                <span className="text-xs text-gray-400">
                  {formatDate(w.weekStart)} – {formatDate(w.weekEnd)}
                </span>
              </div>
              <StatusBadge status={w.status} />
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
