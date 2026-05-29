import { useState } from 'react'
import type { AssignmentResponse } from '../../api/types'

interface Props {
  employeeName: string
  date: string
  existing: AssignmentResponse | null
  hasPrev: boolean
  hasNext: boolean
  onNavPrev: () => void
  onNavNext: () => void
  onConfirm: (startTime: string, endTime: string, breakStart: string | null, breakEnd: string | null) => void
  onDelete: () => void
  onCancel: () => void
}

function formatDateLabel(dateStr: string) {
  const d = new Date(dateStr + 'T00:00:00')
  return d.toLocaleDateString('pt-PT', { weekday: 'long', day: 'numeric', month: 'long' })
}

export default function AssignmentModal({
  employeeName, date, existing,
  hasPrev, hasNext, onNavPrev, onNavNext,
  onConfirm, onDelete, onCancel,
}: Props) {
  const [startTime,  setStartTime]  = useState(existing?.startTime  ?? '09:00')
  const [endTime,    setEndTime]    = useState(existing?.endTime    ?? '17:00')
  const [breakStart, setBreakStart] = useState(existing?.breakStart ?? '')
  const [breakEnd,   setBreakEnd]   = useState(existing?.breakEnd   ?? '')
  const [error,      setError]      = useState<string | null>(null)

  const validate = (): boolean => {
    setError(null)
    if (startTime >= endTime) {
      setError('Hora de início deve ser antes do fim.')
      return false
    }
    if ((breakStart && !breakEnd) || (!breakStart && breakEnd)) {
      setError('Preencha ambos os campos de pausa ou nenhum.')
      return false
    }
    if (breakStart && breakEnd && breakStart >= breakEnd) {
      setError('Início da pausa deve ser antes do fim.')
      return false
    }
    return true
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (validate()) onConfirm(startTime, endTime, breakStart || null, breakEnd || null)
  }

  return (
    <>
      <div className="fixed inset-0 bg-black/20 z-40" onClick={onCancel} />
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
        <div className="bg-white rounded-lg shadow-xl w-full max-w-xs">

          {/* Header with employee navigation */}
          <div className="px-4 py-3 border-b border-gray-200 flex items-center gap-2">
            <button
              type="button"
              onClick={onNavPrev}
              disabled={!hasPrev}
              className="w-7 h-7 flex items-center justify-center rounded hover:bg-gray-100 text-gray-400 disabled:opacity-30 disabled:cursor-not-allowed text-base leading-none"
              title="Funcionário anterior"
            >‹</button>

            <div className="flex-1 min-w-0 text-center">
              <div className="text-sm font-semibold text-gray-800 truncate">{employeeName}</div>
              <div className="text-xs text-gray-400 capitalize">{formatDateLabel(date)}</div>
            </div>

            <button
              type="button"
              onClick={onNavNext}
              disabled={!hasNext}
              className="w-7 h-7 flex items-center justify-center rounded hover:bg-gray-100 text-gray-400 disabled:opacity-30 disabled:cursor-not-allowed text-base leading-none"
              title="Funcionário seguinte"
            >›</button>
          </div>

          <form onSubmit={handleSubmit} className="px-5 py-4 flex flex-col gap-4">

            {/* Time fields */}
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Início</label>
                <input
                  type="time" value={startTime} onChange={e => setStartTime(e.target.value)} required
                  className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm focus:outline-none focus:border-blue-400"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Fim</label>
                <input
                  type="time" value={endTime} onChange={e => setEndTime(e.target.value)} required
                  className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm focus:outline-none focus:border-blue-400"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Início pausa</label>
                <input
                  type="time" value={breakStart} onChange={e => setBreakStart(e.target.value)}
                  className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm focus:outline-none focus:border-blue-400"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Fim pausa</label>
                <input
                  type="time" value={breakEnd} onChange={e => setBreakEnd(e.target.value)}
                  className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm focus:outline-none focus:border-blue-400"
                />
              </div>
            </div>

            {error && (
              <p className="text-xs text-red-600 bg-red-50 border border-red-200 rounded px-3 py-2">{error}</p>
            )}

            <div className="flex gap-2">
              <button type="submit"
                className="flex-1 bg-blue-600 text-white text-sm font-medium py-2 rounded hover:bg-blue-700">
                Guardar
              </button>
              <button type="button" onClick={onCancel}
                className="flex-1 border border-gray-300 text-gray-700 text-sm font-medium py-2 rounded hover:bg-gray-50">
                Cancelar
              </button>
            </div>

            {existing && (
              <button type="button" onClick={onDelete}
                className="w-full border border-red-200 text-red-600 text-sm font-medium py-2 rounded hover:bg-red-50">
                Remover turno
              </button>
            )}
          </form>
        </div>
      </div>
    </>
  )
}
