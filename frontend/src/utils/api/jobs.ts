// -----------------------------------------------------------------------------
// MOCK jobs service. The Spring Boot backend does not exist yet, so every call
// fakes a delayed response instead of hitting the network. The real axios call
// is left commented above each mock body. DTO shapes are separate from the DB
// model (new_docx/types.ts): user-facing DTOs OMIT `createdBy` and ship a stripped,
// discriminated `createdFor`. Transport envelopes come from `./types`.
//
// All rejections are `ProblemDetail` shapes so callers can `parseProblem(err)`
// uniformly. Magic inputs make the failure/empty paths reachable:
//   - fetchJobs   : q === "prazno"            -> empty page (0 results)
//   - fetchJobBySlug : slug === "nepostojeci" -> 404 Not Found
//   - submitApplication : filename contains "fail" -> 413 rejection
//   - fetchActorJobPostings : actorId ends with "-empty" -> [] (no/hidden postings)
//
// Saved jobs no longer live here (new_docx/types.ts, end of the Jobs block —
// there is no FavoriteJob table); they are browser
// localStorage (utils/savedJobs.ts). `fetchJobsByIds` only HYDRATES an id set.
// -----------------------------------------------------------------------------

// import api from '@utils/api'

import type { ContactEntry } from '@shared/components'
import type { PagedResponse, ProblemDetail } from './types'
import type { JobQuery } from '@pages/jobs/lib/filters'
import { filtersToSearchParams } from '@pages/jobs/lib/filters'

// ── Local enum mirrors (the project mirrors backend enums as local unions) ─────

export type JobType = 'FULL_TIME' | 'PART_TIME' | 'CONTRACT' | 'GIG' | 'INTERNSHIP' | 'VOLUNTEER'
export type WorkplaceType = 'ONSITE' | 'REMOTE' | 'HYBRID' | 'OTHER'
export type ApplicationType = 'CONTACT_ONLY' | 'EXTERNAL_URL' | 'CV_DROP'
export type JobStatus = 'DRAFT' | 'OPEN' | 'CLOSED'
export type ExperienceLevel =
  | 'ENTRY'
  | 'INTERNSHIP'
  | 'MEDIOR'
  | 'SENIOR'
  | 'MANAGER'
  | 'DIRECTOR'
  | 'EXECUTIVE'
export type SalaryPeriod = 'HOUR' | 'DAY' | 'WEEK' | 'MONTH' | 'JOB'

export interface Salary {
  min: number
  max: number | null
  currency: string
  period: SalaryPeriod
  displayText: string | null
}

// ── DTOs ──────────────────────────────────────────────────────────────────────

/** "Who posted this", stripped for public consumption. */
export type JobPosterDto =
  | { actorType: 'org'; displayName: string; slug: string; avatarUrl: string | null }
  | { actorType: 'user' } // person-posted: intentionally minimal - render a generic tag, NOT a name

export interface JobListItemDto {
  id: string
  slug: string
  title: string
  createdFor: JobPosterDto
  location: string | null
  salary: Salary | null
  createdAt: string
}

export interface JobDetailDto extends JobListItemDto {
  jobType: JobType
  workplaceType: WorkplaceType
  experienceLevel: ExperienceLevel
  status: JobStatus
  applicationType: ApplicationType
  contactInformation: ContactEntry[] | null
  externalUrl: string | null
  description: string
}

/** JobListItemDto + status, so the profile Saved-jobs pane filters client-side. */
export interface SavedJobItemDto extends JobListItemDto {
  status: JobStatus
}

export interface JobApplication {
  id: string
  jobId: string
  cvUrl: string | null
  submittedAt: string
}

// ── Mock plumbing ──────────────────────────────────────────────────────────────

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

const makeProblem = (status: number, detail: string, title = ''): ProblemDetail => ({
  type: 'about:blank',
  title,
  status,
  detail,
  instance: '/api/jobs',
})

const rejectProblem = <T>(status: number, detail: string, title?: string): Promise<T> =>
  Promise.reject(makeProblem(status, detail, title))

const MAGIC_EMPTY_QUERY = 'prazno'
const MAGIC_NOT_FOUND_SLUG = 'nepostojeci'
const MAX_MOCK_PAGES = 3

const TITLES = [
  'Frontend programer',
  'Backend programer',
  'Dizajner proizvoda',
  'DevOps inženjer',
  'QA inženjer',
  'Analitičar podataka',
  'Menadžer proizvoda',
  'Softverski inženjer',
]

const ORGS: readonly { displayName: string; slug: string; avatarUrl: string | null }[] = [
  { displayName: 'Nordeus', slug: 'nordeus', avatarUrl: null },
  { displayName: 'Vega IT', slug: 'vega-it', avatarUrl: null },
  { displayName: 'HTEC Group', slug: 'htec-group', avatarUrl: null },
  { displayName: 'Levi9', slug: 'levi9', avatarUrl: null },
  { displayName: 'Zühlke', slug: 'zuhlke', avatarUrl: null },
]

