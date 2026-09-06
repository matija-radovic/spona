import { z } from 'zod'
import {
  CONTACT_DESC_MAX,
  CONTACT_MAX_ENTRIES,
  CONTACT_VALUE_MAX,
  JOB_DESCRIPTION_MAX,
  JOB_DESCRIPTION_MIN,
  JOB_EXTERNAL_URL_MAX,
  JOB_KEYWORD_MAX,
  JOB_KEYWORDS_MAX,
  JOB_LOCATION_MAX,
  JOB_TITLE_MAX,
  JOB_TITLE_MIN,
} from '@utils/constants/validation'
import { isSafeHref, WEB_PROTOCOLS } from '@utils/safeUrl'
import type {
  CreateJobPayload,
  CreateJobStatus,
  JobDetailDto,
  UpdateJobPayload,
} from '@utils/api/jobs'
import {
  APPLICATION_TYPE_VALUES,
  EXPERIENCE_LEVEL_VALUES,
  JOB_CATEGORY_VALUES,
  JOB_TYPE_VALUES,
  SALARY_CURRENCY_VALUES,
  SALARY_PERIOD_VALUES,
  WORKING_HOURS_VALUES,
  WORKPLACE_TYPE_VALUES,
} from './jobEnums'

type Translate = (key: string, opts?: Record<string, unknown>) => string

const orNull = <T extends string>(value: T | ''): T | null => (value === '' ? null : value)

/** Amounts are typed, so they travel as strings and are parsed once, here. */
const toAmount = (raw: string): number | null => {
  const trimmed = raw.trim().replace(',', '.')
  if (trimmed === '') return null
  const parsed = Number(trimmed)
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : Number.NaN
}

const contactInfoSchema = (t: Translate) =>
  z
    .array(
      z.object({
        type: z.string(),
        description: z.string().max(CONTACT_DESC_MAX, t('errors.maxLength', { max: CONTACT_DESC_MAX })),
        value: z
          .string()
          .min(1, t('errors.contactValueRequired'))
          .max(CONTACT_VALUE_MAX, t('errors.maxLength', { max: CONTACT_VALUE_MAX })),
      }),
    )
    .max(CONTACT_MAX_ENTRIES, t('errors.contactMax', { max: CONTACT_MAX_ENTRIES }))

export const buildJobSchema = (t: Translate, draft = false) =>
  z
    .object({
      title: z.string().max(JOB_TITLE_MAX, t('errors.maxLength', { max: JOB_TITLE_MAX })),
      description: z
        .string()
        .max(JOB_DESCRIPTION_MAX, t('errors.maxLength', { max: JOB_DESCRIPTION_MAX })),
      keywords: z
        .array(z.string().max(JOB_KEYWORD_MAX, t('errors.maxLength', { max: JOB_KEYWORD_MAX })))
        .max(JOB_KEYWORDS_MAX, t('errors.keywordMax', { max: JOB_KEYWORDS_MAX })),
      category: z.enum(JOB_CATEGORY_VALUES).or(z.literal('')),
      jobType: z.enum(JOB_TYPE_VALUES).or(z.literal('')),
      workplaceType: z.enum(WORKPLACE_TYPE_VALUES).or(z.literal('')),
      workingHours: z.enum(WORKING_HOURS_VALUES).or(z.literal('')),
      experienceLevel: z.enum(EXPERIENCE_LEVEL_VALUES).or(z.literal('')),
      location: z.string().max(JOB_LOCATION_MAX, t('errors.maxLength', { max: JOB_LOCATION_MAX })),
      salaryMin: z.string(),
      salaryMax: z.string(),
      salaryCurrency: z.enum(SALARY_CURRENCY_VALUES),
      salaryPeriod: z.enum(SALARY_PERIOD_VALUES),
      applicationType: z.enum(APPLICATION_TYPE_VALUES),
      contactInformation: contactInfoSchema(t),
      externalUrl: z
        .string()
        .max(JOB_EXTERNAL_URL_MAX, t('errors.maxLength', { max: JOB_EXTERNAL_URL_MAX })),
    })
    .superRefine((values, ctx) => {
      const required = (path: keyof typeof values) =>
        ctx.addIssue({ code: 'custom', path: [path], message: t('errors.required') })

      const min = toAmount(values.salaryMin)
      const max = toAmount(values.salaryMax)

      if (Number.isNaN(min)) {
        ctx.addIssue({ code: 'custom', path: ['salaryMin'], message: t('errors.notANumber') })
      }
      if (Number.isNaN(max)) {
        ctx.addIssue({ code: 'custom', path: ['salaryMax'], message: t('errors.notANumber') })
      }
      if (min === null && max !== null) {
        ctx.addIssue({ code: 'custom', path: ['salaryMin'], message: t('errors.salaryMinRequired') })
      }
      if (typeof min === 'number' && typeof max === 'number' && !Number.isNaN(min) && !Number.isNaN(max) && max < min) {
        ctx.addIssue({ code: 'custom', path: ['salaryMax'], message: t('errors.salaryMaxBelowMin') })
      }

      const tooShort = (path: 'title' | 'description', value: string, min: number) => {
        if (value.trim() !== '' && value.trim().length < min) {
          ctx.addIssue({ code: 'custom', path: [path], message: t('errors.minLength', { min }) })
        }
      }
      tooShort('title', values.title, JOB_TITLE_MIN)
      tooShort('description', values.description, JOB_DESCRIPTION_MIN)

      const externalUrl = values.externalUrl.trim()
      if (
        externalUrl !== '' &&
        !isSafeHref(externalUrl, {
          protocols: WEB_PROTOCOLS,
          allowSchemeless: false,
          allowRelative: false,
        })
      ) {
        ctx.addIssue({ code: 'custom', path: ['externalUrl'], message: t('errors.invalidUrl') })
      }

      if (draft) return

      if (values.title.trim() === '') required('title')
      if (values.description.trim() === '') required('description')
      if (values.category === '') required('category')
      if (values.jobType === '') required('jobType')
      if (values.workplaceType === '') required('workplaceType')
      if (values.location.trim() === '') required('location')
      if (values.applicationType === 'EXTERNAL_URL' && values.externalUrl.trim() === '') {
        required('externalUrl')
      }
      if (values.applicationType === 'CONTACT_ONLY' && values.contactInformation.length === 0) {
        ctx.addIssue({
          code: 'custom',
          path: ['contactInformation'],
          message: t('errors.contactRequired'),
        })
      }
    })

