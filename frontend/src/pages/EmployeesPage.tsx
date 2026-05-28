import { useState, useEffect } from 'react'
import { employeeApi } from '../api/client'
import type { EmployeeDetailDto } from '../api/types'

interface SlideOverProps {
  employee: EmployeeDetailDto
  onSave: (updated: EmployeeDetailDto) => void
  onClose: () => void
}

function SlideOver({ employee, onSave, onClose }: SlideOverProps) {
  const [name, setName]   = useState(employee.name)
  const [role, setRole]   = useState<'F' | 'T'>(employee.role)
  const [phone, setPhone] = useState(employee.phone ?? '')
  const [email, setEmail] = useState(employee.email ?? '')
  const [notes, setNotes] = useState(employee.notes ?? '')
  const [saving, setSaving] = useState(false)
  const [error, setError]   = useState<string | null>(null)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSaving(true)
    setError(null)
    try {
      const updated = await employeeApi.update(employee.id, {
        id: employee.id,
        name,
        role,
        phone: phone.trim() || null,
        email: email.trim() || null,
        notes: notes.trim() || null,
      })
      onSave(updated)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro desconhecido')
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <div className="fixed inset-0 bg-black/20 z-40" onClick={onClose} />
      <div className="fixed right-0 top-0 h-full w-96 bg-white shadow-xl z-50 flex flex-col overflow-y-auto">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200 sticky top-0 bg-white">
          <h2 className="text-base font-semibold text-gray-800">Editar funcionário</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl leading-none">×</button>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-5 px-6 py-6 flex-1">

          {/* Identity */}
          <section>
            <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-3">Identificação</h3>
            <div className="flex flex-col gap-3">
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Nome</label>
                <input
                  type="text"
                  value={name}
                  onChange={e => setName(e.target.value)}
                  required
                  className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:border-blue-400"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Função</label>
                <select
                  value={role}
                  onChange={e => setRole(e.target.value as 'F' | 'T')}
                  className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:border-blue-400"
                >
                  <option value="F">Farmacêutica (F)</option>
                  <option value="T">Técnica (T)</option>
                </select>
              </div>
            </div>
          </section>

          {/* Contacts */}
          <section>
            <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-3">Contactos</h3>
            <div className="flex flex-col gap-3">
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Telemóvel</label>
                <input
                  type="tel"
                  value={phone}
                  onChange={e => setPhone(e.target.value)}
                  placeholder="+351 912 345 678"
                  className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:border-blue-400"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Email</label>
                <input
                  type="email"
                  value={email}
                  onChange={e => setEmail(e.target.value)}
                  placeholder="nome@exemplo.pt"
                  className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:border-blue-400"
                />
              </div>
            </div>
          </section>

          {/* Notes */}
          <section>
            <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-3">Notas</h3>
            <textarea
              value={notes}
              onChange={e => setNotes(e.target.value)}
              rows={4}
              placeholder="Contrato termina em Dezembro, prefere manhãs..."
              className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:border-blue-400 resize-none"
            />
          </section>

          {error && (
            <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded px-3 py-2">{error}</p>
          )}

          <div className="flex gap-2 mt-auto pt-2">
            <button
              type="submit"
              disabled={saving}
              className="flex-1 bg-blue-600 text-white text-sm font-medium py-2 rounded hover:bg-blue-700 disabled:opacity-50"
            >
              {saving ? 'A guardar...' : 'Guardar'}
            </button>
            <button
              type="button"
              onClick={onClose}
              className="flex-1 border border-gray-300 text-gray-700 text-sm font-medium py-2 rounded hover:bg-gray-50"
            >
              Cancelar
            </button>
          </div>
        </form>
      </div>
    </>
  )
}

export default function EmployeesPage() {
  const [employees, setEmployees] = useState<EmployeeDetailDto[]>([])
  const [loading, setLoading]     = useState(false)
  const [error, setError]         = useState<string | null>(null)
  const [editing, setEditing]     = useState<EmployeeDetailDto | null>(null)

  useEffect(() => {
    setLoading(true)
    employeeApi.list()
      .then(setEmployees)
      .catch(e => setError(e instanceof Error ? e.message : 'Erro desconhecido'))
      .finally(() => setLoading(false))
  }, [])

  const handleSave = (updated: EmployeeDetailDto) => {
    setEmployees(prev => prev.map(e => e.id === updated.id ? updated : e))
    setEditing(null)
  }

  return (
    <div className="p-8 max-w-lg">
      <h1 className="text-lg font-semibold text-gray-800 mb-6">Funcionários</h1>

      {error && (
        <p className="text-sm text-red-600 mb-4 bg-red-50 border border-red-200 rounded px-3 py-2">{error}</p>
      )}

      {loading ? (
        <p className="text-sm text-gray-400">A carregar...</p>
      ) : (
        <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
          {employees.map((emp, i) => (
            <button
              key={emp.id}
              onClick={() => setEditing(emp)}
              className={`w-full flex items-center justify-between px-4 py-3 hover:bg-gray-50 transition-colors text-left ${i > 0 ? 'border-t border-gray-100' : ''}`}
            >
              <div className="flex flex-col gap-0.5">
                <span className="text-sm font-medium text-gray-800">{emp.name}</span>
                {(emp.phone || emp.email) && (
                  <span className="text-xs text-gray-400">{emp.phone ?? emp.email}</span>
                )}
              </div>
              <div className="flex items-center gap-2 shrink-0">
                {emp.notes && (
                  <span className="text-xs text-gray-300" title={emp.notes}>📝</span>
                )}
                <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${emp.role === 'F' ? 'bg-blue-100 text-blue-700' : 'bg-teal-100 text-teal-700'}`}>
                  {emp.role === 'F' ? 'Farmacêutica' : 'Técnica'}
                </span>
              </div>
            </button>
          ))}
        </div>
      )}

      {editing && (
        <SlideOver
          employee={editing}
          onSave={handleSave}
          onClose={() => setEditing(null)}
        />
      )}
    </div>
  )
}
