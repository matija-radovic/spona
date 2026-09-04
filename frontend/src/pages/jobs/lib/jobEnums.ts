import type { Translation } from '@utils/i18n/resources'
import type {
  ApplicationType,
  ExperienceLevel,
  JobCategory,
  JobType,
  SalaryPeriod,
  WorkingHours,
  WorkplaceType,
} from '@utils/api/jobs'

type Translate = (key: string, opts?: Record<string, unknown>) => string

export const JOB_CATEGORY_VALUES = [
  'IT_SOFTWARE',
  'IT_INFRASTRUCTURE',
  'DATA_AI',
  'DESIGN_CREATIVE',
  'MEDIA_JOURNALISM',
  'MARKETING_PR',
  'SALES',
  'RETAIL',
  'CUSTOMER_SUPPORT',
  'ADMIN_OFFICE',
  'FINANCE_ACCOUNTING',
  'LEGAL',
  'HR',
  'PROJECT_PRODUCT',
  'EDUCATION',
  'HEALTHCARE',
  'CARE_SERVICES',
  'CONSTRUCTION',
  'SKILLED_TRADES',
  'MANUFACTURING',
  'ENGINEERING',
  'AUTOMOTIVE',
  'TRANSPORT_LOGISTICS',
  'WAREHOUSE',
  'HOSPITALITY_FOOD',
  'TOURISM',
  'AGRICULTURE',
  'CLEANING_MAINTENANCE',
  'SECURITY',
  'BEAUTY_WELLNESS',
  'SPORT_RECREATION',
  'ARTS_CULTURE',
  'TRANSLATION',
  'SCIENCE_RESEARCH',
  'REAL_ESTATE',
  'OTHER',
] as const satisfies readonly JobCategory[]

export const JOB_TYPE_VALUES = [
  'PERMANENT',
  'FIXED_TERM',
  'SEASONAL',
  'FREELANCE',
  'INTERNSHIP',
  'VOLUNTEER',
] as const satisfies readonly JobType[]

export const WORKING_HOURS_VALUES = [
  'FULL_TIME',
  'PART_TIME',
] as const satisfies readonly WorkingHours[]

export const WORKPLACE_TYPE_VALUES = [
  'ONSITE',
  'HYBRID',
  'REMOTE',
  'FIELD',
  'OTHER',
] as const satisfies readonly WorkplaceType[]

export const EXPERIENCE_LEVEL_VALUES = [
  'NONE',
  'MONTHS_6',
  'YEARS_1',
  'YEARS_2',
  'YEARS_3',
  'YEARS_5',
  'YEARS_10',
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

export const enumOptionsWithEmpty = <T extends string>(
  values: readonly T[],
  keyPrefix: string,
  t: Translate,
): EnumOption<T | ''>[] => [
  { value: '', label: t('jobs.fields.unspecified') },
  ...enumOptions(values, keyPrefix, t),
]

type LocaleSynonyms = Translation['jobs']['categorySynonyms']

export const categoryOptions = (
  t: Translate,
): (EnumOption<JobCategory | ''> & { searchTokens?: readonly string[] })[] =>
  enumOptionsWithEmpty(JOB_CATEGORY_VALUES, 'jobs.category', t).map((option) => {
    if (!option.value) return option
    const key: keyof LocaleSynonyms = option.value
    const tokens: unknown = t(`jobs.categorySynonyms.${key}`, { returnObjects: true })
    return { ...option, searchTokens: Array.isArray(tokens) ? (tokens as string[]) : [] }
  })
