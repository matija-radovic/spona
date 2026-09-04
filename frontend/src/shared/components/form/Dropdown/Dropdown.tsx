import './Dropdown.scss'
import usePopover from '@shared/hooks/usePopover'
import { useFieldContext } from '../FieldShell/fieldContext'
import { SelectTrigger } from '../SelectTrigger'
import DropdownPanel from './DropdownPanel'
import type { DropdownNoMatch, DropdownOption } from './dropdownOption'

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
  /** Pins a filter input at the top of the panel. Single-select only. */
  searchable?: boolean
  searchPlaceholder?: string
  /** Offered when the filter matches nothing. */
  noMatch?: DropdownNoMatch
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
  searchable = false,
  searchPlaceholder,
  noMatch,
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
        <DropdownPanel
          panelRef={panelRef}
          above={openAbove}
          label={label}
          options={options}
          isSelected={isSelected}
          onSelect={handleSelect}
          multiple={multiple}
          loading={loading}
          emptyLabel={emptyLabel}
          filtering={searchable && !multiple}
          searchPlaceholder={searchPlaceholder}
          noMatch={noMatch}
        />
      )}
    </div>
  )
}

export default Dropdown
