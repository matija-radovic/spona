import './ContactPane.scss'
import { useTranslation } from 'react-i18next'
import { ContactValue, contactTypeOption, type ContactEntry } from '@shared/components'

interface ContactPaneProps {
  /** The tab only renders when this is non-empty (§6), so it is always populated. */
  entries: ContactEntry[]
}

/**
 * The actor's additional contacts in a grid of equal boxes (icon + description /
 * type fallback + value). Mirrors ContactApply's per-entry rendering, reusing the
 * icon-per-type map and the shared `ContactValue` (which decides link vs text).
 */
const ContactPane = ({ entries }: ContactPaneProps) => {
  const { t } = useTranslation()

  return (
    <ul className="contact-pane">
      {entries.map((entry, index) => {
        const option = contactTypeOption(entry.type)
        const EntryIcon = option.icon
        return (
          <li key={`${entry.type}-${index}`} className="contact-pane-box">
            <span className="contact-pane-icon">
              <EntryIcon aria-hidden />
            </span>
            <span className="contact-pane-text">
              <span className="contact-pane-desc">{entry.description || t(option.labelKey)}</span>
              <ContactValue entry={entry} className="contact-pane-value" />
            </span>
          </li>
        )
      })}
    </ul>
  )
}

export default ContactPane
