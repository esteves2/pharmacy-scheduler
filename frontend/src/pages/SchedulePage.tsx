import { useState, useEffect, useCallback } from 'react'
import { scheduleApi } from '../api/client'
import type { WeekSummaryResponse, WeekResponse } from '../api/types'
import MonthView from '../components/schedule/MonthView'
import WeekView from '../components/schedule/WeekView'

export default function SchedulePage() {
  const today = new Date()
  const [year, setYear] = useState(today.getFullYear())
  const [month, setMonth] = useState(today.getMonth() + 1)
  const [weekSummaries, setWeekSummaries] = useState<WeekSummaryResponse[]>([])
  const [selectedWeek, setSelectedWeek] = useState<{ isoYear: number; isoWeek: number } | null>(null)
  const [weekData, setWeekData] = useState<WeekResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const loadMonth = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await scheduleApi.listByMonth(year, month)
      setWeekSummaries(data)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro desconhecido')
    } finally {
      setLoading(false)
    }
  }, [year, month])

  useEffect(() => { loadMonth() }, [loadMonth])

  const openWeek = async (isoYear: number, isoWeek: number) => {
    setSelectedWeek({ isoYear, isoWeek })
    const summary = weekSummaries.find(w => w.isoYear === isoYear && w.isoWeek === isoWeek)
    if (!summary?.status) {
      setWeekData(null)
      return
    }
    setLoading(true)
    setError(null)
    try {
      const data = await scheduleApi.getWeek(isoYear, isoWeek)
      setWeekData(data)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro desconhecido')
    } finally {
      setLoading(false)
    }
  }

  const closeWeek = () => {
    setSelectedWeek(null)
    setWeekData(null)
    loadMonth()
  }

  const handleWeekDataChange = (data: WeekResponse) => {
    setWeekData(data)
    setWeekSummaries(prev =>
      prev.map(w =>
        w.isoYear === data.isoYear && w.isoWeek === data.isoWeek
          ? { ...w, status: data.status }
          : w
      )
    )
  }

  if (selectedWeek) {
    return (
      <WeekView
        isoYear={selectedWeek.isoYear}
        isoWeek={selectedWeek.isoWeek}
        weekData={weekData}
        loading={loading}
        error={error}
        onBack={closeWeek}
        onWeekDataChange={handleWeekDataChange}
      />
    )
  }

  return (
    <MonthView
      year={year}
      month={month}
      weekSummaries={weekSummaries}
      loading={loading}
      error={error}
      onMonthChange={(y, m) => { setYear(y); setMonth(m) }}
      onWeekClick={openWeek}
    />
  )
}