export type JobFormData = z.infer<ReturnType<typeof buildJobSchema>>

export const JOB_DEFAULTS: JobFormData = {
  title: '',
  description: '',
  keywords: [],
  category: '',
  jobType: '',
  workplaceType: '',
  workingHours: '',
  experienceLevel: '',
  location: '',
  salaryMin: '',
  salaryMax: '',
  salaryCurrency: 'EUR',
  salaryPeriod: 'MONTH',
  applicationType: 'CONTACT_ONLY',
  contactInformation: [],
  externalUrl: '',
}

type SalaryCurrency = JobFormData['salaryCurrency']
type SalaryPeriodValue = JobFormData['salaryPeriod']

const toCurrency = (raw: string | undefined): SalaryCurrency =>
  SALARY_CURRENCY_VALUES.find((value) => value === raw) ?? JOB_DEFAULTS.salaryCurrency

const toPeriod = (raw: string | undefined): SalaryPeriodValue =>
  SALARY_PERIOD_VALUES.find((value) => value === raw) ?? JOB_DEFAULTS.salaryPeriod

export const toJobFormData = (job: JobDetailDto): JobFormData => ({
  title: job.title,
  description: job.description,
  keywords: job.keywords ?? [],
  category: job.category ?? '',
  jobType: job.jobType ?? '',
  workplaceType: job.workplaceType ?? '',
  workingHours: job.workingHours ?? '',
  experienceLevel: job.experienceLevel ?? '',
  location: job.location,
  salaryMin: job.salary ? String(job.salary.min) : '',
  salaryMax: job.salary?.max == null ? '' : String(job.salary.max),
  salaryCurrency: toCurrency(job.salary?.currency),
  salaryPeriod: toPeriod(job.salary?.period),
  applicationType: job.applicationType,
  contactInformation: job.contactInformation ?? [],
  externalUrl: job.externalUrl ?? '',
})

export const toUpdateJobPayload = (values: JobFormData): UpdateJobPayload => {
  const min = toAmount(values.salaryMin)
  const max = toAmount(values.salaryMax)

  return {
    title: values.title.trim(),
    description: values.description.trim(),
    keywords: values.keywords,
    category: orNull(values.category),
    jobType: orNull(values.jobType),
    workplaceType: orNull(values.workplaceType),
    workingHours: orNull(values.workingHours),
    experienceLevel: orNull(values.experienceLevel),
    location: values.location.trim(),
    salary:
      min === null || Number.isNaN(min)
        ? null
        : {
            min,
            max: max === null || Number.isNaN(max) ? null : max,
            currency: values.salaryCurrency,
            period: values.salaryPeriod,
          },
    applicationType: values.applicationType,
    contactInformation:
      values.applicationType === 'CONTACT_ONLY' ? values.contactInformation : null,
    externalUrl: values.applicationType === 'EXTERNAL_URL' ? values.externalUrl.trim() : null,
  }
}

export const toCreateJobPayload = (
  values: JobFormData,
  createdForId: string,
  status: CreateJobStatus,
): CreateJobPayload => ({
  ...toUpdateJobPayload(values),
  createdForId,
  status,
})
