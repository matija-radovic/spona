// -----------------------------------------------------------------------------
// Jobs service. The three reads and the four posting writes hit the backend;
// `fetchJobApplications`, `submitApplication` and `fetchActorJobPostings` are
// mocks that fake a delayed response, the real axios call commented above each
// body. DTO shapes are separate from the DB model (new_docx/types.ts):
// user-facing DTOs OMIT `createdBy` and ship a stripped, discriminated
// `createdFor`. Transport envelopes come from `./types`.
//
// All rejections are `ProblemDetail` shapes so callers can `parseProblem(err)`
// uniformly. Magic inputs make the mocks' failure/empty paths reachable:
//   - submitApplication : filename contains "fail" -> 413 rejection
//   - fetchActorJobPostings : actorId ends with "-empty" -> [] (no/hidden postings)
//   - fetchActorJobPostings : an actor whose postings the viewer manages -> the
//                      owner's shape: every status, and the parameters answered
//
// Saved jobs no longer live here (new_docx/types.ts, end of the Jobs block —
// there is no FavoriteJob table); they are browser
// localStorage (utils/savedJobs.ts). `fetchJobsByIds` only HYDRATES an id set.
// -----------------------------------------------------------------------------

import api from '@utils/api'

import type { ContactEntry } from '@shared/components'
import type { ActorRefDto } from './profiles'
import type { PagedResponse, ProblemDetail } from './types'
import type { JobQuery } from '@pages/jobs/lib/filters'
import { filtersToSearchParams } from '@pages/jobs/lib/filters'
import {
  ORGANIZATIONS,
  VIEWER,
  findOrgById,
  orgRef,
  orgViewerCanManagePostings,
  personRef,
  viewerRoleIn,
} from './mockActors'

// ── Local enum mirrors (the project mirrors backend enums as local unions) ─────

/** The engagement being offered - the contract, not the hours. How many hours it
 *  runs to is `WorkingHours`, which varies independently of every member here. */
export type JobType =
  | 'PERMANENT'
  | 'FIXED_TERM'
  | 'SEASONAL'
  | 'FREELANCE'
  | 'INTERNSHIP'
  | 'VOLUNTEER'

/** Full or reduced hours. Nullable on `Job`: most postings never say. */
export type WorkingHours = 'FULL_TIME' | 'PART_TIME'

/** Where the work is performed, and nothing else - the kind of work is `category`.
 *  `FIELD` is work whose location changes: client homes, sites, routes, venues. */
export type WorkplaceType = 'ONSITE' | 'REMOTE' | 'HYBRID' | 'FIELD' | 'OTHER'

export type ApplicationType = 'CONTACT_ONLY' | 'EXTERNAL_URL' | 'CV_DROP'
export type JobStatus = 'DRAFT' | 'OPEN' | 'CLOSED'

/** Sort of an actor's own postings. `updated_*` reads `Job.updatedAt`, which is
 *  never served in a DTO - the value only travels towards the server. */
export type JobPostingsSort = 'created_desc' | 'created_asc' | 'updated_desc' | 'updated_asc'

/** The minimum experience the posting asks for, as a threshold rather than a
 *  band: a poster wanting 8 years reaches for "over 5" and is not forced to
 *  choose between 5-10 and 10-15. Ordered by increasing requirement. */
export type ExperienceLevel =
  | 'NONE'
  | 'MONTHS_6'
  | 'YEARS_1'
  | 'YEARS_2'
  | 'YEARS_3'
  | 'YEARS_5'
  | 'YEARS_10'

export type SalaryPeriod = 'HOUR' | 'DAY' | 'WEEK' | 'MONTH' | 'JOB'

/**
 * The occupation axis, cut at the occupational-group level - never a job title,
 * which is `title` and already weighted above everything else in search.
 *
 * It has to stay a closed enum: the `(status, category)` index, the `?role=`
 * chips and the slug round-trip all match exactly, and free text fragments a
 * group into four spellings that each return a quarter of its jobs. The URL
 * carries the kebab slug (`?role=it-software`) and the backend maps it back;
 * the client only goes the other way, through `slugFromCategory`.
 *
 * Finding the right group is a client-side synonym index over this list
 * (`jobs.categorySynonyms` in the locale file), not a taxonomy table.
 */
