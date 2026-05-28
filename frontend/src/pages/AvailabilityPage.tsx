import { useState, useEffect, useCallback } from 'react'
import { employeeApi, absenceApi } from '../api/client'
import type { EmployeeDetailDto, AbsenceResponse, AbsenceType } from '../api/types'
import AbsenceModal from '../components/availability/AbsenceModal'

const MONTH_NAMES = [
  'Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
  'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro',
]

const ABSENCE_COLORS: Record<AbsenceType, string> = {
  FERIAS:    'bg-blue-400',
  DOENCA:    'bg-red-400',
  MATERNITY: 'bg-purple-400',
  FOLGA:     'bg-slate-400',
  OTHER:     'bg-orange-400',
}

const ABSENCE_LABELS: Record<AbsenceType, string> = {
  FERIAS:    'Férias',
  DOENCA:    'Doença',
  MATERNITY: 'Maternidade',
  FOLGA:     'Folga',
  OTHER:     'Outro',
}

const CELL_W = 36     // px per day column
const NAME_W = 160    // px for the sticky name column
const ROW_H  = 44     // px per employee row
const TODAY  = new Date().toISOString().slice(0, 10)

function monthDays(year: number, month: number): string[] {
  const days: string[] = []
  const d = new Date(year, month - 1, 1)
  while (d.getMonth() === month - 1) {
    days.push(d.toISOString().slice(0, 10))
    d.setDate(d.getDate() + 1)
  }
  return days
}

function isWeekend(dateStr: string) {
  const dow = new Date(dateStr + 'T00:00:00').getDay()
  return dow === 0 || dow === 6
}

function clamp(val: string, min: string, max: string) {
  if (val < min) return min
  if (val > max) return max
  return val
}

interface BarProps {
  absence: AbsenceResponse
  days: string[]
  onDelete: (absence: AbsenceResponse) => void
}

function AbsenceBar({ absence, days, onDelete }: BarProps) {
  const first = days[0]
  const last  = days[days.length - 1]

  const visibleStart = clamp(absence.startDate, first, last)
  const visibleEnd   = clamp(absence.endDate,   first, last)

  const startIdx = days.indexOf(visibleStart)
  const endIdx   = days.indexOf(visibleEnd)
  if (startIdx === -1 || endIdx === -1) return null

  const left  = startIdx * CELL_W + 3
  const width = (endIdx - startIdx + 1) * CELL_W - 6

  const handleClick = (e: React.MouseEvent) => {
    e.stopPropagation()
    const label = ABSENCE_LABELS[absence.type]
    if (window.confirm(`Remover ${label} de ${absence.employee.name} (${absence.startDate} – ${absence.endDate})?`)) {
      onDelete(absence)
    }
  }

  return (
    <div
      onClick={handleClick}
      title={`${ABSENCE_LABELS[absence.type]}${absence.note ? ` — ${absence.note}` : ''}\n${absence.startDate} – ${absence.endDate}`}
      style={{ left, width, top: '50%', transform: 'translateY(-50%)', height: 26 }}
      className={`absolute ${ABSENCE_COLORS[absence.type]} rounded-full flex items-center px-3 cursor-pointer hover:brightness-95 transition-all overflow-hidden z-10`}
    >
      <span className="text-white text-xs font-medium truncate select-none">
        {ABSENCE_LABELS[absence.type]}
      </span>
    </div>
  )
}

interface ModalState {
  employee: EmployeeDetailDto
  date: string
}

