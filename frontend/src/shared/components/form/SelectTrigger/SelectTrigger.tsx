import './SelectTrigger.scss'
import { forwardRef, type ComponentPropsWithoutRef, type ReactNode } from 'react'
import { IconChevronDown } from '@tabler/icons-react'
import { truncateText } from '@utils/truncateText'

interface SelectTriggerOwnProps {
  /** Drives `aria-expanded` and the chevron's rotation. */
  open: boolean
  /** Accent capsule: the control is carrying a value other than its default. */
  active?: boolean
  /** A node before the label: an avatar, a filter glyph. */
  leading?: ReactNode
  /** False for a trigger that reads as an icon button rather than a picker. */
  chevron?: boolean
  /** Word-boundary cut for the label, when the label is a plain string. */
  maxLabelChars?: number
  /** The label; absent for an icon-only trigger. */
  children?: ReactNode
}

type SelectTriggerProps = SelectTriggerOwnProps & ComponentPropsWithoutRef<'button'>

/** The one capsule button that opens something. */
const SelectTrigger = forwardRef<HTMLButtonElement, SelectTriggerProps>(
  (
    { open, active, leading, chevron = true, maxLabelChars, className, children, ...rest },
    ref,
  ) => {
    const label =
      maxLabelChars && typeof children === 'string'
        ? truncateText(children, maxLabelChars).text
        : children

    const rootClass = ['select-trigger', active ? 'is-active' : '', className ?? '']
      .filter(Boolean)
      .join(' ')

    return (
      <button type="button" ref={ref} className={rootClass} aria-expanded={open} {...rest}>
        {leading}
        {label != null && <span className="select-trigger-label">{label}</span>}
        {chevron && <IconChevronDown className="select-trigger-chevron" aria-hidden />}
      </button>
    )
  },
)

SelectTrigger.displayName = 'SelectTrigger'

export default SelectTrigger
