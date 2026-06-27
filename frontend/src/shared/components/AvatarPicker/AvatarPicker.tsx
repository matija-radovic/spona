import './AvatarPicker.scss'
import { useRef, useState, type ChangeEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { resizeImageToSquare } from '@utils/image/resizeImage'
import { IconPencil, IconUser } from '@tabler/icons-react'

interface AvatarPickerProps {
  label: string
  /** Processed 256×256 data URL, or '' when none picked. */
  value: string
  onChange: (dataUrl: string) => void
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
      onChange(await resizeImageToSquare(file))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="avatar-picker">
      <span className="avatar-picker-label">{label}</span>

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
          <IconUser className="avatar-picker-placeholder" size={44} />
        )}
        <span className="avatar-picker-pencil" aria-hidden="true">
          <IconPencil size={15} />
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
  )
}

export default AvatarPicker
