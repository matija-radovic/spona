import { z } from 'zod'
import {
  USERNAME_MIN,
  USERNAME_MAX,
  PASSWORD_MIN,
  PASSWORD_MAX,
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
