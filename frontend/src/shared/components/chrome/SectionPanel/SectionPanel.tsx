import './SectionPanel.scss'
import type { ReactNode } from 'react'

interface SectionPanelProps {
  /** Omitted: the whole header is dropped, and `description` with it. */
  title?: string
  description?: string
  className?: string
  children: ReactNode
}

/** One routed section inside a `SectionLayout`. */
const SectionPanel = ({ title, description, className, children }: SectionPanelProps) => (
  <section className={className ? `section-panel ${className}` : 'section-panel'}>
    {title && (
      <header className="section-panel-head">
        <h4 className="section-panel-title">{title}</h4>
        {description && <p className="section-panel-hint">{description}</p>}
      </header>
    )}

    {children}
  </section>
)

export default SectionPanel
