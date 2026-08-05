import './Dropdown.scss'
import { useEffect, useRef, useState } from 'react'
import { IconCheck, IconChevronDown, IconChevronUp } from '@tabler/icons-react'
import type { Icon } from '@tabler/icons-react'

export interface DropdownOption {
  value: string
  label: string
  icon?: Icon
}

interface DropdownProps {
  label: string
  options: readonly DropdownOption[]
  /** single mode: `string | null` · multi mode: `string[]` */
  value: string | string[] | null
  onChange: (next: string | string[] | null) => void
  multiple?: boolean
  /** shown as the trigger text when nothing is selected (the "none"/"all" case) */
  placeholder?: string
  disabled?: boolean
  className?: string
}

/**
 * Controlled picker (single + multi), extracted/generalised from the
 * ContactInfoWidget type-selector. Pure `value`/`onChange` — drops into RHF via
 * `<Controller>` without this component depending on react-hook-form.
 *
 * Overflow caveat (job-filter.md §4.3): the panel is `position: absolute`, so an
 * `overflow: hidden/auto` ancestor would clip it. The filter menu that hosts
 * these dropdowns therefore keeps `overflow: visible` (option 1 — no portal).
 */
const Dropdown = ({
  label,
  options,
  value,
  onChange,
  multiple = false,
  placeholder,
  disabled = false,
  className,
}: DropdownProps) => {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onPointerDown = (event: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) setOpen(false)
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('mousedown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

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
  const rootClass = ['dropdown', open ? 'is-open' : '', hasSelection ? 'is-active' : '', className ?? '']
    .filter(Boolean)
    .join(' ')

  const Chevron = open ? IconChevronUp : IconChevronDown

  return (
    <div className={rootClass} ref={rootRef}>
      <button
        type="button"
        className="dropdown-trigger"
        onClick={() => setOpen((o) => !o)}
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={open}
      >
        <span className="dropdown-trigger-label">{triggerLabel}</span>
        <Chevron aria-hidden />
      </button>

      {open && (
        <ul className="dropdown-panel scroll-container" role="listbox" aria-label={label} aria-multiselectable={multiple}>
          {options.map((option) => {
            const OptionIcon = option.icon
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
                  {OptionIcon && <OptionIcon aria-hidden />}
                  <span className="dropdown-option-label">{option.label}</span>
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

export default Dropdown
