import { useState, useEffect, useCallback } from 'react'
import { employeeApi, absenceApi } from '../api/client'
import type { EmployeeDto, AbsenceResponse, AbsenceType } from '../api/types'
import AbsenceModal from '../components/availability/AbsenceModal'

const MONTH_NAMES = [
  'Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
  'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro',
]

const ABSENCE_COLORS: Record<AbsenceType, string> = {
  FERIAS:    'bg-blue-400',
  DOENCA:    'bg-red-400',
  MATERNITY: 'bg-purple-400',
  FOLGA:     'bg-gray-400',
  OTHER:     'bg-orange-400',
}

const ABSENCE_LABELS: Record<AbsenceType, string> = {
  FERIAS:    'Férias',
  DOENCA:    'Doença',
  MATERNITY: 'Maternidade',
  FOLGA:     'Folga',
  OTHER:     'Outro',
}

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

// Build lookup: employeeId -> date -> absence
function buildCoverage(absences: AbsenceResponse[]): Record<number, Record<string, AbsenceResponse>> {
  const coverage: Record<number, Record<string, AbsenceResponse>> = {}
  for (const absence of absences) {
    const cur = new Date(absence.startDate + 'T00:00:00')
    const end = new Date(absence.endDate + 'T00:00:00')
    while (cur <= end) {
      const dateStr = cur.toISOString().slice(0, 10)
      if (!coverage[absence.employee.id]) coverage[absence.employee.id] = {}
      coverage[absence.employee.id][dateStr] = absence
      cur.setDate(cur.getDate() + 1)
    }
  }
  return coverage
}

interface ModalState {
  employee: EmployeeDto
  date: string
}

export default function AvailabilityPage() {
  const today = new Date()
  const [year, setYear] = useState(today.getFullYear())
  const [month, setMonth] = useState(today.getMonth() + 1)
  const [employees, setEmployees] = useState<EmployeeDto[]>([])
  const [absences, setAbsences] = useState<AbsenceResponse[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [modal, setModal] = useState<ModalState | null>(null)

  const prev = () => month === 1 ? (setYear(y => y - 1), setMonth(12)) : setMonth(m => m - 1)
  const next = () => month === 12 ? (setYear(y => y + 1), setMonth(1)) : setMonth(m => m + 1)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const from = `${year}-${String(month).padStart(2, '0')}-01`
      const lastDay = new Date(year, month, 0).getDate()
      const to = `${year}-${String(month).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`
      const [emps, abs] = await Promise.all([
        employeeApi.list(),
        absenceApi.list(from, to),
      ])
      setEmployees(emps)
      setAbsences(abs)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro desconhecido')
    } finally {
      setLoading(false)
    }
  }, [year, month])

  useEffect(() => { load() }, [load])

  const handleCellClick = (employee: EmployeeDto, date: string, existing: AbsenceResponse | undefined) => {
    if (existing) {
      const label = ABSENCE_LABELS[existing.type]
      const confirmed = window.confirm(
        `Remover ${label} de ${employee.name} (${existing.startDate} – ${existing.endDate})?`
      )
      if (confirmed) {
        absenceApi.delete(existing.id).then(load).catch(e => setError(String(e)))
      }
    } else {
      setModal({ employee, date })
    }
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
  const coverage = buildCoverage(absences)

  return (
    <div className="p-8">
      {/* Header */}
      <div className="flex items-center gap-3 mb-6">
        <button onClick={prev} className="w-8 h-8 flex items-center justify-center rounded hover:bg-gray-100 text-gray-500 text-lg">‹</button>
        <h1 className="text-lg font-semibold w-44 text-center">{MONTH_NAMES[month - 1]} {year}</h1>
        <button onClick={next} className="w-8 h-8 flex items-center justify-center rounded hover:bg-gray-100 text-gray-500 text-lg">›</button>
      </div>

      {/* Legend */}
      <div className="flex gap-4 mb-4">
        {Object.entries(ABSENCE_LABELS).map(([type, label]) => (
          <div key={type} className="flex items-center gap-1.5">
            <div className={`w-3 h-3 rounded-sm ${ABSENCE_COLORS[type as AbsenceType]}`} />
            <span className="text-xs text-gray-500">{label}</span>
          </div>
        ))}
      </div>

      {error && (
        <p className="text-sm text-red-600 mb-4 bg-red-50 border border-red-200 rounded px-3 py-2">{error}</p>
      )}

      {loading ? (
        <p className="text-sm text-gray-400">A carregar...</p>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-gray-200">
          <table className="border-collapse text-xs min-w-full">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-200">
                <th className="sticky left-0 bg-gray-50 z-10 px-4 py-2 text-left font-semibold text-gray-500 min-w-[140px] border-r border-gray-200">
                  Funcionário
                </th>
                {days.map(d => {
                  const day = new Date(d + 'T00:00:00')
                  const weekend = isWeekend(d)
                  return (
                    <th key={d} className={`px-1 py-2 text-center font-medium min-w-[32px] ${weekend ? 'text-gray-300' : 'text-gray-500'}`}>
                      <div>{day.getDate()}</div>
                      <div className="text-gray-300 font-normal">
                        {['D','S','T','Q','Q','S','S'][day.getDay()]}
                      </div>
                    </th>
                  )
                })}
              </tr>
            </thead>
            <tbody>
              {employees.map(emp => (
                <tr key={emp.id} className="border-b border-gray-100 last:border-b-0 hover:bg-gray-50">
                  <td className="sticky left-0 bg-white z-10 px-4 py-1.5 border-r border-gray-200">
                    <div className="font-medium text-gray-800">{emp.name}</div>
                    <div className={`text-xs ${emp.role === 'F' ? 'text-blue-500' : 'text-teal-500'}`}>{emp.role === 'F' ? 'Farmacêutica' : 'Técnica'}</div>
                  </td>
                  {days.map(d => {
                    const absence = coverage[emp.id]?.[d]
                    const weekend = isWeekend(d)
                    return (
                      <td
                        key={d}
                        onClick={() => handleCellClick(emp, d, absence)}
                        className={`border border-gray-100 p-0.5 text-center cursor-pointer transition-colors
                          ${weekend ? 'bg-gray-50' : ''}
                          ${absence ? '' : 'hover:bg-blue-50'}
                        `}
                      >
                        {absence && (
                          <div
                            title={`${ABSENCE_LABELS[absence.type]}${absence.note ? ` — ${absence.note}` : ''}`}
                            className={`${ABSENCE_COLORS[absence.type]} rounded w-5 h-5 mx-auto`}
                          />
                        )}
                      </td>
                    )
                  })}
                </tr>
              ))}
            </tbody>
          </table>
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
