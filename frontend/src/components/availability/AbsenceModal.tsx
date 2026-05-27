import { useState } from 'react'
import type { AbsenceType, EmployeeDto } from '../../api/types'

const ABSENCE_TYPES: { value: AbsenceType; label: string }[] = [
  { value: 'FERIAS',    label: 'Férias' },
  { value: 'DOENCA',    label: 'Doença' },
  { value: 'MATERNITY', label: 'Maternidade' },
  { value: 'FOLGA',     label: 'Folga' },
  { value: 'OTHER',     label: 'Outro' },
]

interface Props {
  employee: EmployeeDto
  defaultStart: string
  defaultEnd: string
  onConfirm: (startDate: string, endDate: string, type: AbsenceType, note: string | null) => void
  onCancel: () => void
}

export default function AbsenceModal({ employee, defaultStart, defaultEnd, onConfirm, onCancel }: Props) {
  const [startDate, setStartDate] = useState(defaultStart)
  const [endDate, setEndDate] = useState(defaultEnd)
  const [type, setType] = useState<AbsenceType>('FERIAS')
  const [note, setNote] = useState('')

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    onConfirm(startDate, endDate, type, note.trim() || null)
  }

  return (
    <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-sm p-6">
        <h2 className="text-base font-semibold text-gray-800 mb-1">Nova ausência</h2>
        <p className="text-sm text-gray-500 mb-4">{employee.name}</p>

        <form onSubmit={handleSubmit} className="flex flex-col gap-3">
          <div className="flex gap-3">
            <div className="flex-1">
              <label className="block text-xs font-medium text-gray-600 mb-1">De</label>
              <input
                type="date"
                value={startDate}
                onChange={e => setStartDate(e.target.value)}
                className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm focus:outline-none focus:border-blue-400"
                required
              />
            </div>
            <div className="flex-1">
              <label className="block text-xs font-medium text-gray-600 mb-1">Até</label>
              <input
                type="date"
                value={endDate}
                min={startDate}
                onChange={e => setEndDate(e.target.value)}
                className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm focus:outline-none focus:border-blue-400"
                required
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Tipo</label>
            <select
              value={type}
              onChange={e => setType(e.target.value as AbsenceType)}
              className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm focus:outline-none focus:border-blue-400"
            >
              {ABSENCE_TYPES.map(t => (
                <option key={t.value} value={t.value}>{t.label}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Nota (opcional)</label>
            <input
              type="text"
              value={note}
              onChange={e => setNote(e.target.value)}
              placeholder="Observação..."
              className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm focus:outline-none focus:border-blue-400"
            />
          </div>

          <div className="flex gap-2 pt-1">
            <button
              type="submit"
              className="flex-1 bg-blue-600 text-white text-sm font-medium py-2 rounded hover:bg-blue-700"
            >
              Guardar
            </button>
            <button
              type="button"
              onClick={onCancel}
              className="flex-1 border border-gray-300 text-gray-700 text-sm font-medium py-2 rounded hover:bg-gray-50"
            >
              Cancelar
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
