import { useTranslation } from 'react-i18next'
import { Button, ButtonVariant } from '@shared/components/form/Button'
import { Modal } from '@shared/components/overlay/Modal'
import type { UnsavedGuard } from '@shared/hooks/useUnsavedGuard'

interface UnsavedChangesModalProps {
  guard: UnsavedGuard
  /** Primary control label */
  saveLabel?: string
}

/** Esc and the backdrop mean "stay": they leave the edits intact and send nothing. */
const UnsavedChangesModal = ({ guard, saveLabel }: UnsavedChangesModalProps) => {
  const { t } = useTranslation()

  return (
    <Modal
      isOpen={guard.isOpen}
      onClose={guard.stay}
      title={t('common.unsaved.title')}
      footer={
        <>
          <Button variant={ButtonVariant.GHOST} onClick={guard.discard} disabled={guard.isSaving}>
            {t('common.unsaved.discard')}
          </Button>
          <Button variant={ButtonVariant.PRIMARY} onClick={guard.save} disabled={guard.isSaving}>
            {guard.isSaving ? t('common.saving') : (saveLabel ?? t('common.saveChanges'))}
          </Button>
        </>
      }
    >
      <p>{t('common.unsaved.body')}</p>
    </Modal>
  )
}

export default UnsavedChangesModal
