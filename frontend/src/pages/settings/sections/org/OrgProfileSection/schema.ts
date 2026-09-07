import { z } from 'zod'
import {
  CONTACT_DESC_MAX,
  CONTACT_VALUE_MAX,
  CONTACT_MAX_ENTRIES,
  LOCATION_MAX,
  ORG_DESCRIPTION_MAX,
  ORG_DISPLAY_NAME_MAX,
  ORG_LEGAL_NAME_MAX,
  ORG_WEBSITE_MAX,
} from '@utils/constants/validation'
import type { OrgSettingsDto, UpdateOrgProfilePayload } from '@utils/api/organizations'

type Translate = (key: string, opts?: Record<string, unknown>) => string

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

/** No slug: renaming it breaks links, so it lives in Upravljanje instead. */
export const buildOrgProfileSchema =(t: Translate) =>
  z.object({
    displayName: z
      .string()
      .min(1, t('errors.required'))
      .max(ORG_DISPLAY_NAME_MAX, t('errors.maxLength', { max: ORG_DISPLAY_NAME_MAX })),
    legalName: z
      .string()
      .min(1, t('errors.required'))
      .max(ORG_LEGAL_NAME_MAX, t('errors.maxLength', { max: ORG_LEGAL_NAME_MAX })),
    description: z
      .string()
      .max(ORG_DESCRIPTION_MAX, t('errors.maxLength', { max: ORG_DESCRIPTION_MAX })),
    website: z
      .string()
      .max(ORG_WEBSITE_MAX, t('errors.maxLength', { max: ORG_WEBSITE_MAX }))
      .refine((value) => value === '' || /^https?:\/\/.+/.test(value), t('errors.invalidUrl')),
    avatarUrl: z.string(),
    location: z.string().max(LOCATION_MAX, t('errors.maxLength', { max: LOCATION_MAX })),
    otherContactInfo: contactInfoSchema(t),
  })

export type OrgProfileData = z.infer<ReturnType<typeof buildOrgProfileSchema>>

export const toOrgProfileData = (dto: OrgSettingsDto): OrgProfileData => ({
  displayName: dto.displayName,
  legalName: dto.legalName,
  description: dto.description ?? '',
  website: dto.website ?? '',
  avatarUrl: dto.avatarUrl ?? '',
  location: dto.location ?? '',
  otherContactInfo: dto.otherContactInfo,
})

export const toUpdateOrgPayload = (data: OrgProfileData): UpdateOrgProfilePayload => ({
  displayName: data.displayName.trim(),
  legalName: data.legalName.trim(),
  description: data.description.trim() === '' ? null : data.description.trim(),
  website: data.website.trim() === '' ? null : data.website.trim(),
  avatarUrl: data.avatarUrl || null,
  location: data.location.trim() === '' ? null : data.location.trim(),
  otherContactInfo: data.otherContactInfo,
})
