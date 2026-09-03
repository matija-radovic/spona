import './FieldShell.scss'
import { useId, type ReactNode } from 'react'
import { FieldContext } from './fieldContext'

interface FieldShellProps {
  label: string
  required?: boolean
  /** Dimmed line under the control; stays visible while an error is showing. */
  hint?: ReactNode
  /** Rendered as the line below. Hand it to `Input` instead for the ✕ marker. */
  error?: string
  /** False when the child is not a labelable element - a button, a capsule group, a widget. */
  htmlLabel?: boolean
  className?: string
  children: ReactNode
}

/**
 * The chrome around one control: label, asterisk, hint, error. It hands the
 * control an id, a description and an invalid flag through context, so no
 * control writes a label of its own and the wiring covers every message the
 * field renders.
 */
const FieldShell = ({
  label,
  required,
  hint,
  error,
  htmlLabel = true,
  className,
  children,
}: FieldShellProps) => {
  const id = useId()
  const controlId = `${id}-control`
  const labelId = `${id}-label`
  const hintId = hint ? `${id}-hint` : undefined
  const errorId = error ? `${id}-error` : undefined
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined

  const rootClass = ['field-shell', error ? 'is-invalid' : '', className ?? '']
    .filter(Boolean)
    .join(' ')

  const labelBody = (
    <>
      {label}
      {required && (
        <span className="field-shell-asterisk" aria-hidden="true">
          *
        </span>
      )}
    </>
  )

  return (
    <div className={rootClass}>
      {htmlLabel ? (
        <label id={labelId} className="field-shell-label" htmlFor={controlId}>
          {labelBody}
        </label>
      ) : (
        <span id={labelId} className="field-shell-label">
          {labelBody}
        </span>
      )}

      <FieldContext value={{ controlId, labelId, describedBy, invalid: !!error }}>
        {children}
      </FieldContext>

      {/* The hint explains what to type and stays true while the value is bad,
          so an error joins it rather than replacing it. */}
      {hint && (
        <p id={hintId} className="field-shell-hint">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="field-shell-error">
          {error}
        </p>
      )}
    </div>
  )
}

export default FieldShell
