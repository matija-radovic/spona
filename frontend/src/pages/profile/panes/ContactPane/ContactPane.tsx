import './ContactPane.scss'
import { useTranslation } from 'react-i18next'
import { ContactValue, contactTypeOption, type ContactEntry } from '@shared/components'

interface ContactPaneProps {
  /** Never empty, the tab only renders when there are entries */
  entries: ContactEntry[]
}

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
