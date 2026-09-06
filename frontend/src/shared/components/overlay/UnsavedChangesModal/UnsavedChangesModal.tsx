import { useTranslation } from 'react-i18next'
import { Button, ButtonVariant } from '@shared/components/form/Button'
import { Modal } from '@shared/components/overlay/Modal'
import type { UnsavedGuard } from '@shared/hooks/useUnsavedGuard'

interface UnsavedChangesModalProps {
  guard: UnsavedGuard
}

/** Esc and the backdrop mean "stay": they leave the edits intact and send nothing. */
const UnsavedChangesModal = ({ guard }: UnsavedChangesModalProps) => {
  const { t } = useTranslation()

  return (
    <Modal
      isOpen={guard.isOpen}
      onClose={guard.stay}
      title={t('settings.unsaved.title')}
      footer={
        <>
          <Button variant={ButtonVariant.GHOST} onClick={guard.discard} disabled={guard.isSaving}>
            {t('settings.unsaved.discard')}
          </Button>
          <Button variant={ButtonVariant.PRIMARY} onClick={guard.save} disabled={guard.isSaving}>
            {guard.isSaving ? t('common.saving') : t('settings.save')}
          </Button>
        </>
      }
    >
      <p>{t('settings.unsaved.body')}</p>
    </Modal>
  )
}

export default UnsavedChangesModal
