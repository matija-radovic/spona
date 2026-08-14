import './SettingsModal.scss'
import type { ReactNode } from 'react'
import { Modal } from '@shared/components'

interface SettingsModalProps {
  isOpen: boolean
  /** Esc, the backdrop and the close "X" call this. */
  onClose: () => void
  title: string
  children: ReactNode
  footer: ReactNode
}

const SettingsModal = ({ isOpen, onClose, title, children, footer }: SettingsModalProps) => (
  <Modal className="settings-modal" isOpen={isOpen} onClose={onClose} title={title} footer={footer}>
    {children}
  </Modal>
)

export default SettingsModal
