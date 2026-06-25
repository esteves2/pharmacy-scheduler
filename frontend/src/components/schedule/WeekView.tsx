import { useState, useEffect } from 'react'
import { scheduleApi, absenceApi } from '../../api/client'
import type {
  WeekResponse,
  WeekStatus,
  HourStatus,
  DayType,
  AssignmentResponse,
  EmployeeSummaryResponse,
  ValidationMessageResponse,
  EmployeeDto,
  WeekWriteRequest,
  AbsenceType,
} from '../../api/types'
import AssignmentModal from './AssignmentModal'

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
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    return `${y}-${m}-${day}`
  })
}

function formatDate(dateStr: string) {
  const d = new Date(dateStr + 'T00:00:00')
  return d.toLocaleDateString('pt-PT', { day: 'numeric', month: 'short' })
}

function computeHours(start: string, end: string, bStart: string | null, bEnd: string | null): number {
  const toMins = (t: string) => { const [h, m] = t.split(':').map(Number); return h * 60 + m }
  let mins = toMins(end) - toMins(start)
  if (bStart && bEnd) mins -= toMins(bEnd) - toMins(bStart)
  return Math.max(0, mins / 60)
}

const DAY_LABELS = ['Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sab', 'Dom']

const ABSENCE_STYLES: Record<AbsenceType, { cell: string; label: string; text: string }> = {
  MATERNITY: { cell: 'bg-pink-50',   label: 'Maternidade', text: 'text-pink-300'  },
  SICK:      { cell: 'bg-red-50',    label: 'Doença',      text: 'text-red-300'   },
  FERIAS:    { cell: 'bg-green-50',  label: 'Férias',      text: 'text-green-400' },
  FOLGA:     { cell: 'bg-gray-100',  label: 'Folga',       text: 'text-gray-300'  },
  BIRTHDAY:  { cell: 'bg-yellow-50', label: 'Aniversário', text: 'text-yellow-500'},
  OTHER:     { cell: 'bg-orange-50', label: 'Ausência',    text: 'text-orange-400'},
}

function StatusBadge({ status }: { status: WeekStatus }) {
  if (status === 'DRAFT') {
    return <span className="text-xs bg-yellow-100 text-yellow-800 px-2 py-0.5 rounded-full font-medium">Rascunho</span>
  }
  return <span className="text-xs bg-green-100 text-green-800 px-2 py-0.5 rounded-full font-medium">Publicada</span>
}

function HoursBadge({ hours, status }: { hours: number; status: HourStatus }) {
  const colour = status === 'OVERTIME' ? 'text-red-600' : status === 'UNDERTIME' ? 'text-amber-600' : 'text-gray-700'
  return <span className={`text-xs font-medium ${colour}`}>{hours.toFixed(1)}h</span>
}

function RolePill({ role }: { role: string }) {
  return (
    <span className={`text-xs px-1 rounded font-medium ${role === 'F' ? 'bg-brand-faint text-brand' : 'bg-teal-100 text-teal-700'}`}>
      {role}
    </span>
  )
}

interface AssignmentCellProps {
  assignment: AssignmentResponse | null
  absence: AbsenceType | null
  editable: boolean
  onClick: () => void
}

