import { z } from 'zod'
import {
  USERNAME_MIN,
  USERNAME_MAX,
  PASSWORD_MIN,
  PASSWORD_MAX,
  SLUG_MIN,
  SLUG_MAX,
  SLUG_REGEX,
  PASSWORD_REGEX,
  CONTACT_DESC_MAX,
  CONTACT_VALUE_MAX,
  CONTACT_MAX_ENTRIES,
} from '@utils/constants/validation'

type Translate = (key: string, opts?: Record<string, unknown>) => string

/** Additional-contact array shared by the user and organization schemas. */
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

/** Account + personal-info form (wizard stages Account & Personal). */
export const buildRegisterSchema = (t: Translate) =>
  z
    .object({
      username: z
        .string()
        .min(USERNAME_MIN, t('errors.rangeLength', { min: USERNAME_MIN, max: USERNAME_MAX }))
        .max(USERNAME_MAX, t('errors.rangeLength', { min: USERNAME_MIN, max: USERNAME_MAX }))
        .regex(SLUG_REGEX, t('errors.usernameFormat')),
      email: z.string().min(1, t('errors.required')).email(t('errors.invalidEmail')),
      password: z
        .string()
        .min(PASSWORD_MIN, t('errors.rangeLength', { min: PASSWORD_MIN, max: PASSWORD_MAX }))
        .max(PASSWORD_MAX, t('errors.rangeLength', { min: PASSWORD_MIN, max: PASSWORD_MAX }))
        .regex(PASSWORD_REGEX, t('errors.passwordFormat')),
      confirmPassword: z.string().min(1, t('errors.required')),
      // Personal stage - all optional.
      fullName: z.string().max(120, t('errors.maxLength', { max: 120 })),
      bio: z.string().max(500, t('errors.maxLength', { max: 500 })),
      avatarUrl: z.string(),
      location: z.string().max(120, t('errors.maxLength', { max: 120 })),
      otherContactInfo: contactInfoSchema(t),
    })
    .refine((data) => data.password === data.confirmPassword, {
      path: ['confirmPassword'],
      message: t('errors.passwordMismatch'),
    })

export type RegisterData = z.infer<ReturnType<typeof buildRegisterSchema>>

/** Organization form (Organization stage, org mode only). */
export const buildOrgSchema = (t: Translate) =>
  z.object({
    slug: z
      .string()
      .min(SLUG_MIN, t('errors.rangeLength', { min: SLUG_MIN, max: SLUG_MAX }))
      .max(SLUG_MAX, t('errors.rangeLength', { min: SLUG_MIN, max: SLUG_MAX }))
      .regex(SLUG_REGEX, t('errors.slugFormat')),
    displayName: z.string().min(1, t('errors.required')).max(120, t('errors.maxLength', { max: 120 })),
    legalName: z.string().min(1, t('errors.required')).max(160, t('errors.maxLength', { max: 160 })),
    description: z.string().max(1000, t('errors.maxLength', { max: 1000 })),
    website: z
      .string()
      .max(200, t('errors.maxLength', { max: 200 }))
      .refine((value) => value === '' || /^https?:\/\/.+/.test(value), t('errors.invalidUrl')),
    avatarUrl: z.string(),
    location: z.string().max(120, t('errors.maxLength', { max: 120 })),
    otherContactInfo: contactInfoSchema(t),
  })

export type OrgData = z.infer<ReturnType<typeof buildOrgSchema>>

export const REGISTER_DEFAULTS: RegisterData = {
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  fullName: '',
  bio: '',
  avatarUrl: '',
  location: '',
  otherContactInfo: [],
}

export const ORG_DEFAULTS: OrgData = {
  slug: '',
  displayName: '',
  legalName: '',
  description: '',
  website: '',
  avatarUrl: '',
  location: '',
  otherContactInfo: [],
}
