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
//   - fetchJobBySlug : slug hashing onto VIEWER_ORG_HANDLE -> viewerCan* true
//   - fetchJobBySlug : slug containing "zatvoren" -> CLOSED, "nacrt" -> DRAFT
//                      (a DRAFT also forces viewerCan* true - see below)
//   - submitApplication : filename contains "fail" -> 413 rejection
//   - fetchActorJobPostings : actorId ends with "-empty" -> [] (no/hidden postings)
//   - createJob   : title containing "greska"  -> 422 rejection
//   - createJob   : title containing "tudja"   -> 403 (createdForId refused)
//
// Saved jobs no longer live here (new_docx/types.ts, end of the Jobs block —
// there is no FavoriteJob table); they are browser
// localStorage (utils/savedJobs.ts). `fetchJobsByIds` only HYDRATES an id set.
// -----------------------------------------------------------------------------

// import api from '@utils/api'

import type { ContactEntry } from '@shared/components'
import type { ActorRefDto } from './profiles'
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

/** The occupation axis. The URL carries the kebab slug (`?role=frontend-developer`),
 *  the column stores the enum - `utils/constants/jobRoles.ts` maps between them. */
export type JobCategory =
  | 'SOFTWARE_ENGINEER'
  | 'FRONTEND_DEVELOPER'
  | 'BACKEND_DEVELOPER'
  | 'PRODUCT_DESIGNER'
  | 'PRODUCT_MANAGER'
  | 'DATA_ANALYST'
  | 'DEVOPS_ENGINEER'
  | 'QA_ENGINEER'
  | 'MARKETING_SPECIALIST'
  | 'SALES_REPRESENTATIVE'
  | 'HR_RECRUITER'
  | 'PROJECT_MANAGER'
  | 'OTHER'

/** `POST /jobs` reaches DRAFT or OPEN and never CLOSED: a posting that has never
 *  been open cannot be closed, and the rest of the ladder is a status write. */
export type CreateJobStatus = Extract<JobStatus, 'DRAFT' | 'OPEN'>

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
  | (ActorRefDto & { actorType: 'org' })
  | { actorType: 'user' } // person-posted: intentionally minimal - render a generic tag, NOT a name

export interface JobListItemDto {
  id: string
  slug: string
  title: string
  createdFor: JobPosterDto
  /** NOT NULL even for a REMOTE listing: a remote job still states where the
   *  hiring organization sits, and the create form makes it required. */
  location: string
  salary: Salary | null
  createdAt: string
}

