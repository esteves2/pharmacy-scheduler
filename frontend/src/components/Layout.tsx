import { NavLink, Outlet } from 'react-router-dom'

const links = [
  { to: '/schedule',     label: 'Horários' },
  { to: '/availability', label: 'Disponibilidade' },
  { to: '/employees',    label: 'Funcionários' },
  { to: '/holidays',     label: 'Feriados' },
]

export default function Layout() {
  return (
    <div className="flex h-screen bg-gray-50 text-gray-900">
      <nav className="w-52 shrink-0 bg-white border-r border-gray-200 flex flex-col py-6 px-4 gap-1">
        <span className="text-xs font-semibold uppercase tracking-widest text-gray-400 px-2 mb-3">
          Farmácia Esperança
        </span>
        {links.map(({ to, label }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              `rounded-md px-3 py-2 text-sm font-medium transition-colors ${
                isActive
                  ? 'bg-blue-50 text-blue-700'
                  : 'text-gray-600 hover:bg-gray-100'
              }`
            }
          >
            {label}
          </NavLink>
        ))}
      </nav>
      <main className="flex-1 overflow-auto">
        <Outlet />
      </main>
    </div>
  )
}