const LOCATIONS: readonly (string | null)[] = ['Beograd', 'Novi Sad', 'Niš', null, 'Kragujevac']
const JOB_TYPES: readonly JobType[] = ['FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERNSHIP', 'VOLUNTEER']
const WORKPLACES: readonly WorkplaceType[] = ['REMOTE', 'HYBRID', 'ONSITE']
const LEVELS: readonly ExperienceLevel[] = ['ENTRY', 'MEDIOR', 'SENIOR', 'MANAGER', 'DIRECTOR']
const APPLICATION_TYPES: readonly ApplicationType[] = ['CONTACT_ONLY', 'EXTERNAL_URL', 'CV_DROP']

const slugify = (value: string): string =>
  value
    .toLowerCase()
    .replace(/[čć ]/g, (c) => (c === ' ' ? '-' : c === 'č' || c === 'ć' ? 'c' : c))
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-|-$)/g, '')

const titleFromSlug = (slug: string): string => {
  const words = slug.replace(/-\d+$/, '').split('-').filter(Boolean)
  if (words.length === 0) return 'Oglas za posao'
  return words.map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(' ')
}

/** Cheap stable hash so a given slug always renders the same detail shape. */
const hashString = (value: string): number => {
  let hash = 0
  for (let i = 0; i < value.length; i += 1) hash = (hash * 31 + value.charCodeAt(i)) >>> 0
  return hash
}

const mockSalary = (seed: number): Salary => {
  const period = (['MONTH', 'HOUR', 'JOB', 'MONTH'] as const)[seed % 4]
  const min = period === 'HOUR' ? 8 + (seed % 12) : 1200 + (seed % 8) * 400
  const isRange = seed % 2 === 0
  return {
    min,
    max: isRange ? min + (period === 'HOUR' ? 10 : 900) : null,
    currency: seed % 3 === 0 ? 'RSD' : 'EUR',
    period,
    displayText: null,
  }
}

const mockContact = (): ContactEntry[] => [
  { type: 'email', description: 'Email za prijave', value: 'posao@primer.rs' },
  { type: 'phone', description: 'Kontakt telefon', value: '+381 11 234 567' },
  { type: 'linkedin', description: 'Profil kompanije', value: 'linkedin.com/company/primer' },
]

const mockDescription = (title: string): string =>
  [
    `Tražimo motivisanog kandidata za poziciju „${title}“.`,
    'Radićete u timu koji gradi proizvode za korisnike širom regiona.',
    '',
    'Odgovornosti:',
    '- Razvoj i održavanje ključnih funkcionalnosti',
    '- Saradnja sa dizajnerima i backend timom',
    '- Učešće u code review procesu',
    '',
    'Nudimo fleksibilno radno vreme, edukacije i prijatnu radnu atmosferu.',
  ].join('\n')

const buildListItem = (index: number): JobListItemDto => {
  const title = TITLES[index % TITLES.length]
  const isUser = index % 3 === 0
  const hasSalary = index % 2 === 0
  const createdFor: JobPosterDto = isUser
    ? { actorType: 'user' }
    : { actorType: 'org', ...ORGS[index % ORGS.length] }

  return {
    id: crypto.randomUUID?.() ?? `mock-${Math.floor(Math.random() * 10000000)}`,
    slug: `${slugify(title)}-${index + 1000}`,
    title,
    createdFor,
    location: LOCATIONS[index % LOCATIONS.length],
    salary: hasSalary ? mockSalary(index) : null,
    createdAt: new Date(Date.now() - index * 36 * 60 * 60 * 1000).toISOString(),
  }
}

/** Query string embedded in HAL hrefs, ordered `page`, `size`, then filters. */
const hrefFor = (params: JobQuery, page: number, size: number): string => {
  const sp = new URLSearchParams()
  sp.set('page', String(page))
  sp.set('size', String(size))
  filtersToSearchParams(params).forEach((value, key) => sp.append(key, value))
  return `/api/jobs?${sp.toString()}`
}

/**
 * MOCK - would be:
 *   const { data } = await api.get('/jobs', { params: { page, size, ...toParams(params) } })
 * Page/size pagination, 1-indexed. `totalElements` is fixed per query; results
 * are capped at MAX_MOCK_PAGES so infinite scroll terminates.
 */
export const fetchJobs = async (
  params: JobQuery,
  page = 1,
  size = 20,
): Promise<PagedResponse<JobListItemDto>> => {
  await delay(450)

  const empty = params.q.trim().toLowerCase() === MAGIC_EMPTY_QUERY
  const totalElements = empty ? 0 : 55
  const totalPages = totalElements === 0 ? 0 : Math.min(Math.ceil(totalElements / size), MAX_MOCK_PAGES)
  const last = page >= totalPages
  const count = totalElements === 0 || page > totalPages ? 0 : last ? totalElements - (page - 1) * size : size

  const content: JobListItemDto[] = Array.from({ length: count }, (_, i) =>
    buildListItem((page - 1) * size + i),
  )

  return {
    _links: {
      self: { href: hrefFor(params, page, size) },
      ...(page > 1 ? { prev: { href: hrefFor(params, page - 1, size) } } : {}),
      ...(last ? {} : { next: { href: hrefFor(params, page + 1, size) } }),
    },
    content,
    page,
    size,
    totalElements,
    totalPages,
    last,
  }
}

