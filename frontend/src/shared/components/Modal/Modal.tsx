import './Modal.scss'
import type { ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { AnimatePresence, motion } from 'motion/react'
import { useTranslation } from 'react-i18next'
import { IconX } from '@tabler/icons-react'
import useModalLayer from '@shared/hooks/useModalLayer'

const fade = {
  initial: { opacity: 0 },
  animate: { opacity: 1 },
  exit: { opacity: 0 },
  transition: { duration: 0.1, ease: 'easeOut' },
} as const

interface ModalProps {
  isOpen: boolean
  /** Esc, the backdrop and the close "X" call this. */
  onClose: () => void
  children: ReactNode
  title?: string
  /** Action row pinned under the body, omitted entirely when not given. */
  footer?: ReactNode
  /** Lands on the overlay root, so a host can scope its own rules from there. */
  className?: string
  showClose?: boolean
  /** Falls back to `title`, pass it when the dialog shows no visible title. */
  ariaLabel?: string
}

const Modal = ({
  isOpen,
  onClose,
  children,
  title,
  footer,
  className,
  showClose = true,
  ariaLabel,
}: ModalProps) => {
  const { t } = useTranslation()
  const layerRef = useModalLayer(onClose)

  return createPortal(
    <AnimatePresence>
      {isOpen && (
        <motion.div ref={layerRef} className={['modal', className].filter(Boolean).join(' ')}>
          <motion.div className="modal-backdrop" onClick={onClose} {...fade} />

          <motion.div
            className="modal-panel"
            role="dialog"
            aria-modal="true"
            aria-label={ariaLabel ?? title}
            onClick={(event) => event.stopPropagation()}
            {...fade}
          >
            {(title || showClose) && (
              <header className="modal-head">
                {title && <h4 className="modal-title">{title}</h4>}
                {showClose && (
                  <button
                    type="button"
                    className="modal-close"
                    onClick={onClose}
                    aria-label={t('common.close')}
                  >
                    <IconX aria-hidden />
                  </button>
                )}
              </header>
            )}

            <div className="modal-body">{children}</div>

            {footer && <footer className="modal-footer">{footer}</footer>}
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>,
    document.body,
  )
}

export default Modal
