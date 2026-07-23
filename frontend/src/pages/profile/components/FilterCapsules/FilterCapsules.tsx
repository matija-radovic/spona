import './FilterCapsules.scss'

export interface CapsuleOption {
  key: string
  label: string
}

interface FilterCapsulesProps {
  options: CapsuleOption[]
  active: string
  onChange: (key: string) => void
  /** Accessible group label. */
  ariaLabel: string
}

/**
 * A dumb, single-select pill group. Shared by the Activity filter (Sve / Objave /
 * Komentari) and the Saved-jobs filter (Aktivni / Završeni / Svi). Callers narrow
 * the emitted key back to their own union.
 */
const FilterCapsules = ({ options, active, onChange, ariaLabel }: FilterCapsulesProps) => (
  <div className="filter-capsules" role="group" aria-label={ariaLabel}>
    {options.map((option) => (
      <button
        key={option.key}
        type="button"
        className={`filter-capsule${option.key === active ? ' is-active' : ''}`}
        aria-pressed={option.key === active}
        onClick={() => onChange(option.key)}
      >
        {option.label}
      </button>
    ))}
  </div>
)

export default FilterCapsules
