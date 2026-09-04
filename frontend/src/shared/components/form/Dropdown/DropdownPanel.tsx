import { useRef, useState, type KeyboardEvent } from 'react'
import { IconCheck, IconSearch } from '@tabler/icons-react'
import { foldText } from '@utils/foldText'
import type { DropdownNoMatch, DropdownOption } from './dropdownOption'

interface DropdownPanelProps {
  panelRef: (node: HTMLElement | null) => void
  above: boolean
  /** Accessible name for the listbox. */
  label: string
  options: readonly DropdownOption[]
  isSelected: (value: string) => boolean
  onSelect: (value: string) => void
  multiple: boolean
  loading: boolean
  emptyLabel?: string
  /** Renders the filter input and the query-driven behaviour with it. */
  filtering: boolean
  searchPlaceholder?: string
  noMatch?: DropdownNoMatch
}

const matches = (option: DropdownOption, query: string): boolean =>
  foldText(option.label).includes(query) ||
  (option.searchTokens ?? []).some((token) => foldText(token).includes(query))

const DropdownPanel = ({
  panelRef,
  above,
  label,
  options,
  isSelected,
  onSelect,
  multiple,
  loading,
  emptyLabel,
  filtering,
  searchPlaceholder,
  noMatch,
}: DropdownPanelProps) => {
  const [query, setQuery] = useState('')
  const [activeIndex, setActiveIndex] = useState(0)
  const listRef = useRef<HTMLUListElement>(null)

  const folded = foldText(query.trim())
  const visible = filtering && folded ? options.filter((o) => matches(o, folded)) : options
  const fallback = filtering && folded && visible.length === 0 ? noMatch : undefined
  const rows = fallback?.option ? [fallback.option] : visible

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      if (rows.length === 0) return
      const step = event.key === 'ArrowDown' ? 1 : -1
      const next = (activeIndex + step + rows.length) % rows.length
      setActiveIndex(next)
      listRef.current?.children[next]?.scrollIntoView({ block: 'nearest' })
    } else if (event.key === 'Enter') {
      event.preventDefault()
      const option = rows[activeIndex]
      if (option) onSelect(option.value)
    }
  }

  const body = (() => {
    if (loading) {
      return (
        <li className="dropdown-placeholder" role="presentation">
          {[0, 1, 2].map((row) => (
            <span key={row} className="dropdown-placeholder-bar" />
          ))}
        </li>
      )
    }
    if (rows.length === 0) {
      return emptyLabel ? (
        <li className="dropdown-empty" role="presentation">
          {emptyLabel}
        </li>
      ) : null
    }
    return rows.map((option, index) => {
      const active = isSelected(option.value)
      const highlighted = filtering && index === activeIndex
      return (
        <li key={option.value}>
          <button
            type="button"
            role="option"
            aria-selected={active}
            className={`dropdown-option${active ? ' is-selected' : ''}${highlighted ? ' is-active' : ''}`}
            onClick={() => onSelect(option.value)}
            onMouseEnter={filtering ? () => setActiveIndex(index) : undefined}
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
    <div ref={panelRef} className={`dropdown-panel${above ? ' is-above' : ''}`}>
      {filtering && (
        <div className="dropdown-filter">
          <IconSearch aria-hidden />
          <input
            type="text"
            value={query}
            onChange={(event) => {
              setQuery(event.target.value)
              setActiveIndex(0)
            }}
            onKeyDown={onKeyDown}
            placeholder={searchPlaceholder}
            aria-label={searchPlaceholder ?? label}
            autoComplete="off"
            autoFocus
          />
        </div>
      )}

      <ul
        ref={listRef}
        className="dropdown-list scroll-container"
        role="listbox"
        aria-label={label}
        aria-multiselectable={multiple}
        aria-busy={loading || undefined}
      >
        {body}
      </ul>

      {fallback?.hint && <p className="dropdown-hint">{fallback.hint}</p>}
    </div>
  )
}

export default DropdownPanel
