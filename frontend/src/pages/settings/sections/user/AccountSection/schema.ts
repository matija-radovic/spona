import { z } from 'zod'
import {
  USERNAME_MIN,
  USERNAME_MAX,
  PASSWORD_MIN,
  PASSWORD_MAX,
  PASSWORD_REGEX,
  SLUG_REGEX,
} from '@utils/constants/validation'

type Translate = (key: string, opts?: Record<string, unknown>) => string

export const buildUsernameSchema = (t: Translate) =>
  z.object({
    username: z
      .string()
      .min(USERNAME_MIN, t('errors.rangeLength', { min: USERNAME_MIN, max: USERNAME_MAX }))
      .max(USERNAME_MAX, t('errors.rangeLength', { min: USERNAME_MIN, max: USERNAME_MAX }))
      .regex(SLUG_REGEX, t('errors.usernameFormat')),
  })

export type UsernameData = z.infer<ReturnType<typeof buildUsernameSchema>>

export const buildEmailSchema = (t: Translate) =>
  z.object({
    email: z.email(t('errors.invalidEmail')).min(1, t('errors.required')),
    currentPassword: z.string().min(1, t('errors.required')),
  })

export type EmailData = z.infer<ReturnType<typeof buildEmailSchema>>

export const buildPasswordSchema = (t: Translate) =>
  z
    .object({
      currentPassword: z.string().min(1, t('errors.required')),
      newPassword: z
        .string()
        .min(PASSWORD_MIN, t('errors.rangeLength', { min: PASSWORD_MIN, max: PASSWORD_MAX }))
        .max(PASSWORD_MAX, t('errors.rangeLength', { min: PASSWORD_MIN, max: PASSWORD_MAX }))
        .regex(PASSWORD_REGEX, t('errors.passwordFormat')),
      repeatPassword: z.string().min(1, t('errors.required')),
    })
    .refine((data) => data.newPassword === data.repeatPassword, {
      path: ['repeatPassword'],
      message: t('errors.passwordMismatch'),
    })

export type PasswordData = z.infer<ReturnType<typeof buildPasswordSchema>>

export const PASSWORD_DEFAULTS: PasswordData = {
  currentPassword: '',
  newPassword: '',
  repeatPassword: '',
}
