// -----------------------------------------------------------------------------
// Jobs service. Every call hits the backend. DTO shapes are separate from the DB
// model (new_docx/types.ts): user-facing DTOs OMIT `createdBy` and ship a
// stripped, discriminated `createdFor`. Transport envelopes come from `./types`.
//
// All rejections are `ProblemDetail` shapes so callers can `parseProblem(err)`
// uniformly.
//
// Saved jobs no longer live here (new_docx/types.ts, end of the Jobs block —
// there is no FavoriteJob table); they are browser
// localStorage (utils/savedJobs.ts). `fetchJobsByIds` only HYDRATES an id set.
// -----------------------------------------------------------------------------

import api from '@utils/api'

import type { ContactEntry } from '@shared/components'
import type { ActorRefDto } from './profiles'
import type { PagedResponse } from './types'
import type { JobQuery } from '@pages/jobs/lib/filters'
import { filtersToSearchParams } from '@pages/jobs/lib/filters'

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
 * Poster-only, and CV_DROP only: contact and external applications happen
 * outside the system and leave no row. Newest first, per the
 * `(jobId, submittedAt DESC)` index.
 */
export const fetchJobApplications = async (
  id: string,
  page = 1,
  size = 10,
): Promise<PagedResponse<JobApplicationDto>> => {
  const { data } = await api.get<PagedResponse<JobApplicationDto>>(`/jobs/${id}/applications`, {
    params: { page, size },
  })
  return data
}

/**
 * Only an OPEN posting takes applications (backend-contract.md §8 rule 20): a
 * DRAFT has not been published and a CLOSED one has stopped asking, and a draft
 * the caller may not edit answers 404 rather than 409.
 *
 * No headers: axios leaves a `FormData`'s content type to the browser, which is
 * what adds the boundary. Naming a JSON content type would serialise the form.
 */
export const submitApplication = async (
  jobId: string,
  file: File,
): Promise<JobApplicationDto> => {
  const form = new FormData()
  form.append('cv', file)
  const { data } = await api.post<JobApplicationDto>(`/jobs/${jobId}/applications`, form)
  return data
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

/** One route, two audiences: every status to whoever manages the postings, OPEN
 *  only to anyone else. An empty page is also what hidden postings answer. */
export const fetchActorJobPostings = async (
  actorId: string,
  params?: ActorJobPostingsQuery,
  page = 1,
  size = 10,
): Promise<PagedResponse<SavedJobItemDto>> => {
  const { data } = await api.get<PagedResponse<SavedJobItemDto>>(`/actors/${actorId}/jobs`, {
    params: { page, size, ...params },
  })
  return data
}
