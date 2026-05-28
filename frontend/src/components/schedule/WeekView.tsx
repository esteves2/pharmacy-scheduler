import { useState } from 'react'
import { scheduleApi } from '../../api/client'
import type {
  WeekResponse,
  WeekStatus,
  HourStatus,
  DayType,
  AssignmentResponse,
  EmployeeSummaryResponse,
  ValidationMessageResponse,
} from '../../api/types'

// --- Helpers ---

function isoWeekMonday(isoYear: number, isoWeek: number): Date {
  const jan4 = new Date(isoYear, 0, 4)
  const dow = jan4.getDay() === 0 ? 7 : jan4.getDay()
  const monday = new Date(jan4)
  monday.setDate(jan4.getDate() - (dow - 1) + (isoWeek - 1) * 7)
  return monday
}

function weekDates(isoYear: number, isoWeek: number): string[] {
  const monday = isoWeekMonday(isoYear, isoWeek)
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(monday)
    d.setDate(monday.getDate() + i)
    return d.toISOString().slice(0, 10)
  })
}

function formatDate(dateStr: string) {
  const d = new Date(dateStr + 'T00:00:00')
  return d.toLocaleDateString('pt-PT', { day: 'numeric', month: 'short' })
}

const DAY_LABELS = ['Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb', 'Dom']

// --- Sub-components ---

