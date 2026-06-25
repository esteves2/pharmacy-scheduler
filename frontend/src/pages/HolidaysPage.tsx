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
  const [adding, setAdding] = useState(false)
  const [newDate, setNewDate] = useState('')
  const [newName, setNewName] = useState('')
  const [saving, setSaving] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<HolidayResponse | null>(null)

  const load = (y: number) => {
    setLoading(true)
    setError(null)
    holidayApi.list(y)
      .then(setHolidays)
      .catch(e => setError(e instanceof Error ? e.message : 'Erro desconhecido'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load(year) }, [year])

  const handleDelete = async () => {
    if (!deleteTarget) return
    try {
      await holidayApi.delete(deleteTarget.id)
      setHolidays(prev => prev.filter(h => h.id !== deleteTarget.id))
      setDeleteTarget(null)
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
    <div className="p-8 max-w-2xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <button onClick={() => setYear(y => y - 1)}
            className="w-8 h-8 flex items-center justify-center rounded-full hover:bg-gray-100 text-gray-500 text-xl">‹</button>
          <h1 className="text-2xl font-semibold text-gray-800 w-20 text-center">{year}</h1>
          <button onClick={() => setYear(y => y + 1)}
            className="w-8 h-8 flex items-center justify-center rounded-full hover:bg-gray-100 text-gray-500 text-xl">›</button>
        </div>
        <button onClick={() => setAdding(a => !a)}
          className="text-base font-medium text-brand hover:text-brand-dark">
          {adding ? 'Cancelar' : '+ Adicionar'}
        </button>
      </div>

      {error && (
        <p className="text-sm text-red-600 mb-4 bg-red-50 border border-red-200 rounded px-3 py-2">{error}</p>
      )}

      {adding && (
        <form onSubmit={handleAdd} className="flex gap-2 mb-4 bg-brand-faint border border-brand/30 rounded-lg px-4 py-3">
          <input type="date" value={newDate} onChange={e => setNewDate(e.target.value)} required
            className="border border-gray-300 rounded px-2 py-1.5 text-sm focus:outline-none focus:border-brand" />
          <input type="text" value={newName} onChange={e => setNewName(e.target.value)}
            placeholder="Nome do feriado" required
            className="flex-1 border border-gray-300 rounded px-2 py-1.5 text-sm focus:outline-none focus:border-brand" />
          <button type="submit" disabled={saving}
            className="bg-brand text-white text-sm font-medium px-3 py-1.5 rounded hover:bg-brand-dark disabled:opacity-50">
            {saving ? '...' : 'Guardar'}
          </button>
        </form>
      )}

      {loading ? (
        <p className="text-sm text-gray-400">A carregar...</p>
      ) : (
        <div className="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm">
          {holidays.length === 0 && (
            <p className="text-base text-gray-400 px-6 py-8 text-center">Nenhum feriado encontrado.</p>
          )}
          {holidays.map((h, i) => (
            <div key={h.id}
              className={`flex items-center justify-between px-6 py-4 ${i > 0 ? 'border-t border-gray-100' : ''}`}>
              <div>
                <div className="text-base font-medium text-gray-800">{h.name}</div>
                <div className="text-sm text-gray-400">{formatDate(h.date)}</div>
              </div>
              <button onClick={() => setDeleteTarget(h)}
                className="text-gray-300 hover:text-red-500 transition-colors ml-4 text-lg leading-none">✕</button>
            </div>
          ))}
        </div>
      )}

      {deleteTarget && (
        <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50"
          onClick={() => setDeleteTarget(null)}>
          <div className="bg-white rounded-xl shadow-xl p-6 w-80 flex flex-col gap-4"
            onClick={e => e.stopPropagation()}>
            <div>
              <p className="text-sm font-semibold text-gray-800 mb-1">Remover feriado?</p>
              <p className="text-sm text-gray-500">{deleteTarget.name}</p>
              <p className="text-xs text-gray-400 mt-1">{formatDate(deleteTarget.date)}</p>
            </div>
            <div className="flex gap-2 justify-end">
              <button onClick={() => setDeleteTarget(null)}
                className="px-4 py-1.5 text-sm rounded-lg border border-gray-200 text-gray-600 hover:bg-gray-50">
                Cancelar
              </button>
              <button onClick={handleDelete}
                className="px-4 py-1.5 text-sm rounded-lg bg-red-500 text-white hover:bg-red-600">
                Remover
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
