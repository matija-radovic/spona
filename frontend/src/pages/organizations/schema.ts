import { z } from 'zod'
import {
  SLUG_MIN,
  SLUG_MAX,
  SLUG_REGEX,
  CONTACT_DESC_MAX,
  CONTACT_VALUE_MAX,
  CONTACT_MAX_ENTRIES,
  LOCATION_MAX,
  ORG_DESCRIPTION_MAX,
  ORG_DISPLAY_NAME_MAX,
  ORG_LEGAL_NAME_MAX,
  ORG_WEBSITE_MAX,
} from '@utils/constants/validation'
import type { CreateOrganizationPayload } from '@utils/api/organizations'

type Translate = (key: string, opts?: Record<string, unknown>) => string

/** Empty optional strings become null to match the nullable DTO columns. */
const orNull = (value: string): string | null => (value.trim() === '' ? null : value.trim())

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

export const buildCreateOrgSchema = (t: Translate) =>
  z.object({
    slug: z
      .string()
      .min(SLUG_MIN, t('errors.rangeLength', { min: SLUG_MIN, max: SLUG_MAX }))
      .max(SLUG_MAX, t('errors.rangeLength', { min: SLUG_MIN, max: SLUG_MAX }))
      .regex(SLUG_REGEX, t('errors.slugFormat')),
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
    avatar: z.string(),
    location: z.string().max(LOCATION_MAX, t('errors.maxLength', { max: LOCATION_MAX })),
    otherContactInfo: contactInfoSchema(t),
  })

export type CreateOrgData = z.infer<ReturnType<typeof buildCreateOrgSchema>>

export const CREATE_ORG_DEFAULTS: CreateOrgData = {
  slug: '',
  displayName: '',
  legalName: '',
  description: '',
  website: '',
  avatar: '',
  location: '',
  otherContactInfo: [],
}

export const toCreateOrgPayload = (data: CreateOrgData): CreateOrganizationPayload => ({
  slug: data.slug,
  displayName: data.displayName.trim(),
  legalName: data.legalName.trim(),
  description: orNull(data.description),
  website: orNull(data.website),
  avatar: data.avatar || null,
  location: orNull(data.location),
  otherContactInfo: data.otherContactInfo,
})
