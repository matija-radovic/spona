import './ContactValue.scss'
import { contactHref, contactDisplayValue } from './contactLink'
import { isLinkType } from './contactTypes'
import type { ContactEntry } from './contactEntry'

interface ContactValueProps {
  entry: ContactEntry
  /** Class for the rendered element - each surface keeps its own SCSS. */
  className: string
}

/**
 * One contact value, as a link when it can be made into a safe one and as plain
 * text when it can't.
 *
 * `_blank` is only for web links - a `tel:`/`mailto:` hands off to another app
 * and would strand an empty tab.
 */
const ContactValue = ({ entry, className }: ContactValueProps) => {
  const href = contactHref(entry)
  const text = contactDisplayValue(entry)

  if (!href) return <span className={className}>{text}</span>

  const web = isLinkType(entry.type)

  return (
    <a
      className={`${className} contact-value-link`}
      href={href}
      {...(web ? { target: '_blank', rel: 'noopener noreferrer' } : {})}
    >
      {text}
    </a>
  )
}

export default ContactValue
