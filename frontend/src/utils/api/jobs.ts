import api from '@utils/api'

import type { ContactEntry } from '@shared/components'
import type { ActorRefDto } from './profiles'
import type { PagedResponse } from './types'
import type { JobQuery } from '@pages/jobs/lib/filters'
import { filtersToSearchParams } from '@pages/jobs/lib/filters'

// --- Enums ---

/** Contract type, independent of the working hours */
export type JobType =
  | 'PERMANENT'
  | 'FIXED_TERM'
  | 'SEASONAL'
  | 'FREELANCE'
  | 'INTERNSHIP'
  | 'VOLUNTEER'

export type WorkingHours = 'FULL_TIME' | 'PART_TIME'

/** FIELD is work whose location changes, such as client homes or sites */
export type WorkplaceType = 'ONSITE' | 'REMOTE' | 'HYBRID' | 'FIELD' | 'OTHER'

export type ApplicationType = 'CONTACT_ONLY' | 'EXTERNAL_URL' | 'CV_DROP'
export type JobStatus = 'DRAFT' | 'OPEN' | 'CLOSED'

export type JobPostingsSort = 'created_desc' | 'created_asc' | 'updated_desc' | 'updated_asc'

/** Minimum required experience, as a threshold rather than a band */
export type ExperienceLevel =
  | 'NONE'
  | 'MONTHS_6'
  | 'YEARS_1'
  | 'YEARS_2'
  | 'YEARS_3'
  | 'YEARS_5'
  | 'YEARS_10'

export type SalaryPeriod = 'HOUR' | 'DAY' | 'WEEK' | 'MONTH' | 'JOB'

/** Occupational group, carried in the URL as a kebab-case slug */
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

export type CreateJobStatus = Extract<JobStatus, 'DRAFT' | 'OPEN'>

export interface Salary {
  min: number
  max: number | null
  currency: string
  period: SalaryPeriod
  displayText: string | null
}

// --- DTOs ---

export type JobPosterDto =
  | (ActorRefDto & { actorType: 'org' })
  | { actorType: 'user' } // A user poster stays anonymous

export interface JobListItemDto {
  id: string
  slug: string
  title: string
  createdFor: JobPosterDto
  /** Required even for a remote job */
  location: string
  salary: Salary | null
  createdAt: string
  keywords?: string[]
}

export interface JobDetailDto extends JobListItemDto {
  /** The three below are null only on a draft */
  category: JobCategory | null
  jobType: JobType | null
  workplaceType: WorkplaceType | null
  /** The two below may stay null on a published posting */
  workingHours: WorkingHours | null
  experienceLevel: ExperienceLevel | null
  status: JobStatus
  applicationType: ApplicationType
  contactInformation: ContactEntry[] | null
  externalUrl: string | null
  description: string
  createdForBio?: string | null
  createdForFollowedByViewer?: boolean
  viewerCanEdit?: boolean
  viewerCanDelete?: boolean
  /** Served only to a viewer who may edit the posting */
  applicationCount?: number
}

/** List item with its status, for lists that also hold postings that are not open */
export interface SavedJobItemDto extends JobListItemDto {
  status: JobStatus
}

/** Owner-only, anyone else gets a 400 */
export interface ActorJobPostingsQuery {
  status?: JobStatus
  sort?: JobPostingsSort
}

export interface JobApplicationDto {
  id: string
  /** Always a user */
  applicant: ActorRefDto
  cvUrl: string
  submittedAt: string
}

// --- Payloads ---

export type SalaryPayload = Omit<Salary, 'displayText'>

export interface CreateJobPayload {
  /** The actor this is published as */
  createdForId: string
  title: string
  description: string
  keywords: string[]
  /** Null means not stated, the first three are required to publish */
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

/** The identity is fixed at creation and the status changes only through setJobStatus */
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

// --- Calls ---

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

export const createJob = async (payload: CreateJobPayload): Promise<JobDetailDto> => {
  const { data } = await api.post<JobDetailDto>('/jobs', payload)
  return data
}

export const updateJob = async (id: string, payload: UpdateJobPayload): Promise<JobDetailDto> => {
  const { data } = await api.put<JobDetailDto>(`/jobs/${id}`, payload)
  return data
}

export const setJobStatus = async (id: string, status: JobStatus): Promise<JobDetailDto> => {
  const { data } = await api.put<JobDetailDto>(`/jobs/${id}/status`, { status })
  return data
}

export const deleteJob = async (id: string): Promise<void> => {
  await api.delete(`/jobs/${id}`)
}

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

export const submitApplication = async (
  jobId: string,
  file: File,
): Promise<JobApplicationDto> => {
  const form = new FormData()
  form.append('cv', file)
  const { data } = await api.post<JobApplicationDto>(`/jobs/${jobId}/applications`, form)
  return data
}

export const fetchJobsByIds = async (
  ids: string[],
  statuses: JobStatus[],
): Promise<SavedJobItemDto[]> => {
  const { data } = await api.get<SavedJobItemDto[]>('/jobs', {
    params: { id: ids.join(','), status: statuses.join(',') },
  })
  return data
}

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
