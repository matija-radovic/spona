import { useState } from 'react'
import type { Icon } from '@tabler/icons-react'
import HamburgerMenu from './HamburgerMenu'
import MobileNavPanel from './MobileNavPanel'

export interface NavItem {
  to: string
  labelKey: string
  icon: Icon
  // Maps to NavLink's `end`: highlight only on the exact path, not on nested
  // routes. Omit for tabs that should stay lit inside their subtree.
  end?: boolean
}

interface MobileNavProps {
  items: readonly NavItem[]
  isAuthenticated: boolean
}

const MobileNav = ({ items, isAuthenticated }: MobileNavProps) => {
  const [open, setOpen] = useState(false)

  return (
    <>
      <HamburgerMenu isOpen={open} onToggle={() => setOpen((o) => !o)} />
      <MobileNavPanel
        isOpen={open}
        onClose={() => setOpen(false)}
        items={items}
        isAuthenticated={isAuthenticated}
      />
    </>
  )
}

export default MobileNav