function StatusBadge({ status }: { status: WeekStatus }) {
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

function HoursBadge({ hours, status }: { hours: number; status: HourStatus }) {
  const colour =
    status === 'OVERTIME' ? 'text-red-600' :
    status === 'UNDERTIME' ? 'text-amber-600' :
    'text-gray-700'
  return <span className={`text-xs font-medium ${colour}`}>{hours.toFixed(1)}h</span>
}

function RolePill({ role }: { role: string }) {
  return (
    <span className={`text-xs px-1 rounded font-medium ${role === 'F' ? 'bg-blue-100 text-blue-700' : 'bg-teal-100 text-teal-700'}`}>
      {role}
    </span>
  )
}

function DayRowBg({ dayType }: { dayType: DayType | undefined }) {
  if (dayType === 'SATURDAY') return 'bg-blue-50'
  if (dayType === 'SUNDAY') return 'bg-purple-50'
  if (dayType === 'HOLIDAY') return 'bg-orange-50'
  return 'bg-white'
}

function AssignmentCell({ assignment }: { assignment: AssignmentResponse | undefined }) {
  if (!assignment) {
    return <td className="border border-gray-100 px-2 py-2 text-center text-gray-200 text-xs">—</td>
  }
  return (
    <td className="border border-gray-100 px-2 py-2 text-center">
      <div className="text-xs font-medium text-gray-800">
        {assignment.startTime}–{assignment.endTime}
      </div>
      {assignment.breakStart && (
        <div className="text-xs text-gray-400">{assignment.breakStart}–{assignment.breakEnd}</div>
      )}
      <div className="text-xs text-gray-400">{assignment.hours.toFixed(1)}h</div>
    </td>
  )
}

function ValidationPanel({ messages }: { messages: ValidationMessageResponse[] }) {
  const [open, setOpen] = useState(true)
  if (messages.length === 0) return null

  const errors   = messages.filter(m => m.severity === 'ERROR')
  const warnings = messages.filter(m => m.severity === 'WARNING')
  const infos    = messages.filter(m => m.severity === 'INFO')

  return (
    <div className="mt-6 border border-gray-200 rounded-lg overflow-hidden">
      <button
        onClick={() => setOpen(o => !o)}
        className="w-full flex items-center justify-between px-4 py-2 bg-gray-50 text-sm font-medium text-gray-700 hover:bg-gray-100"
      >
        <span>
          Validação
          {errors.length > 0 && (
            <span className="ml-2 text-xs bg-red-100 text-red-700 px-1.5 py-0.5 rounded-full">
              {errors.length} erro{errors.length > 1 ? 's' : ''}
            </span>
          )}
          {warnings.length > 0 && (
            <span className="ml-2 text-xs bg-yellow-100 text-yellow-700 px-1.5 py-0.5 rounded-full">
              {warnings.length} aviso{warnings.length > 1 ? 's' : ''}
            </span>
          )}
          {infos.length > 0 && (
            <span className="ml-2 text-xs bg-blue-50 text-blue-500 px-1.5 py-0.5 rounded-full">
              {infos.length} nota{infos.length > 1 ? 's' : ''}
            </span>
          )}
        </span>
        <span className="text-gray-400">{open ? '▲' : '▼'}</span>
      </button>
      {open && (
        <ul className="divide-y divide-gray-100">
          {messages.map((m, i) => (
            <li key={i} className="flex items-start gap-3 px-4 py-2 text-sm">
              <span className={`mt-0.5 text-xs font-semibold shrink-0 ${
                m.severity === 'ERROR' ? 'text-red-600' :
                m.severity === 'WARNING' ? 'text-amber-600' : 'text-blue-600'
              }`}>
                {m.severity === 'ERROR' ? 'ERRO' : m.severity === 'WARNING' ? 'AVISO' : 'INFO'}
              </span>
              <span className="text-gray-600">
                {m.date && <span className="text-gray-400 mr-1">{formatDate(m.date)}</span>}
                {m.message}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

// --- Main component ---

interface Props {
  isoYear: number
  isoWeek: number
  weekData: WeekResponse | null
  loading: boolean
  error: string | null
  onBack: () => void
  onWeekDataChange: (data: WeekResponse) => void
  onDeleted: () => void
}

export default function WeekView({
  isoYear, isoWeek, weekData, loading, error, onBack, onWeekDataChange, onDeleted,
}: Props) {
  const [actionLoading, setActionLoading] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  const handleDelete = async () => {
    if (!window.confirm('Apagar este rascunho? Esta acção não pode ser desfeita.')) return
    setActionLoading(true)
    setActionError(null)
    try {
      await scheduleApi.delete(isoYear, isoWeek)
      onDeleted()
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'Erro desconhecido')
    } finally {
      setActionLoading(false)
    }
  }

  const runAction = async (fn: () => Promise<WeekResponse>) => {
    setActionLoading(true)
    setActionError(null)
    try {
      const data = await fn()
      onWeekDataChange(data)
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'Erro desconhecido')
    } finally {
      setActionLoading(false)
    }
  }

  const dates = weekDates(isoYear, isoWeek)
  const monday = isoWeekMonday(isoYear, isoWeek)
  const sunday = new Date(monday)
  sunday.setDate(monday.getDate() + 6)

  const weekTitle = `Semana ${isoWeek} · ${formatDate(dates[0])} – ${formatDate(dates[6])}`

  // Build lookup: date → employeeId → assignment
  const assignmentLookup: Record<string, Record<number, AssignmentResponse>> = {}
  const dayTypeByDate: Record<string, DayType> = {}
  if (weekData) {
    for (const day of weekData.days) {
      dayTypeByDate[day.date] = day.dayType
      assignmentLookup[day.date] = {}
      for (const a of day.assignments) {
        assignmentLookup[day.date][a.employee.id] = a
      }
    }
  }

  // Sorted employees (consistent column order)
  const employees: EmployeeSummaryResponse[] = weekData
    ? [...weekData.employeeSummaries].sort((a, b) => a.employee.id - b.employee.id)
    : []

  const hasErrors = (weekData?.validationMessages ?? []).some(m => m.severity === 'ERROR')

  return (
    <div className="p-8">
      {/* Header */}
      <div className="flex items-center gap-4 mb-6">
        <button
          onClick={onBack}
          className="text-sm text-gray-500 hover:text-gray-700 flex items-center gap-1"
        >
          ← Voltar
        </button>
        <h1 className="text-lg font-semibold text-gray-800">{weekTitle}</h1>
        {weekData && <StatusBadge status={weekData.status} />}
      </div>

      {/* Action buttons */}
      <div className="flex items-center gap-2 mb-6">
        {!weekData && (
          <button
            disabled={actionLoading}
            onClick={() => runAction(() => scheduleApi.generate(isoYear, isoWeek))}
            className="px-4 py-1.5 bg-blue-600 text-white text-sm font-medium rounded hover:bg-blue-700 disabled:opacity-50"
          >
            Gerar
          </button>
        )}
        {weekData && weekData.status === 'DRAFT' && (
          <button
            disabled={actionLoading || hasErrors}
            onClick={() => runAction(() => scheduleApi.publish(isoYear, isoWeek))}
            title={hasErrors ? 'Corrija os erros antes de publicar' : undefined}
            className="px-4 py-1.5 bg-green-600 text-white text-sm font-medium rounded hover:bg-green-700 disabled:opacity-50"
          >
            Publicar
          </button>
        )}
        {weekData && (
          <>
            <button
              disabled={actionLoading || weekData.status === 'PUBLISHED'}
              onClick={() => runAction(() => scheduleApi.regenerate(isoYear, isoWeek))}
              title={weekData.status === 'PUBLISHED' ? 'Não é possível regenerar uma semana publicada' : undefined}
              className="px-4 py-1.5 bg-white border border-gray-300 text-gray-700 text-sm font-medium rounded hover:bg-gray-50 disabled:opacity-50"
            >
              Regenerar
            </button>
            <button
              disabled={actionLoading}
              onClick={() => runAction(() => scheduleApi.replan(isoYear, isoWeek))}
              className="px-4 py-1.5 bg-white border border-gray-300 text-gray-700 text-sm font-medium rounded hover:bg-gray-50 disabled:opacity-50"
            >
              Replanejar
            </button>
            {weekData.status === 'DRAFT' && (
              <button
                disabled={actionLoading}
                onClick={handleDelete}
                className="ml-auto px-4 py-1.5 bg-white border border-red-300 text-red-600 text-sm font-medium rounded hover:bg-red-50 disabled:opacity-50"
              >
                Apagar rascunho
              </button>
            )}
          </>
        )}
      </div>

      {/* Errors */}
      {(error || actionError) && (
        <p className="text-sm text-red-600 mb-4 bg-red-50 border border-red-200 rounded px-3 py-2">
          {error ?? actionError}
        </p>
      )}

      {loading && <p className="text-sm text-gray-400">A carregar...</p>}

      {/* Grid */}
      {weekData && (
        <>
          <div className="overflow-x-auto rounded-lg border border-gray-200">
            <table className="text-sm border-collapse min-w-full">
              <thead>
                <tr className="bg-gray-50">
                  <th className="border border-gray-200 px-3 py-2 text-left text-xs font-semibold text-gray-500 w-24">
                    Dia
                  </th>
                  {employees.map(s => (
                    <th key={s.employee.id} className="border border-gray-200 px-2 py-2 text-center min-w-[100px]">
                      <div className="text-xs font-semibold text-gray-700">{s.employee.name}</div>
                      <RolePill role={s.employee.role} />
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {dates.map((date, i) => {
                  const dayType = dayTypeByDate[date]
                  const rowBg = DayRowBg({ dayType })
                  return (
                    <tr key={date} className={rowBg}>
                      <td className="border border-gray-200 px-3 py-2">
                        <div className="text-xs font-medium text-gray-700">{DAY_LABELS[i]}</div>
                        <div className="text-xs text-gray-400">{formatDate(date)}</div>
                        {dayType === 'HOLIDAY' && (
                          <div className="text-xs text-orange-500 font-medium">Feriado</div>
                        )}
                      </td>
                      {employees.map(s => (
                        <AssignmentCell
                          key={s.employee.id}
                          assignment={assignmentLookup[date]?.[s.employee.id]}
                        />
                      ))}
                    </tr>
                  )
                })}
              </tbody>
              <tfoot>
                <tr className="bg-gray-50 border-t-2 border-gray-300">
                  <td className="border border-gray-200 px-3 py-2 text-xs font-semibold text-gray-500">
                    Total
                  </td>
                  {employees.map(s => (
                    <td key={s.employee.id} className="border border-gray-200 px-2 py-2 text-center">
                      <HoursBadge hours={s.weeklyHours} status={s.status} />
                    </td>
                  ))}
                </tr>
              </tfoot>
            </table>
          </div>

          <ValidationPanel messages={weekData.validationMessages} />
        </>
      )}
    </div>
  )
}