function AssignmentCell({ assignment, absence, editable, onClick }: AssignmentCellProps) {
  const absenceStyle = absence ? ABSENCE_STYLES[absence] : null
  const bgClass      = absenceStyle?.cell ?? ''
  const interactive  = editable ? 'cursor-pointer hover:brightness-95 transition-all' : ''

  if (!assignment) {
    return (
      <td className={`border border-gray-200 px-2 py-2 text-center text-xs ${bgClass} ${interactive}`}
          onClick={editable ? onClick : undefined}>
        {absenceStyle
          ? <span className={`${absenceStyle.text} font-medium`}>{absenceStyle.label}</span>
          : <span className="text-gray-300">—</span>
        }
      </td>
    )
  }
  return (
    <td className={`border border-gray-200 px-2 py-2 text-center ${bgClass} ${interactive}`}
        onClick={editable ? onClick : undefined}>
      <div className="text-xs font-medium text-gray-800">{assignment.startTime}–{assignment.endTime}</div>
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
      <button onClick={() => setOpen(o => !o)}
        className="w-full flex items-center justify-between px-4 py-2 bg-gray-50 text-sm font-medium text-gray-700 hover:bg-gray-100">
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
            <span className="ml-2 text-xs bg-brand-faint text-brand px-1.5 py-0.5 rounded-full">
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
                m.severity === 'ERROR' ? 'text-red-600' : m.severity === 'WARNING' ? 'text-amber-600' : 'text-blue-600'
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

interface ModalState { date: string; employee: EmployeeDto; employeeIndex: number }

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

export default function WeekView({ isoYear, isoWeek, weekData, loading, error, onBack, onWeekDataChange, onDeleted }: Props) {
  const [actionLoading, setActionLoading] = useState(false)
  const [actionError,   setActionError]   = useState<string | null>(null)
  const [localMap,      setLocalMap]      = useState<Record<string, AssignmentResponse | null>>({})
  const [isDirty,       setIsDirty]       = useState(false)
  const [modal,         setModal]         = useState<ModalState | null>(null)
  const [saveIssues,       setSaveIssues]       = useState<string[] | null>(null)
  const [showDeleteConfirm,  setShowDeleteConfirm]  = useState(false)
  const [showPublishConfirm, setShowPublishConfirm] = useState(false)
  // absence map: "${empId}|${date}" -> AbsenceType
  const [absenceMap,    setAbsenceMap]    = useState<Record<string, AbsenceType>>({})

  const dates = weekDates(isoYear, isoWeek)

  useEffect(() => {
    if (!weekData) { setLocalMap({}); setIsDirty(false); return }
    const map: Record<string, AssignmentResponse | null> = {}
    for (const day of weekData.days)
      for (const a of day.assignments)
        map[`${day.date}|${a.employee.id}`] = a
    setLocalMap(map)
    setIsDirty(false)
  }, [weekData])

  // Fetch absences covering this week
  useEffect(() => {
    absenceApi.list(dates[0], dates[6]).then(absences => {
      const map: Record<string, AbsenceType> = {}
      for (const abs of absences) {
        for (const date of dates) {
          if (date >= abs.startDate && date <= abs.endDate) {
            map[`${abs.employee.id}|${date}`] = abs.type
          }
        }
      }
      setAbsenceMap(map)
    }).catch(() => {/* non-critical, fail silently */})
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isoYear, isoWeek])

  const buildSaveRequest = (): WeekWriteRequest => ({
    assignments: Object.entries(localMap)
      .filter(([, a]) => a !== null)
      .map(([key, a]) => {
        const date = key.split('|')[0]
        return { id: a!.id < 0 ? null : a!.id, employeeId: a!.employee.id, date,
                 startTime: a!.startTime, endTime: a!.endTime, breakStart: a!.breakStart, breakEnd: a!.breakEnd }
      }),
  })

  const runAction = async (fn: () => Promise<WeekResponse>) => {
    setActionLoading(true); setActionError(null)
    try { onWeekDataChange(await fn()) }
    catch (e) { setActionError(e instanceof Error ? e.message : 'Erro desconhecido') }
    finally { setActionLoading(false) }
  }

  const collectSaveIssues = (): string[] => {
    if (!weekData) return []
    const issues: string[] = []
    for (const m of weekData.validationMessages)
      if (m.severity === 'ERROR' || m.severity === 'WARNING') issues.push(m.message)
    for (const s of weekData.employeeSummaries) {
      if (s.status === 'OVERTIME')  issues.push(`${s.employee.name}: horas a mais (${s.weeklyHours.toFixed(1)}h)`)
      if (s.status === 'UNDERTIME') issues.push(`${s.employee.name}: horas a menos (${s.weeklyHours.toFixed(1)}h)`)
    }
    return issues
  }

  const doSave = async () => {
    setActionLoading(true); setActionError(null); setSaveIssues(null)
    try { onWeekDataChange(await scheduleApi.save(isoYear, isoWeek, buildSaveRequest())) }
    catch (e) { setActionError(e instanceof Error ? e.message : 'Erro desconhecido') }
    finally { setActionLoading(false) }
  }

  const handleSave = () => {
    const issues = collectSaveIssues()
    if (issues.length > 0) { setSaveIssues(issues); return }
    doSave()
  }

  const handleDelete = async () => {
    setActionLoading(true); setActionError(null); setShowDeleteConfirm(false)
    try { await scheduleApi.delete(isoYear, isoWeek); onDeleted() }
    catch (e) { setActionError(e instanceof Error ? e.message : 'Erro desconhecido') }
    finally { setActionLoading(false) }
  }

  const handleCellClick = (date: string, employee: EmployeeDto, employeeIndex: number) =>
    setModal({ date, employee, employeeIndex })

  const handleNavPrev = () => {
    if (!modal || modal.employeeIndex <= 0) return
    const prev = employees[modal.employeeIndex - 1]
    setModal({ date: modal.date, employee: prev.employee, employeeIndex: modal.employeeIndex - 1 })
  }

  const handleNavNext = () => {
    if (!modal || modal.employeeIndex >= employees.length - 1) return
    const next = employees[modal.employeeIndex + 1]
    setModal({ date: modal.date, employee: next.employee, employeeIndex: modal.employeeIndex + 1 })
  }

  const handleModalConfirm = (start: string, end: string, bStart: string | null, bEnd: string | null) => {
    if (!modal) return
    const key = `${modal.date}|${modal.employee.id}`
    const existing = localMap[key]
    setLocalMap(prev => ({
      ...prev,
      [key]: { id: existing?.id ?? -1, employee: modal.employee,
               startTime: start, endTime: end, breakStart: bStart, breakEnd: bEnd,
               hours: computeHours(start, end, bStart, bEnd) },
    }))
    setIsDirty(true); setModal(null)
  }

  const handleModalDelete = () => {
    if (!modal) return
    setLocalMap(prev => ({ ...prev, [`${modal.date}|${modal.employee.id}`]: null }))
    setIsDirty(true); setModal(null)
  }

  const weekTitle = `Semana ${isoWeek} · ${formatDate(dates[0])} – ${formatDate(dates[6])}`

  const dayTypeByDate: Record<string, DayType> = {}
  if (weekData) for (const day of weekData.days) dayTypeByDate[day.date] = day.dayType

  const avgStart = (empId: number): number => {
    const starts = dates
      .map(date => localMap[`${date}|${empId}`]?.startTime)
      .filter(Boolean)
      .map(t => { const [h, m] = t!.split(':').map(Number); return h * 60 + m })
    return starts.length ? starts.reduce((a, b) => a + b, 0) / starts.length : 9999
  }

  const employees: EmployeeSummaryResponse[] = weekData
    ? [...weekData.employeeSummaries].sort((a, b) => avgStart(a.employee.id) - avgStart(b.employee.id))
    : []

  const isEditable = weekData?.status === 'DRAFT'
  const hasErrors  = (weekData?.validationMessages ?? []).some(m => m.severity === 'ERROR')

  return (
    <div className="p-8 min-w-[800px]">
      <div className="flex items-center gap-4 mb-6">
        <button onClick={onBack} className="text-sm text-gray-500 hover:text-gray-700">← Voltar</button>
        <h1 className="text-lg font-semibold text-gray-800">{weekTitle}</h1>
        {weekData && <StatusBadge status={weekData.status} />}
        {isDirty && (
          <span className="text-xs text-amber-600 bg-amber-50 border border-amber-200 px-2 py-0.5 rounded-full">
            alteracoes por guardar
          </span>
        )}
      </div>

      <div className="flex items-center gap-2 mb-6">
        {!weekData && (
          <button disabled={actionLoading} onClick={() => runAction(() => scheduleApi.generate(isoYear, isoWeek))}
            className="px-4 py-1.5 bg-brand text-white text-sm font-medium rounded hover:bg-brand-dark disabled:opacity-50">
            Gerar
          </button>
        )}
        {weekData && weekData.status === 'DRAFT' && (
          <>
            {isDirty && (
              <button disabled={actionLoading} onClick={handleSave}
                className="px-4 py-1.5 bg-brand text-white text-sm font-medium rounded hover:bg-brand-dark disabled:opacity-50">
                Guardar alteracoes
              </button>
            )}
            <button
              disabled={actionLoading || isDirty}
              onClick={() => hasErrors ? setShowPublishConfirm(true) : runAction(() => scheduleApi.publish(isoYear, isoWeek))}
              title={isDirty ? 'Guarde as alteracoes antes de publicar' : undefined}
              className="px-4 py-1.5 bg-green-600 text-white text-sm font-medium rounded hover:bg-green-700 disabled:opacity-50">
              Publicar
            </button>
          </>
        )}
        {weekData && (
          <>
            <button disabled={actionLoading}
              onClick={() => runAction(weekData.status === 'DRAFT'
                ? () => scheduleApi.regenerate(isoYear, isoWeek)
                : () => scheduleApi.replan(isoYear, isoWeek))}
              title={weekData.status === 'DRAFT'
                ? 'Gerar novamente do zero'
                : 'Recalcular horário com base em ausências e alterações recentes'}
              className="px-4 py-1.5 bg-white border border-gray-300 text-gray-700 text-sm font-medium rounded hover:bg-gray-50 disabled:opacity-50">
              Regenerar horário
            </button>
            <button disabled={actionLoading} onClick={() => setShowDeleteConfirm(true)}
              className="ml-auto px-4 py-1.5 bg-white border border-red-300 text-red-600 text-sm font-medium rounded hover:bg-red-50 disabled:opacity-50">
              {weekData.status === 'DRAFT' ? 'Apagar rascunho' : 'Apagar horário'}
            </button>
          </>
        )}
      </div>

      {(error || actionError) && (
        <p className="text-sm text-red-600 mb-4 bg-red-50 border border-red-200 rounded px-3 py-2">{error ?? actionError}</p>
      )}
      {loading && <p className="text-sm text-gray-400">A carregar...</p>}

      {weekData && (
        <>
          <div className="overflow-x-auto rounded-lg border border-gray-200">
            <table className="text-sm border-collapse min-w-full">
              <thead>
                <tr>
                  <th className="border border-gray-200 px-3 py-2 text-left text-xs font-semibold text-gray-500 bg-gray-100 w-28">
                    Data
                  </th>
                  {employees.map(s => (
                    <th key={s.employee.id} className="border border-gray-200 px-2 py-2 text-center min-w-[100px] bg-gray-100">
                      <div className="text-xs font-semibold text-gray-700">{s.employee.name}</div>
                      <RolePill role={s.employee.role} />
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {dates.map((date, i) => {
                  const dayType = dayTypeByDate[date]
                  const bg = dayType === 'SATURDAY' ? 'bg-blue-50' : dayType === 'SUNDAY' ? 'bg-purple-50' : dayType === 'HOLIDAY' ? 'bg-orange-50' : 'bg-white'
                  return (
                    <tr key={date}>
                      <td className={`border border-gray-200 px-3 py-2 w-28 ${bg}`}>
                        <div className="text-xs font-semibold text-gray-700">{DAY_LABELS[i]}</div>
                        <div className="text-xs text-gray-400">{formatDate(date)}</div>
                        {dayType === 'HOLIDAY' && <div className="text-xs text-orange-500 font-medium">Feriado</div>}
                      </td>
                      {employees.map((s, empIdx) => {
                        const mapKey = `${date}|${s.employee.id}`
                        const absKey = `${s.employee.id}|${date}`
                        return (
                          <AssignmentCell
                            key={s.employee.id}
                            assignment={mapKey in localMap ? localMap[mapKey] : null}
                            absence={absenceMap[absKey] ?? null}
                            editable={isEditable}
                            onClick={() => handleCellClick(date, s.employee, empIdx)}
                          />
                        )
                      })}
                    </tr>
                  )
                })}
                <tr className="bg-gray-50">
                  <td className="border border-gray-200 px-3 py-2 text-xs font-semibold text-gray-500">
                    {isDirty ? <span title="Guarde para actualizar os totais">Total *</span> : 'Total'}
                  </td>
                  {employees.map(s => (
                    <td key={s.employee.id} className="border border-gray-200 px-2 py-2 text-center">
                      <HoursBadge hours={s.weeklyHours} status={s.status} />
                    </td>
                  ))}
                </tr>
              </tbody>
            </table>
          </div>
          <ValidationPanel messages={weekData.validationMessages} />
        </>
      )}

      {modal && (
        <AssignmentModal
          key={`${modal.date}|${modal.employee.id}`}
          employeeName={modal.employee.name}
          date={modal.date}
          existing={localMap[`${modal.date}|${modal.employee.id}`] ?? null}
          hasPrev={modal.employeeIndex > 0}
          hasNext={modal.employeeIndex < employees.length - 1}
          onNavPrev={handleNavPrev}
          onNavNext={handleNavNext}
          onConfirm={handleModalConfirm}
          onDelete={handleModalDelete}
          onCancel={() => setModal(null)}
        />
      )}

      {saveIssues && (
        <>
          <div className="fixed inset-0 bg-black/20 z-40" onClick={() => setSaveIssues(null)} />
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
            <div className="bg-white rounded-lg shadow-xl w-full max-w-sm">
              <div className="px-5 py-4 border-b border-gray-200">
                <h3 className="text-sm font-semibold text-gray-800">Guardar com avisos?</h3>
                <p className="text-xs text-gray-500 mt-0.5">O horario tem os seguintes problemas:</p>
              </div>
              <ul className="px-5 py-3 flex flex-col gap-1.5 max-h-48 overflow-y-auto">
                {saveIssues.map((issue, i) => (
                  <li key={i} className="text-xs text-amber-700 flex items-start gap-1.5">
                    <span className="mt-0.5 shrink-0">!</span>
                    <span>{issue}</span>
                  </li>
                ))}
              </ul>
              <div className="px-5 py-3 border-t border-gray-200 flex gap-2">
                <button onClick={doSave}
                  className="flex-1 bg-brand text-white text-sm font-medium py-2 rounded hover:bg-brand-dark">
                  Guardar mesmo assim
                </button>
                <button onClick={() => setSaveIssues(null)}
                  className="flex-1 border border-gray-300 text-gray-700 text-sm font-medium py-2 rounded hover:bg-gray-50">
                  Cancelar
                </button>
              </div>
            </div>
          </div>
        </>
      )}

 
      {showPublishConfirm && weekData && (
        <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50"
          onClick={() => setShowPublishConfirm(false)}>
          <div className="bg-white rounded-xl shadow-xl p-6 w-80 flex flex-col gap-4"
            onClick={e => e.stopPropagation()}>
            <div>
              <p className="text-sm font-semibold text-gray-800 mb-1">Publicar com erros?</p>
              <p className="text-xs text-gray-500">O horário tem os seguintes problemas:</p>
              <ul className="mt-2 flex flex-col gap-1">
                {weekData.validationMessages.filter(m => m.severity === 'ERROR').map((m, i) => (
                  <li key={i} className="text-xs text-red-600 flex items-start gap-1.5">
                    <span className="shrink-0">✕</span>
                    <span>{m.message}</span>
                  </li>
                ))}
              </ul>
            </div>
            <div className="flex gap-2 justify-end">
              <button onClick={() => setShowPublishConfirm(false)}
                className="px-4 py-1.5 text-sm rounded-lg border border-gray-200 text-gray-600 hover:bg-gray-50">
                Cancelar
              </button>
              <button onClick={() => { setShowPublishConfirm(false); runAction(() => scheduleApi.publish(isoYear, isoWeek)) }}
                className="px-4 py-1.5 text-sm rounded-lg bg-green-600 text-white hover:bg-green-700">
                Publicar mesmo assim
              </button>
            </div>
          </div>
        </div>
      )}

      {showDeleteConfirm && weekData && (
        <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50"
          onClick={() => setShowDeleteConfirm(false)}>
          <div className="bg-white rounded-xl shadow-xl p-6 w-80 flex flex-col gap-4"
            onClick={e => e.stopPropagation()}>
            <div>
              <p className="text-sm font-semibold text-gray-800 mb-1">
                {weekData.status === 'DRAFT' ? 'Apagar rascunho?' : 'Apagar horário publicado?'}
              </p>
              <p className="text-xs text-gray-400">Esta acção não pode ser desfeita.</p>
            </div>
            <div className="flex gap-2 justify-end">
              <button onClick={() => setShowDeleteConfirm(false)}
                className="px-4 py-1.5 text-sm rounded-lg border border-gray-200 text-gray-600 hover:bg-gray-50">
                Cancelar
              </button>
              <button onClick={handleDelete}
                className="px-4 py-1.5 text-sm rounded-lg bg-red-500 text-white hover:bg-red-600">
                Apagar
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
