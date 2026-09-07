import './FilterCapsules.scss'
import { Button, ButtonVariant } from '@shared/components/form/Button'

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
      <Button
        key={option.key}
        variant={option.key === active ? ButtonVariant.PRIMARY : ButtonVariant.TERTIARY}
        className="filter-capsule"
        aria-pressed={option.key === active}
        onClick={() => onChange(option.key)}
      >
        {option.label}
      </Button>
    ))}
  </div>
)

export default FilterCapsules
