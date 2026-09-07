import { z } from 'zod'
import {
  USERNAME_MIN,
  USERNAME_MAX,
  PASSWORD_MIN,
  PASSWORD_MAX,
  SLUG_REGEX,
  PASSWORD_REGEX,
  BIO_MAX,
  CONTACT_DESC_MAX,
  CONTACT_VALUE_MAX,
  CONTACT_MAX_ENTRIES,
  EMAIL_MAX,
  FULL_NAME_MAX,
  LOCATION_MAX,
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
      email: z
        .email(t('errors.invalidEmail'))
        .min(1, t('errors.required'))
        .max(EMAIL_MAX, t('errors.maxLength', { max: EMAIL_MAX })),
      password: z
        .string()
        .min(PASSWORD_MIN, t('errors.rangeLength', { min: PASSWORD_MIN, max: PASSWORD_MAX }))
        .max(PASSWORD_MAX, t('errors.rangeLength', { min: PASSWORD_MIN, max: PASSWORD_MAX }))
        .regex(PASSWORD_REGEX, t('errors.passwordFormat')),
      confirmPassword: z.string().min(1, t('errors.required')),
      // Personal stage - all optional.
      fullName: z.string().max(FULL_NAME_MAX, t('errors.maxLength', { max: FULL_NAME_MAX })),
      bio: z.string().max(BIO_MAX, t('errors.maxLength', { max: BIO_MAX })),
      avatarUrl: z.string(),
      location: z.string().max(LOCATION_MAX, t('errors.maxLength', { max: LOCATION_MAX })),
      otherContactInfo: contactInfoSchema(t),
    })
    .refine((data) => data.password === data.confirmPassword, {
      path: ['confirmPassword'],
      message: t('errors.passwordMismatch'),
    })

export type RegisterData = z.infer<ReturnType<typeof buildRegisterSchema>>

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
