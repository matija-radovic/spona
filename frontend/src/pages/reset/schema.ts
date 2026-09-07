import { z } from 'zod'
import {
  PASSWORD_MIN,
  PASSWORD_MAX,
  PASSWORD_REGEX,
  EMAIL_MAX,
} from '@utils/constants/validation'

type Translate = (key: string, opts?: Record<string, unknown>) => string

/** "Forgot password" request: just the address the link is mailed to. */
export const buildRequestResetSchema = (t: Translate) =>
  z.object({
    email: z
      .email(t('errors.invalidEmail'))
      .min(1, t('errors.required'))
      .max(EMAIL_MAX, t('errors.maxLength', { max: EMAIL_MAX })),
  })

export type RequestResetData = z.infer<ReturnType<typeof buildRequestResetSchema>>

/** New-password form behind `/reset/:token`; same bounds as registration. */
export const buildConfirmResetSchema = (t: Translate) =>
  z
    .object({
      password: z
        .string()
        .min(PASSWORD_MIN, t('errors.rangeLength', { min: PASSWORD_MIN, max: PASSWORD_MAX }))
        .max(PASSWORD_MAX, t('errors.rangeLength', { min: PASSWORD_MIN, max: PASSWORD_MAX }))
        .regex(PASSWORD_REGEX, t('errors.passwordFormat')),
      confirmPassword: z.string().min(1, t('errors.required')),
    })
    .refine((data) => data.password === data.confirmPassword, {
      path: ['confirmPassword'],
      message: t('errors.passwordMismatch'),
    })

export type ConfirmResetData = z.infer<ReturnType<typeof buildConfirmResetSchema>>
