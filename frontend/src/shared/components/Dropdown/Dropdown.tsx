import './Dropdown.scss'
import { useEffect, useRef, useState } from 'react'
import { IconCheck, IconChevronDown, IconChevronUp } from '@tabler/icons-react'
import type { Icon } from '@tabler/icons-react'
import usePopoverFlip from '@shared/hooks/usePopoverFlip'

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
  /** options are still on the wire: the trigger opens onto placeholder rows */
  loading?: boolean
  /** panel text once loading has finished and there is still nothing to pick */
  emptyLabel?: string
  className?: string
}

/**
 * The panel is `position: absolute` and NOT portalled, so any host must keep
 * `overflow: visible` or it gets clipped; `usePopoverFlip` handles the bottom edge.
 */
const Dropdown = ({
  label,
  options,
  value,
  onChange,
  multiple = false,
  placeholder,
  disabled = false,
  loading = false,
  emptyLabel,
  className,
}: DropdownProps) => {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)

  const { above: openAbove, panelRef } = usePopoverFlip(open, triggerRef)

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
    })
  })()

  return (
    <div className={rootClass} ref={rootRef}>
      <button
        type="button"
        ref={triggerRef}
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
