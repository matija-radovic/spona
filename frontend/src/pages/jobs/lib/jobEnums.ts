import type {
  ApplicationType,
  ExperienceLevel,
  JobType,
  SalaryPeriod,
  WorkplaceType,
} from '@utils/api/jobs'

type Translate = (key: string, opts?: Record<string, unknown>) => string

export const JOB_TYPE_VALUES = [
  'FULL_TIME',
  'PART_TIME',
  'CONTRACT',
  'GIG',
  'INTERNSHIP',
  'VOLUNTEER',
] as const satisfies readonly JobType[]

export const WORKPLACE_TYPE_VALUES = [
  'ONSITE',
  'HYBRID',
  'REMOTE',
  'OTHER',
] as const satisfies readonly WorkplaceType[]

export const EXPERIENCE_LEVEL_VALUES = [
  'INTERNSHIP',
  'ENTRY',
  'MEDIOR',
  'SENIOR',
  'MANAGER',
  'DIRECTOR',
  'EXECUTIVE',
] as const satisfies readonly ExperienceLevel[]

export const APPLICATION_TYPE_VALUES = [
  'CONTACT_ONLY',
  'CV_DROP',
  'EXTERNAL_URL',
] as const satisfies readonly ApplicationType[]

export const SALARY_PERIOD_VALUES = [
  'HOUR',
  'DAY',
  'WEEK',
  'MONTH',
  'JOB',
] as const satisfies readonly SalaryPeriod[]

/** ISO 4217, three characters - the schema CHECKs the length. */
export const SALARY_CURRENCY_VALUES = ['EUR', 'RSD', 'USD'] as const

export interface EnumOption<T extends string> {
  value: T
  label: string
}

export const enumOptions = <T extends string>(
  values: readonly T[],
  keyPrefix: string,
  t: Translate,
): EnumOption<T>[] => values.map((value) => ({ value, label: t(`${keyPrefix}.${value}`) }))
