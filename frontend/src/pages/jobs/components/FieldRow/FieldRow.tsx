import './FieldRow.scss'
import type { ReactNode } from 'react'

interface FieldRowProps {
  label: string
  required?: boolean
  /** Dimmed line under the control, in `TextField`'s helper position. */
  hint?: string
  error?: string
  className?: string
  children: ReactNode
}

/** Wrapper around input like components/groups to present errors, hints and label above */
const FieldRow = ({ label, required, hint, error, className, children }: FieldRowProps) => (
  <div className={['field-row', className].filter(Boolean).join(' ')}>
    <span className="field-row-label">
      {label}
      {required && (
        <span className="field-row-asterisk" aria-hidden="true">
          *
        </span>
      )}
    </span>

    {children}

    {hint && !error && <p className="field-row-hint">{hint}</p>}
    {error && <p className="field-row-error">{error}</p>}
  </div>
)

export default FieldRow