export default function AvailabilityPage() {
  const today = new Date()
  const [year, setYear]         = useState(today.getFullYear())
  const [month, setMonth]       = useState(today.getMonth() + 1)
  const [employees, setEmployees] = useState<EmployeeDetailDto[]>([])
  const [absences, setAbsences]   = useState<AbsenceResponse[]>([])
  const [loading, setLoading]     = useState(false)
  const [error, setError]         = useState<string | null>(null)
  const [modal, setModal]         = useState<ModalState | null>(null)

  const prev = () => month === 1 ? (setYear(y => y - 1), setMonth(12)) : setMonth(m => m - 1)
  const next = () => month === 12 ? (setYear(y => y + 1), setMonth(1)) : setMonth(m => m + 1)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const mm   = String(month).padStart(2, '0')
      const last = new Date(year, month, 0).getDate()
      const from = `${year}-${mm}-01`
      const to   = `${year}-${mm}-${String(last).padStart(2, '0')}`
      const [emps, abs] = await Promise.all([employeeApi.list(), absenceApi.list(from, to)])
      setEmployees(emps)
      setAbsences(abs)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro desconhecido')
    } finally {
      setLoading(false)
    }
  }, [year, month])

  useEffect(() => { load() }, [load])

  const handleDelete = (absence: AbsenceResponse) => {
    absenceApi.delete(absence.id).then(load).catch(e => setError(String(e)))
  }

  const handleCellClick = (employee: EmployeeDetailDto, date: string) => {
    setModal({ employee, date })
  }

  const handleModalConfirm = async (
    startDate: string, endDate: string, type: AbsenceType, note: string | null
  ) => {
    if (!modal) return
    try {
      await absenceApi.create({ employeeId: modal.employee.id, startDate, endDate, type, note })
      setModal(null)
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro desconhecido')
    }
  }

  const days = monthDays(year, month)
  const totalGridW = days.length * CELL_W

  // Group absences by employee
  const absencesByEmployee: Record<number, AbsenceResponse[]> = {}
  for (const a of absences) {
    ;(absencesByEmployee[a.employee.id] ??= []).push(a)
  }

  return (
    <div className="p-8">
      {/* Header */}
      <div className="flex items-center gap-3 mb-4">
        <button onClick={prev} className="w-8 h-8 flex items-center justify-center rounded hover:bg-gray-100 text-gray-500 text-lg">‹</button>
        <h1 className="text-lg font-semibold w-44 text-center">{MONTH_NAMES[month - 1]} {year}</h1>
        <button onClick={next} className="w-8 h-8 flex items-center justify-center rounded hover:bg-gray-100 text-gray-500 text-lg">›</button>
      </div>

      {/* Legend */}
      <div className="flex gap-4 mb-5">
        {(Object.entries(ABSENCE_LABELS) as [AbsenceType, string][]).map(([type, label]) => (
          <div key={type} className="flex items-center gap-1.5">
            <div className={`w-3 h-3 rounded-full ${ABSENCE_COLORS[type]}`} />
            <span className="text-xs text-gray-500">{label}</span>
          </div>
        ))}
      </div>

      {error && <p className="text-sm text-red-600 mb-4 bg-red-50 border border-red-200 rounded px-3 py-2">{error}</p>}
      {loading && <p className="text-sm text-gray-400">A carregar...</p>}

      {!loading && (
        <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
          {/* Day header */}
          <div className="flex border-b border-gray-200 bg-gray-50" style={{ minWidth: NAME_W + totalGridW }}>
            <div className="shrink-0 border-r border-gray-200 px-4 py-2 text-xs font-semibold text-gray-400 uppercase tracking-wide"
              style={{ width: NAME_W }}>
              Funcionário
            </div>
            <div className="relative flex" style={{ width: totalGridW }}>
              {days.map(d => {
                const day = new Date(d + 'T00:00:00')
                const weekend = isWeekend(d)
                const isToday = d === TODAY
                return (
                  <div key={d} style={{ width: CELL_W }}
                    className={`shrink-0 py-1.5 text-center ${weekend ? 'bg-gray-100' : ''}`}>
                    <div className={`text-xs font-semibold mx-auto w-6 h-6 flex items-center justify-center rounded-full
                      ${isToday ? 'bg-blue-500 text-white' : weekend ? 'text-gray-300' : 'text-gray-600'}`}>
                      {day.getDate()}
                    </div>
                    <div className={`text-xs ${weekend ? 'text-gray-300' : 'text-gray-300'}`}>
                      {['D','S','T','Q','Q','S','S'][day.getDay()]}
                    </div>
                  </div>
                )
              })}
            </div>
          </div>

          {/* Employee rows */}
          {employees.map(emp => {
            const empAbsences = absencesByEmployee[emp.id] ?? []
            return (
              <div key={emp.id} className="flex border-b border-gray-100 last:border-b-0"
                style={{ minWidth: NAME_W + totalGridW }}>
                {/* Name */}
                <div className="shrink-0 border-r border-gray-200 px-4 flex flex-col justify-center"
                  style={{ width: NAME_W, height: ROW_H }}>
                  <div className="text-sm font-medium text-gray-800">{emp.name}</div>
                  <div className={`text-xs ${emp.role === 'F' ? 'text-blue-400' : 'text-teal-400'}`}>
                    {emp.role === 'F' ? 'Farmacêutica' : 'Técnica'}
                  </div>
                </div>

                {/* Grid area */}
                <div className="relative" style={{ width: totalGridW, height: ROW_H }}>
                  {/* Clickable day cells (background) */}
                  <div className="absolute inset-0 flex">
                    {days.map(d => (
                      <div
                        key={d}
                        style={{ width: CELL_W }}
                        className={`shrink-0 h-full cursor-pointer hover:bg-blue-50 transition-colors
                          ${isWeekend(d) ? 'bg-gray-50' : ''}`}
                        onClick={() => handleCellClick(emp, d)}
                      />
                    ))}
                  </div>

                  {/* Absence bars */}
                  {empAbsences.map(absence => (
                    <AbsenceBar
                      key={absence.id}
                      absence={absence}
                      days={days}
                      onDelete={handleDelete}
                    />
                  ))}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {modal && (
        <AbsenceModal
          employee={modal.employee}
          defaultStart={modal.date}
          defaultEnd={modal.date}
          onConfirm={handleModalConfirm}
          onCancel={() => setModal(null)}
        />
      )}
    </div>
  )
}