export interface JobDetailDto extends JobListItemDto {
  category: JobCategory
  jobType: JobType
  workplaceType: WorkplaceType
  experienceLevel: ExperienceLevel
  status: JobStatus
  applicationType: ApplicationType
  contactInformation: ContactEntry[] | null
  externalUrl: string | null
  description: string
  keywords?: string[]
  createdForBio?: string | null
  createdForFollowedByViewer?: boolean
  /** Management authority, computed server-side from the caller's token: the
   *  client cannot derive it, because user-facing DTOs omit `createdBy`
   *  (backend-contract.md §8 rule 7). Two independent booleans, never collapsed
   *  into one - the backend grants them together today, but an ADMIN allowed to
   *  delete a posting they may not edit is a plausible split. Absent = false: a
   *  guest, or a viewer with no authority over this posting. Detail only - a
   *  list row (`JobListItemDto`, `SavedJobItemDto`, the search results) renders
   *  no management affordance, so it has no use for either flag. */
  viewerCanEdit?: boolean
  viewerCanDelete?: boolean
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

// ── Write payloads ────────────────────────────────────────────────────────────

/** `displayText` is not authored in a form; the server may still serve one back. */
export type SalaryPayload = Omit<Salary, 'displayText'>

export interface CreateJobPayload {
  /** The actor this is published as, resolved by `usePostAs` as `postAs ?? user.id`.
   *  Always sent, never null, and re-checked server-side against the role matrix
   *  (backend-contract.md §8 rule 6) - the picker is a convenience, not the
   *  authorization. `createdById` is never sent: it is the authenticated caller. */
  createdForId: string
  title: string
  description: string
  category: JobCategory
  keywords: string[]
  jobType: JobType
  workplaceType: WorkplaceType
  experienceLevel: ExperienceLevel
  location: string
  salary: SalaryPayload | null
  status: CreateJobStatus
  applicationType: ApplicationType
  contactInformation: ContactEntry[] | null
  externalUrl: string | null
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
/** Substrings, not whole slugs: the public list only ever builds slugs from a
 *  title, so a CLOSED or DRAFT posting is reachable by direct link and by an
 *  actor's own postings, and never from a search result. Any slug carrying one
 *  works - `/jobs/frontend-programer-nacrt` reads as a real posting because
 *  `titleFromSlug` strips the marker back out. */
const MAGIC_CLOSED_SLUG = 'zatvoren'
const MAGIC_DRAFT_SLUG = 'nacrt'
/** The org behind the editable `moja-kompanija` profile in profiles.ts. A job
 *  posted for it is the one case the mock can call the viewer's own, which is
 *  what makes the authority flags reachable; the real API reads them off the
 *  JWT and never off the poster's handle. */
const VIEWER_ORG_HANDLE = 'moja-kompanija'
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

const ORGS: readonly (ActorRefDto & { actorType: 'org' })[] = [
  { actorType: 'org', id: 'org-nordeus', name: 'Nordeus', handle: 'nordeus', avatarUrl: null },
  { actorType: 'org', id: 'org-vega', name: 'Vega IT', handle: 'vega-it', avatarUrl: null },
  { actorType: 'org', id: 'org-htec', name: 'HTEC Group', handle: 'htec-group', avatarUrl: null },
  { actorType: 'org', id: 'org-levi9', name: 'Levi9', handle: 'levi9', avatarUrl: null },
  { actorType: 'org', id: 'org-zuhlke', name: 'Zühlke', handle: 'zuhlke', avatarUrl: null },
  { actorType: 'org', id: 'org-moja', name: 'Moja Kompanija', handle: VIEWER_ORG_HANDLE, avatarUrl: null },
]

const ORG_BIOS: Readonly<Record<string, string>> = {
  'org-nordeus': 'Gejming studio iz Beograda. Pravimo igre koje igraju milioni.',
  'org-vega': 'IT kompanija sa timovima u Novom Sadu i Beogradu.',
  'org-htec': 'Inženjerska kuća koja gradi proizvode za klijente iz Evrope i SAD.',
  'org-levi9': 'Tehnološki servis sa timovima u Srbiji, Rumuniji i Ukrajini.',
  'org-zuhlke': 'Konsultantska kuća za inženjering i inovacije.',
  'org-moja': 'Softverska agencija. Radimo web i mobilne aplikacije po meri.',
}

const LOCATIONS: readonly string[] = ['Beograd', 'Novi Sad', 'Niš', 'Subotica', 'Kragujevac']
/** Parallel to TITLES, so a mocked posting's occupation matches its name. */
const CATEGORIES: readonly JobCategory[] = [
  'FRONTEND_DEVELOPER',
  'BACKEND_DEVELOPER',
  'PRODUCT_DESIGNER',
  'DEVOPS_ENGINEER',
  'QA_ENGINEER',
  'DATA_ANALYST',
  'PRODUCT_MANAGER',
  'SOFTWARE_ENGINEER',
]
const JOB_TYPES: readonly JobType[] = ['FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERNSHIP', 'VOLUNTEER']
const WORKPLACES: readonly WorkplaceType[] = ['REMOTE', 'HYBRID', 'ONSITE']
const LEVELS: readonly ExperienceLevel[] = ['ENTRY', 'MEDIOR', 'SENIOR', 'MANAGER', 'DIRECTOR']
const APPLICATION_TYPES: readonly ApplicationType[] = ['CONTACT_ONLY', 'EXTERNAL_URL', 'CV_DROP']
/** Poster-authored, so deliberately unruly - mixed case and multi-word entries
 *  are what the detail page's plain-text rendering has to survive. */
const KEYWORDS: readonly string[] = [
  'React',
  'TypeScript',
  'REST',
  'Docker',
  'PostgreSQL',
  'agilno okruženje',
  'rad od kuće',
  'CI/CD',
]

const slugify = (value: string): string =>
  value
    .toLowerCase()
    .replace(/[čć ]/g, (c) => (c === ' ' ? '-' : c === 'č' || c === 'ć' ? 'c' : c))
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-|-$)/g, '')

