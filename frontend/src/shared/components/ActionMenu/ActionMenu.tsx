import './ActionMenu.scss'
import { useCallback, useEffect, useRef, useState, type KeyboardEvent as ReactKeyboardEvent } from 'react'
import { useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { IconDots } from '@tabler/icons-react'
import type { Icon } from '@tabler/icons-react'
import usePopoverFlip from '@shared/hooks/usePopoverFlip'
import { MenuAlign } from './menuAlign'

export interface ActionMenuItem {
  key: string
  label: string
  icon: Icon
  onSelect: () => void
  /** Destructive: danger text, danger hover wash. */
  danger?: boolean
}

interface ActionMenuProps {
  items: readonly ActionMenuItem[]
  /** Preferred side; the panel flips at a viewport edge. */
  align?: MenuAlign
  /** Accessible name of the trigger and the menu. */
  label?: string
  className?: string
}

const ITEM_SELECTOR = '[role="menuitem"]'

const ActionMenu = ({ items, align = MenuAlign.END, label, className }: ActionMenuProps) => {
  const { t } = useTranslation()
  const location = useLocation()
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const panelNode = useRef<HTMLUListElement | null>(null)

  const { above, align: openAlign, panelRef } = usePopoverFlip(open, triggerRef, { align })

  // Close on a route change, adjusted during render rather than in an effect:
  // this eslint config errors on `setState` in an effect body.
  const [menuPath, setMenuPath] = useState(location.pathname)
  if (menuPath !== location.pathname) {
    setMenuPath(location.pathname)
    setOpen(false)
  }

  // Focus the first item the instant the panel commits - a callback ref, not an
  // effect, so the panel is never briefly focusless.
  const setPanel = useCallback(
    (node: HTMLUListElement | null) => {
      panelNode.current = node
      panelRef(node)
      node?.querySelector<HTMLElement>(ITEM_SELECTOR)?.focus()
    },
    [panelRef],
  )

  useEffect(() => {
    if (!open) return
    const onPointerDown = (event: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onPointerDown)
    return () => document.removeEventListener('mousedown', onPointerDown)
  }, [open])

  if (items.length === 0) return null

  const close = (restoreFocus: boolean) => {
    setOpen(false)
    if (restoreFocus) triggerRef.current?.focus()
  }

  const moveFocus = (step: number | 'first' | 'last') => {
    const nodes = Array.from(panelNode.current?.querySelectorAll<HTMLElement>(ITEM_SELECTOR) ?? [])
    if (nodes.length === 0) return
    const current = nodes.indexOf(document.activeElement as HTMLElement)
    const next =
      step === 'first'
        ? 0
        : step === 'last'
          ? nodes.length - 1
          : (current + step + nodes.length) % nodes.length
    nodes[next]?.focus()
  }

  const onPanelKeyDown = (event: ReactKeyboardEvent) => {
    switch (event.key) {
      case 'Escape':
        event.preventDefault()
        close(true)
        break
      case 'ArrowDown':
        event.preventDefault()
        moveFocus(1)
        break
      case 'ArrowUp':
        event.preventDefault()
        moveFocus(-1)
        break
      case 'Home':
        event.preventDefault()
        moveFocus('first')
        break
      case 'End':
        event.preventDefault()
        moveFocus('last')
        break
    }
  }

  const menuLabel = label ?? t('common.actions')
  const rootClass = ['action-menu', open ? 'is-open' : '', className ?? ''].filter(Boolean).join(' ')

  return (
    <div className={rootClass} ref={rootRef}>
      <button
        type="button"
        ref={triggerRef}
        className="action-menu-trigger"
        onClick={(event) => {
          event.stopPropagation()
          setOpen((o) => !o)
        }}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={menuLabel}
      >
        <IconDots aria-hidden />
      </button>

      {open && (
        <ul
          ref={setPanel}
          className={`action-menu-panel action-menu-${openAlign}${above ? ' is-above' : ''}`}
          role="menu"
          aria-label={menuLabel}
          onKeyDown={onPanelKeyDown}
        >
          {items.map((item) => {
            const ItemIcon = item.icon
            return (
              <li key={item.key}>
                <button
                  type="button"
                  role="menuitem"
                  tabIndex={-1}
                  className={`action-menu-item${item.danger ? ' is-danger' : ''}`}
                  onClick={(event) => {
                    event.stopPropagation()
                    close(false)
                    item.onSelect()
                  }}
                >
                  <ItemIcon aria-hidden />
                  <span className="action-menu-item-label">{item.label}</span>
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

export default ActionMenu
