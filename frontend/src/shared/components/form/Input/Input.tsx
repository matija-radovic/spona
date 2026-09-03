import './Input.scss'
import {
  forwardRef,
  useId,
  useState,
  type ComponentPropsWithoutRef,
  type ReactNode,
  type Ref,
} from 'react'
import { IconCheck, IconX } from '@tabler/icons-react'
import { useFieldContext } from '../FieldShell/fieldContext'
import { InputShape } from './inputShape'

type FieldStatus = 'idle' | 'valid' | 'invalid'

interface InputOwnProps {
  /** A node inside the box, before the field: a search icon, a currency glyph. */
  leading?: ReactNode
  /** A node inside the box, after the field: a unit, a small action. */
  trailing?: ReactNode
  /** Renders a <textarea> instead of an <input>. */
  multiline?: boolean
  rows?: number
  /** FIELD (10px, the default) or PILL (999px, a search bar). */
  shape?: InputShape
  /** Drives the ✕ marker and its tooltip. Passed here, never read from the shell. */
  error?: string
  /** Green ✓ when the field is filled and clean. */
  valid?: boolean
}

type InputProps = InputOwnProps & ComponentPropsWithoutRef<'input'>

/**
 * The one text control. Its box, its focus ring and its ✕/✓ marker; the label,
 * the hint and the error line belong to the `FieldShell` around it, which is
 * also where the id and the description come from.
 */
const Input = forwardRef<HTMLInputElement | HTMLTextAreaElement, InputProps>(
  (
    {
      leading,
      trailing,
      multiline,
      rows,
      shape = InputShape.FIELD,
      error,
      valid,
      id,
      className,
      ...rest
    },
    ref,
  ) => {
    const field = useFieldContext()
    const generatedId = useId()
    const [tooltipOpen, setTooltipOpen] = useState(false)

    const fieldId = id ?? field?.controlId ?? generatedId
    const status: FieldStatus = error ? 'invalid' : valid ? 'valid' : 'idle'
    const invalid = status === 'invalid' || (field?.invalid ?? false)

    const rootClass = [
      'input',
      shape === InputShape.PILL ? 'input-pill' : '',
      multiline ? 'input-multiline' : '',
      status === 'idle' ? '' : `is-${status}`,
      className ?? '',
    ]
      .filter(Boolean)
      .join(' ')

    const controlProps = {
      id: fieldId,
      className: 'input-field',
      'aria-invalid': invalid,
      'aria-describedby': field?.describedBy,
    }

    return (
      <div className={rootClass}>
        <div className="input-box">
          {leading && <span className="input-leading">{leading}</span>}

          {multiline ? (
            <textarea
              {...controlProps}
              ref={ref as Ref<HTMLTextAreaElement>}
              rows={rows ?? 4}
              {...(rest as ComponentPropsWithoutRef<'textarea'>)}
            />
          ) : (
            <input {...controlProps} ref={ref as Ref<HTMLInputElement>} {...rest} />
          )}

          {trailing && <span className="input-trailing">{trailing}</span>}
        </div>

        {status === 'invalid' && (
          <div
            className={`input-indicator-wrap${tooltipOpen ? ' is-open' : ''}`}
            onMouseLeave={() => setTooltipOpen(false)}
          >
            <button
              type="button"
              className="input-indicator"
              onClick={() => setTooltipOpen((open) => !open)}
              aria-label={error}
            >
              <IconX aria-hidden="true" />
            </button>
            <span className="input-tooltip" role="tooltip">
              {error}
            </span>
          </div>
        )}

        {status === 'valid' && (
          <div className="input-indicator-wrap">
            <span className="input-indicator" aria-hidden="true">
              <IconCheck />
            </span>
          </div>
        )}
      </div>
    )
  },
)

Input.displayName = 'Input'

export default Input
