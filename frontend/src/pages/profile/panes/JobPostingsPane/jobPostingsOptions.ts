import type { DropdownOption } from '@shared/components'
import type { CapsuleOption } from '@pages/profile/components/FilterCapsules'
import type { JobPostingsSort, JobStatus } from '@utils/api/jobs'

type Translate = (key: string, opts?: Record<string, unknown>) => string

export type StatusFilter = JobStatus | 'all'

const STATUS_FILTERS: readonly { key: StatusFilter; labelKey: string }[] = [
  { key: 'all', labelKey: 'profile.postings.filter.all' },
  { key: 'OPEN', labelKey: 'profile.postings.filter.open' },
  { key: 'DRAFT', labelKey: 'profile.postings.filter.draft' },
  { key: 'CLOSED', labelKey: 'profile.postings.filter.closed' },
]

/** The empty value is newest-created, the default, and goes on no request. */
const SORT_ORDERS: readonly { value: JobPostingsSort | ''; labelKey: string }[] = [
  { value: '', labelKey: 'profile.postings.sort.created_desc' },
  { value: 'created_asc', labelKey: 'profile.postings.sort.created_asc' },
  { value: 'updated_desc', labelKey: 'profile.postings.sort.updated_desc' },
  { value: 'updated_asc', labelKey: 'profile.postings.sort.updated_asc' },
]

export const statusCapsules = (t: Translate): CapsuleOption[] =>
  STATUS_FILTERS.map(({ key, labelKey }) => ({ key, label: t(labelKey) }))

export const sortOptions = (t: Translate): DropdownOption[] =>
  SORT_ORDERS.map(({ value, labelKey }) => ({ value, label: t(labelKey) }))
