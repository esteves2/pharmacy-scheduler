import type { WeekSummaryResponse, WeekStatus } from '../../api/types'

const MONTH_NAMES = [
  'Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
  'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro',
]

const DAY_HEADERS = ['Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb', 'Dom']

const TODAY = new Date().toISOString().slice(0, 10)

function statusStyle(status: WeekStatus | null): string {
  if (status === 'PUBLISHED') return 'border-l-4 border-l-green-400'
  if (status === 'DRAFT') return 'border-l-4 border-l-yellow-400'
  return 'border-l-4 border-l-transparent'
}

function StatusBadge({ status }: { status: WeekStatus | null }) {
  if (!status) return (
    <span className="text-sm text-gray-400 bg-gray-100 px-3 py-1 rounded-full">
      Não gerada
    </span>
  )
  if (status === 'DRAFT') return (
    <span className="text-sm text-yellow-700 bg-yellow-100 px-3 py-1 rounded-full font-medium">
      Rascunho
    </span>
  )
  return (
    <span className="text-sm text-green-700 bg-green-100 px-3 py-1 rounded-full font-medium">
      Publicada
    </span>
  )
}

function daysForWeek(weekStart: string): Date[] {
  const monday = new Date(weekStart + 'T00:00:00')
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(monday)
    d.setDate(monday.getDate() + i)
    return d
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
    <div className="p-6 flex flex-col h-full min-w-[800px]">
      {/* Month header */}
      <div className="flex items-center gap-3 mb-4">
        <button onClick={prev} className="w-8 h-8 flex items-center justify-center rounded-full hover:bg-gray-100 text-gray-500 text-xl">
          ‹
        </button>
        <h1 className="text-xl font-semibold w-52 text-center text-gray-800">
          {MONTH_NAMES[month - 1]} {year}
        </h1>
        <button onClick={next} className="w-8 h-8 flex items-center justify-center rounded-full hover:bg-gray-100 text-gray-500 text-xl">
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
        <div className="flex-1 rounded-xl border border-gray-200 overflow-hidden bg-white shadow-sm flex flex-col">
          {/* Day headers */}
          <div className="grid grid-cols-[4px_repeat(7,_minmax(0,_1fr))_128px] border-b border-gray-200 bg-gray-50 shrink-0">
            <div />
            {DAY_HEADERS.map(d => (
              <div key={d} className="py-3 text-center text-sm font-semibold text-gray-400 uppercase tracking-widest">
                {d}
              </div>
            ))}
            <div />
          </div>

          {/* Week rows — grow to fill remaining height */}
          {weekSummaries.map(w => {
            const days = daysForWeek(w.weekStart)
            return (
              <button
                key={`${w.isoYear}-${w.isoWeek}`}
                onClick={() => onWeekClick(w.isoYear, w.isoWeek)}
                className={`flex-1 w-full grid grid-cols-[4px_repeat(7,_minmax(0,_1fr))_128px] items-center border-b border-gray-100 last:border-b-0 hover:bg-brand-faint transition-colors ${statusStyle(w.status)}`}
              >
                <div />
                {days.map(d => {
                  const dateStr = d.toISOString().slice(0, 10)
                  const inMonth = d.getMonth() + 1 === month
                  const isToday = dateStr === TODAY
                  return (
                    <div key={dateStr} className="text-center">
                      <span className={`text-lg w-9 h-9 inline-flex items-center justify-center rounded-full
                        ${isToday ? 'bg-brand text-white font-semibold' : ''}
                        ${!isToday && inMonth ? 'text-gray-800' : ''}
                        ${!isToday && !inMonth ? 'text-gray-300' : ''}
                      `}>
                        {d.getDate()}
                      </span>
                    </div>
                  )
                })}
                <div className="flex justify-end pr-5">
                  <StatusBadge status={w.status} />
                </div>
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}
