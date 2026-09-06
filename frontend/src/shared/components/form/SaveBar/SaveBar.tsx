import './SaveBar.scss'
import type { ReactNode } from 'react'

interface SaveBarProps {
  /** A section-level failure (a field-level one is pinned to its input instead). */
  error?: string | null
  /** The submit, and whatever sits beside it. */
  children: ReactNode
}

/** The rule-topped action row a routed editing section closes with. */
const SaveBar = ({ error, children }: SaveBarProps) => (
  <div className="save-bar">
    {error && <p className="save-bar-error">{error}</p>}
    {children}
  </div>
)

export default SaveBar
