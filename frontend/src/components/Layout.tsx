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
      <nav className="w-48 shrink-0 bg-brand-dark flex flex-col py-6 px-4 gap-1">
        {/* Nav links */}
        <div className="flex flex-col gap-0.5">
          {links.map(({ to, label }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                `flex items-center rounded-lg px-3 py-2.5 text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-brand text-white'
                    : 'text-green-200 hover:bg-brand hover:text-white'
                }`
              }
            >
              {label}
            </NavLink>
          ))}
        </div>
      </nav>

      <main className="flex-1 overflow-auto bg-gray-50">
        <Outlet />
      </main>
    </div>
  )
}
