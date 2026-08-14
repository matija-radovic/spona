import './SettingsSaveBar.scss'
import { useTranslation } from 'react-i18next'
import { Button, ButtonVariant } from '@shared/components'

interface SettingsSaveBarProps {
  isDirty: boolean
  isSaving: boolean
  /** A section-level failure (a field-level one is pinned to its input instead). */
  error?: string | null
}

const SettingsSaveBar = ({ isDirty, isSaving, error }: SettingsSaveBarProps) => {
  const { t } = useTranslation()

  return (
    <div className="settings-save-bar">
      {error && <p className="settings-save-bar-error">{error}</p>}

      <Button type="submit" variant={ButtonVariant.PRIMARY} disabled={!isDirty || isSaving}>
        {isSaving ? t('common.saving') : t('settings.save')}
      </Button>
    </div>
  )
}

export default SettingsSaveBar
