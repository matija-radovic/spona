import './AvatarPicker.scss'
import { useRef, useState, type ChangeEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { IconPencil, IconUser } from '@tabler/icons-react'
import { FieldShell } from '../FieldShell'

interface AvatarPickerProps {
  label: string
  /** The stored avatar URL, or '' when none picked. */
  value: string
  onChange: (file: File) => Promise<void>
}

const AvatarPicker = ({ label, value, onChange }: AvatarPickerProps) => {
  const { t } = useTranslation()
  const inputRef = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)

  const onFile = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    event.target.value = '' // allow re-picking the same file
    if (!file) return
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
          accept="image/*"
          className="avatar-picker-file"
          onChange={onFile}
        />
        <span className="avatar-picker-hint">{t('auth.avatarHint')}</span>
      </div>
    </FieldShell>
  )
}

export default AvatarPicker