/**
 * MOCK - would be: const { data } = await api.get(`/jobs/${slug}`)
 * Single-entry: resolves the bare DTO (no wrapper). Unknown slug -> 404.
 */
export const fetchJobBySlug = async (slug: string): Promise<JobDetailDto> => {
  await delay(400)
  if (slug === MAGIC_NOT_FOUND_SLUG) {
    return rejectProblem(404, 'Traženi oglas ne postoji.', 'Not Found')
  }

  const seed = hashString(slug)
  const title = titleFromSlug(slug)
  const applicationType = APPLICATION_TYPES[seed % APPLICATION_TYPES.length]
  const isUser = seed % 4 === 0
  
  return {
    id: crypto.randomUUID?.() ?? `mock-${Math.floor(Math.random() * 10000000)}`,
    slug,
    title,
    createdFor: isUser ? { actorType: 'user' } : { actorType: 'org', ...ORGS[seed % ORGS.length] },
    location: LOCATIONS[seed % LOCATIONS.length],
    salary: seed % 2 === 0 ? mockSalary(seed) : null,
    createdAt: new Date(Date.now() - (seed % 30) * 24 * 60 * 60 * 1000).toISOString(),
    jobType: JOB_TYPES[seed % JOB_TYPES.length],
    workplaceType: WORKPLACES[seed % WORKPLACES.length],
    experienceLevel: LEVELS[seed % LEVELS.length],
    status: 'OPEN',
    applicationType,
    contactInformation: applicationType === 'CONTACT_ONLY' ? mockContact() : null,
    externalUrl: applicationType === 'EXTERNAL_URL' ? 'https://primer.rs/karijera/oglas' : null,
    description: mockDescription(title),
  }
}

/**
 * MOCK - would be: const { data } = await api.post(`/jobs/${jobId}/applications`, form)
 * A filename containing "fail" is rejected so the error UI is testable.
 */
export const submitApplication = async (jobId: string, file: File): Promise<JobApplication> => {
  await delay(600)
  if (file.name.toLowerCase().includes('fail')) {
    return rejectProblem(413, 'Fajl je odbijen. Pokušajte sa manjim PDF-om.', 'Payload Too Large')
  }
  return {
    id: crypto.randomUUID?.() ?? `mock-${Math.floor(Math.random() * 10000000)}`,
    jobId,
    cvUrl: null,
    submittedAt: new Date().toISOString(),
  }
}

/**
 * MOCK - would be: const { data } = await api.get('/jobs', { params: { id: ids, status: statuses } })
 * Hydrates a set of saved-job ids (from localStorage) into list items, filtered
 * by status. The whole id set resolves in one request (a saved set is bounded).
 * ~20% of ids are intentionally omitted to model deleted jobs the pane must
 * tolerate. Each id deterministically maps to exactly one status.
 */
export const fetchJobsByIds = async (
  ids: string[],
  statuses: JobStatus[],
): Promise<SavedJobItemDto[]> => {
  await delay(360)

  const wanted = new Set(statuses)
  return ids
    .map((id, index): SavedJobItemDto | null => {
      const seed = hashString(id)
      if (seed % 5 === 0) return null // ~20% "deleted" - not returned
      const status: JobStatus = seed % 3 === 0 ? 'CLOSED' : 'OPEN'
      if (!wanted.has(status)) return null
      const base = buildListItem(seed % TITLES.length)
      return { ...base, id, status, slug: `${slugify(base.title)}-${(seed % 9000) + 1000 + index}` }
    })
    .filter((item): item is SavedJobItemDto => item !== null)
}

/**
 * MOCK - would be: const { data } = await api.get(`/actors/${actorId}/jobs`)
 * The actor's PUBLIC job postings: OPEN only, newest first, bare array (a
 * profile's open postings are a bounded set - no paging speculatively). An empty
 * array covers BOTH "has no postings" and "postings hidden by
 * PrivacySettings.showJobPostings" - the two are indistinguishable by design, so
 * the mock does not model the flag at all (§9).
 */
export const fetchActorJobPostings = async (actorId: string): Promise<JobListItemDto[]> => {
  await delay(400)

  // Some actors return [] (no postings, or hidden by privacy - indistinguishable).
  const seed = hashString(actorId)
  if (actorId.endsWith('-empty') || seed % 3 === 0) return []

  const count = (seed % 4) + 2 // 2..5 postings
  const base = seed % TITLES.length
  return Array.from({ length: count }, (_, i) => {
    const item = buildListItem(base + i)
    return { ...item, createdAt: new Date(Date.now() - i * 30 * 60 * 60 * 1000).toISOString() }
  })
}
