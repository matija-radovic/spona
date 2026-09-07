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
//   - fetchJobBySlug : slug containing "prijave" -> a CV_DROP posting the viewer
//                      owns, with a full inbox; "bez-prijava" -> the same, empty
//   - submitApplication : filename contains "fail" -> 413 rejection
//   - submitApplication : a posting that is not OPEN -> 409 rejection
//   - fetchActorJobPostings : actorId ends with "-empty" -> [] (no/hidden postings)
//   - fetchActorJobPostings : an actor whose postings the viewer manages -> the
//                      owner's shape: every status, and the parameters answered
//   - createJob   : title containing "greska"  -> 422 rejection
//   - createJob   : title containing "tudja"   -> 403 (createdForId refused)
//   - updateJob   : title containing "greska"  -> 422 rejection
//   - updateJob   : a different applicationType on a published posting -> 409
//   - updateJob / setJobStatus : a published posting left without the three
//                      pickers, or CONTACT_ONLY without an entry -> 422
//   - setJobStatus / deleteJob / fetchJobApplications : a posting whose stored
//                      title contains "greska" -> 409 / 409 / 500 rejection
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

const MAGIC_EMPTY_QUERY = 'prazno'
const MAGIC_NOT_FOUND_SLUG = 'nepostojeci'
/** Substrings, not whole slugs: the public list only ever builds slugs from a
 *  title, so a CLOSED or DRAFT posting is reachable by direct link and by an
 *  actor's own postings, and never from a search result. Any slug carrying one
 *  works - `/jobs/frontend-programer-nacrt` reads as a real posting because
 *  `titleFromSlug` strips the marker back out. */
const MAGIC_CLOSED_SLUG = 'zatvoren'
const MAGIC_DRAFT_SLUG = 'nacrt'
/** The two the applications inbox needs: a CV_DROP posting the viewer owns,
 *  once with rows and once without, since neither state is reachable through a
 *  hash the caller cannot aim. */
const MAGIC_INBOX_SLUG = 'prijave'
const MAGIC_EMPTY_INBOX_SLUG = 'bez-prijava'
/** Every id-addressed write refuses a posting whose title carries this, the way
 *  `createJob` already refuses one. */
const MAGIC_FAIL_TITLE = 'greska'
/** Carried on the id, because the writes are addressed by id and never see a slug. */
const EMPTY_INBOX_MARK = '-empty'
const SLUG_MARKERS: readonly string[] = [
  MAGIC_EMPTY_INBOX_SLUG,
  MAGIC_INBOX_SLUG,
  MAGIC_CLOSED_SLUG,
  MAGIC_DRAFT_SLUG,
]
/** The org the viewer owns. A job posted for it is the one case the mock can
 *  call the viewer's own, which is what makes the authority flags reachable; the
 *  real API reads them off the JWT and never off the poster's handle. */
const VIEWER_ORG_HANDLE = 'moja-kompanija'
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

const ownedStatusesFor = (actorId: string): readonly JobStatus[] | undefined => {
  if (actorId === VIEWER.id) return VIEWER_OWN_STATUSES
  const org = findOrgById(actorId)
  return org && orgViewerCanManagePostings(viewerRoleIn(org.slug)) ? OWNER_STATUSES : undefined
}
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

const ORG_BIOS: Readonly<Record<string, string>> = Object.fromEntries(
  ORGANIZATIONS.map((row) => [row.id, row.description]),
)

const LOCATIONS: readonly string[] = ['Beograd', 'Novi Sad', 'Niš', 'Subotica', 'Kragujevac']
/** Parallel to TITLES, so a mocked posting's occupational group matches its name. */
const CATEGORIES: readonly JobCategory[] = [
  'IT_SOFTWARE',
  'IT_SOFTWARE',
  'DESIGN_CREATIVE',
  'IT_INFRASTRUCTURE',
  'IT_SOFTWARE',
  'DATA_AI',
  'PROJECT_PRODUCT',
  'IT_SOFTWARE',
]
const JOB_TYPES: readonly JobType[] = ['PERMANENT', 'FIXED_TERM', 'SEASONAL', 'FREELANCE', 'INTERNSHIP']
const WORKPLACES: readonly WorkplaceType[] = ['REMOTE', 'HYBRID', 'ONSITE', 'FIELD']
/** The nulls are load-bearing: without them no mocked posting leaves these two
 *  unstated, and the conditional detail pills are unreachable. */
const WORKING_HOURS: readonly (WorkingHours | null)[] = ['FULL_TIME', 'PART_TIME', null]
const LEVELS: readonly (ExperienceLevel | null)[] = [
  'NONE',
  'MONTHS_6',
  'YEARS_1',
  'YEARS_2',
  'YEARS_3',
  'YEARS_5',
  'YEARS_10',
  null,
]
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
  const stripped = SLUG_MARKERS.reduce(
    (acc, marker) => acc.split(marker).join('-'),
    slug.replace(/-\d+$/, ''),
  )
  const words = stripped.split('-').filter(Boolean)
  if (words.length === 0) return 'Oglas za posao'
  return words.map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(' ')
}

