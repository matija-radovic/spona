import './TextField.scss'
import {
  forwardRef,
  useId,
  useState,
  type ComponentPropsWithoutRef,
  type ReactNode,
  type Ref,
} from 'react'
import { IconX, IconCheck } from '@tabler/icons-react'

type FieldStatus = 'idle' | 'valid' | 'invalid'

interface TextFieldOwnProps {
  label: string
  required?: boolean
  /** Dimmed description rendered below the input; supports multiple rows. */
  helper?: ReactNode
  /** When set, drives the invalid (✕) indicator and the tooltip message. */
  error?: string
  /** When true (and no error), shows the green checkmark indicator. */
  valid?: boolean
  /** Renders a <textarea> instead of an <input>. */
  multiline?: boolean
  rows?: number
}

type TextFieldProps = TextFieldOwnProps & ComponentPropsWithoutRef<'input'>

const TextField = forwardRef<HTMLInputElement | HTMLTextAreaElement, TextFieldProps>(
  ({ label, required, helper, error, valid, multiline, rows, id, className, ...rest }, ref) => {
    const generatedId = useId()
    const fieldId = id ?? generatedId
    const [tooltipOpen, setTooltipOpen] = useState(false)

    const status: FieldStatus = error ? 'invalid' : valid ? 'valid' : 'idle'
    const helperId = helper ? `${fieldId}-helper` : undefined

    const rootClass = [
      'text-field',
      `text-field-${status}`,
      multiline ? 'text-field-multiline' : '',
      className ?? '',
    ]
      .filter(Boolean)
      .join(' ')

    return (
      <div className={rootClass}>
        <label className="text-field-label" htmlFor={fieldId}>
          {label}
          {required && (
            <span className="text-field-asterisk" aria-hidden="true">
              *
            </span>
          )}
        </label>

        <div className="text-field-control">
          {multiline ? (
            <textarea
              id={fieldId}
              ref={ref as Ref<HTMLTextAreaElement>}
              className="text-field-input"
              rows={rows ?? 4}
              aria-invalid={status === 'invalid'}
              aria-describedby={helperId}
              {...(rest as ComponentPropsWithoutRef<'textarea'>)}
            />
          ) : (
            <input
              id={fieldId}
              ref={ref as Ref<HTMLInputElement>}
              className="text-field-input"
              aria-invalid={status === 'invalid'}
              aria-describedby={helperId}
              {...rest}
            />
          )}

          {status !== 'idle' && (
            <div
              className={`text-field-indicator-wrap${tooltipOpen ? ' is-open' : ''}`}
              onMouseLeave={() => setTooltipOpen(false)}
            >
              <button
                type="button"
                className="text-field-indicator"
                onClick={() => setTooltipOpen((open) => !open)}
                aria-label={error ?? label}
                tabIndex={error ? 0 : -1}
              >
                {status === 'invalid' ? <IconX size={14} aria-hidden="true" /> : <IconCheck size={14} aria-hidden="true" />}
              </button>
              {error && (
                <span className="text-field-tooltip" role="tooltip">
                  {error}
                </span>
              )}
            </div>
          )}
        </div>

        {helper && (
          <p id={helperId} className="text-field-helper">
            {helper}
          </p>
        )}
      </div>
    )
  },
)

TextField.displayName = 'TextField'

export default TextField
