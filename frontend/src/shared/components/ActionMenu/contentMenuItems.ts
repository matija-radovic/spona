import { IconLink, IconPencil, IconTrash } from '@tabler/icons-react'
import { copyToClipboard } from '@utils/copyToClipboard'
import type { ActionMenuItem } from './ActionMenu'

interface ContentMenuOptions {
  /** Path on this origin - the permalink, e.g. `/posts/123`. */
  path: string
  copyLabel: string
  editLabel: string
  deleteLabel: string
  /** Gated independently: the backend grants them together today, but need not. */
  canEdit?: boolean
  canDelete?: boolean
  onEdit: () => void
  onDelete: () => void
}

export const contentMenuItems = ({
  path,
  copyLabel,
  editLabel,
  deleteLabel,
  canEdit,
  canDelete,
  onEdit,
  onDelete,
}: ContentMenuOptions): ActionMenuItem[] => {
  const items: ActionMenuItem[] = [
    {
      key: 'copy',
      label: copyLabel,
      icon: IconLink,
      onSelect: () => {
        void copyToClipboard(`${window.location.origin}${path}`)
      },
    },
  ]

  if (canEdit) items.push({ key: 'edit', label: editLabel, icon: IconPencil, onSelect: onEdit })
  if (canDelete) {
    items.push({ key: 'delete', label: deleteLabel, icon: IconTrash, onSelect: onDelete, danger: true })
  }

  return items
}
