import './AvatarPicker.scss'
import { useRef, useState, type ChangeEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { IconPencil, IconUser } from '@tabler/icons-react'
import {
  AVATAR_SOURCE_ACCEPTED_TYPES,
  AVATAR_SOURCE_MAX_BYTES,
} from '@utils/constants/validation'
import { FieldShell } from '../FieldShell'

interface AvatarPickerProps {
  label: string
  value: string
  onChange: (file: File) => Promise<void>
}

const AvatarPicker = ({ label, value, onChange }: AvatarPickerProps) => {
  const { t } = useTranslation()
  const inputRef = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const onFile = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    event.target.value = '' // allow re-picking the same file
    if (!file) return

    // The source is decoded in the browser, so it is bounded before that, not after.
    if (!AVATAR_SOURCE_ACCEPTED_TYPES.includes(file.type)) {
      setError(t('errors.invalidFileType'))
      return
    }
    if (file.size > AVATAR_SOURCE_MAX_BYTES) {
      setError(t('errors.fileTooLarge'))
      return
    }

    setError(null)
    setBusy(true)
    try {
      await onChange(file)
    } finally {
      setBusy(false)
    }
  }

  return (
    <FieldShell label={label} htmlLabel={false}>
      <div className="avatar-picker">
        <button
          type="button"
          className="avatar-picker-circle"
          onClick={() => inputRef.current?.click()}
          aria-label={t('auth.avatar')}
          aria-busy={busy}
        >
          {value ? (
            <img src={value} alt="" className="avatar-picker-image" />
          ) : (
            <IconUser className="avatar-picker-placeholder" />
          )}
          <span className="avatar-picker-pencil" aria-hidden="true">
            <IconPencil />
          </span>
        </button>

        <input
          ref={inputRef}
          type="file"
          accept={AVATAR_SOURCE_ACCEPTED_TYPES.join(',')}
          className="avatar-picker-file"
          onChange={onFile}
        />
        <span className="avatar-picker-hint">{error ?? t('auth.avatarHint')}</span>
      </div>
    </FieldShell>
  )
}

export default AvatarPicker
