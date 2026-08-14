import './SettingsModal.scss'
import { useEffect, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { AnimatePresence, motion } from 'motion/react'
import { useTranslation } from 'react-i18next'
import { IconX } from '@tabler/icons-react'

const fade = {
  initial: { opacity: 0 },
  animate: { opacity: 1 },
  exit: { opacity: 0 },
  transition: { duration: 0.1, ease: 'easeOut' },
} as const

interface SettingsModalProps {
  isOpen: boolean
  /** Esc, the backdrop and the close "X" call this. */
  onClose: () => void
  title: string
  children: ReactNode
  footer: ReactNode
}

const SettingsModal = ({ isOpen, onClose, title, children, footer }: SettingsModalProps) => {
  const { t } = useTranslation()

  useEffect(() => {
    if (!isOpen) return

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }

    window.addEventListener('keydown', onKeyDown)
    document.body.style.overflow = 'hidden'

    return () => {
      window.removeEventListener('keydown', onKeyDown)
      document.body.style.overflow = ''
    }
  }, [isOpen, onClose])

  return createPortal(
    <AnimatePresence>
      {isOpen && (
        <motion.div className="settings-modal">
          <motion.div className="settings-modal-backdrop" onClick={onClose} {...fade} />

          <motion.div
            className="settings-modal-panel"
            role="dialog"
            aria-modal="true"
            aria-label={title}
            onClick={(event) => event.stopPropagation()}
            {...fade}
          >
            <header className="settings-modal-head">
              <h4 className="settings-modal-title">{title}</h4>
              <button
                type="button"
                className="settings-modal-close"
                onClick={onClose}
                aria-label={t('common.close')}
              >
                <IconX aria-hidden />
              </button>
            </header>

            <div className="settings-modal-body">{children}</div>

            <footer className="settings-modal-footer">{footer}</footer>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>,
    document.body,
  )
}

export default SettingsModal
