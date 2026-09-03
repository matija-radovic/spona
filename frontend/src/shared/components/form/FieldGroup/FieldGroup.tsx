import './FieldGroup.scss'
import type { ReactNode } from 'react'

interface FieldGroupProps {
  legend: string
  /** Dimmed line under the legend, explaining the group as a whole. */
  hint?: ReactNode
  className?: string
  children: ReactNode
}

/** `FieldShell`'s twin for a set of fields that are read as one thing. */
const FieldGroup = ({ legend, hint, className, children }: FieldGroupProps) => (
  <fieldset className={['field-group', className].filter(Boolean).join(' ')}>
    <legend className="field-group-legend">{legend}</legend>
    {hint && <p className="field-group-hint">{hint}</p>}
    {children}
  </fieldset>
)

export default FieldGroup
