import { useState, useEffect } from 'react'
import { holidayApi } from '../api/client'
import type { HolidayResponse } from '../api/types'

const CURRENT_YEAR = new Date().getFullYear()

function formatDate(dateStr: string) {
  return new Date(dateStr + 'T00:00:00').toLocaleDateString('pt-PT', {
    day: 'numeric',
    month: 'long',
    weekday: 'short',
  })
}

export default function HolidaysPage() {
  const [year, setYear] = useState(CURRENT_YEAR)
  const [holidays, setHolidays] = useState<HolidayResponse[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Add form state
  const [adding, setAdding] = useState(false)
  const [newDate, setNewDate] = useState('')
  const [newName, setNewName] = useState('')
  const [saving, setSaving] = useState(false)

  const load = (y: number) => {
    setLoading(true)
    setError(null)
    holidayApi.list(y)
      .then(setHolidays)
      .catch(e => setError(e instanceof Error ? e.message : 'Erro desconhecido'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load(year) }, [year])

  const handleDelete = async (id: number, name: string) => {
    if (!window.confirm(`Remover "${name}"?`)) return
    try {
      await holidayApi.delete(id)
      setHolidays(prev => prev.filter(h => h.id !== id))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro desconhecido')
    }
  }

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault()
    setSaving(true)
    setError(null)
    try {
      const created = await holidayApi.create({ date: newDate, name: newName })
      setHolidays(prev => [...prev, created].sort((a, b) => a.date.localeCompare(b.date)))
      setNewDate('')
      setNewName('')
      setAdding(false)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro desconhecido')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="p-8 max-w-lg">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <button
            onClick={() => setYear(y => y - 1)}
            className="w-8 h-8 flex items-center justify-center rounded hover:bg-gray-100 text-gray-500 text-lg"
          >
            ‹
          </button>
          <h1 className="text-lg font-semibold text-gray-800 w-16 text-center">{year}</h1>
          <button
            onClick={() => setYear(y => y + 1)}
            className="w-8 h-8 flex items-center justify-center rounded hover:bg-gray-100 text-gray-500 text-lg"
          >
            ›
          </button>
        </div>
        <button
          onClick={() => setAdding(a => !a)}
          className="text-sm font-medium text-blue-600 hover:text-blue-800"
        >
          {adding ? 'Cancelar' : '+ Adicionar'}
        </button>
      </div>

      {error && (
        <p className="text-sm text-red-600 mb-4 bg-red-50 border border-red-200 rounded px-3 py-2">{error}</p>
      )}

      {adding && (
        <form onSubmit={handleAdd} className="flex gap-2 mb-4 bg-blue-50 border border-blue-200 rounded-lg px-4 py-3">
          <input
            type="date"
            value={newDate}
            onChange={e => setNewDate(e.target.value)}
            required
            className="border border-gray-300 rounded px-2 py-1.5 text-sm focus:outline-none focus:border-blue-400"
          />
          <input
            type="text"
            value={newName}
            onChange={e => setNewName(e.target.value)}
            placeholder="Nome do feriado"
            required
            className="flex-1 border border-gray-300 rounded px-2 py-1.5 text-sm focus:outline-none focus:border-blue-400"
          />
          <button
            type="submit"
            disabled={saving}
            className="bg-blue-600 text-white text-sm font-medium px-3 py-1.5 rounded hover:bg-blue-700 disabled:opacity-50"
          >
            {saving ? '...' : 'Guardar'}
          </button>
        </form>
      )}

      {loading ? (
        <p className="text-sm text-gray-400">A carregar...</p>
      ) : (
        <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
          {holidays.length === 0 && (
            <p className="text-sm text-gray-400 px-4 py-6 text-center">Nenhum feriado encontrado.</p>
          )}
          {holidays.map((h, i) => (
            <div
              key={h.id}
              className={`flex items-center justify-between px-4 py-3 ${i > 0 ? 'border-t border-gray-100' : ''}`}
            >
              <div>
                <div className="text-sm font-medium text-gray-800">{h.name}</div>
                <div className="text-xs text-gray-400">{formatDate(h.date)}</div>
              </div>
              <button
                onClick={() => handleDelete(h.id, h.name)}
                className="text-xs text-gray-300 hover:text-red-500 transition-colors ml-4"
              >
                ✕
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
