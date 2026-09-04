import type { JobCategory } from '@utils/api/jobs'

export const POPULAR_JOB_ROLES: readonly JobCategory[] = Object.freeze([
  'IT_SOFTWARE',
  'RETAIL',
  'HOSPITALITY_FOOD',
  'TRANSPORT_LOGISTICS',
  'CONSTRUCTION',
  'SKILLED_TRADES',
  'SALES',
  'ADMIN_OFFICE',
  'HEALTHCARE',
  'MANUFACTURING',
])

export const slugFromCategory = (category: JobCategory): string =>
  category.toLowerCase().replace(/_/g, '-')

/** Takes the enum and slugs it here, so `?role=IT_SOFTWARE` cannot be written. */
export const jobsFilterPath = (category: JobCategory): string =>
  `/jobs?role=${slugFromCategory(category)}`
