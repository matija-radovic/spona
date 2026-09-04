import type { ExperienceLevel, JobType, WorkingHours, WorkplaceType } from '@utils/api/jobs'

export interface FilterOption {
  value: string
  labelKey: string
}

export interface FilterConfig {
  key: 'posted' | 'sort' | 'workplace' | 'employment' | 'hours' | 'experience' | 'salary'
  labelKey: string
  kind: 'single' | 'multi' | 'toggle'
  options?: readonly FilterOption[]
}

/** Decoded filter state. Also the query object the mock `fetchJobs` consumes. */
export interface FilterState {
  q: string
  /** carried through from the home-page role chips (`/jobs?role=<slug>`). */
  role: string | null
  posted: string | null
  sort: string | null
  workplace: string[]
  employment: string[]
  hours: string[]
  experience: string[]
  salary: boolean
}

export type JobQuery = FilterState

// -- Option sets - labels mirror the backend enum spellings exactly ------------
// (an empty `value` is the "none"/clear entry for single-select filters).

const POSTED_OPTIONS: readonly FilterOption[] = [
  { value: '', labelKey: 'jobs.filters.postedOpt.any' },
  { value: '24h', labelKey: 'jobs.filters.postedOpt.24h' },
  { value: 'week', labelKey: 'jobs.filters.postedOpt.week' },
  { value: 'month', labelKey: 'jobs.filters.postedOpt.month' },
]

const SORT_OPTIONS: readonly FilterOption[] = [
  { value: '', labelKey: 'jobs.filters.sortOpt.recommended' },
  { value: 'date_desc', labelKey: 'jobs.filters.sortOpt.date_desc' },
  { value: 'date_asc', labelKey: 'jobs.filters.sortOpt.date_asc' },
]

const WORKPLACE_OPTIONS: readonly (FilterOption & { value: WorkplaceType })[] = [
  { value: 'REMOTE', labelKey: 'jobs.workplace.REMOTE' },
  { value: 'HYBRID', labelKey: 'jobs.workplace.HYBRID' },
  { value: 'ONSITE', labelKey: 'jobs.workplace.ONSITE' },
  { value: 'FIELD', labelKey: 'jobs.workplace.FIELD' },
  { value: 'OTHER', labelKey: 'jobs.workplace.OTHER' },
]

const EMPLOYMENT_OPTIONS: readonly (FilterOption & { value: JobType })[] = [
  { value: 'PERMANENT', labelKey: 'jobs.type.PERMANENT' },
  { value: 'FIXED_TERM', labelKey: 'jobs.type.FIXED_TERM' },
  { value: 'SEASONAL', labelKey: 'jobs.type.SEASONAL' },
  { value: 'FREELANCE', labelKey: 'jobs.type.FREELANCE' },
  { value: 'INTERNSHIP', labelKey: 'jobs.type.INTERNSHIP' },
  { value: 'VOLUNTEER', labelKey: 'jobs.type.VOLUNTEER' },
]

const HOURS_OPTIONS: readonly (FilterOption & { value: WorkingHours })[] = [
  { value: 'FULL_TIME', labelKey: 'jobs.hours.FULL_TIME' },
  { value: 'PART_TIME', labelKey: 'jobs.hours.PART_TIME' },
]

const EXPERIENCE_OPTIONS: readonly (FilterOption & { value: ExperienceLevel })[] = [
  { value: 'NONE', labelKey: 'jobs.experience.NONE' },
  { value: 'MONTHS_6', labelKey: 'jobs.experience.MONTHS_6' },
  { value: 'YEARS_1', labelKey: 'jobs.experience.YEARS_1' },
  { value: 'YEARS_2', labelKey: 'jobs.experience.YEARS_2' },
  { value: 'YEARS_3', labelKey: 'jobs.experience.YEARS_3' },
  { value: 'YEARS_5', labelKey: 'jobs.experience.YEARS_5' },
  { value: 'YEARS_10', labelKey: 'jobs.experience.YEARS_10' },
]

