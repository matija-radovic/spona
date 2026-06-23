/**
 * Popular job roles surfaced on the home page. Clicking one routes to the jobs
 * page with the role pre-applied as a filter (`/jobs?role=<slug>`).
 * Labels are plain Serbian role names rather than i18n keys.
 */
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
