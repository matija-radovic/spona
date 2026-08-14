import './SettingsSection.scss'
import type { ReactNode } from 'react'

interface SettingsSectionProps {
  /** Omitted: the whole header is dropped, and `description` with it. */
  title?: string
  description?: string
  className?: string
  children: ReactNode
}

const SettingsSection = ({ title, description, className, children }: SettingsSectionProps) => (
  <section className={className ? `settings-section ${className}` : 'settings-section'}>
    {title && (
      <header className="settings-section-head">
        <h4 className="settings-section-title">{title}</h4>
        {description && <p className="settings-section-hint">{description}</p>}
      </header>
    )}

    {children}
  </section>
)

export default SettingsSection