export type JobCategory =
  | 'IT_SOFTWARE'
  | 'IT_INFRASTRUCTURE'
  | 'DATA_AI'
  | 'DESIGN_CREATIVE'
  | 'MEDIA_JOURNALISM'
  | 'MARKETING_PR'
  | 'SALES'
  | 'RETAIL'
  | 'CUSTOMER_SUPPORT'
  | 'ADMIN_OFFICE'
  | 'FINANCE_ACCOUNTING'
  | 'LEGAL'
  | 'HR'
  | 'PROJECT_PRODUCT'
  | 'EDUCATION'
  | 'HEALTHCARE'
  | 'CARE_SERVICES'
  | 'CONSTRUCTION'
  | 'SKILLED_TRADES'
  | 'MANUFACTURING'
  | 'ENGINEERING'
  | 'AUTOMOTIVE'
  | 'TRANSPORT_LOGISTICS'
  | 'WAREHOUSE'
  | 'HOSPITALITY_FOOD'
  | 'TOURISM'
  | 'AGRICULTURE'
  | 'CLEANING_MAINTENANCE'
  | 'SECURITY'
  | 'BEAUTY_WELLNESS'
  | 'SPORT_RECREATION'
  | 'ARTS_CULTURE'
  | 'TRANSLATION'
  | 'SCIENCE_RESEARCH'
  | 'REAL_ESTATE'
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
  /** Poster-authored tags, at most ten. Pills on a card, plain text on the
   *  detail page (design-choices.md §A pill holds a value from a closed set). */
  keywords?: string[]
}

export interface JobDetailDto extends JobListItemDto {
  /** The three below are NULL only on a DRAFT: publishing requires all of them,
   *  so an OPEN or CLOSED posting always states its category, engagement and
   *  work model. */
  category: JobCategory | null
  jobType: JobType | null
  workplaceType: WorkplaceType | null
  /** These two stay NULL on a published posting when it says nothing. Rendered
   *  as nothing, matched by no filter. */
  workingHours: WorkingHours | null
  experienceLevel: ExperienceLevel | null
  status: JobStatus
  applicationType: ApplicationType
  contactInformation: ContactEntry[] | null
  externalUrl: string | null
  description: string
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
  /** `COUNT(JobApplication)` for this posting, served to a viewer who may edit
   *  it and absent to everyone else. Derived per request, not a column: it is
   *  what the applications nav item counts and what the delete dialog names
   *  before destroying the rows the cascade takes with the posting. */
  applicationCount?: number
}

/** JobListItemDto + status, for the two profile panes whose rows are not all
 *  OPEN: the Saved-jobs pane filters on it, the postings pane draws a capsule
 *  from it. The public list and the search results are served OPEN only. */
export interface SavedJobItemDto extends JobListItemDto {
  status: JobStatus
}

/** Owner-only parameters, answered with a `400` rather than narrowed for anyone
 *  else - so the public shape sends neither. */
export interface ActorJobPostingsQuery {
  status?: JobStatus
  sort?: JobPostingsSort
}

/** One row of the poster's applications inbox, and what a submission answers
 *  with. `cvUrl` is NOT NULL because the upload is what creates the row (D20),
 *  and there is no `jobId`: the inbox is already scoped to one posting and the
 *  applicant posted to that posting's URL.
 *
 *  `applicant` is a user reference and never an organization's - `JobApplication`
 *  has no `createdForId`, so an application is always personal. */
export interface JobApplicationDto {
  id: string
  applicant: ActorRefDto
  cvUrl: string
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
  keywords: string[]
  /** Every picker sends NULL when it was left on its "not stated" row. The
   *  first three are refused with `status: 'OPEN'` and accepted on a draft. */
  category: JobCategory | null
  jobType: JobType | null
  workplaceType: WorkplaceType | null
  workingHours: WorkingHours | null
  experienceLevel: ExperienceLevel | null
  location: string
  salary: SalaryPayload | null
  status: CreateJobStatus
  applicationType: ApplicationType
  contactInformation: ContactEntry[] | null
  externalUrl: string | null
}

/**
 * The manage page's write. Deliberately NOT `CreateJobPayload` minus a field:
 * it carries no `createdForId`, because the publishing identity is fixed at
 * creation (design-choices.md §Acting Identity) - it is the posting's identity,
 * what its applications belong to and what its authorization is checked
 * against, not a preference on it. It carries no `status` either: the lifecycle
 * is `PUT /jobs/{id}/status`, so that one ladder has one route.
 */
