import type {
  WeekSummaryResponse,
  WeekResponse,
  WeekWriteRequest,
  EmployeeDetailDto,
  AbsenceResponse,
  AbsenceRequest,
  HolidayResponse,
  HolidayRequest,
} from './types'

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (!res.ok) {
    const body = await res.text()
    throw new Error(`${res.status} ${res.statusText}: ${body}`)
  }
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}

// --- Schedule ---

export const scheduleApi = {
  listByMonth: (year: number, month: number) =>
    request<WeekSummaryResponse[]>(`/api/schedules/weeks?year=${year}&month=${month}`),

  getWeek: (isoYear: number, isoWeek: number) =>
    request<WeekResponse>(`/api/schedules/weeks/${isoYear}/${isoWeek}`),

  generate: (isoYear: number, isoWeek: number) =>
    request<WeekResponse>(`/api/schedules/weeks/${isoYear}/${isoWeek}/generate`, { method: 'POST' }),

  save: (isoYear: number, isoWeek: number, body: WeekWriteRequest) =>
    request<WeekResponse>(`/api/schedules/weeks/${isoYear}/${isoWeek}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),

  publish: (isoYear: number, isoWeek: number) =>
    request<WeekResponse>(`/api/schedules/weeks/${isoYear}/${isoWeek}/publish`, { method: 'POST' }),

  regenerate: (isoYear: number, isoWeek: number) =>
    request<WeekResponse>(`/api/schedules/weeks/${isoYear}/${isoWeek}/regenerate`, { method: 'POST' }),

  replan: (isoYear: number, isoWeek: number) =>
    request<WeekResponse>(`/api/schedules/weeks/${isoYear}/${isoWeek}/replan`, { method: 'POST' }),

  delete: (isoYear: number, isoWeek: number) =>
    request<void>(`/api/schedules/weeks/${isoYear}/${isoWeek}`, { method: 'DELETE' }),
}

// --- Employees ---

export const employeeApi = {
  list: () =>
    request<EmployeeDetailDto[]>('/api/employees'),

  get: (id: number) =>
    request<EmployeeDetailDto>(`/api/employees/${id}`),

  update: (id: number, body: EmployeeDetailDto) =>
    request<EmployeeDetailDto>(`/api/employees/${id}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
}

// --- Absences ---

export const absenceApi = {
  list: (from: string, to: string) =>
    request<AbsenceResponse[]>(`/api/absences?from=${from}&to=${to}`),

  create: (body: AbsenceRequest) =>
    request<AbsenceResponse>('/api/absences', {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  update: (id: number, body: AbsenceRequest) =>
    request<AbsenceResponse>(`/api/absences/${id}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),

  delete: (id: number) =>
    request<void>(`/api/absences/${id}`, { method: 'DELETE' }),
}

// --- Holidays ---

export const holidayApi = {
  list: (year: number) =>
    request<HolidayResponse[]>(`/api/holidays?year=${year}`),

  create: (body: HolidayRequest) =>
    request<HolidayResponse>('/api/holidays', {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  update: (id: number, body: HolidayRequest) =>
    request<HolidayResponse>(`/api/holidays/${id}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),

  delete: (id: number) =>
    request<void>(`/api/holidays/${id}`, { method: 'DELETE' }),
}