export const FILTERS: readonly FilterConfig[] = [
  { key: 'posted', labelKey: 'jobs.filters.posted', kind: 'single', options: POSTED_OPTIONS },
  { key: 'sort', labelKey: 'jobs.filters.sort', kind: 'single', options: SORT_OPTIONS },
  { key: 'workplace', labelKey: 'jobs.filters.workplace', kind: 'multi', options: WORKPLACE_OPTIONS },
  { key: 'employment', labelKey: 'jobs.filters.employment', kind: 'multi', options: EMPLOYMENT_OPTIONS },
  { key: 'hours', labelKey: 'jobs.filters.hours', kind: 'multi', options: HOURS_OPTIONS },
  { key: 'experience', labelKey: 'jobs.filters.experience', kind: 'multi', options: EXPERIENCE_OPTIONS },
  { key: 'salary', labelKey: 'jobs.filters.salary', kind: 'toggle' },
]

export const PRIMARY_FILTER_COUNT = 7

export const emptyFilters = (): FilterState => ({
  q: '',
  role: null,
  posted: null,
  sort: null,
  workplace: [],
  employment: [],
  hours: [],
  experience: [],
  salary: false,
})

// -- Serialise / deserialise --------------------------------------------------

/** Selected -> flat param value. Omitted (empty) when none or all are chosen. */
const serialiseMulti = (selected: string[], options: readonly FilterOption[]): string =>
  selected.length === 0 || selected.length === options.length ? '' : selected.join(',')

/**
 * Flat param value -> selected option values. An option is selected when every
 * enum token it carries is present (handles the compound CONTRACT,GIG bucket).
 */
const deserialiseMulti = (raw: string | null, options: readonly FilterOption[]): string[] => {
  if (!raw) return []
  const tokens = new Set(raw.split(',').filter(Boolean))
  return options
    .filter((o) => o.value.split(',').every((tok) => tokens.has(tok)))
    .map((o) => o.value)
}

const normaliseSingle = (raw: string | null, options: readonly FilterOption[]): string | null =>
  raw && options.some((o) => o.value === raw && o.value !== '') ? raw : null

/** Filter params only (`q`, `role`, `posted`, …) - never `page`/`size`. */
export const filtersToSearchParams = (state: FilterState): URLSearchParams => {
  const params = new URLSearchParams()
  const q = state.q.trim()
  if (q) params.set('q', q)
  if (state.role) params.set('role', state.role)
  if (state.posted) params.set('posted', state.posted)
  if (state.sort) params.set('sort', state.sort)

  const workplace = serialiseMulti(state.workplace, WORKPLACE_OPTIONS)
  if (workplace) params.set('workplace', workplace)
  const employment = serialiseMulti(state.employment, EMPLOYMENT_OPTIONS)
  if (employment) params.set('employment', employment)
  const hours = serialiseMulti(state.hours, HOURS_OPTIONS)
  if (hours) params.set('hours', hours)
  const experience = serialiseMulti(state.experience, EXPERIENCE_OPTIONS)
  if (experience) params.set('experience', experience)

  if (state.salary) params.set('salary', '1')
  return params
}

export const searchParamsToFilters = (params: URLSearchParams): FilterState => ({
  q: params.get('q') ?? '',
  role: params.get('role'),
  posted: normaliseSingle(params.get('posted'), POSTED_OPTIONS),
  sort: normaliseSingle(params.get('sort'), SORT_OPTIONS),
  workplace: deserialiseMulti(params.get('workplace'), WORKPLACE_OPTIONS),
  employment: deserialiseMulti(params.get('employment'), EMPLOYMENT_OPTIONS),
  hours: deserialiseMulti(params.get('hours'), HOURS_OPTIONS),
  experience: deserialiseMulti(params.get('experience'), EXPERIENCE_OPTIONS),
  salary: params.get('salary') === '1',
})
