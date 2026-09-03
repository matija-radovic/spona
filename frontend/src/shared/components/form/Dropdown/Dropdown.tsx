import './Dropdown.scss'
import type { ReactNode } from 'react'
import { IconCheck } from '@tabler/icons-react'
import usePopover from '@shared/hooks/usePopover'
import { useFieldContext } from '../FieldShell/fieldContext'
import { SelectTrigger } from '../SelectTrigger'

export interface DropdownOption {
  value: string
  label: string
  /** Arbitrary node before the label in the panel; the caller builds it. */
  leading?: ReactNode
}

interface DropdownProps {
  /** Accessible name only - never a visible caption. A `FieldShell` above supplies its own. */
  label: string
  options: readonly DropdownOption[]
  /** single mode: `string | null` · multi mode: `string[]` */
  value: string | string[] | null
  onChange: (next: string | string[] | null) => void
  multiple?: boolean
  /** shown as the trigger text when nothing is selected (the "none"/"all" case) */
  placeholder?: string
  /** Word-boundary cut for the trigger label. */
  maxLabelChars?: number
  disabled?: boolean
  /** options are still on the wire: the trigger opens onto placeholder rows */
  loading?: boolean
  /** panel text once loading has finished and there is still nothing to pick */
  emptyLabel?: string
  className?: string
}

/**
 * The panel is `position: absolute` and NOT portalled, so any host must keep
 * `overflow: visible` or it gets clipped; `usePopover` handles both edges.
 *
 * It carries no error concern: a required-and-unpicked dropdown turns red
 * because the `FieldShell` around it is invalid, and the words are the shell's.
 */
const Dropdown = ({
  label,
  options,
  value,
  onChange,
  multiple = false,
  placeholder,
  maxLabelChars,
  disabled = false,
  loading = false,
  emptyLabel,
  className,
}: DropdownProps) => {
  const field = useFieldContext()
  const { open, setOpen, toggle, rootRef, triggerRef, panelRef, above: openAbove } = usePopover()

  const selected = multiple
    ? Array.isArray(value)
      ? value
      : []
    : typeof value === 'string'
      ? [value]
      : []

  const isSelected = (optionValue: string) => selected.includes(optionValue)

  const handleSelect = (optionValue: string) => {
    if (multiple) {
      const next = isSelected(optionValue)
        ? selected.filter((v) => v !== optionValue)
        : [...selected, optionValue]
      onChange(next)
    } else {
      onChange(optionValue)
      setOpen(false)
    }
  }

  const triggerLabel = (() => {
    if (selected.length === 0) return placeholder ?? label
    if (!multiple) return options.find((o) => o.value === selected[0])?.label ?? placeholder ?? label
    const first = options.find((o) => o.value === selected[0])?.label ?? selected[0]
    const extra = selected.length - 1
    return extra > 0 ? `${first} +${extra}` : first
  })()

  const hasSelection = multiple ? selected.length > 0 : selected.some((v) => v !== '')

  // Both ids, so the trigger announces the field's label and then its value; on
  // its own the trigger's name would be the value alone.
  const labelledBy = field ? `${field.labelId} ${field.controlId}` : undefined

  const panelBody = (() => {
    if (loading) {
      return (
        <li className="dropdown-placeholder" role="presentation">
          {[0, 1, 2].map((row) => (
            <span key={row} className="dropdown-placeholder-bar" />
          ))}
        </li>
      )
    }
    if (options.length === 0) {
      return emptyLabel ? (
        <li className="dropdown-empty" role="presentation">
          {emptyLabel}
        </li>
      ) : null
    }
    return options.map((option) => {
      const active = isSelected(option.value)
      return (
        <li key={option.value}>
          <button
            type="button"
            role="option"
            aria-selected={active}
            className={`dropdown-option${active ? ' is-selected' : ''}`}
            onClick={() => handleSelect(option.value)}
          >
            {multiple && (
              <span className={`dropdown-check${active ? ' is-checked' : ''}`} aria-hidden>
                {active && <IconCheck />}
              </span>
            )}
            {option.leading}
            <span className="dropdown-option-label">{option.label}</span>
            {!multiple && active && (
              <span className="dropdown-option-hint" aria-hidden>
                <IconCheck />
              </span>
            )}
          </button>
        </li>
      )
    })
  })()

  return (
    <div className={['dropdown', className].filter(Boolean).join(' ')} ref={rootRef}>
      <SelectTrigger
        ref={triggerRef}
        id={field?.controlId}
        className="dropdown-trigger"
        open={open}
        active={hasSelection}
        disabled={disabled}
        maxLabelChars={maxLabelChars}
        onClick={toggle}
        aria-haspopup="listbox"
        aria-label={field ? undefined : label}
        aria-labelledby={labelledBy}
        aria-describedby={field?.describedBy}
        aria-invalid={field?.invalid}
      >
        {triggerLabel}
      </SelectTrigger>

      {open && (
        <ul
          ref={panelRef}
          className={`dropdown-panel scroll-container${openAbove ? ' is-above' : ''}`}
          role="listbox"
          aria-label={label}
          aria-multiselectable={multiple}
          aria-busy={loading || undefined}
        >
          {panelBody}
        </ul>
      )}
    </div>
  )
}

export default Dropdown
