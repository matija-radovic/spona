import { Link, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { IconChevronDown, IconUserCircle, IconSettings, IconLogout } from '@tabler/icons-react'
import { ActorAvatar, ActorAvatarSize } from '@shared/components'
import { useUser } from '@shared/contexts'
import usePopover from '@shared/hooks/usePopover'

/**
 * The desktop avatar button + account menu. This is a menu (not a value-picker),
 * so it is its own component rather than the shared Dropdown - but it keeps the
 * same dismiss discipline. Navigation closes via each item's onClick.
 */
const HeaderUserMenu = () => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { user, logout } = useUser()
  const { open, setOpen, toggle, rootRef } = usePopover()

  if (!user) return null

  const close = () => setOpen(false)

  const onLogout = () => {
    close()
    logout()
    navigate('/')
  }

  return (
    <div className="header-user-menu" ref={rootRef}>
      <button
        type="button"
        className={`header-avatar-btn${open ? ' is-open' : ''}`}
        onClick={toggle}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={t('nav.profileMenu')}
      >
        <ActorAvatar avatarUrl={user.avatarUrl || null} actorType="user" size={ActorAvatarSize.SM} />
        <IconChevronDown className="header-avatar-chevron" aria-hidden />
      </button>

      {open && (
        <div className="header-user-menu-panel" role="menu">
          <Link to={`/users/${user.username}`} className="header-user-menu-item" role="menuitem" onClick={close}>
            <IconUserCircle aria-hidden />
            {t('nav.myProfile')}
          </Link>
          <Link
            to={`/users/${user.username}/settings`}
            className="header-user-menu-item"
            role="menuitem"
            onClick={close}
          >
            <IconSettings aria-hidden />
            {t('nav.settings')}
          </Link>
          <button type="button" className="header-user-menu-item" role="menuitem" onClick={onLogout}>
            <IconLogout aria-hidden />
            {t('nav.logout')}
          </button>
        </div>
      )}
    </div>
  )
}

export default HeaderUserMenu
