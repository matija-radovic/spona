import './ContactApply.scss'
import { ContactValue, contactTypeOption, type ContactEntry } from '@shared/components'

interface ContactApplyProps {
  entries: ContactEntry[]
}

const ContactApply = ({ entries }: ContactApplyProps) => (
  <ul className="contact-apply">
    {entries.map((entry, index) => {
      const option = contactTypeOption(entry.type)
      const EntryIcon = option.icon
      return (
        <li key={`${entry.type}-${index}`} className="contact-apply-item">
          <span className="contact-apply-icon">
            <EntryIcon aria-hidden />
          </span>
          <span className="contact-apply-text">
            {entry.description && <span className="contact-apply-desc">{entry.description}</span>}
            <ContactValue entry={entry} className="contact-apply-value" />
          </span>
        </li>
      )
    })}
  </ul>
)

export default ContactApply
