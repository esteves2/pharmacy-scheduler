// --- Shared ---

export interface EmployeeDto {
  id: number
  name: string
  role: 'F' | 'T'
}

export interface EmployeeDetailDto {
  id: number
  name: string
  role: 'F' | 'T'
  phone: string | null
  email: string | null
  notes: string | null
  birthday: string | null
  holidaysUsed: number
  holidaysRemaining: number
}

// --- Schedule ---

export type WeekStatus = 'DRAFT' | 'PUBLISHED'
export type DayType = 'WEEKDAY' | 'SATURDAY' | 'SUNDAY' | 'HOLIDAY'
export type HourStatus = 'OK' | 'OVERTIME' | 'UNDERTIME'
export type Severity = 'INFO' | 'WARNING' | 'ERROR'

export interface AssignmentResponse {
  id: number
  employee: EmployeeDto
  startTime: string
  endTime: string
  breakStart: string | null
  breakEnd: string | null
  hours: number
}

export interface DayResponse {
  date: string
  dayOfWeek: string
  dayType: DayType
  assignments: AssignmentResponse[]
}

export interface EmployeeSummaryResponse {
  employee: EmployeeDto
  weeklyHours: number
  effectiveHours: number
  status: HourStatus
}

export interface ValidationMessageResponse {
  severity: Severity
  date: string | null
  hour: number | null
  message: string
}

export interface WeekResponse {
  isoYear: number
  isoWeek: number
  status: WeekStatus
  days: DayResponse[]
  employeeSummaries: EmployeeSummaryResponse[]
  validationMessages: ValidationMessageResponse[]
}

export interface WeekSummaryResponse {
  isoYear: number
  isoWeek: number
  weekStart: string
  weekEnd: string
  status: WeekStatus | null
}

// --- Schedule write ---

export interface AssignmentWriteRequest {
  id: number | null
  employeeId: number
  date: string
  startTime: string
  endTime: string
  breakStart: string | null
  breakEnd: string | null
}

export interface WeekWriteRequest {
  assignments: AssignmentWriteRequest[]
}

// --- Absences ---

export type AbsenceType = 'FERIAS' | 'SICK' | 'MATERNITY' | 'FOLGA' | 'BIRTHDAY' | 'OTHER'

export interface AbsenceResponse {
  id: number
  employee: EmployeeDto
  startDate: string
  endDate: string
  type: AbsenceType
  note: string | null
}

export interface AbsenceRequest {
  employeeId: number
  startDate: string
  endDate: string
  type: AbsenceType
  note: string | null
}

// --- Holidays ---

export interface HolidayResponse {
  id: number
  date: string
  name: string
}

export interface HolidayRequest {
  date: string
  name: string
}
