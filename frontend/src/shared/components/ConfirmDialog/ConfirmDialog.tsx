import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, ButtonVariant } from '@shared/components/Button'
import { Modal } from '@shared/components/Modal'

interface ConfirmDialogProps {
  isOpen: boolean
  onClose: () => void
  title: string
  className?: string

  onConfirm: () => void
  body?: ReactNode
  confirmLabel: string
  /** Defaults to `common.cancel`. */
  cancelLabel?: string
  /** Paints the confirm button red*/
  danger?: boolean
  /** Disables both controls while the action is in flight. */
  busy?: boolean
}

const ConfirmDialog = ({
  isOpen,
  onClose,
  onConfirm,
  title,
  body,
  confirmLabel,
  cancelLabel,
  danger = false,
  busy = false,
  className,
}: ConfirmDialogProps) => {
  const { t } = useTranslation()

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={title}
      className={className}
      footer={
        <>
          <Button variant={ButtonVariant.QUATERNARY} onClick={onClose} disabled={busy}>
            {cancelLabel ?? t('common.cancel')}
          </Button>
          <Button
            variant={danger ? ButtonVariant.DANGER : ButtonVariant.PRIMARY}
            onClick={onConfirm}
            disabled={busy}
          >
            {confirmLabel}
          </Button>
        </>
      }
    >
      {body}
    </Modal>
  )
}

export default ConfirmDialog
