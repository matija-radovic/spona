import { safeHref, displayUrl, WEB_PROTOCOLS } from '@utils/safeUrl'
import { ContactType, type ContactEntry } from './contactEntry'
import { contactTypeOption, ContactGroup } from './contactTypes'

/**
 * How a contact value becomes a link. Contact entries are other people's input
 * rendered on your screen, so nothing here trusts the string: every branch ends
 * at `safeHref`, and anything it rejects renders as plain text (§XSS of
 * contact-types.md).
 */

/** Deliberately conservative - it gates a `mailto:`, it is not RFC 5322. */
const EMAIL = /^[^\s@,;:<>()[\]\\"]+@[^\s@.,;:<>()[\]\\"]+(?:\.[^\s@.,;:<>()[\]\\"]+)+$/

/** Dialable characters. Everything else (letters, `<`, quotes) is dropped. */
const NOT_DIALABLE = /[^0-9+]/g

const telHref = (value: string): string | null => {
  // Keep a leading `+` only; an interior one is junk.
  const digits = value.replace(NOT_DIALABLE, '').replace(/(?!^)\+/g, '')
  if (digits.replace(/\D/g, '').length < 4) return null
  return safeHref(`tel:${digits}`, { protocols: ['tel:'], allowSchemeless: false, allowRelative: false })
}

const mailtoHref = (value: string): string | null => {
  const address = value.trim()
  if (!EMAIL.test(address)) return null
  return safeHref(`mailto:${address}`, {
    protocols: ['mailto:'],
    allowSchemeless: false,
    allowRelative: false,
  })
}

/**
 * The href for a contact value, or `null` when it must stay plain text.
 *
 * `fax` and `other` never link: a fax number isn't dialable from a browser, and
 * `other` is by definition unknown - guessing a scheme for it is exactly the
 * kind of inference this design keeps out of the render path.
 */
export const contactHref = (entry: ContactEntry): string | null => {
  const option = contactTypeOption(entry.type)

  if (option.group === ContactGroup.LINK) {
    return safeHref(entry.value, { protocols: WEB_PROTOCOLS, allowRelative: false })
  }
  if (option.key === ContactType.PHONE) return telHref(entry.value)
  if (option.key === ContactType.EMAIL) return mailtoHref(entry.value)
  return null
}

/**
 * What the user reads. Link types lose the scheme and any `www.`/trailing slash
 * so a 64-character URL still fits a card; everything else shows verbatim.
 *
 * Derived from the same `entry.value` as {@link contactHref} - never from a
 * separate label - so the text and the target can't be made to disagree.
 */
export const contactDisplayValue = (entry: ContactEntry): string =>
  contactTypeOption(entry.type).group === ContactGroup.LINK
    ? displayUrl(entry.value)
    : entry.value
