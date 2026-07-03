import './SearchFilterBar.scss'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import {
  IconSearch,
  IconFilter,
  IconFilterOff,
  IconChevronDown,
  IconChevronUp,
  IconCheck,
  IconAdjustmentsHorizontal,
} from '@tabler/icons-react'
import { Dropdown } from '@shared/components'
import { Button, ButtonVariant } from '@shared/components/Button'
import {
  FILTERS,
  PRIMARY_FILTER_COUNT,
  emptyFilters,
  type FilterConfig,
  type FilterState,
} from '@pages/jobs/lib/filters'

interface SearchFilterBarProps {
  filters: FilterState
  onSearch: (next: FilterState) => void
  isMobile: boolean
}

// The parent keys this component by the URL querystring, so a real navigation
// (e.g. browser Back) remounts it with fresh `filters` — no sync effect needed.
// Staged edits before submit never change the URL, so they survive re-renders.
const SearchFilterBar = ({ filters, onSearch, isMobile }: SearchFilterBarProps) => {
  const { t } = useTranslation()
  const [draft, setDraft] = useState<FilterState>(filters)
  const [menuOpen, setMenuOpen] = useState(false)
  const [showMore, setShowMore] = useState(false)

  const primaryFilters = FILTERS.slice(0, isMobile ? Math.min(2, PRIMARY_FILTER_COUNT) : PRIMARY_FILTER_COUNT)
  const moreFilters = FILTERS.slice(primaryFilters.length)

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    onSearch(draft)
    setMenuOpen(false)
  }

  const clearFilters = () => setDraft({ ...emptyFilters(), q: draft.q, role: draft.role })

  const updateSingle = (key: 'posted' | 'sort', next: string | string[] | null) => {
    const value = typeof next === 'string' && next !== '' ? next : null
    setDraft((d) => (key === 'posted' ? { ...d, posted: value } : { ...d, sort: value }))
  }

  const updateMulti = (key: 'workplace' | 'employment' | 'experience', next: string[]) => {
    setDraft((d) => {
      switch (key) {
        case 'workplace':
          return { ...d, workplace: next }
        case 'employment':
          return { ...d, employment: next }
        case 'experience':
          return { ...d, experience: next }
      }
    })
  }

  const renderFilter = (config: FilterConfig) => {
    const label = t(config.labelKey)

    if (config.kind === 'toggle') {
      return (
        <button
          key={config.key}
          type="button"
          className={`filter-toggle${draft.salary ? ' is-active' : ''}`}
          onClick={() => setDraft((d) => ({ ...d, salary: !d.salary }))}
          aria-pressed={draft.salary}
        >
          <span className="filter-toggle-box" aria-hidden>
            {draft.salary && <IconCheck size={13} />}
          </span>
          {label}
        </button>
      )
    }

    const options = (config.options ?? []).map((o) => ({ value: o.value, label: t(o.labelKey) }))

    if (config.kind === 'single') {
      const key = config.key as 'posted' | 'sort'
      return (
        <Dropdown
          key={config.key}
          label={label}
          options={options}
          value={draft[key] ?? ''}
          placeholder={label}
          onChange={(next) => updateSingle(key, next)}
        />
      )
    }

    const key = config.key as 'workplace' | 'employment' | 'experience'
    return (
      <Dropdown
        key={config.key}
        multiple
        label={label}
        options={options}
        value={draft[key]}
        placeholder={`${label}: ${t('jobs.filters.all')}`}
        onChange={(next) => updateMulti(key, next as string[])}
      />
    )
  }

  const FilterIcon = isMobile ? (menuOpen ? IconFilterOff : IconFilter) : IconFilter
  const Chevron = menuOpen ? IconChevronUp : IconChevronDown

  return (
    <form className="jobs-bar" onSubmit={handleSubmit} role="search">
      <div className="jobs-bar-row">
        <div className="jobs-bar-search">
          <IconSearch size={18} aria-hidden />
          <input
            type="search"
            className="jobs-bar-input"
            value={draft.q}
            onChange={(e) => setDraft((d) => ({ ...d, q: e.target.value }))}
            placeholder={t('jobs.search.placeholder')}
            enterKeyHint="search"
            aria-label={t('jobs.search.placeholder')}
          />
        </div>

        <button
          type="button"
          className={`jobs-bar-filter-btn${menuOpen ? ' is-open' : ''}`}
          onClick={() => setMenuOpen((o) => !o)}
          aria-expanded={menuOpen}
          aria-label={menuOpen ? t('jobs.search.filtersClose') : t('jobs.search.filtersOpen')}
        >
          <FilterIcon size={18} aria-hidden />
          {!isMobile && <Chevron size={16} aria-hidden />}
        </button>
      </div>

      {menuOpen && (
        <div className="jobs-bar-menu">
          <div className="jobs-filters">
            {primaryFilters.map(renderFilter)}

            {moreFilters.length > 0 && (
              <button
                type="button"
                className={`filter-toggle${showMore ? ' is-active' : ''}`}
                onClick={() => setShowMore((s) => !s)}
                aria-pressed={showMore}
              >
                <IconAdjustmentsHorizontal size={15} aria-hidden />
                {t('jobs.filters.more')}
              </button>
            )}
            {showMore && moreFilters.map(renderFilter)}
          </div>

          <div className="jobs-filters-actions">
            <button type="button" className="jobs-filters-clear" onClick={clearFilters}>
              {t('jobs.filters.clear')}
            </button>
            <Button type="submit" variant={ButtonVariant.PRIMARY} size="small">
              {t('jobs.filters.apply')}
            </Button>
          </div>
        </div>
      )}
    </form>
  )
}

export default SearchFilterBar