const titleFromSlug = (slug: string): string => {
  const words = slug
    .replace(/-\d+$/, '')
    .split('-')
    .filter((w) => w && w !== MAGIC_CLOSED_SLUG && w !== MAGIC_DRAFT_SLUG)
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
  const createdFor: JobPosterDto = isUser ? { actorType: 'user' } : ORGS[index % ORGS.length]

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
 * A posting whose slug hashes onto VIEWER_ORG_HANDLE comes back with both
 * authority flags set - the real API reads them off the JWT.
 *
 * A DRAFT always comes back with them too, and that is not a shortcut: the real
 * route answers 404 for a draft to any caller who cannot edit it, so "a draft
 * the viewer has no authority over" is a response the backend never produces
 * and the client must never be shaped around.
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
  const createdFor: JobPosterDto = isUser ? { actorType: 'user' } : ORGS[seed % ORGS.length]
  const isOrg = createdFor.actorType === 'org'
  const status: JobStatus = slug.includes(MAGIC_CLOSED_SLUG)
    ? 'CLOSED'
    : slug.includes(MAGIC_DRAFT_SLUG)
      ? 'DRAFT'
      : 'OPEN'
  const viewerOwns = status === 'DRAFT' || (isOrg && createdFor.handle === VIEWER_ORG_HANDLE)

  return {
    id: crypto.randomUUID?.() ?? `mock-${Math.floor(Math.random() * 10000000)}`,
    slug,
    title,
    createdFor,
    location: LOCATIONS[seed % LOCATIONS.length],
    salary: seed % 2 === 0 ? mockSalary(seed) : null,
    createdAt: new Date(Date.now() - (seed % 30) * 24 * 60 * 60 * 1000).toISOString(),
    category: CATEGORIES[seed % CATEGORIES.length],
    jobType: JOB_TYPES[seed % JOB_TYPES.length],
    workplaceType: WORKPLACES[seed % WORKPLACES.length],
    experienceLevel: LEVELS[seed % LEVELS.length],
    status,
    applicationType,
    contactInformation: applicationType === 'CONTACT_ONLY' ? mockContact() : null,
    externalUrl: applicationType === 'EXTERNAL_URL' ? 'https://primer.rs/karijera/oglas' : null,
    description: mockDescription(title),
    keywords: KEYWORDS.slice(seed % 4, (seed % 4) + 3 + (seed % 3)),
    ...(isOrg
      ? {
          createdForBio: ORG_BIOS[createdFor.id] ?? null,
          createdForFollowedByViewer: seed % 3 === 0,
        }
      : {}),
    ...(viewerOwns ? { viewerCanEdit: true, viewerCanDelete: true } : {}),
  }
}

/**
 * MOCK - would be: const { data } = await api.post('/jobs', payload)
 * A title containing "greska" is rejected so the error UI is testable, and one
 * containing "tudja" comes back 403 - the server disagreeing with the identity
 * the picker offered, which is the one failure that must never be retried.
 *
 * The slug is derived from the title plus a numeric suffix because a real slug
 * is server-assigned and unique, and the page navigates to it.
 */
export const createJob = async (payload: CreateJobPayload): Promise<JobDetailDto> => {
  await delay(650)

  const title = payload.title.trim()
  if (title.toLowerCase().includes('greska')) {
    return rejectProblem(422, 'Oglas nije sačuvan. Proverite unos i pokušajte ponovo.', 'Unprocessable Entity')
  }
  if (title.toLowerCase().includes('tudja')) {
    return rejectProblem(403, 'Nemate pravo da objavljujete u ime izabrane organizacije.', 'Forbidden')
  }

  const org = ORGS.find((row) => row.id === payload.createdForId)
  const createdFor: JobPosterDto = org ?? { actorType: 'user' }

  return {
    id: crypto.randomUUID?.() ?? `mock-${Math.floor(Math.random() * 10000000)}`,
    slug: `${slugify(title)}-${1000 + (hashString(title) % 9000)}`,
    title,
    createdFor,
    location: payload.location,
    salary: payload.salary ? { ...payload.salary, displayText: null } : null,
    createdAt: new Date().toISOString(),
    category: payload.category,
    jobType: payload.jobType,
    workplaceType: payload.workplaceType,
    experienceLevel: payload.experienceLevel,
    status: payload.status,
    applicationType: payload.applicationType,
    contactInformation: payload.contactInformation,
    externalUrl: payload.externalUrl,
    description: payload.description,
    keywords: payload.keywords,
    ...(org ? { createdForBio: ORG_BIOS[org.id] ?? null, createdForFollowedByViewer: false } : {}),
    viewerCanEdit: true,
    viewerCanDelete: true,
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