export interface UpdateJobPayload {
  title: string
  description: string
  keywords: string[]
  category: JobCategory | null
  jobType: JobType | null
  workplaceType: WorkplaceType | null
  workingHours: WorkingHours | null
  experienceLevel: ExperienceLevel | null
  location: string
  salary: SalaryPayload | null
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

/** Put in the slug of an actor's DRAFT or CLOSED posting. */
const MAGIC_CLOSED_SLUG = 'zatvoren'
const MAGIC_DRAFT_SLUG = 'nacrt'
/** Twelve statuses, so an owner's pane pages at all, and all three kinds so the
 *  capsule renders. */
const OWNER_STATUSES: readonly JobStatus[] = [
  'OPEN', 'DRAFT', 'CLOSED', 'OPEN', 'DRAFT', 'OPEN',
  'CLOSED', 'OPEN', 'DRAFT', 'OPEN', 'OPEN', 'DRAFT',
]
/** The statuses an actor's own postings pane serves, or undefined for the public
 *  shape. Set for every actor profiles.ts hands `viewerCanManageJobPostings`: the
 *  viewer, and every organization whose role manages postings (everyone but an
 *  EDITOR). The viewer has no CLOSED posting, which is what makes the
 *  filtered-to-nothing state reachable. Asked per request, since the role is
 *  session state; the real API decides it off the caller's token. */
const VIEWER_OWN_STATUSES: readonly JobStatus[] = ['DRAFT', 'OPEN', 'OPEN']

/** Whether the viewer's live role in an organization manages its postings; the real API
 *  reads it off the JWT and never off the poster's handle. */
const viewerManagesPostingsOf = (actorId: string): boolean => {
  const org = findOrgById(actorId)
  return !!org && orgViewerCanManagePostings(viewerRoleIn(org.slug))
}

const ownedStatusesFor = (actorId: string): readonly JobStatus[] | undefined => {
  if (actorId === VIEWER.id) return VIEWER_OWN_STATUSES
  return viewerManagesPostingsOf(actorId) ? OWNER_STATUSES : undefined
}

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

const ORGS: readonly (ActorRefDto & { actorType: 'org' })[] = ORGANIZATIONS.map((row) => ({
  ...orgRef(row.slug),
  actorType: 'org' as const,
}))

/** Always people: `JobApplication` has no `createdForId`, so nobody applies in
 *  an organization's name. One id per person, so an applicant row and the
 *  profile it links to are the same human. */
const APPLICANTS: readonly ActorRefDto[] = [
  personRef('mila'),
  personRef('stefan'),
  personRef('ana'),
  personRef('marko'),
  personRef('jelena'),
]

const LOCATIONS: readonly string[] = ['Beograd', 'Novi Sad', 'Niš', 'Subotica', 'Kragujevac']
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

const buildListItem = (index: number): JobListItemDto => {
  const title = TITLES[index % TITLES.length]
  const isUser = index % 3 === 0
  const hasSalary = index % 2 === 0
  const createdFor: JobPosterDto = isUser ? { actorType: 'user' } : ORGS[index % ORGS.length]
  // Every fifth posting states none, so the card's "no keywords row" branch is
  // as reachable as the row itself.
  // Every fifth posting states none, so the card's no-keywords branch is reachable.
  const keywordCount = index % 5 === 0 ? 0 : 2 + (index % 3)

  return {
    id: crypto.randomUUID?.() ?? `mock-${Math.floor(Math.random() * 10000000)}`,
    slug: `${slugify(title)}-${index + 1000}`,
    title,
    createdFor,
    location: LOCATIONS[index % LOCATIONS.length],
    salary: hasSalary ? mockSalary(index) : null,
    createdAt: new Date(Date.now() - index * 36 * 60 * 60 * 1000).toISOString(),
    ...(keywordCount > 0
      ? { keywords: KEYWORDS.slice(index % 4, (index % 4) + keywordCount) }
      : {}),
  }
}

/** The public list with nothing selected: `GET /jobs` with no parameters. */
export const NO_JOB_FILTERS: JobQuery = {
  q: '',
  role: null,
  posted: null,
  sort: null,
  workplace: [],
  employment: [],
  hours: [],
  experience: [],
  salary: false,
}

export const fetchJobs = async (
  query: JobQuery = NO_JOB_FILTERS,
  page = 1,
  size = 20,
): Promise<PagedResponse<JobListItemDto>> => {
  const params = filtersToSearchParams(query)
  params.set('page', String(page))
  params.set('size', String(size))
  const { data } = await api.get<PagedResponse<JobListItemDto>>('/jobs', { params })
  return data
}

/** How many applications the mocked inbox holds, pinned above a page so its
 *  "load more" is reachable. */
const applicationCountFor = (id: string): number => 11 + (hashString(id) % 9)

export const fetchJobBySlug = async (slug: string): Promise<JobDetailDto> => {
  const { data } = await api.get<JobDetailDto>(`/jobs/${slug}`)
  return data
}

/** Answers the created posting, whose server-assigned slug the page navigates to. */
export const createJob = async (payload: CreateJobPayload): Promise<JobDetailDto> => {
  const { data } = await api.post<JobDetailDto>('/jobs', payload)
  return data
}

/** Every column but the status, which moves only through `setJobStatus`. */
export const updateJob = async (id: string, payload: UpdateJobPayload): Promise<JobDetailDto> => {
  const { data } = await api.put<JobDetailDto>(`/jobs/${id}`, payload)
  return data
}

/** DRAFT -> OPEN, and OPEN <-> CLOSED. */
export const setJobStatus = async (id: string, status: JobStatus): Promise<JobDetailDto> => {
  const { data } = await api.put<JobDetailDto>(`/jobs/${id}/status`, { status })
  return data
}

/** The posting's applications go with it. */
export const deleteJob = async (id: string): Promise<void> => {
  await api.delete(`/jobs/${id}`)
}

/**
 * MOCK - would be:
 *   const { data } = await api.get(`/jobs/${id}/applications`, { params: { page, size } })
 * Poster-only, and CV_DROP only: contact and external applications happen
 * outside the system and leave no row. Newest first, per the
 * `(jobId, submittedAt DESC)` index.
 */
export const fetchJobApplications = async (
  id: string,
  page = 1,
  size = 10,
): Promise<PagedResponse<JobApplicationDto>> => {
  await delay(420)

  const totalElements = applicationCountFor(id)
  const totalPages = totalElements === 0 ? 0 : Math.ceil(totalElements / size)
  const last = page >= totalPages
  const offset = (page - 1) * size
  const count = Math.max(0, Math.min(size, totalElements - offset))

  const content: JobApplicationDto[] = Array.from({ length: count }, (_, i) => {
    const index = offset + i
    const seed = hashString(`${id}-${index}`)
    return {
      id: `${id}-application-${index}`,
      applicant: APPLICANTS[seed % APPLICANTS.length],
      cvUrl: `https://primer.rs/cv/${seed}.pdf`,
      submittedAt: new Date(Date.now() - index * 19 * 60 * 60 * 1000).toISOString(),
    }
  })

  const href = (target: number) => `/api/jobs/${id}/applications?page=${target}&size=${size}`

  return {
    _links: {
      self: { href: href(page) },
      ...(page > 1 ? { prev: { href: href(page - 1) } } : {}),
      ...(last ? {} : { next: { href: href(page + 1) } }),
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
 * MOCK - would be: const { data } = await api.post(`/jobs/${jobId}/applications`, form)
 * Only an OPEN posting takes applications (backend-contract.md §8 rule 20): a
 * DRAFT has not been published and a CLOSED one has stopped asking. The real
 * route answers 404 rather than 409 for a draft the caller may not edit, which is
 * a response the mock cannot produce - it owns every draft it serves.
 * A filename containing "fail" is rejected so the error UI is testable.
 */
export const submitApplication = async (
  jobId: string,
  file: File,
): Promise<JobApplicationDto> => {
  await delay(600)

  if (file.name.toLowerCase().includes('fail')) {
    return rejectProblem(413, 'Fajl je odbijen. Pokušajte sa manjim PDF-om.', 'Payload Too Large')
  }
  return {
    id: crypto.randomUUID?.() ?? `mock-${Math.floor(Math.random() * 10000000)}`,
    applicant: APPLICANTS[hashString(jobId) % APPLICANTS.length],
    cvUrl: `https://primer.rs/cv/${slugify(file.name)}.pdf`,
    submittedAt: new Date().toISOString(),
  }
}

/** Both lists go comma-joined: axios would write an array as `id[]=`, which the backend does not read. */
export const fetchJobsByIds = async (
  ids: string[],
  statuses: JobStatus[],
): Promise<SavedJobItemDto[]> => {
  const { data } = await api.get<SavedJobItemDto[]>('/jobs', {
    params: { id: ids.join(','), status: statuses.join(',') },
  })
  return data
}

/** An actor's own postings were published by that actor, so the poster is
 *  stamped from the id rather than left to buildListItem's rotation. The id is
 *  unwound with the `<actorType>-<handle>` rule that built it, so an
 *  organization outside the fixture still attributes its own postings. */
const postingPoster = (actorId: string): JobPosterDto | null => {
  if (actorId.startsWith('org-')) {
    return { ...orgRef(actorId.slice('org-'.length)), actorType: 'org' as const }
  }
  return actorId.startsWith('user-') ? { actorType: 'user' } : null
}

/** A DRAFT or CLOSED row carries its status marker in the slug. */
const buildPosting = (
  index: number,
  status: JobStatus,
  order: number,
  poster: JobPosterDto | null,
): SavedJobItemDto => {
  const base = buildListItem(index)
  const marker = status === 'DRAFT' ? MAGIC_DRAFT_SLUG : MAGIC_CLOSED_SLUG
  return {
    ...base,
    ...(poster ? { createdFor: poster } : {}),
    status,
    slug:
      status === 'OPEN' ? base.slug : `${slugify(base.title)}-${marker}-${index + 1000 + order}`,
    createdAt: new Date(Date.now() - order * 30 * 60 * 60 * 1000).toISOString(),
  }
}

/** Stays inside the mock, as the column stays inside the database. Derived from
 *  the row so filtering never restamps it. */
const mockUpdatedAt = (item: SavedJobItemDto): number =>
  new Date(item.createdAt).getTime() + (hashString(item.slug) % 5) * 12 * 60 * 60 * 1000

const sortPostings = (rows: SavedJobItemDto[], sort: JobPostingsSort): SavedJobItemDto[] => {
  const byUpdated = sort.startsWith('updated')
  const ascending = sort.endsWith('_asc')
  const stampOf = (item: SavedJobItemDto): number =>
    byUpdated ? mockUpdatedAt(item) : new Date(item.createdAt).getTime()

  return [...rows].sort((a, b) => (ascending ? stampOf(a) - stampOf(b) : stampOf(b) - stampOf(a)))
}

const postingsHref = (
  actorId: string,
  params: ActorJobPostingsQuery | undefined,
  page: number,
  size: number,
): string => {
  const sp = new URLSearchParams()
  sp.set('page', String(page))
  sp.set('size', String(size))
  if (params?.status) sp.set('status', params.status)
  if (params?.sort) sp.set('sort', params.sort)
  return `/api/actors/${actorId}/jobs?${sp.toString()}`
}

/** Every posting the caller is allowed to see, before paging. */
const allPostingsFor = (
  actorId: string,
  params?: ActorJobPostingsQuery,
): SavedJobItemDto[] => {
  if (actorId.endsWith('-empty')) return []

  const seed = hashString(actorId)
  const owned = ownedStatusesFor(actorId)
  const poster = postingPoster(actorId)

  if (!owned) {
    // A typed handle may return [] (no postings, or hidden by privacy - the two
    // are indistinguishable by design). A fixture organization never does: which
    // of the named companies has a postings tab must not depend on how its id
    // happens to hash, or renaming one silently moves the empty case.
    if (seed % 3 === 0 && !findOrgById(actorId)) return []
    const count = (seed % 9) + 4 // 4..12 postings
    const base = seed % TITLES.length
    return Array.from({ length: count }, (_, i) => buildPosting(base + i, 'OPEN', i, poster))
  }

  const rows = owned
    .map((status, i) => buildPosting((seed + i) % TITLES.length, status, i, poster))
    .filter((row) => !params?.status || row.status === params.status)
  return sortPostings(rows, params?.sort ?? 'created_desc')
}

/**
 * MOCK - would be:
 *   const { data } = await api.get(`/actors/${actorId}/jobs`, { params: { page, size, ...params } })
 * One route, two audiences. Anyone else gets the actor's PUBLIC postings, OPEN
 * only, and an empty page there covers BOTH "has no postings" and "postings
 * hidden by PrivacySettings.showJobPostings" - indistinguishable by design, so
 * the mock does not model the flag at all. Someone who may manage them gets
 * every status plus `status` and `sort`, which the real route answers with a
 * `400` for anyone else rather than narrowing.
 */
export const fetchActorJobPostings = async (
  actorId: string,
  params?: ActorJobPostingsQuery,
  page = 1,
  size = 10,
): Promise<PagedResponse<SavedJobItemDto>> => {
  await delay(400)

  const all = allPostingsFor(actorId, params)
  const totalElements = all.length
  const totalPages = totalElements === 0 ? 0 : Math.ceil(totalElements / size)
  const last = page >= totalPages
  const content = all.slice((page - 1) * size, page * size)

  return {
    _links: {
      self: { href: postingsHref(actorId, params, page, size) },
      ...(page > 1 ? { prev: { href: postingsHref(actorId, params, page - 1, size) } } : {}),
      ...(last ? {} : { next: { href: postingsHref(actorId, params, page + 1, size) } }),
    },
    content,
    page,
    size,
    totalElements,
    totalPages,
    last,
  }
}
