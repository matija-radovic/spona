import './TagInput.scss'
import { useId, useState, type KeyboardEvent } from 'react'
import { IconX } from '@tabler/icons-react'
import { useFieldContext } from '../FieldShell/fieldContext'

interface TagInputProps {
  value: string[]
  onChange: (next: string[]) => void
  /** Hard cap; the field goes disabled and loses its placeholder once reached. */
  max?: number
  /** Longest one entry may be, in characters. */
  maxLength?: number
  placeholder?: string
  /** Accessible name for a capsule's remove button, built from the entry. */
  removeLabel?: (entry: string) => string
  /** Accessible name when the control stands outside a `FieldShell`. */
  label?: string
  className?: string
}

/** Free text as removable capsules: type, commit on Enter or comma, remove by capsule. */
const TagInput = ({
  value,
  onChange,
  max,
  maxLength,
  placeholder,
  removeLabel,
  label,
  className,
}: TagInputProps) => {
  const field = useFieldContext()
  const generatedId = useId()
  const [draft, setDraft] = useState('')

  const fieldId = field?.controlId ?? generatedId
  const full = max !== undefined && value.length >= max

  const commit = () => {
    const entry = draft.trim()
    setDraft('')
    if (!entry || full) return
    // Case-insensitive, because two spellings of one entry are one entry on the wire.
    if (value.some((held) => held.toLowerCase() === entry.toLowerCase())) return
    onChange([...value, entry])
  }

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault()
      commit()
      return
    }
    // Backspace on an empty draft takes the last capsule back, the way tag inputs do.
    if (event.key === 'Backspace' && draft === '' && value.length > 0) {
      onChange(value.slice(0, -1))
    }
  }

  return (
    <div className={['tag-input', className].filter(Boolean).join(' ')}>
      {value.map((entry) => (
        <span key={entry} className="tag-input-capsule">
          {entry}
          <button
            type="button"
            className="tag-input-remove"
            onClick={() => onChange(value.filter((held) => held !== entry))}
            aria-label={removeLabel ? removeLabel(entry) : entry}
          >
            <IconX />
          </button>
        </span>
      ))}

      <input
        id={fieldId}
        type="text"
        className="tag-input-field"
        value={draft}
        maxLength={maxLength}
        disabled={full}
        onChange={(event) => setDraft(event.target.value)}
        onKeyDown={onKeyDown}
        onBlur={commit}
        placeholder={full ? '' : placeholder}
        aria-label={field ? undefined : label}
        aria-invalid={field?.invalid ?? false}
        aria-describedby={field?.describedBy}
        enterKeyHint="done"
      />
    </div>
  )
}

export default TagInput
