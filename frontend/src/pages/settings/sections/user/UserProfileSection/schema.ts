import { z } from 'zod'
import {
  CONTACT_DESC_MAX,
  CONTACT_VALUE_MAX,
  CONTACT_MAX_ENTRIES,
} from '@utils/constants/validation'
import type { UserSettingsDto, UpdateProfilePayload } from '@utils/api/settings'

type Translate = (key: string, opts?: Record<string, unknown>) => string

export const FULL_NAME_MAX = 120
export const BIO_MAX = 500
export const LOCATION_MAX = 120

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

export const buildUserProfileSchema = (t: Translate) =>
  z.object({
    fullName: z.string().max(FULL_NAME_MAX, t('errors.maxLength', { max: FULL_NAME_MAX })),
    bio: z.string().max(BIO_MAX, t('errors.maxLength', { max: BIO_MAX })),
    avatarUrl: z.string(),
    location: z.string().max(LOCATION_MAX, t('errors.maxLength', { max: LOCATION_MAX })),
    otherContactInfo: contactInfoSchema(t),
  })

export type UserProfileData = z.infer<ReturnType<typeof buildUserProfileSchema>>

export const toUserProfileData = (dto: UserSettingsDto): UserProfileData => ({
  fullName: dto.fullName,
  bio: dto.bio ?? '',
  avatarUrl: dto.avatarUrl ?? '',
  location: dto.location ?? '',
  otherContactInfo: dto.otherContactInfo,
})

export const toUpdateProfilePayload = (data: UserProfileData): UpdateProfilePayload => ({
  fullName: data.fullName.trim(),
  bio: data.bio.trim() === '' ? null : data.bio.trim(),
  avatarUrl: data.avatarUrl || null,
  location: data.location.trim() === '' ? null : data.location.trim(),
  otherContactInfo: data.otherContactInfo,
})