/** Cheap stable hash so a given slug always renders the same detail shape. */
const hashString = (value: string): number => {
  let hash = 0
  for (let i = 0; i < value.length; i += 1) hash = (hash * 31 + value.charCodeAt(i)) >>> 0
  return hash
}

/** Derived rather than random, so a posting keeps its id across reloads: the
 *  saved-jobs set, `applicationCount` and the inbox all key off it. */
const jobIdFromSlug = (slug: string): string =>
  `job-${hashString(slug)}${slug.includes(MAGIC_EMPTY_INBOX_SLUG) ? EMPTY_INBOX_MARK : ''}`

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

/** How many applications a posting holds. CV_DROP only - the other two types
 *  are answered outside the system and leave no row - and pinned above a page so
 *  the inbox's "load more" is reachable. */
const applicationCountFor = (id: string, applicationType: ApplicationType): number =>
  applicationType !== 'CV_DROP' || id.endsWith(EMPTY_INBOX_MARK) ? 0 : 11 + (hashString(id) % 9)

const deriveJob = (slug: string): JobDetailDto => {
  const seed = hashString(slug)
  const id = jobIdFromSlug(slug)
  const title = titleFromSlug(slug)
  const isInbox = slug.includes(MAGIC_INBOX_SLUG) || slug.includes(MAGIC_EMPTY_INBOX_SLUG)
  const applicationType: ApplicationType = isInbox
    ? 'CV_DROP'
    : APPLICATION_TYPES[seed % APPLICATION_TYPES.length]
  const isUser = seed % 4 === 0
  const createdFor: JobPosterDto = isUser ? { actorType: 'user' } : ORGS[seed % ORGS.length]
  const isOrg = createdFor.actorType === 'org'
  const status: JobStatus = slug.includes(MAGIC_CLOSED_SLUG)
    ? 'CLOSED'
    : slug.includes(MAGIC_DRAFT_SLUG)
      ? 'DRAFT'
      : 'OPEN'
  const viewerOwns =
    isInbox || status === 'DRAFT' || (isOrg && createdFor.handle === VIEWER_ORG_HANDLE)

  return {
    id,
    slug,
    title,
    createdFor,
    location: LOCATIONS[seed % LOCATIONS.length],
    salary: seed % 2 === 0 ? mockSalary(seed) : null,
    createdAt: new Date(Date.now() - (seed % 30) * 24 * 60 * 60 * 1000).toISOString(),
    category: CATEGORIES[seed % CATEGORIES.length],
    jobType: JOB_TYPES[seed % JOB_TYPES.length],
    workplaceType: WORKPLACES[seed % WORKPLACES.length],
    workingHours: WORKING_HOURS[seed % WORKING_HOURS.length],
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
    ...(viewerOwns
      ? {
          viewerCanEdit: true,
          viewerCanDelete: true,
          applicationCount: applicationCountFor(id, applicationType),
        }
      : {}),
  }
}

/**
 * The mock's only mutable state. A posting is derived the first time it is asked
 * for and kept here, so the four writes below have a row to merge onto and the
 * lifecycle is demonstrable without a backend. A reload starts over from the
 * derivation.
 */
const jobStore = new Map<string, JobDetailDto>()

const storedJob = (slug: string): JobDetailDto => {
  const held = jobStore.get(jobIdFromSlug(slug))
  if (held) return held
  const derived = deriveJob(slug)
  jobStore.set(derived.id, derived)
  return derived
}

/** Every id-addressed write refuses the same posting, the way `createJob`
 *  refuses the same title. */
const refuses = (id: string): boolean =>
  (jobStore.get(id)?.title ?? '').toLowerCase().includes(MAGIC_FAIL_TITLE)

/**
 * A CLOSED posting exposes no application path to a caller who cannot edit it -
 * the contact entries are the poster's phone number, and "not rendered" is not
 * the same as "not sent" (backend-contract.md §7). The poster keeps the full
 * record, which is what the manage page is for.
 */
