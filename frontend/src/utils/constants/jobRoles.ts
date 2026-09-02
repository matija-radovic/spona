import type { JobCategory } from '@utils/api/jobs'

export interface JobRole {
  slug: string
  label: string
}

export const POPULAR_JOB_ROLES: readonly JobRole[] = Object.freeze([
  { slug: 'software-engineer', label: 'Softverski inženjer' },
  { slug: 'frontend-developer', label: 'Frontend programer' },
  { slug: 'backend-developer', label: 'Backend programer' },
  { slug: 'product-designer', label: 'Dizajner proizvoda' },
  { slug: 'product-manager', label: 'Menadžer proizvoda' },
  { slug: 'data-analyst', label: 'Analitičar podataka' },
  { slug: 'devops-engineer', label: 'DevOps inženjer' },
  { slug: 'qa-engineer', label: 'QA inženjer' },
  { slug: 'marketing-specialist', label: 'Stručnjak za marketing' },
  { slug: 'sales-representative', label: 'Komercijalista' },
  { slug: 'hr-recruiter', label: 'HR regruter' },
  { slug: 'project-manager', label: 'Projektni menadžer' },
])

export const jobsFilterPath = (slug: string): string => `/jobs?role=${slug}`

export const categoryFromSlug = (slug: string): JobCategory =>
  slug.toUpperCase().replace(/-/g, '_') as JobCategory

export const slugFromCategory = (category: JobCategory): string =>
  category.toLowerCase().replace(/_/g, '-')

export const jobCategoryOptions = (
  otherLabel: string,
): readonly { value: JobCategory; label: string }[] => [
  ...POPULAR_JOB_ROLES.map((role) => ({ value: categoryFromSlug(role.slug), label: role.label })),
  { value: 'OTHER', label: otherLabel },
]
