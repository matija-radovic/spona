import type { Icon } from '@tabler/icons-react'
import {
  IconPhone,
  IconMail,
  IconPrinter,
  IconWorld,
  IconBrandLinkedin,
  IconBrandInstagram,
  IconBrandFacebook,
  IconDots,
} from '@tabler/icons-react'
import { safeHref, WEB_PROTOCOLS } from '@utils/safeUrl'
import { ContactType } from './contactEntry'

/**
 * - DIRECT = a value you dial, write to, or just read. 
 * - LINK = a value that is a URL and renders clickable. The groups are the two 
 * sections of the type picker; they exist so eight options stay scannable.
 */
export const ContactGroup = Object.freeze({
  DIRECT: 'direct',
  LINK: 'link',
} as const)

export type ContactGroup = (typeof ContactGroup)[keyof typeof ContactGroup]

export interface ContactTypeOption {
  key: ContactType
  icon: Icon
  labelKey: string
  hintKey: string
  group: ContactGroup
  /**
   * Host suffixes that identify this type when a URL is pasted (`inferContactType`).
   * Matched against the registrable host, so `www.` and any subdomain are covered.
   */
  hosts?: readonly string[]
}

/**
 * The closed set of types the UI can draw, in picker order. `other` MUST stay
 * last - `contactTypeOption` falls back to the final entry for unknown slugs.
 */
export const CONTACT_TYPE_OPTIONS: readonly ContactTypeOption[] = [
  {
    key: ContactType.PHONE,
    icon: IconPhone,
    labelKey: 'auth.contact.types.phone',
    hintKey: 'auth.contact.hints.phone',
    group: ContactGroup.DIRECT,
  },
  {
    key: ContactType.EMAIL,
    icon: IconMail,
    labelKey: 'auth.contact.types.email',
    hintKey: 'auth.contact.hints.email',
    group: ContactGroup.DIRECT,
  },
  {
    key: ContactType.FAX,
    icon: IconPrinter,
    labelKey: 'auth.contact.types.fax',
    hintKey: 'auth.contact.hints.fax',
    group: ContactGroup.DIRECT,
  },
  {
    key: ContactType.WEBSITE,
    icon: IconWorld,
    labelKey: 'auth.contact.types.website',
    hintKey: 'auth.contact.hints.website',
    group: ContactGroup.LINK,
  },
  {
    key: ContactType.LINKEDIN,
    icon: IconBrandLinkedin,
    labelKey: 'auth.contact.types.linkedin',
    hintKey: 'auth.contact.hints.linkedin',
    group: ContactGroup.LINK,
    hosts: ['linkedin.com'],
  },
  {
    key: ContactType.INSTAGRAM,
    icon: IconBrandInstagram,
    labelKey: 'auth.contact.types.instagram',
    hintKey: 'auth.contact.hints.instagram',
    group: ContactGroup.LINK,
    hosts: ['instagram.com'],
  },
  {
    key: ContactType.FACEBOOK,
    icon: IconBrandFacebook,
    labelKey: 'auth.contact.types.facebook',
    hintKey: 'auth.contact.hints.facebook',
    group: ContactGroup.LINK,
    hosts: ['facebook.com', 'fb.com', 'fb.me'],
  },
  {
    key: ContactType.OTHER,
    icon: IconDots,
    labelKey: 'auth.contact.types.other',
    hintKey: 'auth.contact.hints.other',
    group: ContactGroup.DIRECT,
  },
]

/** Picker order: DIRECT first, then LINK. Drives the two panel sections. */
export const CONTACT_GROUP_ORDER: readonly ContactGroup[] = [ContactGroup.DIRECT, ContactGroup.LINK]

export const CONTACT_GROUP_LABEL_KEYS: Readonly<Record<ContactGroup, string>> = Object.freeze({
  [ContactGroup.DIRECT]: 'auth.contact.groups.direct',
  [ContactGroup.LINK]: 'auth.contact.groups.link',
})

/**
 * The option for a slug. Unknown slugs resolve to the last option (`other`) -
 * that total fallback is what lets `ContactEntry.type` stay a plain string.
 */
export const contactTypeOption = (key: string): ContactTypeOption =>
  CONTACT_TYPE_OPTIONS.find((option) => option.key === key) ??
  CONTACT_TYPE_OPTIONS[CONTACT_TYPE_OPTIONS.length - 1]

/** Types whose value is a URL, and therefore renders as a link. */
export const isLinkType = (key: string): boolean => contactTypeOption(key).group === ContactGroup.LINK

/** `www.linkedin.com` matches `linkedin.com`; `notlinkedin.com` does not. */
const hostMatches = (host: string, suffix: string): boolean =>
  host === suffix || host.endsWith(`.${suffix}`)

/**
 * Best guess at the type of a pasted value, or `null` when nothing matches.
 *
 * Input-time only - never call this while rendering. The stored slug is the
 * source of truth, so an entry's icon can't change under a viewer just because
 * the host table did.
 */
export const inferContactType = (value: string): ContactType | null => {
  const href = safeHref(value, { protocols: WEB_PROTOCOLS })
  if (!href) return null

  const host = new URL(href).hostname.toLowerCase()
  const branded = CONTACT_TYPE_OPTIONS.find((option) =>
    option.hosts?.some((suffix) => hostMatches(host, suffix)),
  )
  return branded?.key ?? ContactType.WEBSITE
}

/**
 * The slug to actually store for a draft: the user's pick, upgraded when they
 * left it on one of the two unspecific types and pasted something recognisable.
 * An explicit pick (`phone`, `linkedin`, ...) is never overridden.
 */
export const resolveContactType = (picked: string, value: string): string => {
  if (picked !== ContactType.WEBSITE && picked !== ContactType.OTHER) return picked
  return inferContactType(value) ?? picked
}
