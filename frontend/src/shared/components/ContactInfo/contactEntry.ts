/**
 * This is the single source of truth for one contact entry.
 */

export const ContactType = Object.freeze({
  PHONE: 'phone',
  EMAIL: 'email',
  FAX: 'fax',
  WEBSITE: 'website',
  LINKEDIN: 'linkedin',
  INSTAGRAM: 'instagram',
  FACEBOOK: 'facebook',
  OTHER: 'other',
} as const)

export type ContactType = (typeof ContactType)[keyof typeof ContactType]

export interface ContactEntry {
  type: ContactType | (string & {})
  description: string
  value: string
}
