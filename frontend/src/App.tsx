import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import Layout from './components/Layout'
import SchedulePage from './pages/SchedulePage'
import AvailabilityPage from './pages/AvailabilityPage'
import EmployeesPage from './pages/EmployeesPage'
import HolidaysPage from './pages/HolidaysPage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Layout />}>
          <Route index element={<Navigate to="/schedule" replace />} />
          <Route path="schedule" element={<SchedulePage />} />
          <Route path="availability" element={<AvailabilityPage />} />
          <Route path="employees" element={<EmployeesPage />} />
          <Route path="holidays" element={<HolidaysPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
