import { IconHome2, IconBriefcase2, IconBuildingFactory2, IconUsers } from '@tabler/icons-react'
import type { NavItem } from './MobileNav'

const exact = (path: string) => (pathname: string) => pathname === path

const jobsActive = (pathname: string) => {
  if (pathname === '/jobs') return true
  if (!pathname.startsWith('/jobs/')) return false
  const slug = pathname.slice('/jobs/'.length)
  return slug !== '' && slug !== 'new' && !slug.includes('/')
}

export const GUEST_NAV_ITEMS: readonly NavItem[] = [
  { to: '/', labelKey: 'nav.home', icon: IconHome2, isActive: exact('/') },
  { to: '/jobs', labelKey: 'nav.jobs', icon: IconBriefcase2, isActive: jobsActive },
  { to: '/companies', labelKey: 'nav.companies', icon: IconBuildingFactory2, isActive: exact('/companies'), },
]

export const AUTH_NAV_ITEMS: readonly NavItem[] = [
  { to: '/feed', labelKey: 'nav.home', icon: IconHome2, isActive: exact('/feed') },
  { to: '/jobs', labelKey: 'nav.jobs', icon: IconBriefcase2, isActive: jobsActive },
  { to: '/companies', labelKey: 'nav.companies', icon: IconBuildingFactory2, isActive: exact('/companies'), },
  { to: '/users', labelKey: 'nav.users', icon: IconUsers, isActive: exact('/users') },
]