const withoutClosedApplyPath = (job: JobDetailDto): JobDetailDto =>
  job.status === 'CLOSED' && !job.viewerCanEdit
    ? { ...job, contactInformation: null, externalUrl: null }
    : job

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

  return withoutClosedApplyPath(storedJob(slug))
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
  if (title.toLowerCase().includes(MAGIC_FAIL_TITLE)) {
    return rejectProblem(422, 'Oglas nije sačuvan. Proverite unos i pokušajte ponovo.', 'Unprocessable Entity')
  }
  if (title.toLowerCase().includes('tudja')) {
    return rejectProblem(403, 'Nemate pravo da objavljujete u ime izabrane organizacije.', 'Forbidden')
  }

  const org = ORGS.find((row) => row.id === payload.createdForId)
  const createdFor: JobPosterDto = org ?? { actorType: 'user' }
  const slug = `${slugify(title)}-${1000 + (hashString(title) % 9000)}`

  const created: JobDetailDto = {
    id: jobIdFromSlug(slug),
    slug,
    title,
    createdFor,
    location: payload.location,
    salary: payload.salary ? { ...payload.salary, displayText: null } : null,
    createdAt: new Date().toISOString(),
    category: payload.category,
    jobType: payload.jobType,
    workplaceType: payload.workplaceType,
    workingHours: payload.workingHours,
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
    applicationCount: 0,
  }

  jobStore.set(created.id, created)
  return created
}

/** `job_publishable_check`, as the row sees it: the three pickers a publish
 *  demands, plus one contact entry when contact is the way in. A posting whose
 *  only way in is a phone number nobody wrote down has no way in. */
const publishable = (job: JobDetailDto): boolean =>
  job.category !== null &&
  job.jobType !== null &&
  job.workplaceType !== null &&
  (job.applicationType !== 'CONTACT_ONLY' || (job.contactInformation?.length ?? 0) > 0)

/**
 * MOCK - would be: const { data } = await api.put(`/jobs/${id}`, payload)
 * Every column the manage page's two sections write, in one request. The status
 * is not among them - it moves through the route below - and neither is the
 * application type once the posting is published: switching it would strand the
 * applications already collected under it.
 */
export const updateJob = async (id: string, payload: UpdateJobPayload): Promise<JobDetailDto> => {
  await delay(650)

  const held = jobStore.get(id)
  if (!held) return rejectProblem(404, 'Traženi oglas ne postoji.', 'Not Found')
  if (payload.title.trim().toLowerCase().includes(MAGIC_FAIL_TITLE)) {
    return rejectProblem(422, 'Oglas nije sačuvan. Proverite unos i pokušajte ponovo.', 'Unprocessable Entity')
  }
  if (held.status !== 'DRAFT' && payload.applicationType !== held.applicationType) {
    return rejectProblem(409, 'Način prijave se ne menja nakon objave oglasa.', 'Conflict')
  }

  const updated: JobDetailDto = {
    ...held,
    ...payload,
    title: payload.title.trim(),
    description: payload.description.trim(),
    salary: payload.salary ? { ...payload.salary, displayText: null } : null,
    applicationCount: applicationCountFor(id, payload.applicationType),
  }

  if (held.status !== 'DRAFT' && !publishable(updated)) {
    return rejectProblem(422, 'Objavljen oglas mora imati bar jedan kontakt.', 'Unprocessable Entity')
  }

  jobStore.set(id, updated)
  return updated
}

/**
 * MOCK - would be: const { data } = await api.put(`/jobs/${id}/status`, { status })
 * The whole lifecycle: DRAFT -> OPEN, and OPEN <-> CLOSED. There is no
 * un-publish, so the ladder never walks back to DRAFT.
 */
export const setJobStatus = async (id: string, status: JobStatus): Promise<JobDetailDto> => {
  await delay(450)

  const held = jobStore.get(id)
  if (!held) return rejectProblem(404, 'Traženi oglas ne postoji.', 'Not Found')
  if (refuses(id)) {
    return rejectProblem(409, 'Status oglasa nije promenjen. Pokušajte ponovo.', 'Conflict')
  }
  if (status === 'OPEN' && !publishable(held)) {
    return rejectProblem(422, 'Oglas nije potpun i ne može biti objavljen.', 'Unprocessable Entity')
  }

  // A status change is a write to the row, so it stamps `updatedAt` - the column
  // the owner's postings pane sorts "recently touched" on. It never reaches a DTO.
  const updated: JobDetailDto = { ...held, status }
  jobStore.set(id, updated)
  return updated
}

/**
 * MOCK - would be: await api.delete(`/jobs/${id}`)
 * `JobApplication.jobId` is ON DELETE CASCADE, so the applications go with the
 * posting - which is why the dialog names their count before the fact.
 */
export const deleteJob = async (id: string): Promise<void> => {
  await delay(500)
  if (refuses(id)) {
    return rejectProblem(409, 'Oglas nije obrisan. Pokušajte ponovo.', 'Conflict')
  }
  jobStore.delete(id)
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
  if (refuses(id)) {
    return rejectProblem(500, 'Prijave nisu učitane. Pokušajte ponovo.', 'Server Error')
  }

  const totalElements = jobStore.get(id)?.applicationCount ?? 0
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

  const held = jobStore.get(jobId)
  if (held && held.status !== 'OPEN') {
    return rejectProblem(409, 'Ovaj oglas ne prima prijave.', 'Conflict')
  }
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

/** A DRAFT or CLOSED row carries its marker in the slug, so opening it from the
 *  pane lands on a detail page in the same status. */
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
