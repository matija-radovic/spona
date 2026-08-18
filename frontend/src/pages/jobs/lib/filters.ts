// -----------------------------------------------------------------------------
// Jobs filter state <-> URL. The golden rule: filter state lives in the URL query
// string. This is the single canonical serialisation - the page
// and the service both go through here; never hand-roll `?a=b` at call sites.
//
// Pagination (`page`/`size`) is deliberately NOT part of these params - the
// service prepends it when building the real request; the browser URL never
// carries it (keeps Back-restoration and shared links clean).
// -----------------------------------------------------------------------------

export interface FilterOption {
  value: string
  labelKey: string
}

export interface FilterConfig {
  key: 'posted' | 'sort' | 'workplace' | 'employment' | 'experience' | 'salary'
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

const WORKPLACE_OPTIONS: readonly FilterOption[] = [
  { value: 'REMOTE', labelKey: 'jobs.workplace.REMOTE' },
  { value: 'HYBRID', labelKey: 'jobs.workplace.HYBRID' },
  { value: 'ONSITE', labelKey: 'jobs.workplace.ONSITE' },
]

// The "Ugovor / Honorarno" bucket is ONE UI choice that expands to two JobType
// values (new_docx/types.ts); its compound value round-trips through the URL.
const EMPLOYMENT_OPTIONS: readonly FilterOption[] = [
  { value: 'PART_TIME', labelKey: 'jobs.type.PART_TIME' },
  { value: 'CONTRACT,GIG', labelKey: 'jobs.filters.employmentOpt.contractGig' },
  { value: 'INTERNSHIP', labelKey: 'jobs.type.INTERNSHIP' },
  { value: 'FULL_TIME', labelKey: 'jobs.type.FULL_TIME' },
  { value: 'VOLUNTEER', labelKey: 'jobs.type.VOLUNTEER' },
]

const EXPERIENCE_OPTIONS: readonly FilterOption[] = [
  { value: 'ENTRY', labelKey: 'jobs.experience.ENTRY' },
  { value: 'INTERNSHIP', labelKey: 'jobs.experience.INTERNSHIP' },
  { value: 'MEDIOR', labelKey: 'jobs.experience.MEDIOR' },
  { value: 'SENIOR', labelKey: 'jobs.experience.SENIOR' },
  { value: 'MANAGER', labelKey: 'jobs.experience.MANAGER' },
  { value: 'DIRECTOR', labelKey: 'jobs.experience.DIRECTOR' },
  { value: 'EXECUTIVE', labelKey: 'jobs.experience.EXECUTIVE' },
]

/**
 * Config-driven filters - adding a seventh is a one-liner here, and the
 * "primary vs. More" split is just `slice(0, N)`.
 */
export const FILTERS: readonly FilterConfig[] = [
  { key: 'posted', labelKey: 'jobs.filters.posted', kind: 'single', options: POSTED_OPTIONS },
  { key: 'sort', labelKey: 'jobs.filters.sort', kind: 'single', options: SORT_OPTIONS },
  { key: 'workplace', labelKey: 'jobs.filters.workplace', kind: 'multi', options: WORKPLACE_OPTIONS },
  { key: 'employment', labelKey: 'jobs.filters.employment', kind: 'multi', options: EMPLOYMENT_OPTIONS },
  { key: 'experience', labelKey: 'jobs.filters.experience', kind: 'multi', options: EXPERIENCE_OPTIONS },
  { key: 'salary', labelKey: 'jobs.filters.salary', kind: 'toggle' },
]

/** How many filters render inline before the rest collapse behind "More". */
export const PRIMARY_FILTER_COUNT = 6

export const emptyFilters = (): FilterState => ({
  q: '',
  role: null,
  posted: null,
  sort: null,
  workplace: [],
  employment: [],
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
  experience: deserialiseMulti(params.get('experience'), EXPERIENCE_OPTIONS),
  salary: params.get('salary') === '1',
})
