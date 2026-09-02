import './KeywordInput.scss'
import { useState, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { IconX } from '@tabler/icons-react'
import { JOB_KEYWORD_MAX, JOB_KEYWORDS_MAX } from '@utils/constants/validation'

interface KeywordInputProps {
  label: string
  value: string[]
  onChange: (next: string[]) => void
  max?: number
  error?: string
}

/** Free-text tags: type, commit on Enter or comma, remove by capsule. */
const KeywordInput = ({ label, value, onChange, max = JOB_KEYWORDS_MAX, error }: KeywordInputProps) => {
  const { t } = useTranslation()
  const [draft, setDraft] = useState('')

  const full = value.length >= max

  const commit = () => {
    const keyword = draft.trim()
    setDraft('')
    if (!keyword || full) return
    // Case-insensitive, because two spellings of one tag are one tag on the wire.
    if (value.some((entry) => entry.toLowerCase() === keyword.toLowerCase())) return
    onChange([...value, keyword])
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
    <div className="keyword-input">
      <span className="keyword-input-label">{label}</span>

      <div className={`keyword-input-box${error ? ' has-error' : ''}`}>
        {value.map((keyword) => (
          <span key={keyword} className="keyword-input-capsule">
            {keyword}
            <button
              type="button"
              className="keyword-input-remove"
              onClick={() => onChange(value.filter((entry) => entry !== keyword))}
              aria-label={t('jobs.create.keywordRemove', { keyword })}
            >
              <IconX />
            </button>
          </span>
        ))}

        <input
          type="text"
          className="keyword-input-field"
          value={draft}
          maxLength={JOB_KEYWORD_MAX}
          disabled={full}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={onKeyDown}
          onBlur={commit}
          placeholder={full ? '' : t('jobs.create.keywordPlaceholder')}
          aria-label={label}
          enterKeyHint="done"
        />
      </div>

      <p className={`keyword-input-hint${error ? ' has-error' : ''}`}>
        {error ?? t('jobs.create.keywordsHint', { max })}
      </p>
    </div>
  )
}

export default KeywordInput
