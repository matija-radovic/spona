import './SearchFilterBar.scss'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import {
  IconSearch,
  IconFilter,
  IconFilterOff,
  IconCheck,
  IconAdjustmentsHorizontal,
} from '@tabler/icons-react'
import { Dropdown, Input, InputShape, SelectTrigger } from '@shared/components'
import { Button, ButtonVariant, ButtonSize } from '@shared/components/form/Button'
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

const SearchFilterBar = ({ filters, onSearch, isMobile }: SearchFilterBarProps) => {
  const { t } = useTranslation()
  const { register, control, handleSubmit, reset, getValues } = useForm<FilterState>({
    defaultValues: filters,
  })
  const [menuOpen, setMenuOpen] = useState(false)
  const [showMore, setShowMore] = useState(false)

  const primaryFilters = FILTERS.slice(0, isMobile ? Math.min(2, PRIMARY_FILTER_COUNT) : PRIMARY_FILTER_COUNT)
  const moreFilters = FILTERS.slice(primaryFilters.length)

  const onSubmit = handleSubmit((values) => {
    onSearch(values)
    setMenuOpen(false)
  })

  // Clear every filter but keep the free-text query and the carried-through role.
  // keepDefaultValues leaves the "since URL" baseline intact.
  const clearFilters = () => {
    const { q, role } = getValues()
    reset({ ...emptyFilters(), q, role }, { keepDefaultValues: true })
  }

  const renderFilter = (config: FilterConfig) => {
    const label = t(config.labelKey)

    if (config.kind === 'toggle') {
      return (
        <Controller
          key={config.key}
          control={control}
          name="salary"
          render={({ field }) => (
            <button
              type="button"
              className={`filter-toggle${field.value ? ' is-active' : ''}`}
              onClick={() => field.onChange(!field.value)}
              aria-pressed={field.value}
            >
              <span className="filter-toggle-box" aria-hidden>
                {field.value && <IconCheck />}
              </span>
              {label}
            </button>
          )}
        />
      )
    }

    const options = (config.options ?? []).map((o) => ({ value: o.value, label: t(o.labelKey) }))

    if (config.kind === 'single') {
      const key = config.key as 'posted' | 'sort'
      return (
        <Controller
          key={config.key}
          control={control}
          name={key}
          render={({ field }) => (
            <Dropdown
              label={label}
              options={options}
              value={field.value ?? ''}
              placeholder={label}
              // Selecting the empty ("any"/"recommended") option clears to null.
              onChange={(next) => field.onChange(typeof next === 'string' && next !== '' ? next : null)}
            />
          )}
        />
      )
    }

    const key = config.key as 'workplace' | 'employment' | 'hours' | 'experience'
    return (
      <Controller
        key={config.key}
        control={control}
        name={key}
        render={({ field }) => (
          <Dropdown
            multiple
            label={label}
            options={options}
            value={field.value}
            placeholder={`${label}: ${t('jobs.filters.all')}`}
            onChange={(next) => field.onChange(next as string[])}
          />
        )}
      />
    )
  }

  const FilterIcon = isMobile ? (menuOpen ? IconFilterOff : IconFilter) : IconFilter

  return (
    <form className="jobs-bar" onSubmit={onSubmit} role="search">
      <div className="jobs-bar-row">
        <Input
          className="jobs-bar-search"
          shape={InputShape.PILL}
          leading={<IconSearch aria-hidden />}
          type="search"
          placeholder={t('jobs.search.placeholder')}
          enterKeyHint="search"
          aria-label={t('jobs.search.placeholder')}
          {...register('q')}
        />

        <SelectTrigger
          className="jobs-bar-filter-btn"
          open={menuOpen}
          active={menuOpen}
          chevron={!isMobile}
          leading={<FilterIcon aria-hidden />}
          onClick={() => setMenuOpen((o) => !o)}
          aria-label={menuOpen ? t('jobs.search.filtersClose') : t('jobs.search.filtersOpen')}
        />
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
                <IconAdjustmentsHorizontal aria-hidden />
                {t('jobs.filters.more')}
              </button>
            )}
            {showMore && moreFilters.map(renderFilter)}
          </div>

          <div className="jobs-filters-actions">
            <Button
              variant={ButtonVariant.GHOST}
              size={ButtonSize.SMALL}
              className="jobs-filters-clear"
              onClick={clearFilters}
            >
              {t('jobs.filters.clear')}
            </Button>
            <Button type="submit" variant={ButtonVariant.PRIMARY} size={ButtonSize.SMALL}>
              {t('jobs.filters.apply')}
            </Button>
          </div>
        </div>
      )}
    </form>
  )
}

export default SearchFilterBar
